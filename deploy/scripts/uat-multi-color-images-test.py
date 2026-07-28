#!/usr/bin/env python3
"""Create a test product with multiple images per colour and verify round-trip + gallery logic."""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from typing import Any

GATEWAY = os.environ.get("GATEWAY_URL", "http://127.0.0.1:9180").rstrip("/")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ADMIN_PASS", "admin6")
CATEGORY_ID = int(os.environ.get("E2E_CATEGORY_ID", "26402"))
SLUG = "multi-color-images-test"


def unwrap(payload: Any) -> Any:
    if isinstance(payload, dict) and "data" in payload:
        return payload["data"]
    return payload


def request(method: str, path: str, body=None, token: str | None = None):
    url = f"{GATEWAY}{path}"
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode()
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw}
        return e.code, parsed


def fail(msg: str):
    print(f"FAIL {msg}")
    sys.exit(1)


def ok(msg: str):
    print(f"OK   {msg}")


def main():
    code, body = request("POST", "/api/authenticate", {"username": ADMIN_USER, "password": ADMIN_PASS, "rememberMe": False})
    if code != 200 or not body or "id_token" not in body:
        fail(f"authenticate {code} {body}")
    token = body["id_token"]
    ok("authenticate")

    colors_json = [
        {
            "name": "Print 1",
            "imageUrls": [
                "https://cdn.example.com/print1-front.jpg",
                "https://cdn.example.com/print1-back.jpg",
            ],
        },
        {"name": "Print 2", "imageUrl": "https://cdn.example.com/print2.jpg"},
    ]

    product = {
        "slug": SLUG,
        "name": "Multi Colour Images Test",
        "sku": "TEST-MULTI-COLOR-IMG",
        "description": "Automated test product for multi-image per colour.",
        "price": 799,
        "compareAtPrice": 999,
        "currency": "INR",
        "sizesJson": json.dumps(["0-3 M", "3-6 M"]),
        "colorsJson": json.dumps(colors_json),
        "itemStructure": "SIMPLE",
        "imagesJson": json.dumps(["https://cdn.example.com/default.jpg", "https://cdn.example.com/lifestyle.jpg"]),
        "stock": 10,
        "sortOrder": 9997,
        "featured": False,
        "trending": False,
        "newArrival": False,
        "bestSeller": False,
        "active": True,
        "category": {"id": CATEGORY_ID},
    }

    code, listed_raw = request("GET", "/api/v1/admin/products", token=token)
    listed = unwrap(listed_raw)
    if code != 200 or not isinstance(listed, list):
        fail(f"list products {code} {listed_raw}")
    found = next((p for p in listed if p.get("slug") == SLUG), None)

    if found:
        code, saved_raw = request(
            "PUT",
            f"/api/v1/admin/products/{found['id']}",
            {**product, "id": found["id"]},
            token=token,
        )
        action = "update"
    else:
        code, saved_raw = request("POST", "/api/v1/admin/products", product, token=token)
        action = "create"
    saved = unwrap(saved_raw)
    if code not in (200, 201) or not saved:
        fail(f"{action} product {code} {saved_raw}")
    ok(f"{action} product id={saved.get('id')}")

    stored_colors = json.loads(saved.get("colorsJson") or "[]")
    print("stored colorsJson:", json.dumps(stored_colors, indent=2))

    print1 = next((c for c in stored_colors if c.get("name") == "Print 1"), None)
    if not print1:
        fail("Print 1 missing after save")
    urls1 = print1.get("imageUrls") or ([print1["imageUrl"]] if print1.get("imageUrl") else [])
    if urls1 != [
        "https://cdn.example.com/print1-front.jpg",
        "https://cdn.example.com/print1-back.jpg",
    ]:
        fail(f"Print 1 images mismatch: {urls1}")

    print2 = next((c for c in stored_colors if c.get("name") == "Print 2"), None)
    if not print2 or print2.get("imageUrl") != "https://cdn.example.com/print2.jpg":
        fail(f"Print 2 single image mismatch: {print2}")
    ok("colors_json round-trip")

    code, public_raw = request("GET", f"/api/v1/products/{SLUG}")
    public = unwrap(public_raw)
    if code != 200 or not public:
        fail(f"public product {code} {public_raw}")
    ok("public product fetch")

    if public.get("slug") != SLUG:
        fail("public slug mismatch")

    # Re-fetch via admin list and verify persistence
    code, listed_raw = request("GET", "/api/v1/admin/products", token=token)
    listed = unwrap(listed_raw)
    refetch = next((p for p in listed if p.get("id") == saved.get("id")), None) if isinstance(listed, list) else None
    if code != 200 or not refetch:
        fail(f"admin list refetch {code}")
    refetch_colors = json.loads(refetch.get("colorsJson") or "[]")
    if len(refetch_colors) != 2:
        fail(f"refetch color count {refetch_colors}")
    ok("admin list persistence")

    print("\nAdmin: Products → search 'Multi Colour Images Test'")
    print(f"Storefront: /product/{SLUG}")
    print("PASS multi-colour images test")


if __name__ == "__main__":
    main()
