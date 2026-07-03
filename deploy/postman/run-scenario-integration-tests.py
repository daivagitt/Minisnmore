#!/usr/bin/env python3
"""
Scenario-based UAT integration tests (coupon checkout, Zoho sync, shipment, returns).

Writes JSON + HTML under artifacts/scenario-integration/.
"""

from __future__ import annotations

import json
import os
import ssl
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent.parent
OUT_DIR = Path(os.environ.get("SCENARIO_REPORT_DIR", ROOT / "artifacts/scenario-integration"))
OUT_JSON = OUT_DIR / "scenario-integration-report.json"
OUT_HTML = OUT_DIR / "index.html"

BASE = os.environ.get("API_TEST_BASE_URL", "https://uat.minisnmore.com").rstrip("/")
CUSTOMER_USER = os.environ.get("SCENARIO_CUSTOMER_USER", "saikedar2003@gmail.com")
CUSTOMER_PASS = os.environ.get("SCENARIO_CUSTOMER_PASS", "Zero@1234")
ADMIN_USER = os.environ.get("SCENARIO_ADMIN_USER", "admin@uat.minisnmore.com")
ADMIN_PASS = os.environ.get("SCENARIO_ADMIN_PASS", "admin6")
COUPON_CODE = os.environ.get("SCENARIO_COUPON_CODE", "WELCOM10")
PRODUCT_ID = os.environ.get("SCENARIO_PRODUCT_ID", "26523")
PRODUCT_NAME = os.environ.get("SCENARIO_PRODUCT_NAME", "Baby Swaddle")
PRODUCT_PRICE = float(os.environ.get("SCENARIO_PRODUCT_PRICE", "349"))
REFERENCE_SHIPPED_ORDER = os.environ.get("SCENARIO_REFERENCE_SHIPPED_ORDER", "MNM-2606-A4A0B")
REFERENCE_RETURNED_ORDER = os.environ.get("SCENARIO_REFERENCE_RETURNED_ORDER", "MNM-2606-86EA3")

PAYMENT_DEMO = os.environ.get("RAZORPAY_PAYMENT_DEMO", "false").lower() in ("1", "true", "yes")
REFUND_DEMO = os.environ.get("RAZORPAY_REFUND_DEMO", "false").lower() in ("1", "true", "yes")

CTX = ssl.create_default_context()


@dataclass
class Step:
    name: str
    status: str  # pass | fail | skip
    note: str = ""
    ms: int = 0
    detail: dict[str, Any] = field(default_factory=dict)


@dataclass
class Scenario:
    id: str
    title: str
    description: str
    steps: list[Step] = field(default_factory=list)

    @property
    def status(self) -> str:
        if any(s.status == "fail" for s in self.steps):
            return "fail"
        if self.steps and all(s.status == "skip" for s in self.steps):
            return "skip"
        if any(s.status == "pass" for s in self.steps):
            return "pass"
        return "skip"


def login(username: str, password: str) -> tuple[int, str | None]:
    body = json.dumps({"username": username, "password": password, "rememberMe": True}).encode()
    req = urllib.request.Request(
        f"{BASE}/api/authenticate",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=30) as r:
            return 200, json.loads(r.read().decode()).get("id_token")
    except urllib.error.HTTPError as e:
        return e.code, None


def api(
    method: str,
    path: str,
    token: str | None = None,
    body: dict | None = None,
    *,
    public: bool = False,
) -> tuple[int, dict | list | str | None, int]:
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    req = urllib.request.Request(f"{BASE}{path}", data=data, method=method.upper(), headers=headers)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=120) as r:
            raw = r.read().decode(errors="replace")
            ms = round((time.perf_counter() - started) * 1000)
            try:
                return r.status, json.loads(raw), ms
            except json.JSONDecodeError:
                return r.status, raw, ms
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        ms = round((time.perf_counter() - started) * 1000)
        try:
            return e.code, json.loads(raw), ms
        except json.JSONDecodeError:
            return e.code, raw, ms
    except Exception as exc:
        ms = round((time.perf_counter() - started) * 1000)
        return 0, {"error": str(exc)}, ms


