#!/usr/bin/env python3
"""
End-to-end positive / negative scenario suite for the whole Mini's n More stack.

Covers gateway auth, storefront catalogue, customer account, checkout, cancel / return /
exchange, admin catalogue + operations, RBAC, security, webhooks and storefront pages.

Safe for UAT by default (API_TEST_NO_MAIL=true): anything that would email a customer or
staff via Care@minisnmore.com, move money, or pull live stock is recorded as SKIP with the
reason. Negative cases only target inputs / states that the backend rejects before any
side effect. Every record created by a positive case is cleaned up in the same run.

Writes JSON + HTML + JUnit under artifacts/e2e-scenarios/.
"""

from __future__ import annotations

import base64
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable
from xml.sax.saxutils import escape as xml_escape

ROOT = Path(__file__).resolve().parent.parent.parent
OUT_DIR = Path(os.environ.get("E2E_REPORT_DIR", ROOT / "artifacts/e2e-scenarios"))
OUT_JSON = OUT_DIR / "e2e-scenarios-report.json"
OUT_HTML = OUT_DIR / "index.html"
OUT_JUNIT = OUT_DIR / "e2e-scenarios-junit.xml"

BASE = os.environ.get("API_TEST_BASE_URL", "http://127.0.0.1:9180").rstrip("/")
FRONTEND = os.environ.get("FRONTEND_BASE_URL", "http://127.0.0.1:3000").rstrip("/")
CUSTOMER_USER = os.environ.get("E2E_CUSTOMER_USER", "irfanpatan9948@gmail.com")
CUSTOMER_PASS = os.environ.get("E2E_CUSTOMER_PASS", "Zero@1234")
ADMIN_USER = os.environ.get("E2E_ADMIN_USER", os.environ.get("API_LIFECYCLE_ADMIN_USER", "admin@uat.minisnmore.com"))
ADMIN_PASS = os.environ.get("E2E_ADMIN_PASS", os.environ.get("API_LIFECYCLE_ADMIN_PASS", os.environ.get("UAT_ADMIN_PASSWORD", "")))
ADMIN_TOKEN = os.environ.get("E2E_ADMIN_TOKEN", "")
NO_MAIL = os.environ.get("API_TEST_NO_MAIL", "true").lower() not in ("0", "false", "no")
# Creates unpaid online orders (Razorpay order only, no email) and fails them via a bad signature.
ALLOW_ORDER_WRITES = os.environ.get("E2E_ALLOW_ORDER_WRITES", "true").lower() not in ("0", "false", "no")

TS = str(int(time.time()))
CTX = ssl.create_default_context()

SKIP_MAIL = "no-mail mode: this step emails the customer/staff via Care@minisnmore.com"
SKIP_MONEY = "moves money (live Razorpay refund / capture) — manual UAT only"
SKIP_STATE = "needs a delivered, paid order inside the 3-day window — manual UAT only"


# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):  # noqa: ANN002, ANN003
        return None


_NO_REDIRECT_OPENER = urllib.request.build_opener(_NoRedirect, urllib.request.HTTPSHandler(context=CTX))


@dataclass
class Resp:
    code: int
    body: Any
    ms: int
    headers: dict[str, str] = field(default_factory=dict)

    @property
    def data(self) -> Any:
        if isinstance(self.body, dict) and "data" in self.body and "success" in self.body:
            return self.body["data"]
        return self.body

    def detail(self) -> str:
        if isinstance(self.body, dict):
            for key in ("detail", "error", "message", "title"):
                if self.body.get(key):
                    return str(self.body[key])[:160]
            data = self.body.get("data")
            if isinstance(data, dict):
                for key in ("error", "message"):
                    if data.get(key):
                        return str(data[key])[:160]
        if isinstance(self.body, str):
            return self.body.strip()[:120]
        return ""


