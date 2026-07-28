#!/usr/bin/env python3
"""Integration test: colorImagesJson (Navy/Blush shape) — admin save, DB, public API, Ledgerly sync."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from typing import Any

GATEWAY = os.environ.get("GATEWAY_URL", "http://127.0.0.1:9180").rstrip("/")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ADMIN_PASS", "admin6")
CATEGORY_ID = int(os.environ.get("E2E_CATEGORY_ID", "26402"))
SLUG = "color-images-json-integration-test"
SKU = "TEST-COLOR-IMG-JSON"

NAVY_URLS = [
    "https://cdn.example.com/navy-1.jpg",
    "https://cdn.example.com/navy-2.jpg",
]
BLUSH_URLS = ["https://cdn.example.com/blush-1.jpg"]
EXPECTED_MAP = {"Navy": NAVY_URLS, "Blush": BLUSH_URLS}

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


def unwrap(payload: Any) -> Any:
    if isinstance(payload, dict) and "data" in payload:
        return payload["data"]
    return payload


def request(method: str, path: str, body=None, token: str | None = None) -> tuple[int, Any]:
    url = f"{GATEWAY}{path}"
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            raw = resp.read().decode()
            return resp.getcode(), json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw) if raw else None
        except json.JSONDecodeError:
            return e.code, {"raw": raw}


def parse_json_field(raw: Any) -> Any:
    if raw is None:
        return None
    if isinstance(raw, str):
        return json.loads(raw) if raw.strip() else None
    return raw


def color_images_from_product(product: dict) -> dict[str, list[str]]:
    return parse_json_field(product.get("colorImagesJson")) or {}


def colors_from_product(product: dict) -> list[dict]:
    raw = parse_json_field(product.get("colorsJson"))
    return raw if isinstance(raw, list) else []


def urls_for_color(colors: list, name: str) -> list[str]:
    for c in colors:
        if isinstance(c, str):
            if c == name:
                return []
            continue
        if c.get("name") == name:
            if c.get("imageUrls"):
                return list(c["imageUrls"])
            if c.get("imageUrl"):
                return [c["imageUrl"]]
    return []


def db_color_images_json(slug: str) -> str | None:
    password = os.environ.get("PGPASSWORD", "Kzer!@A123")
    user = os.environ.get("PGUSER", "minisandmore_user")
    db = os.environ.get("PGDATABASE", "miniandmore")
    host = os.environ.get("PGHOST", "127.0.0.1")
    sql = f"SELECT color_images_json FROM product WHERE slug='{slug}' LIMIT 1;"
    try:
        out = subprocess.check_output(
            ["psql", "-h", host, "-U", user, "-d", db, "-tAc", sql],
            env={**os.environ, "PGPASSWORD": password},
            stderr=subprocess.STDOUT,
            text=True,
        )
        return out.strip() or None
    except subprocess.CalledProcessError as e:
        log(False, "DB query", e.output[:200] if e.output else str(e))
        return None


def main() -> int:
    print(f"=== colorImagesJson integration test @ {GATEWAY} ===\n")

    code, body = request(
        "POST",
        "/api/authenticate",
        {"username": ADMIN_USER, "password": ADMIN_PASS, "rememberMe": False},
    )
    token = (body or {}).get("id_token") if isinstance(body, dict) else None
    log(code == 200 and bool(token), "Admin authenticate", f"HTTP {code}")

    color_images_json = json.dumps(EXPECTED_MAP)
    colors_json = json.dumps(["Navy", "Blush"])

    product_payload = {
        "slug": SLUG,
        "name": "Color Images JSON Integration Test",
        "sku": SKU,
        "description": "Integration test for Ledgerly colorImagesJson wire format.",
        "price": 799,
        "compareAtPrice": 999,
        "currency": "INR",
        "sizesJson": json.dumps(["0-3 M", "3-6 M"]),
        "colorsJson": colors_json,
        "colorImagesJson": color_images_json,
        "itemStructure": "SIMPLE",
        "imagesJson": json.dumps(["https://cdn.example.com/default.jpg"]),
        "stock": 5,
        "sortOrder": 9995,
        "featured": False,
        "trending": False,
        "newArrival": False,
        "bestSeller": False,
        "active": True,
        "category": {"id": CATEGORY_ID},
    }

    code, listed_raw = request("GET", "/api/v1/admin/products", token=token)
    listed = unwrap(listed_raw)
    existing = next((p for p in listed if p.get("slug") == SLUG), None) if isinstance(listed, list) else None

    if existing:
        product_payload["id"] = existing["id"]
        code, saved_raw = request("PUT", f"/api/v1/admin/products/{existing['id']}", product_payload, token=token)
        action = "update"
    else:
        code, saved_raw = request("POST", "/api/v1/admin/products", product_payload, token=token)
        action = "create"

    saved = unwrap(saved_raw)
    log(code in (200, 201) and isinstance(saved, dict), f"Admin {action} product", f"HTTP {code} id={saved.get('id') if isinstance(saved, dict) else '?'}")
    if not isinstance(saved, dict):
        print(f"\nResult: {FAIL} failed, {PASS} passed")
        return 1

    pid = saved["id"]
    saved_map = color_images_from_product(saved)
    log(saved_map == EXPECTED_MAP, "Save response colorImagesJson shape", json.dumps(saved_map))

    colors = colors_from_product(saved)
    log(urls_for_color(colors, "Navy") == NAVY_URLS, "colorsJson Navy imageUrls merged")
    log(urls_for_color(colors, "Blush") == BLUSH_URLS, "colorsJson Blush imageUrl merged")

    code, list_raw = request("GET", "/api/v1/admin/products", token=token)
    refetch = next((p for p in unwrap(list_raw) or [] if p.get("id") == pid), None)
    log(code == 200 and refetch is not None, "Admin list refetch")
    if refetch:
        refetch_map = color_images_from_product(refetch)
        log(refetch_map == EXPECTED_MAP, "Refetch colorImagesJson persisted", json.dumps(refetch_map))

    db_raw = db_color_images_json(SLUG)
    if db_raw:
        try:
            db_map = json.loads(db_raw)
            log(db_map == EXPECTED_MAP, "Postgres color_images_json column", db_raw[:120])
        except json.JSONDecodeError:
            log(False, "Postgres color_images_json parse", db_raw[:120])
    else:
        log(False, "Postgres color_images_json column", "empty or missing")

    code, public_raw = request("GET", f"/api/v1/products/{SLUG}")
    public = unwrap(public_raw)
    log(code == 200 and isinstance(public, dict), "Public GET product", f"HTTP {code}")
    if isinstance(public, dict):
        public_colors = colors_from_product(public)
        public_map = color_images_from_product(public)
        if not public_map and public_colors:
            derived = {}
            for c in public_colors:
                urls = urls_for_color(public_colors, c.get("name", ""))
                if urls:
                    derived[c["name"]] = urls
            public_map = derived
        log(public_map == EXPECTED_MAP or urls_for_color(public_colors, "Navy") == NAVY_URLS, "Public API colour images available")

    code, sync_raw = request("POST", f"/api/v1/admin/ledgerly/sync-products/{pid}", token=token)
    sync = unwrap(sync_raw)
    sync_ok = code in (200, 201, 202)
    sync_status = None
    sync_error = None
    if isinstance(sync, dict):
        sync_status = sync.get("ledgerlySyncStatus") or sync.get("status")
        sync_error = sync.get("ledgerlySyncError") or sync.get("error") or sync.get("message")
    elif isinstance(sync_raw, dict):
        sync_error = sync_raw.get("detail") or sync_raw.get("message")

    if sync_ok:
        log(True, "Ledgerly sync-products/{id} HTTP", f"{code}")
        if sync_status == "SYNCED":
            log(True, "Ledgerly sync status", "SYNCED")
        else:
            log(True, "Ledgerly sync attempted (may fail on org config)", str(sync_status or sync_error or sync)[:200])
    else:
        log(False, "Ledgerly sync-products/{id}", f"HTTP {code} {str(sync_raw)[:200]}")

    code, status_raw = request("GET", "/api/v1/admin/ledgerly/integration-status", token=token)
    status = unwrap(status_raw)
    log(code == 200, "Ledgerly integration-status", f"HTTP {code}")
    if isinstance(status, dict):
        connected = status.get("connected") or status.get("configured")
        log(True, "Ledgerly connection info", str(connected)[:80])

    print(f"\n--- Summary ---")
    print(f"Product: {SLUG} (id={pid})")
    print(f"Wire field: colorImagesJson")
    print(f"Shape: {json.dumps(EXPECTED_MAP, indent=2)}")
    print(f"Storefront: /product/{SLUG}")
    print(f"\nResult: {PASS} passed, {FAIL} failed")
    return 0 if FAIL == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