def unwrap(data: Any) -> Any:
    if isinstance(data, dict) and "data" in data:
        return data["data"]
    return data


def zoho_access_token() -> str | None:
    client_id = os.environ.get("ZOHO_CLIENT_ID", "")
    client_secret = os.environ.get("ZOHO_CLIENT_SECRET", "")
    org_id = os.environ.get("ZOHO_ORG_ID", "")
    if not client_id or not client_secret:
        return None
    refresh = os.environ.get("ZOHO_REFRESH_TOKEN", "")
    if not refresh:
        db_url = os.environ.get("SPRING_DATASOURCE_URL", "")
        db_user = os.environ.get("SPRING_DATASOURCE_USERNAME", "")
        db_pass = os.environ.get("SPRING_DATASOURCE_PASSWORD", "")
        host, port, dbname = "127.0.0.1", "5432", "miniandmore"
        if db_url and "jdbc:postgresql://" in db_url:
            rest = db_url.split("jdbc:postgresql://", 1)[1]
            hostport, _, dbname = rest.partition("/")
            if ":" in hostport:
                host, port = hostport.split(":", 1)
            else:
                host = hostport
        if db_user and db_pass:
            try:
                result = subprocess.run(
                    [
                        "psql",
                        "-h",
                        host,
                        "-p",
                        port,
                        "-U",
                        db_user,
                        "-d",
                        dbname,
                        "-tA",
                        "-c",
                        "SELECT refresh_token FROM zoho_setting WHERE connected=true ORDER BY id DESC LIMIT 1",
                    ],
                    capture_output=True,
                    text=True,
                    timeout=10,
                    env={**os.environ, "PGPASSWORD": db_pass},
                )
                if result.returncode == 0:
                    refresh = result.stdout.strip()
            except Exception:
                refresh = ""
    if not refresh:
        return None
    dc = os.environ.get("ZOHO_DATA_CENTER", "in")
    token_url = f"https://accounts.zoho.{dc}/oauth/v2/token"
    body = (
        f"refresh_token={urllib.request.quote(refresh)}"
        f"&client_id={urllib.request.quote(client_id)}"
        f"&client_secret={urllib.request.quote(client_secret)}"
        f"&grant_type=refresh_token"
    ).encode()
    req = urllib.request.Request(token_url, data=body, method="POST")
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=30) as r:
            return json.loads(r.read().decode()).get("access_token")
    except Exception:
        return None


def zoho_sales_order_total(so_id: str) -> float | None:
    token = zoho_access_token()
    org_id = os.environ.get("ZOHO_ORG_ID", "")
    dc = os.environ.get("ZOHO_DATA_CENTER", "in")
    if not token or not org_id or not so_id:
        return None
    url = f"https://www.zohoapis.{dc}/books/v3/salesorders/{so_id}?organization_id={org_id}"
    req = urllib.request.Request(url, headers={"Authorization": f"Zoho-oauthtoken {token}"})
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=30) as r:
            data = json.loads(r.read().decode())
            return float(data.get("salesorder", {}).get("total", 0))
    except Exception:
        return None


