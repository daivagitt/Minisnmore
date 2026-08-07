#!/usr/bin/env python3
"""Restore UAT catalog to the pre-split 'good stage'.

Rebuilds color_images_json from R2 per print:
  - color_matches_filename + product association (no cross-product junk)
  - product-named gallery files go to every print (shared) — undoes gallery-number splits
  - NO orphan spreading of non-gallery product matches
  - NO sequential gallery-number splitting

Then rebuilds colors_json with preserved swatchUrl + lifestyle URLs from that map.
Sets listing hero from first print's first lifestyle URL.

Usage:
  GATEWAY_URL=http://127.0.0.1:9180 CATALOG_URL=http://127.0.0.1:9184 \\
    python3 deploy/scripts/uat-restore-catalog-good-stage.py [--apply] [--sku SKU]

Without --apply, dry-run only. Skip TEST-COLOR-IMG-JSON.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

_DIR = Path(__file__).resolve().parent
_r2_spec = importlib.util.spec_from_file_location("r2restore", _DIR / "uat-restore-galleries-from-r2.py")
r2 = importlib.util.module_from_spec(_r2_spec)
assert _r2_spec.loader is not None
_r2_spec.loader.exec_module(r2)

# Reuse strict per-SKU product gates from the audit script (match helpers only — never run splits).
_fix_spec = importlib.util.spec_from_file_location("ppfix", _DIR / "uat-fix-per-print-galleries.py")
pp = importlib.util.module_from_spec(_fix_spec)
assert _fix_spec.loader is not None
_fix_spec.loader.exec_module(pp)

GATEWAY = r2.GATEWAY
CATALOG = r2.CATALOG
MAX_GALLERY = 10
SKIP_SKUS = {"TEST-COLOR-IMG-JSON"}


def gallery_urls(entry: dict[str, Any]) -> list[str]:
    urls = entry.get("imageUrls") or ([] if not entry.get("imageUrl") else [entry["imageUrl"]])
    swatch = (entry.get("swatchUrl") or "").strip()
    out: list[str] = []
    seen: set[str] = set()
    for u in urls:
        if not u or u == swatch or u in seen:
            continue
        seen.add(u)
        out.append(u)
    return out


def apply_gallery(entry: dict[str, Any], urls: list[str]) -> None:
    swatch = (entry.get("swatchUrl") or "").strip()
    clean = [u for u in dict.fromkeys(urls) if u and u != swatch][:MAX_GALLERY]
    entry.pop("imageUrl", None)
    entry.pop("imageUrls", None)
    if len(clean) == 1:
        entry["imageUrl"] = clean[0]
    elif len(clean) > 1:
        entry["imageUrls"] = clean


def serialize_color_images(gallery_map: dict[str, list[str]]) -> str:
    out: dict[str, list[str]] = {}
    for name, urls in gallery_map.items():
        clean = [u for u in dict.fromkeys(urls) if u][:MAX_GALLERY]
        if clean:
            out[name] = clean
    return json.dumps(out)


def listing_hero(entries: list[dict[str, Any]]) -> str | None:
    for entry in entries:
        gallery = gallery_urls(entry)
        if gallery:
            return gallery[0]
    return None


def is_gallery_filename(key: str) -> bool:
    k = key.lower()
    return "--gallery" in k or "gallery" in k.rsplit("/", 1)[-1]


def rebuild_gallery_map(
    product: dict[str, Any],
    color_names: list[str],
    objects: list[dict[str, Any]],
) -> dict[str, list[str]]:
    """Rebuild per-print galleries from R2 without number-splitting or orphan spread.

    1) color_matches + product_url_ok → that print only
    2) product gallery file (product_url_ok, gallery in name, no print match) → ALL prints
       (shared lifestyle set — undoes artificial Body Suit / Jabla number splits)
    3) product_matches orphans that are not galleries → ignored
    """
    buckets: dict[str, list[str]] = defaultdict(list)
    seen: dict[str, set[str]] = defaultdict(set)

    def add(cn: str, url: str) -> None:
        nk = r2.norm_key(cn)
        if url in seen[nk]:
            return
        seen[nk].add(url)
        buckets[nk].append(url)

    for obj in objects:
        key = obj.get("key") or ""
        url = obj.get("publicUrl") or ""
        if not url or r2.is_swatch_key(key):
            continue
        if not pp.product_url_ok(product, key):
            continue

        matched = [cn for cn in color_names if r2.color_matches_filename(cn, key)]
        if matched:
            for cn in matched:
                add(cn, url)
            continue

        # Product-named lifestyle galleries with no print token: share across prints
        # (pre-split good stage). Do NOT spread non-gallery orphans.
        if is_gallery_filename(key):
            for cn in color_names:
                add(cn, url)

    out: dict[str, list[str]] = {}
    for cn in color_names:
        urls = buckets.get(r2.norm_key(cn), [])[:MAX_GALLERY]
        if urls:
            out[cn] = urls
    return out


def build_colors_from_map(
    entries: list[dict[str, Any]],
    gallery_map: dict[str, list[str]],
) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for entry in entries:
        name = (entry.get("name") or "").strip()
        if not name:
            continue
        row: dict[str, Any] = {"name": name}
        swatch = (entry.get("swatchUrl") or "").strip()
        if swatch:
            row["swatchUrl"] = swatch
        urls = gallery_map.get(name) or []
        if not urls:
            for k, v in gallery_map.items():
                if r2.norm_key(k) == r2.norm_key(name):
                    urls = v
                    break
        apply_gallery(row, urls)
        out.append(row)
    return out


def parse_existing_cij(raw: str | None) -> dict[str, list[str]]:
    if not raw or not raw.strip():
        return {}
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    if not isinstance(data, dict):
        return {}
    out: dict[str, list[str]] = {}
    for k, v in data.items():
        if isinstance(v, list):
            out[str(k)] = [u for u in v if isinstance(u, str) and u]
        elif isinstance(v, str) and v:
            out[str(k)] = [v]
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Admin-PUT changes (default: dry-run)")
    parser.add_argument("--sku", action="append", default=[], help="Limit to SKU(s)")
    args = parser.parse_args()

    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"=== Restore catalog good-stage ({mode}) @ {GATEWAY} ===")
    print("Rules: R2 color+product match, shared product galleries, no orphan/number split")

    token = r2.auth()
    code, body = r2.request("GET", f"{GATEWAY}/api/v1/admin/products?size=200", token=token)
    if code != 200:
        print(f"list products failed: {code} {body}", file=sys.stderr)
        return 1
    products: list[dict[str, Any]] = r2.unwrap(body) or []
    if args.sku:
        want = {s.upper() for s in args.sku}
        products = [p for p in products if (p.get("sku") or "").upper() in want]

    objects = r2.list_r2_objects()
    print(f"Products: {len(products)} | R2 non-swatch objects: {len(objects)}")

    updated = 0
    skipped = 0
    unchanged = 0
    failed = 0

    for product in products:
        sku = (product.get("sku") or "").upper()
        pid = product.get("id")
        if not pid:
            continue
        if sku in SKIP_SKUS:
            skipped += 1
            print(f"  skip {sku} (test fixture)")
            continue

        db_colors = r2.parse_colors(r2.fetch_colors_json_from_db(int(pid)))
        api_colors = r2.parse_colors(product.get("colorsJson"))
        # Prefer API colour order; merge DB swatches. Also pull names from existing cij keys
        # when colors_json was stripped to plain strings after a bad split pass.
        entries = r2.merge_rich_colors(api_colors if api_colors else db_colors, db_colors)
        existing_cij = parse_existing_cij(product.get("colorImagesJson"))
        if len(entries) < 2 and len(existing_cij) >= 2:
            # Rebuild colour slots from cij keys (preserve any swatches by name).
            by = {r2.norm_key(e.get("name") or ""): e for e in entries}
            entries = []
            for name in existing_cij.keys():
                prev = by.get(r2.norm_key(name), {})
                row: dict[str, Any] = {"name": name}
                if prev.get("swatchUrl"):
                    row["swatchUrl"] = prev["swatchUrl"]
                entries.append(row)

        if len(entries) < 2:
            skipped += 1
            continue

        color_names = [(e.get("name") or "").strip() for e in entries if e.get("name")]
        gallery_map = rebuild_gallery_map(product, color_names, objects)

        # If R2 rebuild is empty for a print that already had cij galleries, keep prior
        # URLs only when they still pass product_url_ok (don't reintroduce junk).
        for cn in color_names:
            if gallery_map.get(cn):
                continue
            prior = existing_cij.get(cn) or []
            kept = [u for u in prior if pp.product_url_ok(product, u)]
            if kept:
                gallery_map[cn] = kept[:MAX_GALLERY]

        new_entries = build_colors_from_map(entries, gallery_map)
        new_colors_json = r2.serialize_colors(new_entries)
        new_cij = serialize_color_images(
            {e["name"]: gallery_urls(e) for e in new_entries if gallery_urls(e)}
        )
        hero = listing_hero(new_entries)
        new_images = json.dumps([hero]) if hero else product.get("imagesJson")

        old_colors = product.get("colorsJson") or ""
        old_cij = (product.get("colorImagesJson") or "").strip() or "{}"
        old_images = product.get("imagesJson") or ""

        try:
            old_cij_norm = json.dumps(json.loads(old_cij), sort_keys=True)
        except json.JSONDecodeError:
            old_cij_norm = old_cij
        try:
            new_cij_norm = json.dumps(json.loads(new_cij), sort_keys=True)
        except json.JSONDecodeError:
            new_cij_norm = new_cij

        if (
            new_colors_json == old_colors
            and new_cij_norm == old_cij_norm
            and (not hero or new_images == old_images)
        ):
            unchanged += 1
            counts = [f"{e.get('name')}={len(gallery_urls(e))}" for e in new_entries]
            print(f"  ok   {sku}: already good-stage ({', '.join(counts)})")
            continue

        counts = [f"{e.get('name')}={len(gallery_urls(e))}" for e in new_entries]
        pname = (product.get("name") or "")[:45]
        if not args.apply:
            print(f"  [dry-run] {sku} — {pname}: {', '.join(counts)}")
            updated += 1
            continue

        product["colorsJson"] = new_colors_json
        product["colorImagesJson"] = new_cij
        if hero:
            product["imagesJson"] = new_images

        code, saved = r2.request("PUT", f"{GATEWAY}/api/v1/admin/products/{pid}", product, token=token)
        if code in (200, 201):
            updated += 1
            print(f"  restored {sku}: {', '.join(counts)}")
        else:
            failed += 1
            print(f"  FAIL {sku}: HTTP {code} {saved}", file=sys.stderr)

    print(
        f"\nDone — restored/would-restore={updated} unchanged={unchanged} "
        f"skipped={skipped} failed={failed}"
    )
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
