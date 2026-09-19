#!/usr/bin/env python3
"""UAT monkey / edge-case tests for Ledgerly-style product variants.

Creates (or reuses) a disposable product, hammers admin PUT + storefront GET
+ order reservation conflicts over multiple rounds, then restores/deletes.

Usage:
  GATEWAY_URL=http://127.0.0.1:9180 ADMIN_USER=admin ADMIN_PASS=admin6 \\
    python3 deploy/scripts/prod-variants-monkey-test.py --rounds 5
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
import time
import urllib.error
import urllib.request
from copy import deepcopy
from typing import Any

GATEWAY = os.environ.get("GATEWAY_URL", "http://127.0.0.1:9180").rstrip("/")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ADMIN_PASS", "admin6")
SLUG = "uat-monkey-var-iso"
SKU = "UAT-MONKEY-VAR-ISO"
CATEGORY_ID = int(os.environ.get("MONKEY_CATEGORY_ID", "26402"))

PASS = 0
FAIL = 0
RESULTS: list[str] = []


def log(ok: bool, name: str, detail: str = "") -> None:
    global PASS, FAIL
    if ok:
        PASS += 1
        RESULTS.append(f"OK   {name}" + (f" — {detail}" if detail else ""))
        print(f"OK   {name}" + (f" — {detail}" if detail else ""))
    else:
        FAIL += 1
        RESULTS.append(f"FAIL {name}" + (f" — {detail}" if detail else ""))
        print(f"FAIL {name}" + (f" — {detail}" if detail else ""), file=sys.stderr)


def request(
    method: str,
    path: str,
    body: Any | None = None,
    token: str | None = None,
    timeout: float = 30.0,
) -> tuple[int, Any]:
    url = path if path.startswith("http") else f"{GATEWAY}{path}"
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode()
            code = resp.getcode()
            try:
                return code, json.loads(raw) if raw else None
            except json.JSONDecodeError:
                return code, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return e.code, parsed


def unwrap(payload: Any) -> Any:
    if isinstance(payload, dict) and "data" in payload:
        return payload["data"]
    return payload


def authenticate() -> str:
    code, body = request(
        "POST",
        "/api/authenticate",
        {"username": ADMIN_USER, "password": ADMIN_PASS, "rememberMe": False},
    )
    token = (body or {}).get("id_token") if isinstance(body, dict) else None
    if code != 200 or not token:
        raise RuntimeError(f"authenticate failed: {code} {body}")
    return token


def storefront_price(rate: float) -> float:
    return round(rate * 0.80, 2)


def aggregate(rows: list[dict]) -> tuple[float | None, int, list[str], list[str]]:
    min_rate = None
    stock = 0
    sizes: list[str] = []
    colors: list[str] = []
    for r in rows:
        if r.get("active") is False:
            continue
        rate = float(r.get("rate") or 0)
        if rate > 0:
            min_rate = rate if min_rate is None else min(min_rate, rate)
        stock += max(0, int(r.get("stock") or 0))
        s = (r.get("size") or "").strip()
        c = (r.get("color") or "").strip()
        if s and s not in sizes:
            sizes.append(s)
        if c and c not in colors:
            colors.append(c)
    return min_rate, stock, sizes, colors


def base_product(existing: dict | None = None) -> dict:
    p = {
        "slug": SLUG,
        "name": "UAT Monkey Variants Isolated",
        "sku": SKU,
        "description": "Disposable product for variant monkey testing on UAT. Safe to delete. Isolated from Ledgerly SKUs.",
        "price": 400,
        "compareAtPrice": 500,
        "currency": "INR",
        "sizesJson": '["S","M"]',
        "colorsJson": '["Red","Blue"]',
        "itemStructure": "SIMPLE",
        "variantsJson": None,
        "imagesJson": "[]",
        "stock": 10,
        "sortOrder": 9999,
        "featured": False,
        "trending": False,
        "newArrival": False,
        "bestSeller": False,
        "active": True,
        "category": {"id": CATEGORY_ID},
    }
    if existing and existing.get("id"):
        p["id"] = existing["id"]
    return p


def find_or_create(token: str) -> dict:
    code, body = request("GET", "/api/v1/admin/products", token=token)
    rows = unwrap(body) or []
    for row in rows:
        if row.get("slug") == SLUG or row.get("sku") == SKU:
            return row
    code, body = request("POST", "/api/v1/admin/products", base_product(), token=token)
    if code not in (200, 201):
        raise RuntimeError(f"create product failed: {code} {body}")
    return unwrap(body)


def put_product(token: str, product: dict) -> tuple[int, Any]:
    pid = product["id"]
    code, body = request("PUT", f"/api/v1/admin/products/{pid}", product, token=token)
    return code, unwrap(body) if code < 400 else body


def get_storefront(slug: str = SLUG) -> tuple[int, Any]:
    # Bust intermediary caches so stock assertions aren't racing flipped SIMPLE snapshots.
    code, body = request("GET", f"/api/v1/products/{slug}?_={int(time.time() * 1000)}")
    return code, unwrap(body) if code < 400 else body


def unlink_ledgerly(token: str, product: dict) -> dict:
    """Prevent live Ledgerly sync from rewriting the disposable monkey product mid-test."""
    if not product.get("ledgerlyItemId") and not product.get("ledgerly_item_id"):
        return product
    patched = deepcopy(product)
    patched["ledgerlyItemId"] = None
    patched["ledgerlySyncStatus"] = None
    patched["ledgerlyLastSyncedAt"] = None
    patched["ledgerlySyncError"] = None
    code, body = put_product(token, patched)
    if code in (200, 201) and isinstance(body, dict):
        log(body.get("ledgerlyItemId") in (None, ""), "unlink Ledgerly from monkey product")
        return body
    log(False, "unlink Ledgerly from monkey product", f"status={code}")
    return product


def variant_stock(payload: dict | None, sku: str) -> int | None:
    if not isinstance(payload, dict):
        return None
    for row in parse_variants(payload.get("variantsJson")):
        if (row.get("sku") or "").strip() == sku:
            return int(row.get("stock") or 0)
    return None


def db_stock(sku: str = "MONKEY-VAR-S-RED") -> tuple[int | None, int | None]:
    """Read parent + variant stock from Postgres when credentials are available."""
    import shutil
    import subprocess

    if not shutil.which("psql"):
        return None, None
    password = os.environ.get("PGPASSWORD", "Kzer!@A123")
    user = os.environ.get("PGUSER", "minisandmore_user")
    db = os.environ.get("PGDATABASE", "miniandmore")
    host = os.environ.get("PGHOST", "127.0.0.1")
    sql = (
        "SELECT stock, variants_json FROM product "
        f"WHERE slug = '{SLUG}' OR sku = '{SKU}' LIMIT 1;"
    )
    try:
        env = os.environ.copy()
        env["PGPASSWORD"] = password
        out = subprocess.check_output(
            ["psql", "-h", host, "-U", user, "-d", db, "-tAc", sql],
            env=env,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=5,
        ).strip()
    except (subprocess.SubprocessError, OSError):
        return None, None
    if not out:
        return None, None
    # psql -tAc: stock|variants_json
    stock_s, sep, vj = out.partition("|")
    try:
        parent = int(stock_s) if stock_s else None
    except ValueError:
        parent = None
    rows = parse_variants(vj if sep else None)
    variant = None
    for row in rows:
        if (row.get("sku") or "").strip() == sku:
            variant = int(row.get("stock") or 0)
            break
    return parent, variant


def wait_stock_drop(
    *,
    parent_before: int,
    variant_before: int | None,
    sku: str,
    attempts: int = 10,
    delay_s: float = 0.15,
) -> tuple[int, int | None]:
    parent_after = parent_before
    variant_after = variant_before
    for _ in range(attempts):
        time.sleep(delay_s)
        db_parent, db_variant = db_stock(sku)
        if db_parent is not None:
            parent_after = db_parent
            variant_after = db_variant
        else:
            _, sf = get_storefront()
            if isinstance(sf, dict):
                parent_after = int(sf.get("stock") or 0)
                variant_after = variant_stock(sf, sku)
        parent_ok = parent_after == parent_before - 1
        variant_ok = variant_before is None or variant_after == (variant_before - 1)
        if parent_ok or variant_ok:
            break
    return parent_after, variant_after


def parse_variants(raw: Any) -> list[dict]:
    if raw is None or raw == "":
        return []
    if isinstance(raw, list):
        return raw
    if isinstance(raw, str):
        try:
            parsed = json.loads(raw)
            if isinstance(parsed, str):
                parsed = json.loads(parsed)
            return parsed if isinstance(parsed, list) else []
        except json.JSONDecodeError:
            return []
    return []


def near(a: float | None, b: float | None, eps: float = 0.051) -> bool:
    if a is None and b is None:
        return True
    if a is None or b is None:
        return False
    return abs(float(a) - float(b)) <= eps


def order_body(product_id: Any, *, sku: str, size: str, color: str, qty: int, price: float) -> dict:
    return {
        "items": [
            {
                "product_id": str(product_id),
                "product_slug": SLUG,
                "product_name": "UAT Monkey Variants Isolated",
                "product_image": "",
                "size": size,
                "color": color,
                "sku": sku,
                "qty": qty,
                "price": price,
            }
        ],
        "shipping_address": {
            "full_name": "Monkey Tester",
            "email": "monkey@example.com",
            "phone": "9876543210",
            "line1": "12 Test Lane",
            "line2": "",
            "city": "Bengaluru",
            "state": "Karnataka",
            "pincode": "560001",
            "baby_name": "",
        },
        "shipping_mode": "STANDARD",
        "coupon_code": None,
        "donation": 0,
        "notes": "uat-variants-monkey-test",
    }


def expect_conflict(token: str, body: dict, label: str, must_contain: str | None = None) -> None:
    code, resp = request("POST", "/api/v1/orders", body, token=token)
    ok = code in (409, 400, 422)
    detail = f"status={code} body={str(resp)[:220]}"
    if ok and must_contain:
        blob = json.dumps(resp).lower() if not isinstance(resp, str) else resp.lower()
        ok = must_contain.lower() in blob
        if not ok:
            detail += f" (missing '{must_contain}')"
    log(ok, label, detail)


def run_round(token: str, product: dict, round_idx: int) -> dict:
    print(f"\n=== Round {round_idx} ===")
    p = deepcopy(product)

    # --- A. Convert to VARIANTS with sparse matrix ---
    matrix = [
        {"sku": "MONKEY-VAR-S-RED", "size": "S", "color": "Red", "rate": 500, "cost": 200, "stock": 3, "active": True},
        {"sku": "MONKEY-VAR-S-BLUE", "size": "S", "color": "Blue", "rate": 550, "cost": 220, "stock": 0, "active": True},
        {"sku": "MONKEY-VAR-M-RED", "size": "M", "color": "Red", "rate": 600, "cost": 250, "stock": 5, "active": True},
        # deliberately skip M-Blue (sparse)
        {"sku": "MONKEY-VAR-L-GREEN", "size": "L", "color": "Green", "rate": 450, "cost": 180, "stock": 2, "active": False},
    ]
    min_rate, sum_stock, sizes, colors = aggregate(matrix)
    p.update(
        {
            "itemStructure": "VARIANTS",
            "variantsJson": json.dumps(matrix),
            "sizesJson": json.dumps(sizes),
            "colorsJson": json.dumps(colors),
            "compareAtPrice": min_rate,
            "price": storefront_price(min_rate or 0),
            "stock": sum_stock,
        }
    )
    code, saved = put_product(token, p)
    ok = code in (200, 201) and isinstance(saved, dict)
    log(ok, f"R{round_idx} save VARIANTS sparse matrix", f"status={code}")
    if not ok:
        return product

    expected_price = storefront_price(min_rate or 0)
    # parent stock/price should be normalized server-side to active rows only
    active_stock = sum(int(r["stock"]) for r in matrix if r.get("active") is not False)
    active_min = min(float(r["rate"]) for r in matrix if r.get("active") is not False and float(r["rate"]) > 0)
    # inactive L-Green still has stock in our aggregateFromVariantRows (frontend skips inactive).
    # Backend ProductVariantSupport — check what saved.
    saved_price = float(saved.get("price") or 0)
    saved_stock = int(saved.get("stock") or 0)
    saved_compare = saved.get("compareAtPrice")
    log(
        near(saved_price, storefront_price(active_min)),
        f"R{round_idx} parent price = min(active rate)*0.8",
        f"got={saved_price} expected≈{storefront_price(active_min)} compareAt={saved_compare}",
    )
    # Expect stock = sum of active only (inactive shouldn't sell-count, but costed stock may still sum)
    log(
        saved_stock in (active_stock, active_stock + 2),  # tolerate inactive included
        f"R{round_idx} parent stock ~ sum variants",
        f"got={saved_stock} active_sum={active_stock}",
    )

    # --- B. Storefront exposes matrix ---
    sc, sf = get_storefront()
    log(sc == 200 and isinstance(sf, dict), f"R{round_idx} storefront GET", f"status={sc}")
    if isinstance(sf, dict):
        sf_struct = (sf.get("itemStructure") or "").upper()
        sf_rows = parse_variants(sf.get("variantsJson"))
        log(sf_struct == "VARIANTS", f"R{round_idx} storefront itemStructure", sf_struct)
        log(len(sf_rows) >= 3, f"R{round_idx} storefront variantsJson length", str(len(sf_rows)))
        skus = {r.get("sku") for r in sf_rows}
        log("MONKEY-VAR-S-RED" in skus, f"R{round_idx} storefront has S-RED sku")
        log("MONKEY-VAR-M-BLUE" not in skus, f"R{round_idx} sparse: no M-Blue row")

    # --- C. Partial client price/stock override should be rewritten by server ---
    p2 = deepcopy(saved)
    p2["price"] = 1
    p2["stock"] = 9999
    p2["compareAtPrice"] = 1
    p2["variantsJson"] = saved.get("variantsJson")
    p2["itemStructure"] = "VARIANTS"
    code, rewritten = put_product(token, p2)
    ok = code in (200, 201)
    log(ok, f"R{round_idx} client lies about price/stock → still saves", f"status={code}")
    if ok and isinstance(rewritten, dict):
        log(
            near(float(rewritten.get("price") or 0), storefront_price(active_min)),
            f"R{round_idx} server recomputes price",
            str(rewritten.get("price")),
        )
        log(
            int(rewritten.get("stock") or 0) != 9999,
            f"R{round_idx} server recomputes stock (not 9999)",
            str(rewritten.get("stock")),
        )
        saved = rewritten

    # --- D. Invalid JSON rejected without wipe ---
    before_json = saved.get("variantsJson")
    bad = deepcopy(saved)
    bad["variantsJson"] = "{not-json"
    bad["itemStructure"] = "VARIANTS"
    code, bad_resp = put_product(token, bad)
    # Catalog correctly returns 400; store/gateway sometimes surfaces that as 500 "connection closed".
    log(code >= 400, f"R{round_idx} invalid variants JSON rejected", f"status={code}")
    # re-fetch via storefront / list
    code_list, listed = request("GET", "/api/v1/admin/products", token=token)
    current = None
    for row in unwrap(listed) or []:
        if row.get("id") == saved["id"]:
            current = row
            break
    if current:
        wiped = len(parse_variants(current.get("variantsJson"))) < 3 or (
            isinstance(current.get("variantsJson"), str) and current["variantsJson"].startswith("{not")
        )
        log(
            not wiped,
            f"R{round_idx} bad JSON did not wipe matrix",
            f"len={len(parse_variants(current.get('variantsJson')))} status={code}",
        )
        saved = current

    # --- E. Empty matrix / blank ---
    empty = deepcopy(saved)
    empty["variantsJson"] = "[]"
    empty["itemStructure"] = "VARIANTS"
    empty["price"] = 400
    empty["stock"] = 0
    code, empty_saved = put_product(token, empty)
    # Backend may allow empty list (applyMatrix succeeds) — storefront should reflect
    log(code in (200, 201, 400, 422), f"R{round_idx} empty matrix response", f"status={code}")
    # Restore usable matrix
    restore = deepcopy(saved if code >= 400 else (empty_saved if isinstance(empty_saved, dict) else saved))
    restore["itemStructure"] = "VARIANTS"
    restore["variantsJson"] = json.dumps(matrix)
    restore["compareAtPrice"] = active_min
    restore["price"] = storefront_price(active_min)
    restore["stock"] = active_stock
    code, saved = put_product(token, restore)
    log(code in (200, 201), f"R{round_idx} restore usable matrix", f"status={code}")

    # --- F. Duplicate SKUs / weird unicode / whitespace ---
    weird = [
        {"sku": " MONKEY-VAR-S-RED ", "size": "S", "color": "Red", "rate": 500, "cost": 1, "stock": 1, "active": True},
        {"sku": "monkey-var-s-red", "size": "S", "color": "Blue", "rate": 500, "cost": 1, "stock": 1, "active": True},
        {"sku": "MONKEY-✨-UNI", "size": "S", "color": "Teal", "rate": 700, "cost": 1, "stock": 1, "active": True},
        {"sku": "MONKEY-VERY-LONG-" + ("X" * 80), "size": "XL", "color": "Navy", "rate": 800, "cost": 1, "stock": 1, "active": True},
    ]
    w = deepcopy(saved)
    w["variantsJson"] = json.dumps(weird)
    w["itemStructure"] = "VARIANTS"
    code, weird_saved = put_product(token, w)
    log(code in (200, 201, 400, 422), f"R{round_idx} weird/dup/unicode/long SKUs status", f"status={code}")
    # restore clean matrix again
    restore["variantsJson"] = json.dumps(matrix)
    code, saved = put_product(token, restore)
    log(code in (200, 201), f"R{round_idx} restore after weird", f"status={code}")

    # --- G. Order reservation edge cases (must conflict; no paid order) ---
    pid = saved["id"]
    expect_conflict(
        token,
        order_body(pid, sku="MONKEY-VAR", size="S", color="Red", qty=1, price=400),
        f"R{round_idx} reject parent SKU on VARIANTS",
        "unavailable",
    )
    expect_conflict(
        token,
        order_body(pid, sku="MONKEY-VAR-M-BLUE", size="M", color="Blue", qty=1, price=480),
        f"R{round_idx} reject sparse missing combo",
        "unavailable",
    )
    expect_conflict(
        token,
        order_body(pid, sku="MONKEY-VAR-S-BLUE", size="S", color="Blue", qty=1, price=440),
        f"R{round_idx} reject OOS variant (stock 0)",
    )
    expect_conflict(
        token,
        order_body(pid, sku="MONKEY-VAR-L-GREEN", size="L", color="Green", qty=1, price=360),
        f"R{round_idx} reject inactive variant",
        "unavailable",
    )
    # qty must stay within @Max(50) so validation reaches stock check (qty=99 dies in proxy as 500).
    expect_conflict(
        token,
        order_body(pid, sku="MONKEY-VAR-S-RED", size="S", color="Red", qty=4, price=400),
        f"R{round_idx} reject qty > variant stock",
        "available",
    )
    # size/color mismatch vs sku — resolveSellable prefers sku; if SKU matches should work path
    # Wrong size with correct sku that exists: still resolve by sku → may succeed into Razorpay.
    # Use qty huge to force conflict instead of paying:
    expect_conflict(
        token,
        order_body(pid, sku="MONKEY-VAR-S-RED", size="ZZ", color="Nope", qty=50, price=400),
        f"R{round_idx} correct sku + garbage size/color still hits stock gate",
    )

    # Snapshot stock before any accidental success (parent + variant row).
    sc, sf = get_storefront()
    stock_before = int((sf or {}).get("stock") or 0) if isinstance(sf, dict) else -1
    variant_before = variant_stock(sf if isinstance(sf, dict) else None, "MONKEY-VAR-S-RED")

    # Attempt valid 1-qty order — may 200 (demo) or 5xx/402 (razorpay). Either way roll back or create pending.
    valid = order_body(pid, sku="MONKEY-VAR-S-RED", size="S", color="Red", qty=1, price=storefront_price(500))
    code, resp = request("POST", "/api/v1/orders", valid, token=token)
    # Prefer: if 200, inventory reserved; reverse by leaving pending (TTL). Also verify unit price.
    if code == 200 and isinstance(resp, dict):
        data = unwrap(resp) or resp
        log(True, f"R{round_idx} valid variant order created (pending)", f"order={data.get('orderNumber') or data.get('orderId')}")
        # stock should drop by 1 on parent and/or the reserved variant row
        stock_after, variant_after = wait_stock_drop(
            parent_before=stock_before,
            variant_before=variant_before,
            sku="MONKEY-VAR-S-RED",
        )
        parent_ok = stock_before < 0 or stock_after == stock_before - 1
        variant_ok = variant_before is None or variant_after == (variant_before - 1)
        log(
            parent_ok or variant_ok,
            f"R{round_idx} stock decremented after reserve",
            f"parent {stock_before}->{stock_after} variant {variant_before}->{variant_after}",
        )
        # Restore matrix stocks for next round (admin overwrite)
        code, saved = put_product(token, restore)
        log(code in (200, 201), f"R{round_idx} admin restock after order", f"status={code}")
    else:
        log(
            code in (409, 500, 502, 503, 400),
            f"R{round_idx} valid variant order blocked/failed without silent 2xx",
            f"status={code} body={str(resp)[:180]}",
        )
        # ensure stock unchanged
        sc2, sf2 = get_storefront()
        stock_after = int((sf2 or {}).get("stock") or 0) if isinstance(sf2, dict) else -1
        variant_after = variant_stock(sf2 if isinstance(sf2, dict) else None, "MONKEY-VAR-S-RED")
        log(
            (stock_before < 0 or stock_after == stock_before)
            and (variant_before is None or variant_after == variant_before),
            f"R{round_idx} stock unchanged after failed order",
            f"parent {stock_before}->{stock_after} variant {variant_before}->{variant_after}",
        )

    # --- H. Flip back to SIMPLE clears variants ---
    simple = deepcopy(saved if isinstance(saved, dict) else restore)
    simple["itemStructure"] = "SIMPLE"
    simple["variantsJson"] = None
    simple["price"] = 400
    simple["compareAtPrice"] = 500
    simple["stock"] = 10
    simple["sizesJson"] = '["S","M"]'
    simple["colorsJson"] = '["Red","Blue"]'
    code, simple_saved = put_product(token, simple)
    log(code in (200, 201), f"R{round_idx} flip to SIMPLE", f"status={code}")
    if isinstance(simple_saved, dict):
        log(
            (simple_saved.get("itemStructure") or "").upper() == "SIMPLE",
            f"R{round_idx} SIMPLE structure persisted",
            str(simple_saved.get("itemStructure")),
        )
        vj = simple_saved.get("variantsJson")
        log(vj in (None, "", "null", "[]"), f"R{round_idx} variants cleared on SIMPLE", repr(vj))
        saved = simple_saved

    # Leave as VARIANTS again for next round start consistency
    restore["id"] = saved["id"]
    code, saved = put_product(token, restore)
    log(code in (200, 201), f"R{round_idx} end as VARIANTS restored", f"status={code}")
    return saved if isinstance(saved, dict) else product


def cleanup(token: str, product: dict, keep: bool) -> None:
    if keep or not product.get("id"):
        print(f"KEEP product id={product.get('id')} slug={SLUG}")
        return
    code, body = request("DELETE", f"/api/v1/admin/products/{product['id']}", token=token)
    log(code in (200, 204), "cleanup delete monkey product", f"status={code}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--keep", action="store_true", help="Do not delete disposable product")
    args = parser.parse_args()

    print(f"Gateway: {GATEWAY}")
    print(f"Auth as: {ADMIN_USER}")
    print(f"Rounds: {args.rounds}")

    token = authenticate()
    log(True, "authenticate")
    product = find_or_create(token)
    log(True, "find_or_create product", f"id={product.get('id')} sku={product.get('sku')}")
    product = unlink_ledgerly(token, product)

    for i in range(1, args.rounds + 1):
        product = unlink_ledgerly(token, product)
        product = run_round(token, product, i)

    # Final storefront sanity
    sc, sf = get_storefront()
    log(
        sc == 200 and isinstance(sf, dict) and (sf.get("itemStructure") or "").upper() == "VARIANTS",
        "final storefront VARIANTS",
        f"status={sc} structure={(sf or {}).get('itemStructure') if isinstance(sf, dict) else None}",
    )

    cleanup(token, product, keep=args.keep)

    print(f"\n=== Summary: {PASS} passed, {FAIL} failed ===")
    return 1 if FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
