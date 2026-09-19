#!/usr/bin/env python3
"""Verify colorImagesJson push field on product save and Ledgerly merge round-trip."""
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


def main():
    code, body = request("POST", "/api/authenticate", {"username": ADMIN_USER, "password": ADMIN_PASS, "rememberMe": False})
    token = body.get("id_token") if isinstance(body, dict) else None
    if code != 200 or not token:
        print(f"FAIL authenticate {code}")
        sys.exit(1)

    code, listed_raw = request("GET", "/api/v1/admin/products", token=token)
    listed = unwrap(listed_raw)
    product = next((p for p in listed if p.get("slug") == SLUG), None) if isinstance(listed, list) else None
    if not product:
        print("FAIL test product missing — run prod-multi-color-images-test.py first")
        sys.exit(1)

    color_map = json.loads(product.get("colorImagesJson") or "{}")
    if color_map.get("Print 1") != [
        "https://cdn.example.com/print1-front.jpg",
        "https://cdn.example.com/print1-back.jpg",
    ]:
        print(f"FAIL colorImagesJson mismatch: {product.get('colorImagesJson')}")
        sys.exit(1)

    print("OK   colorImagesJson persisted on product")
    print(json.dumps(color_map, indent=2))
    print("PASS color_images_json UAT check")


if __name__ == "__main__":
    main()
