#!/usr/bin/env python3
"""Realign colors_json + variants_json to color_images_json print names after SKU import drift."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
import re
from typing import Any

GATEWAY = os.environ.get("GATEWAY_URL", "http://127.0.0.1:9180").rstrip("/")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin@uat.minisnmore.com")
ADMIN_PASS = os.environ.get("ADMIN_PASS", "admin6")


def psql_json(sql: str) -> list[dict[str, Any]]:
    out = subprocess.check_output(
        [
            "psql",
            "-h",
            os.environ.get("PGHOST", "127.0.0.1"),
            "-U",
            os.environ.get("PGUSER", "minisandmore_user"),
            "-d",
            os.environ.get("PGDATABASE", "miniandmore"),
            "-tA",
            "-c",
            sql,
        ],
        env={**os.environ, "PGPASSWORD": os.environ.get("PGPASSWORD", "Kzer!@A123")},
        text=True,
    )
    rows: list[dict[str, Any]] = []
    for line in out.strip().split("\n"):
        if not line.strip():
            continue
        pid, sku, colors_json, cij_json, variants_json = line.split("|||", 4)
        rows.append(
            {
                "id": pid,
                "sku": sku,
                "colors_json": colors_json,
                "cij_json": cij_json,
                "variants_json": variants_json,
            }
        )
    return rows


def parse_names(colors_json: str) -> list[str]:
    raw = json.loads(colors_json)
    names: list[str] = []
    for item in raw:
        if isinstance(item, str) and item.strip():
            names.append(item.strip())
        elif isinstance(item, dict) and item.get("name"):
            names.append(str(item["name"]).strip())
    return names


def is_generic_print_slot(name: str) -> bool:
    return bool(re.match(r"^Print\s+\d+$", (name or "").strip(), re.I))


def sort_gallery_keys(keys: list[str]) -> list[str]:
    def sort_key(label: str) -> tuple:
        parts = re.split(r"(\d+)", label.strip().lower())
        out: list[Any] = []
        for part in parts:
            out.append(int(part) if part.isdigit() else part)
        return tuple(out)

    return sorted(keys, key=sort_key)


def build_colors(colors_json: str, gallery: dict[str, list[str]], rename: dict[str, str]) -> list[Any]:
    entries = json.loads(colors_json)
    gallery_keys = list(gallery.keys())
    out: list[Any] = []
    for i, item in enumerate(entries):
        old_name = item if isinstance(item, str) else str(item.get("name", "")).strip()
        new_name = rename.get(old_name, gallery_keys[i] if i < len(gallery_keys) else old_name)
        swatch = item.get("swatchUrl") if isinstance(item, dict) else None
        urls = gallery.get(new_name) or []
        row: dict[str, Any] = {"name": new_name}
        if swatch:
            row["swatchUrl"] = swatch
        if len(urls) == 1:
            row["imageUrl"] = urls[0]
        elif len(urls) > 1:
            row["imageUrls"] = urls
        out.append(row)
    return out


def realign(sku: str, colors_json: str, cij_json: str, variants_json: str) -> dict[str, Any] | None:
    gallery = json.loads(cij_json)
    if not isinstance(gallery, dict) or not gallery:
        return None
    names = parse_names(colors_json)
    keys = sort_gallery_keys(list(gallery.keys()))
    if not names or not keys:
        return None
    if set(names) & set(keys):
        return None
    # SKU import drift: variant matrix uses Print 1…N while galleries keep real print names.
    if all(is_generic_print_slot(n) for n in names):
        pair_count = min(len(names), len(keys))
        if pair_count == 0:
            return None
        rename = {names[i]: keys[i] for i in range(pair_count)}
    elif len(names) != len(keys):
        return None
    else:
        rename = {names[i]: keys[i] for i in range(len(names))}
    new_colors = build_colors(colors_json, gallery, rename)
    variants = json.loads(variants_json) if variants_json and variants_json.strip() else []
    if isinstance(variants, list):
        for v in variants:
            if isinstance(v, dict) and v.get("color") in rename:
                v["color"] = rename[v["color"]]
    return {
        "colorsJson": json.dumps(new_colors),
        "variantsJson": json.dumps(variants) if variants else variants_json,
        "rename": rename,
    }


def request(method: str, path: str, body=None, token: str | None = None):
    url = f"{GATEWAY}{path}"
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=120) as resp:
        raw = resp.read().decode()
        return resp.status, json.loads(raw) if raw else None


def main() -> int:
    print(f"=== Realign print colour names @ {GATEWAY} ===")
    code, auth = request(
        "POST",
        "/api/authenticate",
        {"username": ADMIN_USER, "password": ADMIN_PASS, "rememberMe": False},
    )
    token = auth.get("id_token") if isinstance(auth, dict) else None
    if code != 200 or not token:
        print(f"FAIL authenticate {code}")
        return 1

    rows = psql_json(
        """
        SELECT id::text || '|||' || sku || '|||' || colors_json || '|||' || color_images_json || '|||' || COALESCE(variants_json, '[]')
        FROM product
        WHERE active = true
          AND color_images_json IS NOT NULL
          AND color_images_json NOT IN ('', '{}')
        ORDER BY sku;
        """
    )

    fixed = 0
    skipped = 0
    for row in rows:
        pid = row["id"]
        sku = row["sku"]
        colors_json = row["colors_json"]
        cij_json = row["cij_json"]
        variants_json = row["variants_json"]
        patch = realign(sku, colors_json, cij_json, variants_json)
        if not patch:
            skipped += 1
            continue
        _, listed = request("GET", "/api/v1/admin/products", token=token)
        products = listed if isinstance(listed, list) else listed.get("data", listed)
        product = next((p for p in products if str(p.get("id")) == pid), None)
        if not product:
            print(f"FAIL missing admin product {sku} id={pid}")
            continue
        product["colorsJson"] = patch["colorsJson"]
        if patch.get("variantsJson"):
            product["variantsJson"] = patch["variantsJson"]
        code, saved = request("PUT", f"/api/v1/admin/products/{pid}", product, token=token)
        if code not in (200, 201):
            print(f"FAIL {sku} save HTTP {code}: {str(saved)[:200]}")
            continue
        print(f"OK   {sku}: {patch['rename']}")
        fixed += 1

    print(f"\nFixed {fixed}, skipped {skipped}")
    return 0 if fixed or skipped else 1


if __name__ == "__main__":
    sys.exit(main())