def http(
    method: str,
    url: str,
    *,
    token: str | None = None,
    body: Any = None,
    raw: bytes | None = None,
    headers: dict[str, str] | None = None,
    follow_redirects: bool = True,
    timeout: int = 60,
) -> Resp:
    hdrs = {"Accept": "application/json"}
    if token:
        hdrs["Authorization"] = f"Bearer {token}"
    data = raw
    if body is not None:
        hdrs["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    elif raw is not None:
        hdrs.setdefault("Content-Type", "application/json")
    hdrs.update(headers or {})
    req = urllib.request.Request(url, data=data, method=method.upper(), headers=hdrs)
    started = time.perf_counter()
    opener = urllib.request.urlopen if follow_redirects else _NO_REDIRECT_OPENER.open
    try:
        kwargs = {"context": CTX} if follow_redirects else {}
        with opener(req, timeout=timeout, **kwargs) as r:
            text = r.read().decode(errors="replace")
            code, resp_headers = r.status, dict(r.headers)
    except urllib.error.HTTPError as e:
        text = e.read().decode(errors="replace")
        code, resp_headers = e.code, dict(e.headers or {})
    except Exception as exc:  # noqa: BLE001
        return Resp(0, {"error": str(exc)[:200]}, round((time.perf_counter() - started) * 1000))
    ms = round((time.perf_counter() - started) * 1000)
    try:
        parsed: Any = json.loads(text) if text else None
    except json.JSONDecodeError:
        parsed = text
    return Resp(code, parsed, ms, {k.lower(): v for k, v in resp_headers.items()})


def api(method: str, path: str, token: str | None = None, body: Any = None, **kw: Any) -> Resp:
    return http(method, BASE + path, token=token, body=body, **kw)


def login(username: str, password: str) -> str | None:
    r = api("POST", "/api/authenticate", body={"username": username, "password": password, "rememberMe": True})
    return r.body.get("id_token") if r.code == 200 and isinstance(r.body, dict) else None


# ---------------------------------------------------------------------------
# Case registry
# ---------------------------------------------------------------------------


@dataclass
class Case:
    id: str
    module: str
    kind: str  # positive | negative
    priority: str
    title: str
    expected: str
    status: str = "skip"  # pass | fail | skip
    actual: str = ""
    ms: int = 0
    request: str = ""
    steps: list[str] = field(default_factory=list)


class Suite:
    def __init__(self) -> None:
        self.cases: list[Case] = []
        self._seq: dict[tuple[str, str], int] = {}

    def _next_id(self, module_code: str, kind: str) -> str:
        key = (module_code, kind)
        self._seq[key] = self._seq.get(key, 0) + 1
        return f"E2E-{module_code}-{'P' if kind == 'positive' else 'N'}{self._seq[key]:02d}"

    def add(self, module: str, code: str, kind: str, priority: str, title: str, expected: str) -> Case:
        case = Case(self._next_id(code, kind), module, kind, priority, title, expected)
        self.cases.append(case)
        return case

    def skip(self, module: str, code: str, kind: str, priority: str, title: str, expected: str, reason: str) -> Case:
        case = self.add(module, code, kind, priority, title, expected)
        case.status, case.actual = "skip", reason
        case.steps = [f"Perform the manual scenario: {title}.", f"Verify {expected}."]
        return case

    def run(
        self,
        module: str,
        code: str,
        kind: str,
        priority: str,
        title: str,
        expected: str,
        fn: Callable[[], tuple[bool | None, str, Resp | None]],
        request: str = "",
    ) -> Case:
        """fn returns (ok, actual, resp); ok=None means skip."""
        case = self.add(module, code, kind, priority, title, expected)
        case.request = request
        case.steps = [f"Execute {request}." if request else f"Perform the scenario: {title}.", f"Verify {expected}."]
        try:
            ok, actual, resp = fn()
        except Exception as exc:  # noqa: BLE001
            ok, actual, resp = False, f"runner error: {exc}"[:240], None
        case.status = "skip" if ok is None else ("pass" if ok else "fail")
        case.actual = actual
        case.ms = resp.ms if resp else 0
        return case

    def expect(
        self,
        module: str,
        code: str,
        kind: str,
        priority: str,
        title: str,
        expected: str,
        method: str,
        path: str,
        *,
        token: str | None = None,
        body: Any = None,
        raw: bytes | None = None,
        headers: dict[str, str] | None = None,
        status: int | tuple[int, ...] = 200,
        check: Callable[[Resp], tuple[bool, str]] | None = None,
        base: str | None = None,
        follow_redirects: bool = True,
    ) -> Resp | None:
        holder: dict[str, Resp] = {}
        allowed = status if isinstance(status, tuple) else (status,)

        def fn() -> tuple[bool, str, Resp]:
            r = http(
                method,
                (base or BASE) + path,
                token=token,
                body=body,
                raw=raw,
                headers=headers,
                follow_redirects=follow_redirects,
            )
            holder["r"] = r
            note = f"HTTP {r.code}"
            if r.detail():
                note += f" · {r.detail()}"
            if r.code not in allowed:
                return False, note, r
            if check:
                ok, extra = check(r)
                return ok, f"{note} · {extra}" if extra else note, r
            return True, note, r

        case = self.run(module, code, kind, priority, title, expected, fn, request=f"{method} {path}")
        actor = "an authenticated user" if token else "an anonymous client"
        case.steps = [f"As {actor}, send {method} {path}.", f"Verify {expected}."]
        return holder.get("r")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def as_list(value: Any, *keys: str) -> list:
    if isinstance(value, list):
        return value
    if isinstance(value, dict):
        for key in keys:
            if isinstance(value.get(key), list):
                return value[key]
    return []


def contains_key(obj: Any, names: set[str]) -> bool:
    if isinstance(obj, dict):
        return any(k in names or contains_key(v, names) for k, v in obj.items())
    if isinstance(obj, list):
        return any(contains_key(v, names) for v in obj)
    return False


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def forge_admin_token(token: str) -> str:
    """Same signature, payload rewritten to claim ROLE_ADMIN."""
    header, payload, signature = token.split(".")
    claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    claims["auth"] = "ROLE_ADMIN ROLE_SUPER_ADMIN"
    return f"{header}.{b64url(json.dumps(claims).encode())}.{signature}"


def alg_none_token(token: str) -> str:
    payload = token.split(".")[1]
    return f"{b64url(json.dumps({'alg': 'none', 'typ': 'JWT'}).encode())}.{payload}."


def not_5xx(r: Resp) -> tuple[bool, str]:
    return r.code < 500 and r.code != 0, ""


def sellable_line(max_candidates: int = 25) -> dict[str, Any] | None:
    listing = api("GET", "/api/v1/products")
    for product in as_list(listing.data)[:max_candidates]:
        slug = product.get("slug")
        if not slug:
            continue
        detail = api("GET", f"/api/v1/products/{slug}")
        p = detail.data if detail.code == 200 and isinstance(detail.data, dict) else None
        if not p:
            continue
        line = {"product_id": str(p.get("id")), "product_slug": slug, "product_name": p.get("name") or slug, "qty": 1}
        structure = str(p.get("itemStructure") or "SIMPLE").upper()
        raw = p.get("variantsJson") or "[]"
        try:
            variants = json.loads(raw) if isinstance(raw, str) else raw
        except json.JSONDecodeError:
            variants = []
        if structure == "VARIANTS":
            row = next(
                (v for v in variants or [] if isinstance(v, dict) and v.get("active") is not False and int(v.get("stock") or 0) > 0),
                None,
            )
            if not row:
                continue
            price = float(row.get("rate") or 0) or round(float(row.get("mrp") or 0) * 0.8, 2)
            line.update(size=row.get("size") or "", color=row.get("color") or "", sku=row.get("sku") or "")
            stock = int(row.get("stock") or 0)
        else:
            price = float(p.get("price") or 0)
            stock = int(p.get("stock") or 0)
            if stock < 1:
                continue
        if price <= 0:
            continue
        line["price"] = price
        line["_stock"] = stock
        return line
    return None


def shipping_address(**overrides: Any) -> dict[str, Any]:
    address = {
        "full_name": "Scenario Tester",
        "email": CUSTOMER_USER,
        "phone": "9876543210",
        "line1": "12 Test Street",
        "line2": "",
        "city": "Hyderabad",
        "state": "Telangana",
        "pincode": "500001",
        "baby_name": "",
    }
    address.update(overrides)
    return address


def order_body(line: dict[str, Any], **overrides: Any) -> dict[str, Any]:
    item = {k: v for k, v in line.items() if not k.startswith("_")}
    body = {
        "items": [item],
        "shipping_address": shipping_address(),
        "shipping_mode": "standard",
        "payment_method": "online",
        "donation": 0,
    }
    body.update(overrides)
    return body


# ---------------------------------------------------------------------------
# Modules
# ---------------------------------------------------------------------------


def auth_module(s: Suite, cust: str | None, admin: str | None) -> None:
    m, c = "Authentication & accounts (gateway)", "AUTH"
    s.run(m, c, "positive", "P0", "Customer logs in with valid credentials", "200 + JWT id_token",
          lambda: (bool(cust), "JWT issued" if cust else "login failed", None), "POST /api/authenticate")
    s.run(m, c, "positive", "P0", "Admin logs in with valid credentials", "200 + JWT id_token",
          lambda: (None, "admin credentials not set", None) if not (ADMIN_PASS or ADMIN_TOKEN) else (bool(admin), "JWT available" if admin else "login failed", None),
          "POST /api/authenticate")
    if cust:
        s.expect(m, c, "positive", "P0", "Signed-in customer can read own account", "200 with matching email",
                 "GET", "/api/account", token=cust,
                 check=lambda r: (CUSTOMER_USER.lower() in (str(r.body.get("email", "")).lower(), str(r.body.get("login", "")).lower()),
                                  f"login={r.body.get('login')}"))
        s.expect(m, c, "positive", "P1", "Token check endpoint returns current login", "200/204",
                 "GET", "/api/authenticate", token=cust, status=(200, 204))
    if admin:
        s.expect(m, c, "positive", "P1", "Admin lists users", "200 list", "GET", "/api/admin/users", token=admin,
                 check=lambda r: (isinstance(r.body, list), f"{len(r.body) if isinstance(r.body, list) else 0} users"))
        s.expect(m, c, "positive", "P1", "Admin lists authorities (roles)", "200 includes ROLE_ADMIN", "GET", "/api/authorities",
                 token=admin, check=lambda r: (any(a.get("name") == "ROLE_ADMIN" for a in r.body or []), ""))

    s.expect(m, c, "negative", "P0", "Login with wrong password rejected", "401", "POST", "/api/authenticate",
             body={"username": CUSTOMER_USER, "password": "wrong-password-e2e"}, status=401)
    s.expect(m, c, "negative", "P0", "Login with unknown user rejected", "401", "POST", "/api/authenticate",
             body={"username": f"nobody-{TS}@example.invalid", "password": "Whatever@123"}, status=401)
    s.expect(m, c, "negative", "P1", "Login with empty credentials rejected", "400/401", "POST", "/api/authenticate",
             body={"username": "", "password": ""}, status=(400, 401))
    s.expect(m, c, "negative", "P2", "Login with malformed JSON rejected", "400", "POST", "/api/authenticate",
             raw=b"{not-json", status=(400, 415))
    s.expect(m, c, "negative", "P0", "Account without token rejected", "401", "GET", "/api/account", status=401)
    s.expect(m, c, "negative", "P0", "Account with garbage bearer token rejected", "401", "GET", "/api/account",
             token="not.a.jwt", status=401)
    if cust:
        s.expect(m, c, "negative", "P0", "Forged JWT (payload edited to ROLE_ADMIN) rejected", "401 on admin API",
                 "GET", "/api/v1/admin/dashboard", token=forge_admin_token(cust), status=(401, 403))
        s.expect(m, c, "negative", "P0", "Unsigned JWT (alg=none) rejected", "401", "GET", "/api/account",
                 token=alg_none_token(cust), status=401)
    s.expect(m, c, "negative", "P1", "Register with too-short password rejected", "400", "POST", "/api/register",
             body={"login": f"e2e-{TS}@example.invalid", "email": f"e2e-{TS}@example.invalid", "password": "abc", "langKey": "en"},
             status=400)
    s.expect(m, c, "negative", "P1", "Register with invalid email rejected", "400", "POST", "/api/register",
             body={"login": f"e2e-{TS}", "email": "not-an-email", "password": "ValidPass@123", "langKey": "en"}, status=400)
    s.expect(m, c, "negative", "P0", "Register with an email already in use rejected", "400 (email/login already used)",
             "POST", "/api/register",
             body={"login": CUSTOMER_USER, "email": CUSTOMER_USER, "password": "ValidPass@123", "langKey": "en"}, status=400)
    s.expect(m, c, "negative", "P1", "Activate with invalid key rejected", "400", "GET", f"/api/activate?key=invalid-{TS}",
             status=(400, 404, 500), check=lambda r: (r.code != 500, "server error instead of 400" if r.code == 500 else ""))
    s.expect(m, c, "negative", "P1", "Password reset finish with invalid key rejected", "400", "POST",
             "/api/account/reset-password/finish", body={"key": f"invalid-{TS}", "newPassword": "ValidPass@123"}, status=400)
    s.expect(m, c, "negative", "P2", "Password reset finish with short password rejected", "400", "POST",
             "/api/account/reset-password/finish", body={"key": f"invalid-{TS}", "newPassword": "ab"}, status=400)
    s.expect(m, c, "negative", "P2", "Staff invite finish with invalid key rejected", "400", "POST",
             "/api/account/invite/finish", body={"key": f"invalid-{TS}", "newPassword": "ValidPass@123"}, status=400)
    if cust:
        s.expect(m, c, "negative", "P1", "Change password with wrong current password rejected", "400", "POST",
                 "/api/account/change-password", token=cust,
                 body={"currentPassword": "wrong-current-e2e", "newPassword": "ValidPass@123"}, status=400)
        s.expect(m, c, "negative", "P2", "Change password to a too-short password rejected", "400", "POST",
                 "/api/account/change-password", token=cust, body={"currentPassword": CUSTOMER_PASS, "newPassword": "ab"},
                 status=400)
        s.expect(m, c, "negative", "P0", "Customer cannot list users (admin API)", "403", "GET", "/api/admin/users",
                 token=cust, status=403)
        s.expect(m, c, "negative", "P1", "Customer cannot list authorities", "403", "GET", "/api/authorities",
                 token=cust, status=403)
    s.expect(m, c, "negative", "P1", "Anonymous public users endpoint does not error or leak emails",
             "200 without email fields, or 401", "GET", "/api/users", status=(200, 401),
             check=lambda r: (not contains_key(r.body, {"email"}), "emails exposed" if contains_key(r.body, {"email"}) else ""))

    s.skip(m, c, "positive", "P0", "New customer registers and activates account", "201 + activation email", SKIP_MAIL)
    s.skip(m, c, "positive", "P1", "Customer requests password reset", "200 + reset email", SKIP_MAIL)
    s.skip(m, c, "positive", "P1", "Admin invites a staff user", "201 + invite email", SKIP_MAIL)


def storefront_module(s: Suite, cust: str | None) -> dict[str, Any]:
    m, c = "Storefront catalogue (public)", "SHOP"
    ctx: dict[str, Any] = {}

    def non_empty(label: str) -> Callable[[Resp], tuple[bool, str]]:
        return lambda r: (len(as_list(r.data, label)) > 0, f"{len(as_list(r.data, label))} {label}")

    s.expect(m, c, "positive", "P0", "Categories list loads", "200, at least one category", "GET", "/api/v1/categories",
             check=non_empty("categories"))
    r = s.expect(m, c, "positive", "P0", "Products list loads", "200, at least one product", "GET", "/api/v1/products",
                 check=non_empty("products"))
    products = as_list(r.data if r else None, "products")
    slug = next((p.get("slug") for p in products if p.get("slug")), None)
    ctx["product_slug"] = slug
    if slug:
        s.expect(m, c, "positive", "P0", "Product detail page data by slug", "200, same slug", "GET", f"/api/v1/products/{slug}",
                 check=lambda r: (r.data.get("slug") == slug, f"slug={r.data.get('slug')}"))
    r = s.expect(m, c, "positive", "P1", "Blog list loads", "200", "GET", "/api/v1/blog-posts")
    blog_slug = next((b.get("slug") for b in as_list(r.data if r else None, "posts", "blogPosts") if b.get("slug")), None)
    if blog_slug:
        s.expect(m, c, "positive", "P2", "Blog post detail by slug", "200", "GET", f"/api/v1/blog-posts/{blog_slug}")
    for path, title in [
        ("/api/v1/banners", "Home banners load"),
        ("/api/v1/home-sections", "Home sections load"),
        ("/api/v1/page-visibility", "Page visibility flags load"),
        ("/api/v1/site-settings", "Site settings load"),
        ("/api/v1/subscribe-page", "Subscribe page content loads"),
        ("/api/v1/about-page", "About page content loads"),
        ("/api/v1/gift-page", "Gift page content loads"),
        ("/api/v1/coupons/active", "Active coupon offers load"),
    ]:
        s.expect(m, c, "positive", "P1", title, "200", "GET", path)
    s.expect(m, c, "positive", "P0", "Serviceable pincode returns city/state/ETA", "200, Hyderabad / Telangana",
             "GET", "/api/v1/pincode/500001", check=lambda r: (r.data.get("city") == "Hyderabad", f"{r.data}"))

    coupons = as_list(api("GET", "/api/v1/coupons/active").data, "coupons")
    line = {"qty": 2, "price": 500, "product_id": str(next((p.get("id") for p in products if p.get("id")), "1"))}

    def coupon_body(cp: dict, qty: int = 2) -> dict:
        subtotal = max(float(cp.get("min_order") or 0) + 500, 500 * qty)
        return {"code": cp["code"], "subtotal": subtotal, "items": [{**line, "qty": qty}]}

    usable = None
    for cp in coupons:
        if cust and api("POST", "/api/v1/coupons/validate", cust, coupon_body(cp)).data.get("ok"):
            usable = cp
            break
    if usable:
        s.expect(m, c, "positive", "P0", f"Valid coupon {usable['code']} applies a discount", "200 ok=true, discount>0",
                 "POST", "/api/v1/coupons/validate", token=cust, body=coupon_body(usable),
                 check=lambda r: (bool(r.data.get("ok")) and float(r.data.get("discount") or 0) > 0, f"discount={r.data.get('discount')}"))
    else:
        s.skip(m, c, "positive", "P0", "Valid coupon applies a discount", "ok=true", "no coupon usable by the test customer on this env")
    bogo = next((cp for cp in coupons if str(cp.get("discount_type", "")).lower() == "bogo"), None)
    if bogo and cust:
        s.expect(m, c, "negative", "P1", f"BOGO coupon {bogo['code']} with a single item rejected", "ok=false (add more items)",
                 "POST", "/api/v1/coupons/validate", token=cust, body=coupon_body(bogo, qty=1),
                 check=lambda r: (r.data.get("ok") is False, str(r.data.get("error") or "")))
    first_order = next((cp for cp in coupons if cp.get("code") == "WELCOME10"), None)
    if first_order:
        s.expect(m, c, "negative", "P1", "First-order coupon requires sign-in", "ok=false (sign in)", "POST",
                 "/api/v1/coupons/validate", body=coupon_body(first_order),
                 check=lambda r: (r.data.get("ok") is False, str(r.data.get("error") or "")))
        if cust:
            s.expect(m, c, "negative", "P1", "First-order coupon refused for a repeat customer", "ok=false (first order only)",
                     "POST", "/api/v1/coupons/validate", token=cust, body=coupon_body(first_order),
                     check=lambda r: (r.data.get("ok") is False, str(r.data.get("error") or "")))

    s.expect(m, c, "negative", "P0", "Unknown product slug", "404 Product not found", "GET", "/api/v1/products/does-not-exist-e2e",
             status=404)
    s.expect(m, c, "negative", "P1", "Unknown blog slug", "404", "GET", "/api/v1/blog-posts/does-not-exist-e2e", status=404)
    s.expect(m, c, "negative", "P1", "Unknown policy slug", "404", "GET", "/api/v1/policies/does-not-exist-e2e", status=404)
    s.expect(m, c, "negative", "P1", "SQL-injection style slug handled safely", "404, no 5xx", "GET",
             "/api/v1/products/%27%20OR%201%3D1--", status=(400, 404))
    s.expect(m, c, "negative", "P1", "Pincode all zeros rejected", "400 Invalid pincode format", "GET", "/api/v1/pincode/000000", status=400)
    s.expect(m, c, "negative", "P1", "Non-numeric pincode rejected", "400", "GET", "/api/v1/pincode/abcdef", status=400)
    s.expect(m, c, "negative", "P2", "Five-digit pincode rejected", "400", "GET", "/api/v1/pincode/50000", status=(400, 404))
    s.expect(m, c, "negative", "P0", "Unknown coupon code rejected", "200 ok=false", "POST", "/api/v1/coupons/validate",
             body={"code": f"NOPE{TS}", "subtotal": 1000}, check=lambda r: (r.data.get("ok") is False, str(r.data.get("error"))))
    s.expect(m, c, "negative", "P1", "Blank coupon code rejected", "400", "POST", "/api/v1/coupons/validate",
             body={"code": "", "subtotal": 1000}, status=400)
    s.expect(m, c, "negative", "P1", "Negative subtotal rejected", "400", "POST", "/api/v1/coupons/validate",
             body={"code": "WELCOME10", "subtotal": -5}, status=400)
    s.expect(m, c, "negative", "P2", "Coupon code longer than 64 chars rejected", "400", "POST", "/api/v1/coupons/validate",
             body={"code": "X" * 65, "subtotal": 1000}, status=400)
    s.expect(m, c, "negative", "P2", "Coupon line quantity above 50 rejected", "400", "POST", "/api/v1/coupons/validate",
             body={"code": "WELCOME10", "subtotal": 1000, "items": [{"qty": 51, "price": 10}]}, status=400)
    return ctx


def contact_module(s: Suite, admin: str | None) -> None:
    m, c = "Contact us / Write to us", "CONTACT"
    valid = {
        "name": "E2E Scenario",
        "email": f"e2e-contact-{TS}@example.invalid",
        "phone": "9876543210",
        "address": "12 Test Street, Hyderabad",
        "comment": f"Automated E2E scenario message {TS} — safe to delete",
    }
    created = s.expect(m, c, "positive", "P0", "Visitor submits a valid contact message", "200, message stored",
                       "POST", "/api/v1/contact-messages", body=valid)
    if created and created.code == 200:
        s.expect(m, c, "negative", "P1", "Rapid second message from same email is rate-limited", "429",
                 "POST", "/api/v1/contact-messages", body=valid, status=429)
    msg_id = None
    if admin:
        r = s.expect(m, c, "positive", "P1", "Admin sees the new contact message", "200, message listed",
                     "GET", "/api/v1/admin/contact-messages", token=admin,
                     check=lambda r: (any(x.get("comment") == valid["comment"] for x in as_list(r.data)), ""))
        msg_id = next((x.get("id") for x in as_list(r.data if r else None) if x.get("comment") == valid["comment"]), None)
        if msg_id:
            s.expect(m, c, "positive", "P1", "Admin deletes the contact message", "200/204", "DELETE",
                     f"/api/v1/admin/contact-messages/{msg_id}", token=admin, status=(200, 204))
        s.expect(m, c, "negative", "P2", "Admin deletes a non-existent contact message", "404", "DELETE",
                 "/api/v1/admin/contact-messages/999999999", token=admin, status=404)
    for title, patch in [
        ("Invalid mobile number rejected", {"phone": "1234567890"}),
        ("Missing name rejected", {"name": ""}),
        ("Invalid email rejected", {"email": "not-an-email"}),
        ("Comment shorter than 4 chars rejected", {"comment": "hi"}),
        ("Address shorter than 4 chars rejected", {"address": "ab"}),
    ]:
        s.expect(m, c, "negative", "P1", title, "400", "POST", "/api/v1/contact-messages",
                 body={**valid, "email": f"e2e-neg-{TS}@example.invalid", **patch}, status=400)


def account_module(s: Suite, cust: str | None) -> None:
    m, c = "Customer account", "ACCT"
    if not cust:
        s.skip(m, c, "positive", "P0", "Customer account scenarios", "—", "customer login failed")
        return
    profile = api("GET", "/api/v1/account/profile", cust).data or {}
    s.expect(m, c, "positive", "P0", "Customer views profile", "200 with full_name/phone", "GET", "/api/v1/account/profile",
             token=cust, check=lambda r: ("full_name" in (r.data or {}), ""))
    if profile.get("full_name") and profile.get("phone"):
        s.expect(m, c, "positive", "P1", "Customer saves profile (unchanged values)", "200", "PUT", "/api/v1/account/profile",
                 token=cust, body={"full_name": profile["full_name"], "phone": profile["phone"], "subscribed": profile.get("subscribed")})
    for path, title in [
        ("/api/v1/account/notifications", "Customer views notifications"),
        ("/api/v1/account/cart", "Customer views saved cart"),
        ("/api/v1/account/wishlist", "Customer views wishlist"),
        ("/api/v1/payments", "Customer views payment history"),
        ("/api/v1/orders", "Customer views order history"),
        ("/api/v1/order-returns", "Customer views returns / exchanges"),
    ]:
        s.expect(m, c, "positive", "P1", title, "200", "GET", path, token=cust)

    addr = {"label": "E2E", "full_name": "E2E Address", "phone": "9876543210", "line1": "1 E2E Road", "line2": "",
            "city": "Hyderabad", "state": "Telangana", "pincode": "500001", "is_default": False}
    r = s.expect(m, c, "positive", "P0", "Customer adds a shipping address", "200 with id", "POST", "/api/v1/account/addresses",
                 token=cust, body=addr)
    addr_id = None
    if r and r.code == 200:
        d = r.data or {}
        addr_id = d.get("id") or (d.get("address") or {}).get("id")
        if not addr_id:
            listed = as_list(api("GET", "/api/v1/account/addresses", cust).data, "addresses")
            addr_id = next((a.get("id") for a in listed if a.get("line1") == addr["line1"]), None)
    if addr_id:
        s.expect(m, c, "positive", "P1", "Customer edits the address", "200", "PUT", f"/api/v1/account/addresses/{addr_id}",
                 token=cust, body={**addr, "line2": "Updated by E2E"})
        s.expect(m, c, "positive", "P1", "Customer deletes the address", "200/204", "DELETE",
                 f"/api/v1/account/addresses/{addr_id}", token=cust, status=(200, 204))

    wishlist = as_list(api("GET", "/api/v1/account/wishlist", cust).data, "product_ids")
    product = next((p for p in as_list(api("GET", "/api/v1/products").data) if str(p.get("id")) not in wishlist), None)
    if product:
        pid = str(product["id"])
        s.expect(m, c, "positive", "P1", "Customer adds a product to wishlist", "200, product listed", "POST",
                 "/api/v1/account/wishlist/items", token=cust, body={"product_id": pid},
                 check=lambda r: (pid in as_list(api("GET", "/api/v1/account/wishlist", cust).data, "product_ids"), ""))
        s.expect(m, c, "positive", "P1", "Customer removes the product from wishlist", "200/204", "DELETE",
                 f"/api/v1/account/wishlist/items/{pid}", token=cust, status=(200, 204))

    s.expect(m, c, "negative", "P0", "Profile without login rejected", "401", "GET", "/api/v1/account/profile", status=401)
    s.expect(m, c, "negative", "P0", "Order history without login rejected", "401", "GET", "/api/v1/orders", status=401)
    s.expect(m, c, "negative", "P1", "Profile with invalid mobile rejected", "400", "PUT", "/api/v1/account/profile",
             token=cust, body={"full_name": "Valid Name", "phone": "12345"}, status=400)
    s.expect(m, c, "negative", "P1", "Profile name with digits rejected", "400", "PUT", "/api/v1/account/profile",
             token=cust, body={"full_name": "Name123", "phone": "9876543210"}, status=400)
    s.expect(m, c, "negative", "P2", "Profile with blank name rejected", "400", "PUT", "/api/v1/account/profile",
             token=cust, body={"full_name": "", "phone": "9876543210"}, status=400)
    s.expect(m, c, "negative", "P1", "Address with 5-digit pincode rejected", "400", "POST", "/api/v1/account/addresses",
             token=cust, body={**addr, "pincode": "50000"}, status=400)
    s.expect(m, c, "negative", "P1", "Address without line1 rejected", "400", "POST", "/api/v1/account/addresses",
             token=cust, body={**addr, "line1": ""}, status=400)
    s.expect(m, c, "negative", "P2", "Edit a non-existent address", "404", "PUT", "/api/v1/account/addresses/999999999",
             token=cust, body=addr, status=(403, 404))
    s.expect(m, c, "negative", "P2", "Delete a non-existent address", "404", "DELETE", "/api/v1/account/addresses/999999999",
             token=cust, status=(403, 404))
    s.expect(m, c, "negative", "P2", "Wishlist add with blank product id rejected", "400", "POST",
             "/api/v1/account/wishlist/items", token=cust, body={"product_id": ""}, status=400)
    s.expect(m, c, "negative", "P2", "Cart replace without items rejected", "400", "PUT", "/api/v1/account/cart",
             token=cust, body={"coupon": None}, status=400)


def checkout_module(s: Suite, cust: str | None, admin: str | None, ctx: dict[str, Any]) -> None:
    m, c = "Cart, checkout & payment", "ORDER"
    if not cust:
        s.skip(m, c, "positive", "P0", "Checkout scenarios", "—", "customer login failed")
        return
    own = as_list(api("GET", "/api/v1/orders", cust).data, "orders")
    ctx["own_orders"] = own
    own_numbers = {o.get("order_number") for o in own}
    if own:
        first = own[0]["order_number"]
        s.expect(m, c, "positive", "P0", "Customer opens own order detail", "200", "GET", f"/api/v1/orders/{first}", token=cust)
        s.expect(m, c, "positive", "P0", "Public order tracking by order number", "200, no personal data", "GET",
                 f"/api/v1/orders/track/{first}",
                 check=lambda r: (not contains_key(r.data, {"phone", "email", "line1", "full_name"}), ""))
    if admin:
        admin_orders = as_list(api("GET", "/api/v1/admin/orders?size=50", admin).data, "orders")
        foreign = next((o["order_number"] for o in admin_orders if o.get("order_number") not in own_numbers), None)
        ctx["foreign_order"] = foreign
        if foreign:
            s.expect(m, c, "negative", "P0", "Customer cannot open another customer's order", "404", "GET",
                     f"/api/v1/orders/{foreign}", token=cust, status=(403, 404))

    line = sellable_line()
    if not line:
        s.skip(m, c, "positive", "P0", "Place online order", "200", "no sellable product with stock on this env")
        return

    def bad(title: str, prio: str, body: dict, status: int | tuple[int, ...] = 400) -> None:
        s.expect(m, c, "negative", prio, title, "4xx, no order created", "POST", "/api/v1/orders", token=cust, body=body,
                 status=status)

    bad("Order with no items rejected", "P0", order_body(line, items=[]))
    bad("Order line with quantity 0 rejected", "P1", order_body({**line, "qty": 0}))
    bad("Order line with quantity above 50 rejected", "P1", order_body({**line, "qty": 51}))
    bad("Order without shipping address rejected", "P0", {k: v for k, v in order_body(line).items() if k != "shipping_address"})
    bad("Order with invalid pincode rejected", "P1", order_body(line, shipping_address=shipping_address(pincode="12AB")))
    bad("Order with invalid shipping email rejected", "P2", order_body(line, shipping_address=shipping_address(email="bad")))
    bad("Order with unsupported payment method rejected", "P1", order_body(line, payment_method="bitcoin"))
    bad("Order with negative donation rejected", "P2", order_body(line, donation=-10))
    bad("Order for a non-existent product rejected", "P0",
        order_body({**line, "product_id": "999999999", "product_slug": "does-not-exist-e2e"}), status=(400, 404, 409))
    if line["_stock"] < 50:
        bad(f"Order quantity above available stock ({line['_stock']}) rejected", "P0",
            order_body({**line, "qty": min(50, line["_stock"] + 1)}), status=(400, 409))
    s.expect(m, c, "negative", "P0", "Place order without login rejected", "401", "POST", "/api/v1/orders",
             body=order_body(line), status=401)
    s.expect(m, c, "negative", "P1", "Verify payment with missing fields rejected", "400", "POST", "/api/v1/orders/verify-payment",
             token=cust, body={"orderId": 1}, status=400)

    if not ALLOW_ORDER_WRITES:
        s.skip(m, c, "positive", "P0", "Customer places an online order", "200 + Razorpay order", "E2E_ALLOW_ORDER_WRITES=false")
        return

    created: list[dict[str, Any]] = []
    r = s.expect(m, c, "positive", "P0", f"Customer places an online order ({line['product_slug']})",
                 "200, order number, Razorpay order, total > 0", "POST", "/api/v1/orders", token=cust, body=order_body(line),
                 check=lambda r: (bool(r.data.get("orderNumber")) and float((r.data.get("totals") or {}).get("total") or 0) > 0,
                                  f"{r.data.get('orderNumber')} total ₹{(r.data.get('totals') or {}).get('total')}"))
    if r and r.code == 200 and r.data.get("orderId"):
        order = r.data
        created.append(order)
        num = order["orderNumber"]
        s.expect(m, c, "positive", "P0", "New order appears in order history as pending", "listed, payment pending",
                 "GET", "/api/v1/orders", token=cust,
                 check=lambda r: (any(o.get("order_number") == num and o.get("payment_status") == "pending"
                                      for o in as_list(r.data, "orders")), num))
        s.expect(m, c, "positive", "P1", "Public tracking works for the new order", "200", "GET", f"/api/v1/orders/track/{num}")
        s.expect(m, c, "positive", "P1", "Customer resumes payment on the unpaid order", "200, same Razorpay order",
                 "POST", "/api/v1/orders/retry-payment", token=cust, body={"orderId": order["orderId"]})
        s.expect(m, c, "positive", "P1", "Checking an unpaid order's payment status leaves it unpaid", "200, not paid",
                 "POST", "/api/v1/orders/sync-payment", token=cust, body={"orderId": order["orderId"]},
                 check=lambda r: (str(r.data.get("paymentStatus") or r.data.get("payment_status") or "").lower() != "paid", ""))
        s.expect(m, c, "negative", "P1", "Cancel request with a 1-char reason rejected", "400", "POST",
                 f"/api/v1/orders/{num}/cancel-request", token=cust, body={"reason": "x"}, status=400)
        s.expect(m, c, "negative", "P2", "Cancel request with reason over 500 chars rejected", "400", "POST",
                 f"/api/v1/orders/{num}/cancel-request", token=cust, body={"reason": "x" * 501}, status=400)
        s.expect(m, c, "positive", "P1", "Customer checks for an open cancel request (none)", "200, cancellation_request=null",
                 "GET", f"/api/v1/orders/{num}/cancel-request", token=cust)
        s.expect(m, c, "negative", "P1", "Withdraw a cancel request when none is pending", "404", "POST",
                 f"/api/v1/orders/{num}/cancel-request/withdraw", token=cust, status=404)
        s.expect(m, c, "negative", "P1", "Return on an unpaid order rejected", "400 Only paid orders", "POST",
                 "/api/v1/order-returns", token=cust, body={"order_number": num, "reason": "Scenario negative return test"},
                 status=400)

    tampered = s.expect(m, c, "negative", "P0", "Client-side price tampering (₹1) is ignored", "4xx, or totals use catalogue price",
                        "POST", "/api/v1/orders", token=cust, body=order_body({**line, "price": 1}), status=(200, 400, 409),
                        check=lambda r: (r.code != 200 or float((r.data.get("totals") or {}).get("subtotal") or 0) >= line["price"] * 0.5,
                                         f"subtotal ₹{(r.data.get('totals') or {}).get('subtotal')} vs catalogue ₹{line['price']}"
                                         if r.code == 200 else ""))
    if tampered and tampered.code == 200 and tampered.data.get("orderId"):
        created.append(tampered.data)

    # Failing the payment signature marks each test order FAILED and releases its stock reservation.
    for order in created:
        num = order["orderNumber"]
        s.expect(m, c, "negative", "P0", f"Forged Razorpay signature rejected ({num})", "400 Invalid payment signature, order not paid",
                 "POST", "/api/v1/orders/verify-payment", token=cust,
                 body={"orderId": order["orderId"], "razorpay_order_id": order.get("razorpayOrderId") or "order_x",
                       "razorpay_payment_id": f"pay_e2e_{TS}", "razorpay_signature": "0" * 64},
                 status=400,
                 check=lambda r, num=num: (next((o.get("payment_status") for o in as_list(api("GET", "/api/v1/orders", cust).data, "orders")
                                                 if o.get("order_number") == num), "") != "paid", "order stays unpaid"))
    if created:
        num = created[0]["orderNumber"]
        s.run(m, c, "negative", "P1", "Failed payment signature is recorded (payment FAILED, stock released)",
              "payment_status=failed after the rejected verify-payment",
              lambda: (lambda st: (st == "failed", f"{num} payment_status={st}", None))(
                  next((o.get("payment_status") for o in as_list(api("GET", "/api/v1/orders", cust).data, "orders")
                        if o.get("order_number") == num), None)),
              "GET /api/v1/orders")

    s.skip(m, c, "positive", "P0", "Customer pays online and order is confirmed", "PAID + confirmation email", SKIP_MONEY)
    s.skip(m, c, "positive", "P0", "Customer places a COD order", "CONFIRMED + order email + Ledgerly sync", SKIP_MAIL)


def cancel_module(s: Suite, cust: str | None, admin: str | None, ctx: dict[str, Any]) -> None:
    m, c = "Order cancellation (M2)", "CANCEL"
    own = ctx.get("own_orders") or []
    shipped = next((o["order_number"] for o in own if o.get("tracking_status") in ("shipped", "delivered")), None)
    if cust:
        if shipped:
            s.expect(m, c, "negative", "P0", "Cancel after dispatch blocked", "409 already dispatched", "POST",
                     f"/api/v1/orders/{shipped}/cancel-request", token=cust, body={"reason": "Changed my mind"}, status=409)
        s.expect(m, c, "negative", "P0", "Cancel a non-existent order", "404", "POST", f"/api/v1/orders/MNM-NOPE{TS}/cancel-request",
                 token=cust, body={"reason": "Changed my mind"}, status=404)
        if ctx.get("foreign_order"):
            s.expect(m, c, "negative", "P0", "Cancel another customer's order", "404", "POST",
                     f"/api/v1/orders/{ctx['foreign_order']}/cancel-request", token=cust, body={"reason": "Changed my mind"},
                     status=(403, 404))
        s.expect(m, c, "negative", "P1", "Cancel with empty reason rejected", "400", "POST",
                 f"/api/v1/orders/{shipped or 'MNM-X'}/cancel-request", token=cust, body={"reason": ""}, status=400)
    s.expect(m, c, "negative", "P0", "Cancel without login rejected", "401", "POST", "/api/v1/orders/MNM-X/cancel-request",
             body={"reason": "Changed my mind"}, status=401)

    if admin:
        r = s.expect(m, c, "positive", "P0", "Admin views cancellation requests", "200 list", "GET",
                     "/api/v1/admin/cancellation-requests", token=admin)
        rows = as_list(r.data if r else None)
        s.expect(m, c, "positive", "P2", "Admin filters cancellation requests by status", "only approved rows", "GET",
                 "/api/v1/admin/cancellation-requests?status=APPROVED", token=admin,
                 check=lambda r: (all(str(x.get("status")).lower() == "approved" for x in as_list(r.data)), f"{len(as_list(r.data))} rows"))
        approved = next((x["request_number"] for x in rows if x.get("status") == "approved"), None)
        rejected = next((x["request_number"] for x in rows if x.get("status") == "rejected"), None)
        for req, label in ((approved, "approved"), (rejected, "rejected")):
            if not req:
                continue
            s.expect(m, c, "negative", "P1", f"Approve an already-{label} request blocked", "400 not awaiting approval", "POST",
                     f"/api/v1/admin/cancellation-requests/{req}/approve", token=admin, body={"admin_notes": "e2e"}, status=400)
            s.expect(m, c, "negative", "P1", f"Reject an already-{label} request blocked", "400", "POST",
                     f"/api/v1/admin/cancellation-requests/{req}/reject", token=admin,
                     body={"rejection_reason": "E2E negative check", "admin_notes": "e2e"}, status=400)
        if approved:
            s.expect(m, c, "negative", "P1", "Reject reason shorter than 5 chars rejected", "400", "POST",
                     f"/api/v1/admin/cancellation-requests/{approved}/reject", token=admin, body={"rejection_reason": "no"}, status=400)
            s.expect(m, c, "negative", "P2", "Record COD refund without method / reference rejected", "400", "POST",
                     f"/api/v1/admin/cancellation-requests/{approved}/record-cod-refund", token=admin, body={}, status=400)
        s.expect(m, c, "negative", "P1", "Approve a non-existent cancellation request", "404", "POST",
                 f"/api/v1/admin/cancellation-requests/CAN-NOPE-{TS}/approve", token=admin, body={}, status=404)
    if cust:
        s.expect(m, c, "negative", "P0", "Customer cannot approve cancellations", "403", "POST",
                 "/api/v1/admin/cancellation-requests/CAN-X/approve", token=cust, body={}, status=403)

    for title, exp in [
        ("Customer submits cancel with valid reason (M2-C-P01)", "REQUESTED + email + admin queue"),
        ("Admin approves online cancel → Razorpay refund (M2-C-P02)", "CANCELLED, refund id, stock restored, email"),
        ("Admin approves COD cancel + records manual refund (M2-C-P03)", "refund recorded once, customer informed"),
        ("Admin rejects cancel (M2-C-P04)", "REJECTED + email"),
        ("Customer withdraws pending cancel (M2-C-P05)", "request cleared"),
        ("Duplicate pending cancel blocked (M2-C-N03)", "409 already pending — needs a pending request"),
    ]:
        s.skip(m, c, "negative" if "blocked" in title else "positive", "P0", title, exp,
               SKIP_MONEY if "refund" in exp.lower() else SKIP_MAIL)


def returns_module(s: Suite, cust: str | None, admin: str | None, ctx: dict[str, Any]) -> None:
    m, c = "Returns & exchanges (M2)", "RET"
    own = ctx.get("own_orders") or []
    if cust:
        s.expect(m, c, "positive", "P0", "Returns config exposes 3-day window", "enabled=true, window_days=3", "GET",
                 "/api/v1/order-returns/config", token=cust,
                 check=lambda r: (r.data.get("window_days") == 3, f"{r.data}"))
        if own:
            num = own[0]["order_number"]
            s.expect(m, c, "positive", "P0", "Eligibility check explains why an order can/can't be returned",
                     "200 with eligible flag + message", "GET", f"/api/v1/order-returns/eligibility/{num}", token=cust,
                     check=lambda r: ("eligible" in r.data, f"eligible={r.data.get('eligible')} {r.data.get('message') or ''}"))
            item = next((i for o in own for i in o.get("order_items") or [] if i.get("id")), None)
            if item:
                size = urllib.request.quote(str(item.get("size") or "M"))
                color = urllib.request.quote(str(item.get("color") or "Red"))
                s.expect(m, c, "positive", "P1", "Exchange availability for an ordered item", "200 with sellable flag", "GET",
                         f"/api/v1/order-returns/exchange-availability/{item['id']}?size={size}&color={color}", token=cust,
                         check=lambda r: ("sellable" in (r.data or {}), f"sellable={(r.data or {}).get('sellable')}"))
                s.expect(m, c, "negative", "P2", "Exchange availability without size/colour rejected", "400", "GET",
                         f"/api/v1/order-returns/exchange-availability/{item['id']}", token=cust, status=400)
            s.expect(m, c, "negative", "P1", "Exchange availability for someone else's / unknown item reveals nothing",
                     "sellable=false or 404", "GET", "/api/v1/order-returns/exchange-availability/999999999?size=M&color=Red",
                     token=cust, status=(200, 404),
                     check=lambda r: (r.code == 404 or (r.data or {}).get("sellable") is False, ""))
        unpaid = next((o["order_number"] for o in own if o.get("payment_status") == "pending"), None)
        refunded = next((o["order_number"] for o in own if o.get("payment_status") == "refunded"), None)
        reason = "Scenario negative return test"
        if unpaid:
            s.expect(m, c, "negative", "P0", "Return on unpaid order rejected (M2-R-N03)", "400 Only paid orders", "POST",
                     "/api/v1/order-returns", token=cust, body={"order_number": unpaid, "reason": reason}, status=400)
            s.expect(m, c, "negative", "P0", "Exchange on unpaid order rejected (M2-E-N09)", "400", "POST",
                     "/api/v1/order-returns/exchange", token=cust,
                     body={"order_number": unpaid, "order_item_id": 1, "reason": reason, "replacement_size": "3-6 M",
                           "replacement_color": "Red"}, status=(400, 404))
        if refunded:
            s.expect(m, c, "negative", "P1", "Return on an already-refunded order rejected", "400", "POST",
                     "/api/v1/order-returns", token=cust, body={"order_number": refunded, "reason": reason}, status=(400, 409))
        target = unpaid or (own[0]["order_number"] if own else "MNM-X")
        s.expect(m, c, "negative", "P0", "Return reason under 10 characters rejected (M2-R-N08)", "400", "POST",
                 "/api/v1/order-returns", token=cust, body={"order_number": target, "reason": "short"}, status=400)
        s.expect(m, c, "negative", "P2", "Return reason over 4000 characters rejected", "400", "POST",
                 "/api/v1/order-returns", token=cust, body={"order_number": target, "reason": "x" * 4001}, status=400)
        s.expect(m, c, "negative", "P1", "Return without order number rejected", "400", "POST", "/api/v1/order-returns",
                 token=cust, body={"reason": reason}, status=400)
        s.expect(m, c, "negative", "P1", "Return line with quantity 0 rejected (M2-R-N07)", "400", "POST",
                 "/api/v1/order-returns", token=cust,
                 body={"order_number": target, "reason": reason, "items": [{"order_item_id": 1, "quantity": 0}]}, status=400)
        s.expect(m, c, "negative", "P0", "Return on a non-existent order", "404", "POST", "/api/v1/order-returns", token=cust,
                 body={"order_number": f"MNM-NOPE{TS}", "reason": reason}, status=404)
        if ctx.get("foreign_order"):
            s.expect(m, c, "negative", "P0", "Return on another customer's order (M2-R-N15)", "404", "POST",
                     "/api/v1/order-returns", token=cust, body={"order_number": ctx["foreign_order"], "reason": reason},
                     status=(403, 404))
            s.expect(m, c, "negative", "P1", "Eligibility of another customer's order hidden", "404, or eligible=false 'Order not found'",
                     "GET", f"/api/v1/order-returns/eligibility/{ctx['foreign_order']}", token=cust, status=(200, 403, 404),
                     check=lambda r: (r.code != 200 or (r.data.get("eligible") is False and "not found" in str(r.data.get("message")).lower()),
                                      str((r.data or {}).get("message") or "") if r.code == 200 else ""))
        s.expect(m, c, "negative", "P0", "Exchange reason under 10 characters rejected (M2-E-N10)", "400", "POST",
                 "/api/v1/order-returns/exchange", token=cust,
                 body={"order_number": target, "order_item_id": 1, "reason": "short", "replacement_size": "3-6 M",
                       "replacement_color": "Red"}, status=400)
        s.expect(m, c, "negative", "P1", "Exchange without replacement size rejected", "400", "POST",
                 "/api/v1/order-returns/exchange", token=cust,
                 body={"order_number": target, "order_item_id": 1, "reason": reason, "replacement_size": "",
                       "replacement_color": "Red"}, status=400)
        s.expect(m, c, "negative", "P2", "Cancel a non-existent return", "404", "POST", f"/api/v1/order-returns/RET-NOPE-{TS}/cancel",
                 token=cust, status=404)
    s.expect(m, c, "negative", "P0", "Return request without login rejected", "401", "POST", "/api/v1/order-returns",
             body={"order_number": "MNM-X", "reason": "Scenario negative return test"}, status=401)

    if admin:
        r = s.expect(m, c, "positive", "P0", "Admin views return / exchange desk", "200 list", "GET", "/api/v1/admin/order-returns",
                     token=admin)
        rows = as_list(r.data if r else None)
        refunded_ret = next((x for x in rows if x.get("status") == "refunded" and x.get("request_type") == "return"), None)
        done_exch = next((x for x in rows if x.get("status") in ("completed", "rejected") and x.get("request_type") == "exchange"), None)
        foreign_ret = next((x for x in rows if str(x.get("customer_login", "")).lower() != CUSTOMER_USER.lower()
                            and x.get("status") in ("refunded", "rejected", "completed", "cancelled")), None)

        def guarded(row: dict, action: str, title: str, expected: str, body: dict | None = None) -> None:
            num = row["return_number"]
            fresh = next((x for x in as_list(api("GET", "/api/v1/admin/order-returns", admin).data) if x.get("return_number") == num), {})
            if fresh.get("status") != row.get("status"):
                s.skip(m, c, "negative", "P1", title, expected, f"{num} changed state to {fresh.get('status')} — not safe to probe")
                return
            s.expect(m, c, "negative", "P1", title, expected, "POST", f"/api/v1/admin/order-returns/{num}/{action}", token=admin,
                     body=body or {"admin_notes": "e2e negative"}, status=400)

        if refunded_ret:
            guarded(refunded_ret, "approve", "Approve a refunded return blocked (M2-R-N09)", "400 not awaiting approval")
            guarded(refunded_ret, "reject", "Reject a refunded return blocked", "400",
                    {"rejection_reason": "E2E negative check"})
            guarded(refunded_ret, "book-pickup", "Book pickup before approval blocked (M2-X-N01)", "400 must be approved",
                    {"mode": "MANUAL", "reverse_awb": "E2E-AWB"})
            guarded(refunded_ret, "accept", "Accept before inspection blocked", "400 must be inspected",
                    {"disposition": "RESTOCKABLE"})
            guarded(refunded_ret, "pickup-failed", "Mark pickup failed before scheduling blocked (M2-X-N07)",
                    "400 pickup must be scheduled", {"failure_reason": "E2E negative check"})
            guarded(refunded_ret, "close-pickup-failed", "Close pickup-failed on a non-failed case blocked", "400")
            guarded(refunded_ret, "settle-price-difference", "Settle price difference on a return blocked (M2-E-N11)",
                    "400 Not an exchange request", {"status": "WAIVED"})
        if done_exch:
            guarded(done_exch, "refund", "Refund API on an exchange blocked (M2-R-N11)", "400 use convert-to-refund")
            guarded(done_exch, "allocate-replacement", "Allocate replacement on a closed exchange blocked", "400")
            guarded(done_exch, "book-outbound", "Book outbound before ready-to-ship blocked (M2-E-N06)", "400",
                    {"mode": "MANUAL", "outbound_awb": "E2E-AWB"})
        s.expect(m, c, "negative", "P1", "Reject reason shorter than 5 chars rejected", "400", "POST",
                 f"/api/v1/admin/order-returns/{(refunded_ret or {}).get('return_number', 'RET-X')}/reject", token=admin,
                 body={"rejection_reason": "no"}, status=400)
        s.expect(m, c, "negative", "P2", "Approve a non-existent return", "404", "POST",
                 f"/api/v1/admin/order-returns/RET-NOPE-{TS}/approve", token=admin, body={}, status=404)
        if cust and foreign_ret:
            s.expect(m, c, "negative", "P0", "Customer cannot cancel another customer's return (M2-R-N15)", "404", "POST",
                     f"/api/v1/order-returns/{foreign_ret['return_number']}/cancel", token=cust, status=(400, 403, 404))
    if cust:
        s.expect(m, c, "negative", "P0", "Customer cannot use admin return desk (M2-B-N02)", "403", "GET",
                 "/api/v1/admin/order-returns", token=cust, status=403)

    for title, exp, reason in [
        ("Customer return within 3-day window (M2-R-P01)", "REQUESTED + email", SKIP_STATE),
        ("Full return path approve → pickup → receive → inspect → accept → refund (M2-R-P02)", "REFUNDED + Razorpay + CN", SKIP_MONEY),
        ("Admin rejects return at REQUESTED / after inspection (M2-R-P03/P04)", "REJECTED + email", SKIP_MAIL),
        ("Pickup fail → reschedule / close (M2-R-P05/P06)", "pickup rescheduled or case closed", SKIP_STATE),
        ("Partial-quantity accept with mixed disposition (M2-R-P08)", "refund accepted units only", SKIP_MONEY),
        ("Customer requests size/colour exchange (M2-E-P01)", "EXCHANGE REQUESTED + email", SKIP_STATE),
        ("Exchange happy path to COMPLETED (M2-E-P02)", "COMPLETED + tracking emails", SKIP_STATE),
        ("Settle exchange price difference (M2-E-P05)", "COLLECTED / REFUNDED / WAIVED", SKIP_MONEY),
        ("Convert exchange to refund (M2-E-P08)", "REFUNDED", SKIP_MONEY),
        ("Return window expired (>3 days) blocked (M2-R-N02)", "400 window expired", SKIP_STATE),
        ("Open return blocks second request (M2-R-N06)", "409 already open", SKIP_STATE),
    ]:
        s.skip(m, c, "negative" if "blocked" in title else "positive", "P0", title, exp, reason)


def admin_module(s: Suite, admin: str | None, cust: str | None) -> None:
    m, c = "Admin catalogue & operations", "ADMIN"
    if not admin:
        s.skip(m, c, "positive", "P0", "Admin scenarios", "—", "admin login failed / credentials not set")
        return
    for path, title in [
        ("/api/v1/admin/dashboard", "Admin dashboard loads"),
        ("/api/v1/admin/orders?size=20", "Admin order list loads"),
        ("/api/v1/admin/orders/cod-pending", "COD pending collection list loads"),
        ("/api/v1/admin/orders/ordered-logins", "Ordered-customer list loads"),
        ("/api/v1/admin/categories", "Admin categories list"),
        ("/api/v1/admin/products", "Admin products list"),
        ("/api/v1/admin/banners", "Admin banners list"),
        ("/api/v1/admin/blog-posts", "Admin blog posts list"),
        ("/api/v1/admin/policies", "Admin policies list"),
        ("/api/v1/admin/coupons", "Admin coupons list"),
        ("/api/v1/admin/site-settings", "Admin site settings"),
        ("/api/v1/admin/contact-messages", "Admin contact messages"),
        ("/api/v1/admin/ledgerly/integration-status", "Ledgerly integration status"),
        ("/api/v1/admin/ledgerly/settings", "Ledgerly settings"),
    ]:
        s.expect(m, c, "positive", "P1", title, "200", "GET", path, token=admin)
    orders = as_list(api("GET", "/api/v1/admin/orders?size=5", admin).data, "orders")
    if orders:
        s.expect(m, c, "positive", "P1", "Admin opens an order detail", "200", "GET",
                 f"/api/v1/admin/orders/{orders[0]['order_number']}", token=admin)
    s.expect(m, c, "positive", "P0", "Integration secrets are never returned to the browser", "no clientSecret/refreshToken values",
             "GET", "/api/v1/admin/zoho-settings", token=admin,
             check=lambda r: (not contains_key(r.body, {"clientSecret", "refreshToken", "client_secret", "refresh_token"}), ""))

    crud = [
        ("categories", "Category", {"name": f"E2E Category {TS}", "slug": f"e2e-category-{TS}", "sortOrder": 999, "active": False}, "name"),
        ("banners", "Banner", {"title": f"E2E Banner {TS}", "subtitle": "e2e", "imageUrl": "/uploads/banners/api-test.jpg",
                               "linkUrl": "/shop", "ctaLabel": "Shop", "sortOrder": 999, "active": False}, "title"),
        ("policies", "Policy", {"slug": f"e2e-policy-{TS}", "title": f"E2E Policy {TS}", "body": "E2E scenario policy"}, "title"),
        ("blog-posts", "Blog post", {"slug": f"e2e-blog-{TS}", "title": f"E2E Blog {TS}", "published": False, "excerpt": "e2e"}, "title"),
        ("products", "Product", {"name": f"E2E Product {TS}", "slug": f"e2e-product-{TS}", "price": 199.0, "stock": 0,
                                 "sortOrder": 999, "currency": "INR", "active": False, "featured": False, "trending": False,
                                 "newArrival": False, "bestSeller": False}, "name"),
    ]
    for resource, label, body, field_name in crud:
        base = f"/api/v1/admin/{resource}"
        r = s.expect(m, c, "positive", "P0", f"Admin creates a {label.lower()}", "200 with id", "POST", base, token=admin, body=body,
                     status=(200, 201), check=lambda r: (bool((r.data or {}).get("id")), f"id={(r.data or {}).get('id')}"))
        new_id = (r.data or {}).get("id") if r and r.code in (200, 201) else None
        if not new_id:
            continue
        try:
            if "slug" in body:
                s.expect(m, c, "negative", "P1", f"Duplicate {label.lower()} slug rejected", "4xx", "POST", base, token=admin,
                         body=body, status=(400, 409, 422))
            s.expect(m, c, "positive", "P1", f"Admin updates the {label.lower()}", "200", "PUT", f"{base}/{new_id}", token=admin,
                     body={**body, "id": new_id, field_name: f"{body[field_name]} edited"})
        finally:
            s.expect(m, c, "positive", "P1", f"Admin deletes the {label.lower()}", "200/204", "DELETE", f"{base}/{new_id}",
                     token=admin, status=(200, 204))
        s.expect(m, c, "negative", "P2", f"Update a non-existent {label.lower()}", "404", "PUT", f"{base}/999999999", token=admin,
                 body={**body, "id": 999999999}, status=(400, 404))
        s.expect(m, c, "negative", "P2", f"Delete a non-existent {label.lower()}", "404", "DELETE", f"{base}/999999999",
                 token=admin, status=(400, 404))
        s.expect(m, c, "negative", "P1", f"Create {label.lower()} without required {field_name} rejected", "400", "POST", base,
                 token=admin, body={k: v for k, v in body.items() if k != field_name}, status=400)

    for resource, key, label in (("home-sections", "sectionKey", "home section"), ("page-visibility", "pageKey", "page visibility flag")):
        rows = as_list(api("GET", f"/api/v1/admin/{resource}", admin).data)
        if rows:
            row = rows[0]
            s.expect(m, c, "positive", "P1", f"Admin saves a {label} (unchanged)", "200", "PUT",
                     f"/api/v1/admin/{resource}/{row['id']}", token=admin, body=row)

    s.expect(m, c, "negative", "P1", "Product with negative price rejected", "400", "POST", "/api/v1/admin/products", token=admin,
             body={**crud[4][2], "slug": f"e2e-neg-{TS}", "price": -1}, status=400)
    s.expect(m, c, "negative", "P2", "Product with 4-letter currency rejected", "400", "POST", "/api/v1/admin/products",
             token=admin, body={**crud[4][2], "slug": f"e2e-neg2-{TS}", "currency": "RUPE"}, status=400)
    s.expect(m, c, "negative", "P2", "Category name over 160 chars rejected", "400", "POST", "/api/v1/admin/categories",
             token=admin, body={**crud[0][2], "slug": f"e2e-neg-{TS}", "name": "N" * 161}, status=400)
    s.expect(m, c, "negative", "P1", "Coupon with unknown discount type rejected", "400", "POST", "/api/v1/admin/coupons",
             token=admin, body={"code": f"E2E{TS}", "discountType": "FREEBIE", "discountValue": 10, "minOrder": 0,
                                "eligibilityScope": "ALL", "active": False}, status=400)
    s.expect(m, c, "negative", "P1", "Coupon without code rejected", "400", "POST", "/api/v1/admin/coupons", token=admin,
             body={"discountType": "PERCENT", "discountValue": 10, "minOrder": 0, "eligibilityScope": "ALL", "active": False},
             status=400)
    s.expect(m, c, "negative", "P2", "Book shipment for a non-existent order", "404", "POST",
             f"/api/v1/admin/orders/MNM-NOPE{TS}/book-shipment", token=admin, body={}, status=(400, 404))
    s.expect(m, c, "negative", "P2", "Admin opens a non-existent order", "404", "GET", f"/api/v1/admin/orders/MNM-NOPE{TS}",
             token=admin, status=404)

    s.skip(m, c, "positive", "P1", "Admin creates / edits a coupon", "200 + config-change alert email", SKIP_MAIL)
    s.skip(m, c, "positive", "P1", "Admin updates site settings", "200 + config-change alert email", SKIP_MAIL)
    s.skip(m, c, "positive", "P1", "Admin pulls inventory / products from Ledgerly", "stock refreshed",
           "mutates live catalogue stock — run from the Ledgerly live suite")
    s.skip(m, c, "positive", "P0", "Admin books forward shipment with ExpressBees (M2-X-P03)", "AWB created",
           "books a real courier pickup — manual UAT only")


def rbac_module(s: Suite, cust: str | None) -> None:
    m, c = "RBAC & security", "SEC"
    admin_gets = [
        "/api/v1/admin/dashboard", "/api/v1/admin/orders", "/api/v1/admin/products", "/api/v1/admin/coupons",
        "/api/v1/admin/site-settings", "/api/v1/admin/contact-messages", "/api/v1/admin/cancellation-requests",
        "/api/v1/admin/ledgerly/settings", "/api/v1/admin/zoho-settings",
    ]
    for path in admin_gets:
        s.expect(m, c, "negative", "P0", f"Anonymous call to {path}", "401", "GET", path, status=401)
        if cust:
            s.expect(m, c, "negative", "P0", f"Customer JWT on {path}", "403", "GET", path, token=cust, status=403)
    if cust:
        s.expect(m, c, "negative", "P0", "Customer cannot create a product", "403", "POST", "/api/v1/admin/products", token=cust,
                 body={"name": "x", "slug": f"x-{TS}", "price": 1, "stock": 0, "sortOrder": 0, "currency": "INR", "active": False,
                       "featured": False, "trending": False, "newArrival": False, "bestSeller": False}, status=403)
        s.expect(m, c, "negative", "P0", "Customer cannot delete a category", "403", "DELETE", "/api/v1/admin/categories/1",
                 token=cust, status=403)
        s.expect(m, c, "negative", "P0", "Customer cannot read management env", "403", "GET", "/management/env", token=cust,
                 status=(401, 403, 404))
    s.expect(m, c, "negative", "P0", "Anonymous management env blocked", "401", "GET", "/management/env", status=(401, 403, 404))
    s.expect(m, c, "negative", "P0", "Internal service routes not exposed through gateway", "401/403/404", "GET",
             "/internal/v1/admin/orders", status=(401, 403, 404))
    s.expect(m, c, "positive", "P0", "Health endpoint is up", "200 status=UP", "GET", "/management/health",
             check=lambda r: (r.body.get("status") == "UP", ""))
    s.expect(m, c, "negative", "P1", "CORS does not trust an arbitrary origin", "no Access-Control-Allow-Origin for evil origin",
             "OPTIONS", "/api/v1/products",
             headers={"Origin": "https://evil.example", "Access-Control-Request-Method": "GET"}, status=(200, 204, 403),
             check=lambda r: (r.headers.get("access-control-allow-origin") not in ("https://evil.example", "*"),
                              f"ACAO={r.headers.get('access-control-allow-origin')}"))
    for path, label in [
        ("/api/v1/webhooks/xpressbees/returns", "ExpressBees returns"),
        ("/api/v1/webhooks/xpressbees/shipments", "ExpressBees shipments"),
        ("/api/v1/ledgerly/webhooks", "Ledgerly"),
        ("/api/v1/zoho/stock-webhook", "Zoho stock"),
        ("/api/v1/zoho/order-status-webhook", "Zoho order status"),
        ("/api/v1/zoho/shipment-webhook", "Zoho shipment"),
        ("/api/v1/zoho/invoice-webhook", "Zoho invoice"),
        ("/api/v1/zoho/item-webhook", "Zoho item"),
    ]:
        s.expect(m, c, "negative", "P0", f"Unsigned {label} webhook rejected", "401/403", "POST", path, body={},
                 status=(400, 401, 403, 503))
    s.expect(m, c, "negative", "P0", "Ledgerly webhook with forged signature rejected", "401", "POST", "/api/v1/ledgerly/webhooks",
             body={"event": "order.updated"}, headers={"X-Ledgerly-Signature": "sha256=deadbeef"}, status=(400, 401, 403))


def frontend_module(s: Suite) -> None:
    m, c = "Storefront & admin pages (frontend)", "WEB"
    pages = [
        ("/", "Home"), ("/product/basic-shorts", "Product page"), ("/blog", "Blog"), ("/cart", "Cart"),
        ("/checkout", "Checkout"), ("/login", "Customer login"), ("/account", "Account"), ("/wishlist", "Wishlist"),
        ("/contact-us", "Contact us"), ("/about", "About"), ("/gift", "Gift"), ("/subscribe", "Subscribe"),
        ("/new-arrivals", "New arrivals"), ("/best-sellers", "Best sellers"), ("/trending", "Trending"),
        ("/privacy", "Privacy policy"), ("/terms", "Terms"), ("/shipping", "Shipping policy"),
        ("/admin/login", "Admin login"), ("/admin", "Admin shell"),
    ]
    for path, label in pages:
        s.expect(m, c, "positive", "P1", f"{label} page renders", "200 HTML", "GET", path, base=FRONTEND,
                 headers={"Accept": "text/html"}, check=lambda r: (isinstance(r.body, str) and "<html" in r.body.lower(), ""))
    for path, label in (("/shop", "Shop"), ("/track", "Track order")):
        s.expect(m, c, "positive", "P1", f"{label} route redirects to its canonical page", "3xx / 200", "GET", path,
                 base=FRONTEND, headers={"Accept": "text/html"}, status=(200, 301, 302, 307, 308), follow_redirects=False)
    s.expect(m, c, "negative", "P1", "Unknown route shows not-found page", "404", "GET", "/this-route-does-not-exist-e2e",
             base=FRONTEND, headers={"Accept": "text/html"}, status=404)
    s.expect(m, c, "negative", "P2", "Unknown product page does not crash", "no 5xx", "GET", "/product/does-not-exist-e2e",
             base=FRONTEND, headers={"Accept": "text/html"}, status=(200, 404))
    s.expect(m, c, "negative", "P1", "Path traversal attempt does not leak files", "404/400, no /etc/passwd", "GET",
             "/..%2f..%2f..%2fetc%2fpasswd", base=FRONTEND, headers={"Accept": "text/html"}, status=(400, 404),
             check=lambda r: ("root:x:" not in str(r.body), ""))
    s.expect(m, c, "negative", "P2", "Very long URL handled", "no 5xx", "GET", "/" + "a" * 4000, base=FRONTEND,
             headers={"Accept": "text/html"}, status=(400, 404, 414, 431))


# ---------------------------------------------------------------------------
# Reports
# ---------------------------------------------------------------------------


def summarize(cases: list[Case]) -> dict[str, Any]:
    def count(items: list[Case]) -> dict[str, int]:
        return {
            "total": len(items),
            "passed": sum(1 for x in items if x.status == "pass"),
            "failed": sum(1 for x in items if x.status == "fail"),
            "skipped": sum(1 for x in items if x.status == "skip"),
        }

    modules: dict[str, dict[str, Any]] = {}
    for case in cases:
        modules.setdefault(case.module, {"positive": [], "negative": []})[case.kind].append(case)
    return {
        **count(cases),
        "positive": count([x for x in cases if x.kind == "positive"]),
        "negative": count([x for x in cases if x.kind == "negative"]),
        "modules": {name: {k: count(v) for k, v in kinds.items()} for name, kinds in modules.items()},
    }


def build_html(report: dict[str, Any]) -> str:
    sys.path.insert(0, str(ROOT / "jenkins" / "scripts"))
    from report_theme import PROJECT_NAME, esc, page_shell, stat_block, status_pill  # noqa: E402

    summary = report["summary"]
    cases = report["cases"]
    executed = summary["passed"] + summary["failed"]
    rate = f"{(summary['passed'] / executed * 100):.1f}%" if executed else "—"

    def pill(status: str) -> str:
        return status_pill(status.upper())

    module_rows = []
    for name, kinds in summary["modules"].items():
        p, n = kinds.get("positive", {}), kinds.get("negative", {})
        module_rows.append(
            f"<tr><td>{esc(name)}</td>"
            f"<td>{p.get('passed', 0)}/{p.get('total', 0)}</td><td>{p.get('failed', 0)}</td><td>{p.get('skipped', 0)}</td>"
            f"<td>{n.get('passed', 0)}/{n.get('total', 0)}</td><td>{n.get('failed', 0)}</td><td>{n.get('skipped', 0)}</td></tr>"
        )

    def case_rows(items: list[dict]) -> str:
        def steps(value: list[str]) -> str:
            return "<ol class=\"steps\">" + "".join(f"<li>{esc(step)}</li>" for step in value) + "</ol>"

        return "".join(
            f"<tr class=\"row-{esc(x['status'])}\"><td><code>{esc(x['id'])}</code></td><td>{esc(x['priority'])}</td>"
            f"<td>{esc(x['title'])}<div class=\"muted small\">{esc(x['request'])}</div></td>"
            f"<td>{steps(x['steps'])}</td><td>{esc(x['expected'])}</td><td>{esc(x['actual'])}</td><td>{pill(x['status'])}</td>"
            f"<td>{x['ms'] or '—'}</td></tr>"
            for x in items
        )

    head = "<thead><tr><th>ID</th><th>Pri</th><th>Scenario</th><th>Steps</th><th>Expected</th><th>Actual</th><th>Status</th><th>ms</th></tr></thead>"

    def kind_section(kind: str, label: str) -> str:
        blocks = []
        for module in summary["modules"]:
            items = [x for x in cases if x["module"] == module and x["kind"] == kind]
            if items:
                blocks.append(f"<h3>{esc(module)}</h3><table class=\"data\">{head}<tbody>{case_rows(items)}</tbody></table>")
        k = summary[kind]
        return f"""
      <section class="panel" id="{kind}">
        <h2>{label} scenarios</h2>
        <div class="stats">
          {stat_block("Total", k['total'])}{stat_block("Passed", k['passed'])}
          {stat_block("Failed", k['failed'])}{stat_block("Skipped", k['skipped'])}
        </div>
        {''.join(blocks)}
      </section>"""

    failures = [x for x in cases if x["status"] == "fail"]
    findings = (
        f"<table class=\"data\">{head}<tbody>{case_rows(failures)}</tbody></table>"
        if failures else "<p class=\"muted\">No failing scenarios.</p>"
    )
    overall = "PASS" if summary["failed"] == 0 else "FAIL"
    body = f"""
      <section class="panel">
        <h2>Summary</h2>
        <p>API <code>{esc(report['baseUrl'])}</code> · Frontend <code>{esc(report['frontendUrl'])}</code> · {esc(report['finishedAt'])}</p>
        <p>Status: {pill(overall)} · No-mail mode: <code>{'true' if report['noMailMode'] else 'false'}</code>
           · Order writes: <code>{'true' if report['orderWrites'] else 'false'}</code></p>
        <div class="stats">
          {stat_block("Scenarios", summary['total'])}{stat_block("Passed", summary['passed'])}
          {stat_block("Failed", summary['failed'])}{stat_block("Skipped", summary['skipped'])}
          {stat_block("Pass rate (executed)", rate)}
        </div>
        <nav class="toc"><a href="#findings">Failures</a><a href="#positive">Positive</a><a href="#negative">Negative</a></nav>
      </section>
      <section class="panel">
        <h2>Coverage by module</h2>
        <table class="data">
          <thead><tr><th>Module</th><th>Positive pass</th><th>Positive fail</th><th>Positive skip</th>
          <th>Negative pass</th><th>Negative fail</th><th>Negative skip</th></tr></thead>
          <tbody>{''.join(module_rows)}</tbody>
        </table>
        <p class="muted">Skipped scenarios are real product flows that would email customers/staff, move money, or need an
        order in a specific delivered state; they are listed so the full scenario inventory is visible and should be run in manual UAT.</p>
      </section>
      <section class="panel" id="findings"><h2>Failures ({len(failures)})</h2>{findings}</section>
      {kind_section("positive", "Positive")}
      {kind_section("negative", "Negative")}
    """
    css = """
      .data { width: 100%; border-collapse: collapse; margin: 10px 0 18px; font-size: 13.5px; }
      .data th, .data td { text-align: left; padding: 7px 9px; border-bottom: 1px solid var(--border); vertical-align: top; }
      .data th { background: var(--surface-muted); }
      .row-fail td { background: #fef2f2; }
      .small { font-size: 12px; }
      .steps { margin: 0; padding-left: 18px; min-width: 220px; }
      .steps li + li { margin-top: 4px; }
      .toc { display:flex; gap:8px; margin-top:14px; }
      .toc a { border:1px solid var(--border); border-radius:999px; padding:4px 12px; }
      h3 { margin: 18px 0 4px; font-size: 1rem; }
    """
    return page_shell(f"{PROJECT_NAME} end-to-end test scenarios", body,
                      subtitle="Positive and negative scenarios across every module", extra_css=css, nested_logo=True)


def build_junit(report: dict[str, Any]) -> str:
    def attr(value: str) -> str:
        return xml_escape(value, {'"': "&quot;"})

    suites = []
    for module in report["summary"]["modules"]:
        items = [x for x in report["cases"] if x["module"] == module]
        cases_xml = []
        for x in items:
            name = attr(f"{x['id']} [{x['kind']}] {x['title']}")
            inner = ""
            if x["status"] == "fail":
                inner = f'<failure message="{attr(x["actual"])}">Expected: {xml_escape(x["expected"])}</failure>'
            elif x["status"] == "skip":
                inner = f'<skipped message="{attr(x["actual"])}"/>'
            cases_xml.append(f'<testcase classname="{attr(module)}" name="{name}" time="{x["ms"] / 1000:.3f}">{inner}</testcase>')
        failed = sum(1 for x in items if x["status"] == "fail")
        skipped = sum(1 for x in items if x["status"] == "skip")
        suites.append(
            f'<testsuite name="{attr(module)}" tests="{len(items)}" failures="{failed}" skipped="{skipped}">'
            + "".join(cases_xml) + "</testsuite>"
        )
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites name=\"E2E scenarios\">" + "".join(suites) + "</testsuites>\n"


def write_report(report: dict[str, Any]) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    OUT_HTML.write_text(build_html(report), encoding="utf-8")
    OUT_JUNIT.write_text(build_junit(report), encoding="utf-8")


def render_from_json() -> int:
    if not OUT_JSON.is_file():
        print(f"Missing {OUT_JSON}", file=sys.stderr)
        return 1
    report = json.loads(OUT_JSON.read_text(encoding="utf-8"))
    write_report(report)
    print(f"Rendered E2E scenario report from {OUT_JSON}")
    return 0


def main() -> int:
    if "--from-json" in sys.argv:
        return render_from_json()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    started = datetime.now(timezone.utc).isoformat()
    suite = Suite()
    cust = login(CUSTOMER_USER, CUSTOMER_PASS)
    admin = ADMIN_TOKEN or (login(ADMIN_USER, ADMIN_PASS) if ADMIN_PASS else None)

    ctx: dict[str, Any] = {}
    auth_module(suite, cust, admin)
    ctx.update(storefront_module(suite, cust))
    contact_module(suite, admin)
    account_module(suite, cust)
    # Checkout copies the shipping name/phone onto the customer profile; put the original back afterwards.
    profile = api("GET", "/api/v1/account/profile", cust).data if cust else None
    try:
        checkout_module(suite, cust, admin, ctx)
    finally:
        if isinstance(profile, dict) and profile.get("full_name"):
            api("PUT", "/api/v1/account/profile", cust,
                {k: profile.get(k) for k in ("full_name", "phone", "subscribed")})
    cancel_module(suite, cust, admin, ctx)
    returns_module(suite, cust, admin, ctx)
    admin_module(suite, admin, cust)
    rbac_module(suite, cust)
    frontend_module(suite)

    report = {
        "baseUrl": BASE,
        "frontendUrl": FRONTEND,
        "startedAt": started,
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "noMailMode": NO_MAIL,
        "orderWrites": ALLOW_ORDER_WRITES,
        "credentials": {"customer": CUSTOMER_USER, "admin": ADMIN_USER},
        "summary": summarize(suite.cases),
        "cases": [case.__dict__ for case in suite.cases],
    }
    write_report(report)
    sm = report["summary"]
    print(f"E2E scenarios: {sm['passed']} passed, {sm['failed']} failed, {sm['skipped']} skipped / {sm['total']}")
    print(f"  positive: {sm['positive']}  negative: {sm['negative']}")
    for case in suite.cases:
        if case.status == "fail":
            print(f"  FAIL {case.id} {case.title} — expected {case.expected}; got {case.actual}")
    print(f"Report: {OUT_HTML}")
    return 0 if sm["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
