#!/usr/bin/env python3
"""E2E: create simple + variant product, place order, verify stock/SKU/pricing on UAT.

Usage:
  GATEWAY_URL=http://127.0.0.1:9180 ADMIN_USER=admin ADMIN_PASS=admin6 \\
    python3 deploy/scripts/prod-e2e-variant-order-test.py
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from typing import Any

GATEWAY = os.environ.get("GATEWAY_URL", "http://127.0.0.1:9180").rstrip("/")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ADMIN_PASS", "admin6")
CATEGORY_ID = int(os.environ.get("E2E_CATEGORY_ID", "26402"))
SLUG = "uat-e2e-align-jul22"
PARENT_SKU = "E2E-ALIGN-JUL22"
VARIANT_SKU = f"{PARENT_SKU}-M-NAVY"

PASS = 0
FAIL = 0


def log(ok: bool, name: str, detail: str = "") -> None:
    global PASS, FAIL
    line = f"{'PASS' if ok else 'FAIL'}  {name}" + (f" — {detail}" if detail else "")
    print(line, file=sys.stdout if ok else sys.stderr)
    if ok:
        PASS += 1
    else:
        FAIL += 1


def request(method: str, path: str, body: Any | None = None, token: str | None = None) -> tuple[int, Any]:
    url = path if path.startswith("http") else f"{GATEWAY}{path}"
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            raw = resp.read().decode()
            try:
                return resp.getcode(), json.loads(raw) if raw else None
            except json.JSONDecodeError:
                return resp.getcode(), raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw) if raw else None
        except json.JSONDecodeError:
            return e.code, raw


def unwrap(payload: Any) -> Any:
    if isinstance(payload, dict) and "data" in payload:
        return payload["data"]
    return payload


def auth() -> str:
    code, body = request("POST", "/api/authenticate", {"username": ADMIN_USER, "password": ADMIN_PASS, "rememberMe": False})
    token = (body or {}).get("id_token") if isinstance(body, dict) else None
    if code != 200 or not token:
        raise RuntimeError(f"auth failed: {code} {body}")
    return token


def parse_variants(raw: Any) -> list[dict]:
    if raw is None:
        return []
    if isinstance(raw, list):
        return raw
    if isinstance(raw, str) and raw.strip():
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return []
    return []


def db_query(sql: str) -> str | None:
    password = os.environ.get("PGPASSWORD", "Kzer!@A123")
    user = os.environ.get("PGUSER", "minisandmore_user")
    db = os.environ.get("PGDATABASE", "miniandmore")
    host = os.environ.get("PGHOST", "127.0.0.1")
    try:
        out = subprocess.check_output(
            ["psql", "-h", host, "-U", user, "-d", db, "-tAc", sql],
            env={**os.environ, "PGPASSWORD": password},
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=8,
        ).strip()
        return out or None
    except (subprocess.SubprocessError, OSError):
        return None


def find_product(token: str) -> dict | None:
    code, body = request("GET", "/api/v1/admin/products", token=token)
    for row in unwrap(body) or []:
        if row.get("slug") == SLUG or row.get("sku") == PARENT_SKU:
            return row
    return None


def main() -> int:
    print(f"=== E2E variant order test @ {GATEWAY} ===\n")
    token = auth()
    log(True, "Admin authenticate")

    # --- 1. Create or reset product ---
    existing = find_product(token)
    matrix = [
        {
            "sku": VARIANT_SKU,
            "size": "M",
            "color": "Navy",
            "rate": 1039.2,
            "mrp": 1299,
            "cost": 500,
            "stock": 5,
            "active": True,
        },
        {
            "sku": f"{PARENT_SKU}-S-IVORY",
            "size": "S",
            "color": "Ivory",
            "rate": 959.2,
            "mrp": 1199,
            "cost": 450,
            "stock": 0,
            "active": True,
        },
    ]
    product = {
        "slug": SLUG,
        "name": "E2E Align Test Jul22",
        "sku": PARENT_SKU,
        "description": "Disposable E2E test product for variant order and stock verification on UAT.",
        "price": 1039.2,
        "compareAtPrice": 1299,
        "currency": "INR",
        "sizesJson": json.dumps(["S", "M"]),
        "colorsJson": json.dumps(["Navy", "Ivory"]),
        "itemStructure": "VARIANTS",
        "variantsJson": json.dumps(matrix),
        "imagesJson": "[]",
        "stock": 5,
        "sortOrder": 9998,
        "featured": False,
        "trending": False,
        "newArrival": False,
        "bestSeller": False,
        "active": True,
        "category": {"id": CATEGORY_ID},
    }
    if existing:
        product["id"] = existing["id"]
        code, saved = request("PUT", f"/api/v1/admin/products/{existing['id']}", product, token=token)
    else:
        code, saved = request("POST", "/api/v1/admin/products", product, token=token)

    saved = unwrap(saved)
    ok = code in (200, 201) and isinstance(saved, dict)
    log(ok, "Create/update VARIANTS product", f"status={code} id={saved.get('id') if isinstance(saved, dict) else '?'}")
    if not ok:
        return 1
    pid = saved["id"]

    # Parent aggregates: min selling 959.2? No - S Ivory is 0 stock but active - min selling among active with rate>0: M Navy 1039.2 and S Ivory 959.2 → min 959.2
    # Actually aggregates only considers active rows - both active. min selling = 959.2, min mrp = 1199
    saved_price = float(saved.get("price") or 0)
    saved_mrp = float(saved.get("compareAtPrice") or 0)
    saved_stock = int(saved.get("stock") or 0)
    log(abs(saved_price - 959.2) < 0.02, "Parent selling = min variant rate", f"price={saved_price}")
    log(abs(saved_mrp - 1199) < 0.02, "Parent MRP = min variant mrp", f"compareAt={saved_mrp}")
    log(saved_stock == 5, "Parent stock = sum active variant stock", f"stock={saved_stock}")

    # --- 2. Storefront ---
    sc, sf = request("GET", f"/api/v1/products/{SLUG}?_={int(time.time()*1000)}")
    sf = unwrap(sf)
    log(sc == 200 and isinstance(sf, dict), "Storefront GET product", f"status={sc}")
    if isinstance(sf, dict):
        rows = parse_variants(sf.get("variantsJson"))
        m_navy = next((r for r in rows if r.get("sku") == VARIANT_SKU), None)
        s_ivory = next((r for r in rows if r.get("sku") == f"{PARENT_SKU}-S-IVORY"), None)
        log(m_navy is not None and int(m_navy.get("stock") or 0) == 5, "Storefront M-Navy in stock", str(m_navy))
        log(s_ivory is not None and int(s_ivory.get("stock") or 0) == 0, "Storefront S-Ivory OOS", str(s_ivory))

    # --- 3. Order: valid variant (M-Navy) ---
    order = {
        "items": [
            {
                "product_id": str(pid),
                "product_slug": SLUG,
                "product_name": saved.get("name"),
                "product_image": "",
                "sku": VARIANT_SKU,
                "size": "M",
                "color": "Navy",
                "qty": 1,
                "price": 1039.2,
            }
        ],
        "shipping_address": {
            "full_name": "E2E Tester",
            "phone": "9999999999",
            "email": "e2e@test.local",
            "line1": "1 Test Lane",
            "line2": "",
            "city": "Bengaluru",
            "state": "Karnataka",
            "pincode": "560001",
            "baby_name": "",
        },
        "shipping_mode": "STANDARD",
        "coupon_code": None,
        "donation": 0,
        "notes": "uat-e2e-variant-order-test",
    }
    stock_before = saved_stock
    variant_before = 5
    code, resp = request("POST", "/api/v1/orders", order, token=token)
    data = unwrap(resp) if isinstance(resp, dict) else resp
    order_num = (data or {}).get("orderNumber") if isinstance(data, dict) else None
    order_ok = code == 200 and isinstance(data, dict)
    log(order_ok, "Place order for in-stock variant M-Navy", f"status={code} order={order_num}")

    time.sleep(0.5)
    _, sf2 = request("GET", f"/api/v1/products/{SLUG}?_={int(time.time()*1000)}")
    sf2 = unwrap(sf2)
    stock_after = int((sf2 or {}).get("stock") or 0) if isinstance(sf2, dict) else -1
    variant_after = None
    if isinstance(sf2, dict):
        for r in parse_variants(sf2.get("variantsJson")):
            if r.get("sku") == VARIANT_SKU:
                variant_after = int(r.get("stock") or 0)
    if order_ok:
        log(stock_after == stock_before - 1, "Parent stock decremented", f"{stock_before}->{stock_after}")
        log(variant_after == variant_before - 1, "Variant M-Navy stock decremented", f"{variant_before}->{variant_after}")

        oid = db_query(
            f"SELECT oi.variant_sku FROM order_item oi JOIN customer_order co ON co.id = oi.order_id "
            f"WHERE co.order_number = '{order_num}' LIMIT 1;"
        )
        log(oid == VARIANT_SKU, "Order line variant_sku in DB", oid or "missing")
    else:
        log(stock_after == stock_before, "Stock unchanged when order failed", f"{stock_before}->{stock_after}")

    # --- 4. Order: OOS variant (S-Ivory) must fail ---
    order_oos = dict(order)
    order_oos["items"] = [
        {
            **order["items"][0],
            "sku": f"{PARENT_SKU}-S-IVORY",
            "size": "S",
            "color": "Ivory",
            "price": 959.2,
        }
    ]
    code_oos, resp_oos = request("POST", "/api/v1/orders", order_oos, token=token)
    log(code_oos in (409, 400, 422), "Reject order for OOS variant S-Ivory", f"status={code_oos}")

    # --- 5. Restore stock for repeat runs ---
    restore = dict(saved)
    restore["variantsJson"] = json.dumps(matrix)
    restore["stock"] = 5
    request("PUT", f"/api/v1/admin/products/{pid}", restore, token=token)

    print(f"\n=== Summary: {PASS} passed, {FAIL} failed ===")
    return 0 if FAIL == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
