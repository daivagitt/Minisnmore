#!/usr/bin/env python3
"""E2E: backfilled existing invoices + new order snapshot + invoice APIs on UAT."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from typing import Any

GATEWAY = os.environ.get("GATEWAY_URL", "http://127.0.0.1:9180").rstrip("/")
FRONTEND = os.environ.get("FRONTEND_URL", "http://127.0.0.1:3000").rstrip("/")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ADMIN_PASS", "admin6")
EXISTING_PAID = os.environ.get("EXISTING_PAID_ORDER", "MNM-2B16870A8DA7")

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


def request(method: str, path: str, body: Any | None = None, token: str | None = None, timeout: int = 45, accept: str = "application/json") -> tuple[int, Any]:
    url = path if path.startswith("http") else f"{GATEWAY}{path}"
    headers = {"Accept": accept}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
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


def db_query(sql: str) -> str | None:
    try:
        out = subprocess.check_output(
            ["psql", "-h", os.environ.get("PGHOST", "127.0.0.1"), "-U", os.environ.get("PGUSER", "minisandmore_user"),
             "-d", os.environ.get("PGDATABASE", "miniandmore"), "-tAc", sql],
            env={**os.environ, "PGPASSWORD": os.environ.get("PGPASSWORD", "Kzer!@A123")},
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=8,
        ).strip()
        return out or None
    except (subprocess.SubprocessError, OSError):
        return None


def invoice_shape_ok(order: dict) -> list[str]:
    problems = []
    for field in ("order_number", "subtotal", "tax", "total", "order_items", "shipping_address"):
        if order.get(field) in (None, ""):
            problems.append(f"missing {field}")
    items = order.get("order_items") or []
    if not items:
        problems.append("no line items")
    for item in items:
        if item.get("price") is None:
            problems.append(f"item {item.get('id')} missing price")
        if item.get("mrp") is None:
            problems.append(f"item {item.get('id')} missing mrp")
        if item.get("tax_pct") is None:
            problems.append(f"item {item.get('id')} missing tax_pct")
        if item.get("mrp") is not None and item.get("price") is not None:
            if float(item["mrp"]) + 0.001 < float(item["price"]):
                problems.append(f"item {item.get('id')} mrp < price")
    return problems


def main() -> int:
    print(f"=== Invoice E2E @ {GATEWAY} ===\n")
    code, body = request("POST", "/api/authenticate", {"username": ADMIN_USER, "password": ADMIN_PASS, "rememberMe": False})
    token = (body or {}).get("id_token") if isinstance(body, dict) else None
    log(code == 200 and bool(token), "Admin authenticate", f"status={code}")
    if not token:
        return 1

    snap = db_query(
        "SELECT COUNT(*) || ' items, mrp=' || COUNT(mrp) || ', tax=' || COUNT(tax_pct) FROM order_item"
    )
    log(bool(snap) and "mrp=" in (snap or "") and not str(snap).endswith("mrp=0"), "Existing order_item snapshots", snap or "missing")

    sc, settings = request("GET", "/api/v1/site-settings")
    settings = unwrap(settings) or {}
    log(sc == 200, "Public site-settings", f"status={sc} invoiceLogoUrl={settings.get('invoiceLogoUrl')!r}")

    fc = request("GET", f"{FRONTEND}/admin/invoices", timeout=20, accept="text/html")[0]
    log(fc in (200, 302, 401), "Frontend /admin/invoices", f"status={fc}")
    fc2 = request("GET", f"{FRONTEND}/account/orders/{EXISTING_PAID}/invoice", timeout=20, accept="text/html")[0]
    log(fc2 in (200, 302, 401), f"Frontend invoice route for {EXISTING_PAID}", f"status={fc2}")

    ac, existing = request("GET", f"/api/v1/admin/orders/{EXISTING_PAID}", token=token)
    existing = unwrap(existing) or {}
    existing_order = existing.get("order") if isinstance(existing, dict) else None
    log(ac == 200 and isinstance(existing_order, dict), f"Admin GET existing paid order {EXISTING_PAID}", f"status={ac}")
    if isinstance(existing_order, dict):
        problems = invoice_shape_ok(existing_order)
        log(not problems, "Existing paid order invoice payload", "; ".join(problems) or "ok")

    pc, product = request("GET", "/api/v1/products/mock-notif-test-tee")
    product = unwrap(product) or {}
    log(pc == 200 and isinstance(product, dict), "Load in-stock product mock-notif-test-tee", f"status={pc} stock={product.get('stock')}")
    if not isinstance(product, dict):
        return 1

    order_body = {
        "items": [
            {
                "product_id": str(product["id"]),
                "product_slug": product.get("slug") or "mock-notif-test-tee",
                "product_name": product.get("name") or "Notification Test Tee",
                "product_image": "",
                "sku": product.get("sku") or "MOCK-NOTIF-001",
                "size": "",
                "color": "",
                "qty": 1,
                "price": float(product.get("price") or 299),
            }
        ],
        "shipping_address": {
            "full_name": "Invoice E2E Tester",
            "phone": "9876543210",
            "email": "admin@uat.minisnmore.com",
            "line1": "12 Invoice Road",
            "line2": "",
            "city": "Bengaluru",
            "state": "Karnataka",
            "pincode": "560001",
            "baby_name": "",
        },
        "shipping_mode": "std",
        "payment_method": "cod",
        "coupon_code": "",
        "donation": 0,
        "notes": "invoice-e2e-data-insertion",
    }
    oc, created = request("POST", "/api/v1/orders", order_body, token=token)
    created = unwrap(created) or {}
    order_number = created.get("orderNumber") if isinstance(created, dict) else None
    log(oc == 200 and bool(order_number), "Place COD order for invoice snapshot", f"status={oc} order={order_number} body={created if oc != 200 else 'ok'}")
    if not order_number:
        return 1

    row = db_query(
        "SELECT COALESCE(mrp::text,'') || '|' || COALESCE(tax_pct::text,'') || '|' || COALESCE(hsn,'') "
        "FROM order_item oi JOIN customer_order co ON co.id = oi.order_id "
        f"WHERE co.order_number = '{order_number}' LIMIT 1"
    )
    log(bool(row) and row.split("|")[0] not in ("", "0") and row.split("|")[1] not in ("",), "New order_item snapshot in DB", row or "missing")

    gc, mine = request("GET", f"/api/v1/orders/{order_number}", token=token)
    mine = unwrap(mine) or {}
    mine_order = mine.get("order") if isinstance(mine, dict) else None
    log(gc == 200 and isinstance(mine_order, dict), "Customer GET new order", f"status={gc}")
    if isinstance(mine_order, dict):
        problems = invoice_shape_ok(mine_order)
        log(not problems, "New order invoice payload", "; ".join(problems) or f"items={len(mine_order.get('order_items') or [])} total={mine_order.get('total')}")
        item = (mine_order.get("order_items") or [{}])[0]
        log(float(item.get("mrp") or 0) >= float(item.get("price") or 0), "New order MRP >= selling price", f"mrp={item.get('mrp')} price={item.get('price')}")
        log(float(item.get("tax_pct") or 0) > 0, "New order tax_pct present", str(item.get("tax_pct")))

    ag, admin_new = request("GET", f"/api/v1/admin/orders/{order_number}", token=token)
    admin_new = unwrap(admin_new) or {}
    log(ag == 200 and isinstance((admin_new or {}).get("order"), dict), "Admin GET new order invoice", f"status={ag}")

    print(f"\n=== Summary: {PASS} passed, {FAIL} failed ===")
    return 0 if FAIL == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