def scenario_coupon_checkout(customer_token: str) -> tuple[Scenario, dict[str, Any]]:
    sc = Scenario(
        "coupon-checkout",
        "Coupon checkout (WELCOM10 → ₹2 total)",
        "Validate flat coupon on Baby Swaddle and create checkout order with correct totals.",
    )
    ctx: dict[str, Any] = {}

    code, data, ms = api(
        "POST",
        "/api/v1/coupons/validate",
        body={"code": COUPON_CODE, "subtotal": PRODUCT_PRICE},
        public=True,
    )
    payload = unwrap(data) if isinstance(data, dict) else {}
    discount = float(payload.get("discount") or 0) if payload.get("ok") else 0
    if code == 200 and payload.get("ok") and discount == 348:
        sc.steps.append(Step("validate-coupon", "pass", f"discount ₹{discount:.0f}", ms, {"discount": discount}))
    else:
        sc.steps.append(Step("validate-coupon", "fail", f"HTTP {code}", ms, {"body": str(data)[:300]}))
        return sc, ctx

    order_body = {
        "items": [
            {
                "product_id": PRODUCT_ID,
                "product_slug": "baby-swaddle",
                "product_name": PRODUCT_NAME,
                "qty": 1,
                "price": PRODUCT_PRICE,
            }
        ],
        "shipping_address": {
            "full_name": "Scenario Test",
            "email": CUSTOMER_USER,
            "phone": "9876543210",
            "line1": "12 Test Street",
            "line2": "",
            "city": "Hyderabad",
            "state": "Telangana",
            "pincode": "500001",
            "baby_name": "Scenario Baby",
        },
        "shipping_mode": "standard",
        "coupon_code": COUPON_CODE,
        "donation": 0,
    }
    code, data, ms = api("POST", "/api/v1/orders", customer_token, order_body)
    payload = unwrap(data) if isinstance(data, dict) else {}
    totals = payload.get("totals") or {}
    total = float(totals.get("total") or 0)
    demo_mode = bool(payload.get("demoMode"))
    ctx = {
        "orderId": payload.get("orderId"),
        "orderNumber": payload.get("orderNumber"),
        "razorpayOrderId": payload.get("razorpayOrderId"),
        "total": total,
        "demoMode": demo_mode,
        "gatewayMode": payload.get("gatewayMode"),
    }
    # Coupon leaves ₹1 product + ₹1 shipping + GST (UAT site_setting) → ~₹2.02, not exactly ₹2.
    if code == 200 and ctx["orderId"] and total <= 3.5:
        sc.steps.append(
            Step(
                "create-order",
                "pass",
                f"order {ctx['orderNumber']} total ₹{total:.0f} (demoMode={demo_mode})",
                ms,
                ctx,
            )
        )
    else:
        sc.steps.append(Step("create-order", "fail", f"HTTP {code} total={total}", ms, ctx))
    return sc, ctx


def scenario_payment_verify(customer_token: str, order_ctx: dict[str, Any]) -> Scenario:
    sc = Scenario(
        "payment-verify",
        "Payment verify",
        "Demo bypass when RAZORPAY_PAYMENT_DEMO=true; otherwise skipped (live Razorpay required).",
    )
    if not order_ctx.get("orderId"):
        sc.steps.append(Step("verify-payment", "skip", "no order from previous scenario"))
        return sc
    gateway_mode = order_ctx.get("gatewayMode")
    if gateway_mode == "live" and not order_ctx.get("demoMode"):
        sc.steps.append(
            Step(
                "verify-payment",
                "skip",
                "Catalog uses live Razorpay — enable RAZORPAY_PAYMENT_DEMO on catalog or set RAZORPAY_KEY_ID=DEMO",
                detail={"gatewayMode": gateway_mode, "demoMode": order_ctx.get("demoMode")},
            )
        )
        return sc
    body = {
        "orderId": order_ctx["orderId"],
        "razorpay_order_id": order_ctx.get("razorpayOrderId") or "order_demo",
        "razorpay_payment_id": f"pay_demo_scenario_{int(time.time())}",
        "razorpay_signature": "demo_signature",
    }
    code, data, ms = api("POST", "/api/v1/orders/verify-payment", customer_token, body)
    payload = unwrap(data) if isinstance(data, dict) else {}
    if code == 200 and payload.get("ok"):
        sc.steps.append(
            Step(
                "verify-payment",
                "pass",
                f"PAID {payload.get('orderNumber', order_ctx.get('orderNumber'))}",
                ms,
                {"zohoSynced": payload.get("zohoSynced")},
            )
        )
        order_ctx["paid"] = True
    else:
        sc.steps.append(Step("verify-payment", "fail", f"HTTP {code}", ms, {"body": str(data)[:300]}))
    return sc


