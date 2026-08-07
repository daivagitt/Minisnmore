#!/usr/bin/env python3
"""Remove cross-product junk gallery URLs introduced by orphan-spread R2 restore."""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import sys
import urllib.request
from pathlib import Path
from typing import Any

GATEWAY = os.environ.get("GATEWAY_URL", "http://127.0.0.1:9180").rstrip("/")
CATALOG = os.environ.get("CATALOG_URL", "http://127.0.0.1:9184").rstrip("/")

_SCRIPT = Path(__file__).resolve().parent / "uat-restore-galleries-from-r2.py"
_spec = importlib.util.spec_from_file_location("r2restore", _SCRIPT)
r2 = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
_spec.loader.exec_module(r2)


def slug_fingerprint(slug: str) -> str:
    return re.sub(r"[^a-z0-9]", "", (slug or "").strip().lower())


def gallery_url_ok(product: dict[str, Any], color_name: str, url: str) -> bool:
    key = url.rsplit("/", 1)[-1]
    if r2.is_swatch_key(key):
        return False
    fp = slug_fingerprint(product.get("slug") or "")
    fn = re.sub(r"[^a-z0-9]", "", key.lower())
    if len(fp) >= 10 and fp not in fn:
        return False
    if not r2.product_matches_filename(product, key) and (len(fp) < 10 or fp not in fn):
        return False
    if r2.color_matches_filename(color_name, key):
        return True
    if "gallery" not in key.lower():
        return False
    return len(fp) >= 10 and fp in fn


def strict_r2_urls(product: dict[str, Any], color_name: str, objects: list[dict[str, Any]]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    fp = slug_fingerprint(product.get("slug") or "")
    sku = (product.get("sku") or "").upper()
    for obj in objects:
        key = obj.get("key") or ""
        url = obj.get("publicUrl") or ""
        if not url or r2.is_swatch_key(key):
            continue
        fn = re.sub(r"[^a-z0-9]", "", key.lower())
        if len(fp) >= 10 and fp not in fn:
            continue
        ok = r2.color_matches_filename(color_name, key)
        if not ok and "gallery" in key.lower() and len(fp) >= 10 and fp in fn:
            ok = True
        # Tying Cap: RABBIT print uses images-4x batch uploaded with tying-cap lifestyle set.
        if sku == "TYING-CAP" and "RABBIT" in color_name.upper():
            ok = ok or bool(re.search(r"images-4[0-9]--gallery", key, re.I))
        if sku == "TYING-CAP" and "TREE" in color_name.upper():
            ok = ok and "tying-cap" in key.lower()
        if ok and url not in seen:
            seen.add(url)
            out.append(url)
    return out


def listing_hero(entries: list[dict[str, Any]]) -> str | None:
    for entry in entries:
        sw = (entry.get("swatchUrl") or "").strip()
        urls = entry.get("imageUrls") or ([] if not entry.get("imageUrl") else [entry["imageUrl"]])
        for url in urls:
            if url != sw:
                return url
    return None


def dedupe_shared_galleries(entries: list[dict[str, Any]]) -> None:
    """When prints share identical gallery lists, keep only URLs matching each print name."""

    def gallery_list(entry: dict[str, Any]) -> list[str]:
        if entry.get("imageUrls"):
            return list(entry["imageUrls"])
        if entry.get("imageUrl"):
            return [entry["imageUrl"]]
        return []

    lists = [tuple(sorted(gallery_list(e))) for e in entries]
    non_empty = [g for g in lists if g]
    if len(non_empty) < 2 or len(set(non_empty)) != 1:
        return

    for entry in entries:
        name = entry.get("name") or ""
        urls = gallery_list(entry)
        matched = [
            u for u in urls if r2.color_matches_filename(name, u.rsplit("/", 1)[-1])
        ]
        entry.pop("imageUrl", None)
        entry.pop("imageUrls", None)
        if len(matched) == 1:
            entry["imageUrl"] = matched[0]
        elif len(matched) > 1:
            entry["imageUrls"] = matched[:10]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    print(f"=== Strip junk galleries @ {GATEWAY} ===")
    token = r2.auth()
    code, body = r2.request("GET", f"{GATEWAY}/api/v1/admin/products?size=200", token=token)
    products = r2.unwrap(body) or []
    if code != 200:
        print(f"list failed: {code}", file=sys.stderr)
        return 1

    code, body = r2.request("GET", f"{CATALOG}/internal/v1/admin/uploads/objects?folder=products&maxKeys=5000")
    objects = r2.unwrap(body) or []
    if code != 200:
        print(f"R2 list failed: {code}", file=sys.stderr)
        return 1
    objects = [o for o in objects if not r2.is_swatch_key(o.get("key") or "")]

    updated = 0
    for product in products:
        pid = product.get("id")
        db_colors = r2.parse_colors(r2.fetch_colors_json_from_db(int(pid)) if pid else None)
        api_colors = r2.parse_colors(product.get("colorsJson"))
        by_name = {r2.norm_key(c["name"]): c for c in db_colors if c.get("name")}
        entries: list[dict[str, Any]] = []
        for entry in api_colors if api_colors else db_colors:
            name = (entry.get("name") or "").strip()
            if not name:
                continue
            prev = by_name.get(r2.norm_key(name), {})
            swatch = (entry.get("swatchUrl") or prev.get("swatchUrl") or "").strip()
            existing = entry.get("imageUrls") or prev.get("imageUrls") or (
                [entry.get("imageUrl")]
                if entry.get("imageUrl")
                else ([prev.get("imageUrl")] if prev.get("imageUrl") else [])
            )
            kept = [u for u in existing if gallery_url_ok(product, name, u)]
            if not kept:
                kept = strict_r2_urls(product, name, objects)
            row: dict[str, Any] = {"name": name}
            if swatch:
                row["swatchUrl"] = swatch
            gallery = [u for u in kept if u != swatch][:10]
            if len(gallery) == 1:
                row["imageUrl"] = gallery[0]
            elif len(gallery) > 1:
                row["imageUrls"] = gallery
            entries.append(row)

        dedupe_shared_galleries(entries)

        if not entries:
            continue
        new_json = r2.serialize_colors(entries)
        if new_json == product.get("colorsJson"):
            continue
        sku = product.get("sku", "")
        pname = (product.get("name") or "")[:45]
        if args.dry_run:
            print(f"  [dry-run] {sku} — {pname}")
            updated += 1
            continue
        product["colorsJson"] = new_json
        hero = listing_hero(entries)
        if hero:
            product["imagesJson"] = json.dumps([hero])
        code, _ = r2.request("PUT", f"{GATEWAY}/api/v1/admin/products/{pid}", product, token=token)
        if code in (200, 201):
            updated += 1
            print(f"  cleaned {sku} — {pname}")
        else:
            print(f"  FAIL {sku}: HTTP {code}", file=sys.stderr)

    print(f"\nDone — cleaned {updated} products")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
