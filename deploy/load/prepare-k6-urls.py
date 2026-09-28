#!/usr/bin/env python3
"""Build a resolved GET URL list from OpenAPI for k6 load tests."""

from __future__ import annotations

import argparse
import json
import os
import re
import ssl
import sys
import urllib.error
import urllib.request
from pathlib import Path

HTTP = {"get"}
CTX = ssl.create_default_context()
SPEC_PATH = Path(__file__).resolve().parents[1] / "postman" / "openapi.snapshot.json"

PUBLIC_GET_PREFIXES = (
    "/api/v1/categories",
    "/api/v1/products",
    "/api/v1/blog-posts",
    "/api/v1/banners",
    "/api/v1/home-sections",
    "/api/v1/page-visibility",
    "/api/v1/site-settings",
    "/api/v1/policies",
)

SKIP_PREFIXES = (
    "/api/v1/admin/zoho/",
    "/api/v1/admin/uploads",
)


def request(method: str, path: str, token: str | None, body=None):
    url = os.environ.get("API_TEST_BASE_URL", "https://uat.minisnmore.com").rstrip("/") + path
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode(errors="replace")


def login(username: str, password: str) -> str | None:
    st, raw = request("POST", "/api/authenticate", None, {"username": username, "password": password})
    if st < 200 or st >= 300:
        return None
    try:
        data = json.loads(raw)
        return data.get("id_token") or data.get("idToken")
    except json.JSONDecodeError:
        return None


def unwrap_list(payload):
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict):
        for key in ("data", "items", "content"):
            value = payload.get(key)
            if isinstance(value, list):
                return value
    return []


def first_id(raw: str) -> str | None:
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return None
    rows = unwrap_list(data)
    if not rows:
        return None
    row = rows[0]
    if isinstance(row, dict):
        for key in ("id", "slug", "name", "orderNumber"):
            if row.get(key) is not None:
                return str(row[key]).strip()
    return None


def pick_token(path: str, admin_tok: str | None, customer_tok: str | None) -> str | None:
    if path.startswith(PUBLIC_GET_PREFIXES):
        return None
    if path.startswith("/api/v1/admin/") or path.startswith("/api/admin/"):
        return admin_tok
    if path.startswith("/api/v1/account/") or path.startswith("/api/v1/orders"):
        return customer_tok
    if path.startswith("/api/"):
        return admin_tok
    return admin_tok


def resolve_path(path: str, cache: dict) -> str | None:
    out = path
    for param in re.findall(r"\{([^}]+)\}", path):
        val = cache.get(param)
        if val is None and param == "slug":
            if "blog" in path:
                val = cache.get("blogSlug")
            elif "product" in path:
                val = cache.get("productSlug")
            elif "polic" in path:
                val = cache.get("policySlug")
        if val is None:
            return None
        out = out.replace("{" + param + "}", str(val))
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--admin-user", default=os.environ.get("API_TEST_ADMIN_USER", "admin"))
    parser.add_argument("--admin-pass", default=os.environ.get("API_TEST_ADMIN_PASS", "admin6"))
    parser.add_argument("--customer-user", default=os.environ.get("API_TEST_CUSTOMER_USER", "irfanpatan9948@gmail.com"))
    parser.add_argument("--customer-pass", default=os.environ.get("API_TEST_CUSTOMER_PASS", "Zero@1234"))
    args = parser.parse_args()

    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    admin_tok = login(args.admin_user, args.admin_pass)
    customer_tok = login(args.customer_user, args.customer_pass)

    cache: dict[str, str] = {}
    st, raw = request("GET", "/api/v1/products", None)
    if st >= 200 and st < 300:
        rows = unwrap_list(json.loads(raw))
        if rows and isinstance(rows[0], dict):
            cache["id"] = str(rows[0].get("id") or "")
            cache["productSlug"] = str(rows[0].get("slug") or "").strip()
    st, raw = request("GET", "/api/v1/blog-posts", None)
    if st >= 200 and st < 300:
        rows = unwrap_list(json.loads(raw))
        for row in rows:
            if isinstance(row, dict) and row.get("published") is not False:
                slug = str(row.get("slug") or "").strip()
                if slug:
                    cache["blogSlug"] = slug
                    break
    st, raw = request("GET", "/api/v1/policies", None)
    if st >= 200 and st < 300:
        rows = unwrap_list(json.loads(raw))
        if rows and isinstance(rows[0], dict):
            cache["policySlug"] = str(rows[0].get("slug") or "privacy").strip()

    if admin_tok:
        for list_path in (
            "/api/v1/admin/banners",
            "/api/v1/admin/categories",
            "/api/v1/admin/coupons",
            "/api/v1/admin/policies",
            "/api/v1/admin/products",
            "/api/v1/admin/blog-posts",
        ):
            st, raw = request("GET", list_path, admin_tok)
            if st >= 200 and st < 300:
                fid = first_id(raw)
                if fid:
                    cache[list_path] = fid

    endpoints: list[dict] = []
    for path, path_item in sorted(spec.get("paths", {}).items()):
        if any(path.startswith(p) for p in SKIP_PREFIXES):
            continue
        op = path_item.get("get")
        if not op:
            continue
        if any(p.get("in") == "query" and p.get("required") for p in op.get("parameters") or []):
            continue
        resolved = resolve_path(path, cache)
        if resolved is None and "{" in path:
            base = re.sub(r"/\{[^}]+\}$", "", path)
            tok = pick_token(base, admin_tok, customer_tok)
            st, raw = request("GET", base, tok)
            if st >= 200 and st < 300:
                fid = first_id(raw)
                if fid:
                    if path.endswith("/{id}"):
                        cache["id"] = fid
                    elif path.endswith("/{slug}"):
                        if "blog" in path:
                            cache["blogSlug"] = fid
                        elif "product" in path:
                            cache["productSlug"] = fid
                        elif "polic" in path:
                            cache["policySlug"] = fid
                    elif path.endswith("/{login}"):
                        cache["login"] = fid
                    elif path.endswith("/{name}"):
                        cache["name"] = fid
                    elif path.endswith("/{orderNumber}"):
                        cache["orderNumber"] = fid
                    cache[base] = fid
                resolved = resolve_path(path, cache)
        if resolved is None:
            continue
        auth = pick_token(path, admin_tok, customer_tok)
        tag = (op.get("tags") or ["misc"])[0]
        endpoints.append(
            {
                "method": "GET",
                "path": resolved,
                "openapiPath": path,
                "module": tag,
                "auth": "admin" if auth == admin_tok and admin_tok else ("customer" if auth == customer_tok and customer_tok else "none"),
            }
        )

    payload = {
        "baseUrl": os.environ.get("API_TEST_BASE_URL", "https://uat.minisnmore.com").rstrip("/"),
        "adminAuthenticated": bool(admin_tok),
        "customerAuthenticated": bool(customer_tok),
        "endpointCount": len(endpoints),
        "endpoints": endpoints,
    }
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(f"Prepared {len(endpoints)} GET endpoints for k6 → {out}")
    if len(endpoints) < 10:
        print("WARN: very few endpoints resolved; check UAT credentials and data.", file=sys.stderr)


if __name__ == "__main__":
    main()