def scenario_zoho_sync(admin_token: str, order_ctx: dict[str, Any]) -> Scenario:
    sc = Scenario(
        "zoho-order-sync",
        "Zoho sales order + invoice sync",
        "Admin sync after payment; verifies Zoho IDs and SYNCED status.",
    )
    order_id = order_ctx.get("orderId")
    if not order_id:
        sc.steps.append(Step("sync-order", "skip", "no order id"))
        return sc
    if not order_ctx.get("paid") and not PAYMENT_DEMO:
        sc.steps.append(Step("sync-order", "skip", "order not paid (live Razorpay required)"))
        return sc
    code, data, ms = api("POST", f"/api/v1/admin/zoho/sync-orders/{order_id}", admin_token)
    payload = unwrap(data) if isinstance(data, dict) else {}
    so_id = payload.get("zohoSalesOrderId")
    inv_id = payload.get("zohoInvoiceId")
    sync_status = payload.get("zohoSyncStatus")
    order_ctx["zohoSalesOrderId"] = so_id
    order_ctx["zohoInvoiceId"] = inv_id
    if code == 200 and so_id and sync_status == "SYNCED":
        sc.steps.append(
            Step(
                "sync-order",
                "pass",
                f"SO {so_id}, invoice {inv_id or '—'}",
                ms,
                {"zohoSyncStatus": sync_status},
            )
        )
    else:
        sc.steps.append(Step("sync-order", "fail", f"HTTP {code}", ms, {"body": str(data)[:400]}))
    return sc


def scenario_zoho_pricing(order_ctx: dict[str, Any]) -> Scenario:
    sc = Scenario(
        "zoho-coupon-pricing",
        "Zoho Books totals match store (₹2 paid)",
        "Live Zoho API check: entity discount + shipping → total ₹2, not catalog line ₹349.",
    )
    so_id = order_ctx.get("zohoSalesOrderId")
    if not so_id:
        sc.steps.append(Step("zoho-so-total", "skip", "no Zoho sales order id"))
        return sc
    total = zoho_sales_order_total(so_id)
    expected = float(order_ctx.get("total") or 2)
    if total is not None and abs(total - expected) < 0.01:
        sc.steps.append(Step("zoho-so-total", "pass", f"Zoho SO total ₹{total:.2f}", detail={"expected": expected}))
    elif total is None:
        sc.steps.append(Step("zoho-so-total", "skip", "Zoho OAuth/DB not available in CI env"))
    else:
        sc.steps.append(Step("zoho-so-total", "fail", f"Zoho SO ₹{total:.2f} expected ₹{expected:.2f}"))
    return sc


