#!/usr/bin/env python3
"""Link per-print gallery images from R2 onto catalog products.

Lists all non-swatch objects under uat/products/, matches filenames to print names
and product slugs, preserves swatchUrl from Postgres.

Usage:
  GATEWAY_URL=http://127.0.0.1:9180 ADMIN_USER=admin ADMIN_PASS=admin6 \\
    python3 deploy/scripts/prod-restore-galleries-from-r2.py [--dry-run] [--since YYYY-MM-DD]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Any

REPO = Path(__file__).resolve().parents[2]
CATALOG = os.environ.get("CATALOG_URL", "http://127.0.0.1:9184").rstrip("/")
GATEWAY = os.environ.get("GATEWAY_URL", "http://127.0.0.1:9180").rstrip("/")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ADMIN_PASS", "admin6")


def request(method: str, url: str, body: Any | None = None, token: str | None = None) -> tuple[int, Any]:
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            raw = resp.read().decode()
            return resp.getcode(), json.loads(raw) if raw else None
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
    code, body = request("POST", f"{GATEWAY}/api/authenticate", {"username": ADMIN_USER, "password": ADMIN_PASS, "rememberMe": False})
    token = (body or {}).get("id_token") if isinstance(body, dict) else None
    if code != 200 or not token:
        raise RuntimeError(f"auth failed: {code} {body}")
    return token


def norm_key(s: str) -> str:
    return re.sub(r"\s+", " ", (s or "").strip().lower())


def fetch_colors_json_from_db(product_id: int) -> str | None:
    password = os.environ.get("SPRING_DATASOURCE_PASSWORD", "")
    user = os.environ.get("SPRING_R2DBC_USERNAME", "minisandmore_user")
    if not password:
        return None
    sql = f"SELECT colors_json FROM product WHERE id={product_id} LIMIT 1;"
    try:
        out = subprocess.run(
            ["psql", "-h", "127.0.0.1", "-U", user, "-d", "miniandmore", "-t", "-A", "-c", sql],
            capture_output=True,
            text=True,
            timeout=30,
            env={**os.environ, "PGPASSWORD": password},
        )
        if out.returncode != 0:
            return None
        raw = (out.stdout or "").strip()
        return raw if raw else None
    except Exception:
        return None


def parse_colors(colors_json: str | None) -> list[dict[str, Any]]:
    if not colors_json:
        return []
    try:
        raw = json.loads(colors_json)
    except json.JSONDecodeError:
        return []
    if not isinstance(raw, list):
        return []
    out: list[dict[str, Any]] = []
    for item in raw:
        if isinstance(item, str) and item.strip():
            out.append({"name": item.strip()})
        elif isinstance(item, dict) and item.get("name"):
            out.append(dict(item))
    return out


def serialize_colors(entries: list[dict[str, Any]]) -> str:
    clean: list[Any] = []
    has_rich = any(e.get("swatchUrl") or e.get("imageUrl") or e.get("imageUrls") for e in entries)
    for e in entries:
        name = (e.get("name") or "").strip()
        if not name:
            continue
        if not has_rich:
            clean.append(name)
            continue
        row: dict[str, Any] = {"name": name}
        if e.get("swatchUrl"):
            row["swatchUrl"] = e["swatchUrl"]
        urls = e.get("imageUrls") or ([] if not e.get("imageUrl") else [e["imageUrl"]])
        if isinstance(urls, list) and urls:
            if len(urls) == 1:
                row["imageUrl"] = urls[0]
            else:
                row["imageUrls"] = urls
        clean.append(row)
    return json.dumps(clean)


def filename_stem(key: str) -> str:
    base = key.rsplit("/", 1)[-1]
    m = re.match(r"^\d+-[a-f0-9-]+-(.*)\.[a-z0-9]+$", base, re.I)
    stem = m.group(1) if m else base
    return stem.lower().replace("-", " ").replace("_", " ")


def filename_stem_hyphen(key: str) -> str:
    return filename_stem(key).replace(" ", "-")


def is_swatch_key(key: str) -> bool:
    return "-swatch." in key.lower()


def color_tokens(color_name: str) -> list[str]:
    nk = norm_key(color_name).replace(",", " ")
    return [t for t in re.split(r"[\s&]+", nk) if t]


def color_matches_filename(color_name: str, key: str) -> bool:
    nk = norm_key(color_name)
    fn = filename_stem(key)
    fn_hyphen = filename_stem_hyphen(key)
    if not nk or not fn:
        return False

    compact_color = re.sub(r"[^a-z0-9]", "", nk)
    compact_fn = re.sub(r"[^a-z0-9]", "", fn)
    if compact_color and len(compact_color) >= 4 and compact_color in compact_fn:
        return True

    # COMBO 7 → combo-7 / combo 7
    combo_m = re.match(r"^combo\s*(\d+)$", nk)
    if combo_m:
        n = combo_m.group(1)
        if re.search(rf"combo[\s-]*{n}", fn):
            return True

    tokens = color_tokens(color_name)
    significant = [t for t in tokens if len(t) >= 3 or t.isdigit()]
    if significant and all(t in fn for t in significant):
        return True

    # PINK & BEE → pink-bee (filename without ampersand)
    if "pink" in nk and "bee" in nk:
        if "pink" in fn and "bee" in fn:
            return True

    # SNAIL & BEE → snail-bee
    hyphenated = "-".join(tokens)
    if hyphenated and hyphenated in fn_hyphen:
        return True

    # Short print names: BIRD, BEE, AQUA
    if len(nk) <= 5 and nk in fn:
        return True

    return False


def product_slug_tokens(product: dict[str, Any]) -> list[str]:
    slug = norm_key(product.get("slug") or "").replace("-", " ")
    sku = norm_key(product.get("sku") or "").replace("-", " ")
    name = norm_key(product.get("name") or "")
    tokens: set[str] = set()
    for hint in (slug, sku, name):
        for part in re.split(r"[\s-]+", hint):
            if len(part) >= 4:
                tokens.add(part)
    # aliases
    aliases: list[str] = []
    if "napkin" in slug or "napkin" in name:
        aliases.extend(["napkin", "facenapkin", "face napkin"])
    if "terry" in slug or "towel" in slug:
        aliases.extend(["terry", "towel", "baby terry"])
    if "mitten" in slug:
        aliases.extend(["mitten", "mittens"])
    if "bib" in slug:
        aliases.extend(["bib", "bibs"])
    if "socks" in slug:
        aliases.extend(["socks", "sock", "multi color", "multicolor"])
    if "sleveless" in slug or "sleeveless" in slug:
        aliases.extend(["sleeveless", "sleveless", "vest sleeveless"])
    if "tying" in slug and "cap" in slug:
        aliases.extend(["tying cap", "cap"])
    if "round" in slug and "cap" in slug:
        aliases.extend(["round cap", "cap"])
    for a in aliases:
        tokens.add(a.replace(" ", ""))
        for p in a.split():
            if len(p) >= 4:
                tokens.add(p)
    return list(tokens)


def product_matches_filename(product: dict[str, Any], key: str) -> bool:
    fn = filename_stem(key)
    fn_compact = re.sub(r"[^a-z0-9]", "", fn)
    for token in product_slug_tokens(product):
        t = token.replace(" ", "")
        if len(t) >= 4 and t in fn_compact:
            return True
    return False


def list_r2_objects() -> list[dict[str, Any]]:
    code, body = request("GET", f"{CATALOG}/internal/v1/admin/uploads/objects?folder=products&maxKeys=5000")
    if code != 200:
        raise RuntimeError(f"R2 list failed HTTP {code}: {body}")
    items = unwrap(body) or []
    if not isinstance(items, list):
        raise RuntimeError(f"unexpected list response: {body}")
    return [x for x in items if not is_swatch_key(x.get("key") or "")]


def merge_rich_colors(api_colors: list[dict[str, Any]], db_colors: list[dict[str, Any]]) -> list[dict[str, Any]]:
    db_by_key = {norm_key(c["name"]): c for c in db_colors if c.get("name")}
    merged: list[dict[str, Any]] = []
    for entry in api_colors:
        name = (entry.get("name") or "").strip()
        if not name:
            continue
        prev = db_by_key.get(norm_key(name))
        if prev:
            entry = {**prev, **{k: v for k, v in entry.items() if v}}
            if prev.get("swatchUrl") and not entry.get("swatchUrl"):
                entry["swatchUrl"] = prev["swatchUrl"]
        merged.append(entry)
    return merged


def gallery_count(entry: dict[str, Any]) -> int:
    return len(entry.get("imageUrls") or ([] if not entry.get("imageUrl") else [1]))


def gallery_urls_for_entry(entry: dict[str, Any]) -> list[str]:
    urls = entry.get("imageUrls") or ([] if not entry.get("imageUrl") else [entry["imageUrl"]])
    swatch = (entry.get("swatchUrl") or "").strip()
    gallery = [u for u in urls if u != swatch]
    return gallery if gallery else list(urls)


def listing_hero_from_colors(entries: list[dict[str, Any]]) -> str | None:
    for entry in entries:
        gallery = gallery_urls_for_entry(entry)
        if gallery:
            return gallery[0]
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--since", type=str, default=None, help="Only objects modified on/after date")
    args = parser.parse_args()

    print(f"=== Restore per-print galleries from R2 @ {CATALOG} ===")
    objects = list_r2_objects()
    if args.since:
        objects = [o for o in objects if str(o.get("lastModified", "")).startswith(args.since)]
    print(f"Using {len(objects)} non-swatch objects" + (f" since {args.since}" if args.since else " (all dates)"))

    token = auth()
    code, body = request("GET", f"{GATEWAY}/api/v1/admin/products?size=200", token=token)
    if code != 200:
        print(f"list products failed: {code}", file=sys.stderr)
        return 1
    products = unwrap(body) or []

    # Index: product_id -> {color_key: [urls]}, orphans per product
    per_product: dict[int, dict[str, list[str]]] = defaultdict(lambda: defaultdict(list))
    orphans: dict[int, list[str]] = defaultdict(list)

    for product in products:
        pid = product.get("id")
        if not pid:
            continue
        color_names = [c.get("name", "") for c in parse_colors(product.get("colorsJson"))]
        for obj in objects:
            key = obj.get("key") or ""
            url = obj.get("publicUrl") or ""
            if not url:
                continue
            matched_colors = [cn for cn in color_names if color_matches_filename(cn, key)]
            if matched_colors:
                for cn in matched_colors:
                    per_product[pid][norm_key(cn)].append(url)
                continue
            if product_matches_filename(product, key):
                orphans[pid].append(url)

    # Do not spread orphan uploads across prints — that mixed other products' photos in.
    for product in products:
        pid = product.get("id")
        buckets = per_product.get(pid)
        if not buckets:
            continue

        db_colors = parse_colors(fetch_colors_json_from_db(int(pid)) if pid else None)
        api_colors = parse_colors(product.get("colorsJson"))
        entries = merge_rich_colors(api_colors if api_colors else db_colors, db_colors)
        changed = False
        for entry in entries:
            key = norm_key(entry.get("name") or "")
            urls = list(dict.fromkeys(buckets.get(key, [])))
            if not urls:
                continue
            swatch = (entry.get("swatchUrl") or "").strip()
            gallery_only = [u for u in urls if u != swatch]
            merged = gallery_only if gallery_only else urls
            existing = entry.get("imageUrls") or ([] if not entry.get("imageUrl") else [entry["imageUrl"]])
            combined = list(dict.fromkeys([*(existing or []), *merged]))
            if combined == existing:
                continue
            entry["imageUrls"] = combined
            entry.pop("imageUrl", None)
            changed = True

        if not changed:
            continue

        product["colorsJson"] = serialize_colors(entries)
        hero = listing_hero_from_colors(entries)
        if hero:
            product["imagesJson"] = json.dumps([hero])
        sku = product.get("sku", "")
        name = (product.get("name") or "")[:50]
        if args.dry_run:
            print(f"  [dry-run] would update {sku} — {name}")
            updated += 1
            continue

        code, saved = request("PUT", f"{GATEWAY}/api/v1/admin/products/{pid}", product, token=token)
        if code in (200, 201):
            updated += 1
            print(f"  updated {sku} — {name}")
        else:
            print(f"  FAIL {sku}: {code} {saved}", file=sys.stderr)

    print(f"\nDone — updated {updated} products")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