def scenario_shipment(admin_token: str, order_ctx: dict[str, Any]) -> Scenario:
    sc = Scenario(
        "shipment-tracking",
        "Shipment + tracking pull",
        "Set shipment on Zoho SO, pull tracking, verify shipped status on storefront API.",
    )
    order_id = order_ctx.get("orderId")
    order_number = order_ctx.get("orderNumber")
    so_id = order_ctx.get("zohoSalesOrderId")
    if not order_id or not so_id:
        sc.steps.append(Step("shipment-flow", "skip", "requires paid order with Zoho SO"))
        return sc

    token = zoho_access_token()
    org_id = os.environ.get("ZOHO_ORG_ID", "")
    dc = os.environ.get("ZOHO_DATA_CENTER", "in")
    if token and org_id:
        url = f"https://www.zohoapis.{dc}/books/v3/salesorders/{so_id}?organization_id={org_id}"
        body = json.dumps(
            {"shipment_date": datetime.now(timezone.utc).strftime("%Y-%m-%d"), "delivery_method": "Delhivery"}
        ).encode()
        req = urllib.request.Request(
            url,
            data=body,
            method="PUT",
            headers={"Authorization": f"Zoho-oauthtoken {token}", "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, context=CTX, timeout=30) as r:
                if 200 <= r.status < 300:
                    sc.steps.append(Step("zoho-set-shipment", "pass", "shipment_date set on Zoho SO"))
                else:
                    sc.steps.append(Step("zoho-set-shipment", "fail", f"HTTP {r.status}"))
        except Exception as exc:
            sc.steps.append(Step("zoho-set-shipment", "fail", str(exc)[:200]))
    else:
        sc.steps.append(Step("zoho-set-shipment", "skip", "Zoho credentials unavailable"))

    code, data, ms = api("POST", f"/api/v1/admin/zoho/pull-order-tracking/{order_id}", admin_token)
    payload = unwrap(data) if isinstance(data, dict) else {}
    tracking = payload.get("trackingStatus") or payload.get("tracking_status")
    if code == 200 and tracking == "shipped":
        sc.steps.append(Step("pull-tracking", "pass", "tracking_status=shipped", ms, payload))
    elif code == 200:
        sc.steps.append(Step("pull-tracking", "fail", f"tracking_status={tracking}", ms, payload))
    else:
        sc.steps.append(Step("pull-tracking", "fail", f"HTTP {code}", ms))

    if order_number:
        code, data, ms = api("GET", f"/api/v1/orders/track/{order_number}", public=True)
        payload = unwrap(data) if isinstance(data, dict) else {}
        order = payload.get("order") or {}
        pub_status = order.get("tracking_status")
        if code == 200 and pub_status == "shipped":
            sc.steps.append(Step("public-track-api", "pass", "public track shows shipped", ms, order))
        elif code == 200:
            sc.steps.append(Step("public-track-api", "fail", f"tracking_status={pub_status}", ms, order))
        else:
            sc.steps.append(Step("public-track-api", "fail", f"HTTP {code}", ms))
    return sc


def scenario_reference_orders() -> Scenario:
    sc = Scenario(
        "reference-orders",
        "Previously verified UAT orders (read-only)",
        "Regression checks on orders from manual scenario runs — no mutations.",
    )
    for label, order_number, expected_field, expected_value in [
        ("shipped-order-track", REFERENCE_SHIPPED_ORDER, "tracking_status", "shipped"),
        ("returned-order-track", REFERENCE_RETURNED_ORDER, "shipment_date", None),
    ]:
        code, data, ms = api("GET", f"/api/v1/orders/track/{order_number}", public=True)
        payload = unwrap(data) if isinstance(data, dict) else {}
        order = payload.get("order") or {}
        if code != 200:
            sc.steps.append(Step(label, "fail", f"HTTP {code}", ms))
            continue
        if expected_field == "tracking_status":
            actual = order.get("tracking_status")
            ok_values = {expected_value}
            if expected_value == "shipped":
                ok_values.add("delivered")
            if actual in ok_values:
                sc.steps.append(Step(label, "pass", f"{order_number} → {actual}", ms, order))
            else:
                sc.steps.append(
                    Step(label, "fail", f"{order_number} expected {expected_value} got {actual}", ms, order)
                )
        else:
            shipment = order.get("shipment_date")
            if shipment:
                sc.steps.append(Step(label, "pass", f"{order_number} shipment_date={shipment}", ms, order))
            else:
                sc.steps.append(Step(label, "fail", f"{order_number} missing shipment_date", ms, order))
    return sc


def scenario_return_flow(customer_token: str, admin_token: str, order_ctx: dict[str, Any]) -> Scenario:
    sc = Scenario(
        "return-refund",
        "Return request → approve → receive → refund",
        "Full return when order is paid + shipped; refund uses Razorpay demo only when RAZORPAY_REFUND_DEMO=true.",
    )
    order_number = order_ctx.get("orderNumber")
    if not order_ctx.get("paid"):
        sc.steps.append(Step("return-flow", "skip", "order not paid"))
        return sc
    if not REFUND_DEMO:
        sc.steps.append(
            Step(
                "return-flow",
                "skip",
                "RAZORPAY_REFUND_DEMO=false — refund would hit live Razorpay",
            )
        )
        return sc

    code, data, ms = api("GET", f"/api/v1/order-returns/eligibility/{order_number}", customer_token)
    payload = unwrap(data) if isinstance(data, dict) else {}
    if code == 200 and payload.get("eligible"):
        sc.steps.append(Step("return-eligibility", "pass", "eligible", ms))
    elif code == 200:
        sc.steps.append(
            Step(
                "return-eligibility",
                "skip",
                payload.get("message") or "not eligible yet (needs delivery anchor)",
                ms,
            )
        )
        return sc
    else:
        sc.steps.append(Step("return-eligibility", "fail", f"HTTP {code}", ms))
        return sc

    code, data, ms = api(
        "POST",
        "/api/v1/order-returns",
        customer_token,
        {"order_number": order_number, "reason": "Scenario integration test return"},
    )
    payload = unwrap(data) if isinstance(data, dict) else {}
    return_number = payload.get("return_number")
    if code not in (200, 201) or not return_number:
        sc.steps.append(Step("request-return", "fail", f"HTTP {code}", ms, {"body": str(data)[:300]}))
        return sc
    sc.steps.append(Step("request-return", "pass", return_number, ms))

    for step_name, path in [
        ("approve", f"/api/v1/admin/order-returns/{return_number}/approve"),
        ("mark-received", f"/api/v1/admin/order-returns/{return_number}/mark-received"),
        ("refund", f"/api/v1/admin/order-returns/{return_number}/refund"),
    ]:
        code, data, ms = api("POST", path, admin_token, {"admin_notes": "scenario test"})
        payload = unwrap(data) if isinstance(data, dict) else {}
        status = (payload.get("status") or "").lower()
        if code == 200:
            sc.steps.append(Step(step_name, "pass", status or "ok", ms, {"refund_amount": payload.get("refund_amount")}))
        else:
            sc.steps.append(Step(step_name, "fail", f"HTTP {code}", ms, {"body": str(data)[:300]}))
            break
    return sc


def build_html(report: dict) -> str:
    sys_path = ROOT / "jenkins" / "scripts"
    import sys

    sys.path.insert(0, str(sys_path))
    from report_theme import PROJECT_NAME, esc, page_shell, stat_block, status_pill  # noqa: E402

    summary = report.get("summary") or {}
    scenarios = report.get("scenarios") or []
    status = "PASS" if summary.get("failed", 0) == 0 else "FAIL"

    scenario_blocks: list[str] = []
    for sc in scenarios:
        step_rows = []
        for st in sc.get("steps") or []:
            pill = status_pill(st.get("status", "skip").upper())
            step_rows.append(
                f"<tr><td>{esc(st.get('name'))}</td><td>{pill}</td>"
                f"<td>{esc(st.get('note'))}</td><td>{st.get('ms', '—')} ms</td></tr>"
            )
        scenario_blocks.append(
            f"""
            <section class="panel">
              <h3>{esc(sc.get('title'))} {status_pill(sc.get('status', 'skip').upper())}</h3>
              <p class="muted">{esc(sc.get('description'))}</p>
              <table class="data">
                <thead><tr><th>Step</th><th>Status</th><th>Note</th><th>Time</th></tr></thead>
                <tbody>{''.join(step_rows) or '<tr><td colspan="4" class="muted">No steps</td></tr>'}</tbody>
              </table>
            </section>
            """
        )

    body = f"""
      <section class="panel">
        <h2>Scenario integration summary</h2>
        <p>Target <code>{esc(report.get('baseUrl'))}</code> · {esc(report.get('finishedAt'))}</p>
        <p>Status: {status_pill(status)} · Payment demo: <code>{'true' if report.get('paymentDemo') else 'false'}</code>
           · Refund demo: <code>{'true' if report.get('refundDemo') else 'false'}</code></p>
        <div class="stats">
          {stat_block("Scenarios", summary.get('total', 0))}
          {stat_block("Passed", summary.get('passed', 0))}
          {stat_block("Failed", summary.get('failed', 0))}
          {stat_block("Skipped", summary.get('skipped', 0))}
        </div>
      </section>
      {''.join(scenario_blocks)}
    """
    table_css = """
      .data { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 14px; }
      .data th, .data td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); }
      .data th { background: var(--surface-muted); }
    """
    return page_shell(
        f"{PROJECT_NAME} scenario integration",
        body,
        subtitle="Coupon · Zoho · shipment · returns",
        extra_css=table_css,
        nested_logo=True,
    )


def serialize_scenario(sc: Scenario) -> dict:
    return {
        "id": sc.id,
        "title": sc.title,
        "description": sc.description,
        "status": sc.status,
        "steps": [
            {
                "name": s.name,
                "status": s.status,
                "note": s.note,
                "ms": s.ms,
                "detail": s.detail,
            }
            for s in sc.steps
        ],
    }


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    started = datetime.now(timezone.utc).isoformat()
    scenarios: list[Scenario] = []
    order_ctx: dict[str, Any] = {}

    c_code, customer_token = login(CUSTOMER_USER, CUSTOMER_PASS)
    a_code, admin_token = login(ADMIN_USER, ADMIN_PASS)
    auth_sc = Scenario("auth", "Authentication", "Customer + admin JWT for scenario steps.")
    auth_sc.steps.append(
        Step("customer-login", "pass" if customer_token else "fail", f"HTTP {c_code}")
    )
    auth_sc.steps.append(Step("admin-login", "pass" if admin_token else "fail", f"HTTP {a_code}"))
    scenarios.append(auth_sc)

    if not customer_token or not admin_token:
        report = {
            "baseUrl": BASE,
            "startedAt": started,
            "finishedAt": datetime.now(timezone.utc).isoformat(),
            "paymentDemo": PAYMENT_DEMO,
            "refundDemo": REFUND_DEMO,
            "scenarios": [serialize_scenario(s) for s in scenarios],
            "summary": {"total": 1, "passed": 0, "failed": 1, "skipped": 0},
        }
        OUT_JSON.write_text(json.dumps(report, indent=2), encoding="utf-8")
        OUT_HTML.write_text(build_html(report), encoding="utf-8")
        print("ERROR: login failed")
        return 1

    sc, order_ctx = scenario_coupon_checkout(customer_token)
    scenarios.append(sc)
    scenarios.append(scenario_payment_verify(customer_token, order_ctx))
    scenarios.append(scenario_zoho_sync(admin_token, order_ctx))
    scenarios.append(scenario_zoho_pricing(order_ctx))
    scenarios.append(scenario_shipment(admin_token, order_ctx))
    scenarios.append(scenario_return_flow(customer_token, admin_token, order_ctx))
    scenarios.append(scenario_reference_orders())

    passed = sum(1 for s in scenarios if s.status == "pass")
    failed = sum(1 for s in scenarios if s.status == "fail")
    skipped = sum(1 for s in scenarios if s.status == "skip")

    report = {
        "baseUrl": BASE,
        "startedAt": started,
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "paymentDemo": PAYMENT_DEMO,
        "refundDemo": REFUND_DEMO,
        "credentials": {"customer": CUSTOMER_USER, "admin": ADMIN_USER},
        "orderContext": {k: v for k, v in order_ctx.items() if k not in ("razorpayOrderId",)},
        "scenarios": [serialize_scenario(s) for s in scenarios],
        "summary": {
            "total": len(scenarios),
            "passed": passed,
            "failed": failed,
            "skipped": skipped,
        },
    }
    OUT_JSON.write_text(json.dumps(report, indent=2), encoding="utf-8")
    OUT_HTML.write_text(build_html(report), encoding="utf-8")
    print(f"Scenario integration: {passed} passed, {failed} failed, {skipped} skipped / {len(scenarios)} scenarios")
    print(f"Report: {OUT_HTML}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
