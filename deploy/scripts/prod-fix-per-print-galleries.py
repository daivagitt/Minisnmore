#!/usr/bin/env python3
"""Audit and fix per-print gallery URL overlap on UAT catalog products.

Goal: no multi-print product shares lifestyle image URLs across prints (PDP must not
show every print's images at once).

Usage:
  GATEWAY_URL=http://127.0.0.1:9180 CATALOG_URL=http://127.0.0.1:9184 \\
    python3 deploy/scripts/prod-fix-per-print-galleries.py [--apply] [--sku SKU]

Without --apply, runs audit only (dry-run). With --apply, admin-PUT fixes flagged products.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

_SCRIPT = Path(__file__).resolve().parent / "prod-restore-galleries-from-r2.py"
_spec = importlib.util.spec_from_file_location("r2restore", _SCRIPT)
r2 = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
_spec.loader.exec_module(r2)

GATEWAY = r2.GATEWAY
CATALOG = r2.CATALOG

# Already fixed in earlier UAT runs — leave alone when still zero-overlap.
PRESERVE_IF_OK = {
    "BODY-SUIT-HALF-SLEEVE",
    "BOOK-STYLE-TYING-JABLA",
    "BOOK-STYLE-TYING-JABLA-SHORT-SET",
}

SKIP_SKUS = {
    "TEST-COLOR-IMG-JSON",  # fixture product; skip unless --include-test
}

# Only rewrite these for CROSS_PRODUCT_JUNK alone (overlap flags always fix).
# Avoid rewriting previously-correct catalogs that merely trip a strict junk heuristic.
JUNK_FIX_SKUS = {
    "LOOP-BACK-FLEECE-KNIT-FRONT-OPEN-VEST-FULL-SLEEV",
    "SIDE-BUTTON-T-SHIRT-HALF-SLEEVE",
    "SIDE-BUTTON-T-SHIRT-SHORTS-SET-HALF-SLEEVE",
    "FACE-NAPKIN-WASH-CLOTH-10-10",
    "FRONT-OPEN-VEST-SLEVELESS",
    "FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET",
    "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS",
    "BIBS-SPECIAL",
    "ROUND-CAP",
    "PACK-OF-3-MITTENS",
    "TYING-CAP",  # restored after accidental junk strip
    "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE",  # restore after accidental R2 sprawl
    "BASIC-SHORTS",
    "BABY-TERRY-TOWEL-FLUFFY",
    "FRONT-OPEN-VEST-HALF-SLEEVE",
    "FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET",
    "CAP-SOCKS-MITTEN-SET",
}

# Rebuild from strict R2 pool only — same correct product photos on every print.
STRICT_SHARED_SKUS = {
    "FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET",
    "FRONT-OPEN-VEST-SLEVELESS",
    "FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET",
    "CAP-SOCKS-MITTEN-SET",
    "LOOP-BACK-FLEECE-KNIT-FRONT-OPEN-VEST-FULL-SLEEV",
}

OVERLAP_FLAGS = {"SHARED_IDENTICAL", "PARTIAL_OVERLAP", "GENERIC_SHARED"}
SIDE_BUTTON_HINTS = ("pink", "snail", "blue", "bee", "fish")

MAX_GALLERY = 10

# Verified studio batch → gallery number per print (avoids wrong sequential splits).
# Use int for N in *-N--gallery.*; "gallery" = unnumbered *-gallery.jpg for that product.
KNOWN_PRINT_GALLERY_NUMS: dict[str, dict[str, list[int | str]]] = {
    "BABY-SWADDLE": {
        "BROWN SWADDLE": [2, 7, "gallery"],
        "GREEN SWADDLE": [9, 10, 8],
        "WHITE SWADDLE": [3, 4, 6],
    },
    "BODY-SUIT-HALF-SLEEVE": {
        "AQUA": [1, 3, 10, 11],
        "BIRD": [5, 7, 8, 9],
        "SNAIL": [2, 4, 12, 13],
    },
    "BASIC-SHORTS": {
        "SNAIL": [1, 13, 19, 23, 25],
        "BEE & BLUE": [2, 7, 18, 27, 29],
        "HAPPY BEE": [4, 6, 10, 28],
        "BABY BLUE": [5, 7, 8, 18, 21, 22],
    },
    "TYING-CAP": {
        "RABBIT, BIRD & BLUE": [2, 3, 4],
        "TREE, BIRD & BLUE": [5],
    },
    "BOOK-STYLE-TYING-JABLA": {
        "BLUE": [1, 3, 10, 11],
        "FISH": [12, 13, 14, 15],
        "BIRD": [16, 17, 18, 19],
    },
    "BOOK-STYLE-TYING-JABLA-SHORT-SET": {
        "BLUE": [1, 3, 10, 11],
        "FISH": [12, 13, 14, 15],
        "BIRD": [16, 17, 18, 19],
    },
    "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE": {
        "AQUA": [9, 2, 5, 8],
        "BIRD": [16, 18, 17, 7],
        "HAPPY BEE": [6, 11, 13, 3],
        "PINK BUDS": [4, 14, 19],
    },
    "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS": {
        "HAPPY BEE": [6, 11, 13, 3],
        "PINK BUDS": [4, 14, 19],
        "SNAIL": [9, 2, 5, 8],
    },
}

# Bloomer lifestyle batch numbers per print (paired with frock nums above).
BLOOMER_PRINT_GALLERY_NUMS: dict[str, dict[str, list[int | str]]] = {
    "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS": {
        "HAPPY BEE": [1, 2, 3, 5],
        "PINK BUDS": [7, 9, 11],
        "SNAIL": ["gallery"],
    },
}


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


def url_key(url: str) -> str:
    return (url or "").rsplit("/", 1)[-1]


def gallery_number(url: str) -> int | None:
    key = url_key(url)
    m = re.search(r"-(\d+)--gallery\.", key, re.I)
    if m:
        return int(m.group(1))
    m = re.search(r"--(\d+)-\.", key, re.I)
    if m:
        return int(m.group(1))
    m = re.search(r"-(\d+)\.(?:jpg|jpeg|png|webp)$", key, re.I)
    if m:
        return int(m.group(1))
    m = re.search(r"print-(\d+)-", key, re.I)
    if m:
        return int(m.group(1))
    return None


def sort_by_gallery_num(urls: list[str]) -> list[str]:
    def key(u: str) -> tuple[int, str]:
        n = gallery_number(u)
        return (n if n is not None else 10_000, url_key(u).lower())

    return sorted(urls, key=key)


def urls_for_gallery_nums(pool: list[str], nums: list[int | str]) -> list[str]:
    """Pick URLs from pool by gallery number (verified batch map)."""
    out: list[str] = []
    seen: set[str] = set()
    pool_by_num: dict[int, str] = {}
    unnumbered: list[str] = []
    for u in pool:
        n = gallery_number(u)
        if n is not None and n not in pool_by_num:
            pool_by_num[n] = u
        elif n is None and "gallery" in url_key(u).lower():
            unnumbered.append(u)
    for spec in nums:
        if spec == "gallery":
            for u in unnumbered:
                if u not in seen:
                    seen.add(u)
                    out.append(u)
                    break
        elif isinstance(spec, int) and spec in pool_by_num:
            u = pool_by_num[spec]
            if u not in seen:
                seen.add(u)
                out.append(u)
    return out


def gallery_lifestyle_pool(pool: list[str]) -> list[str]:
    """Prefer numbered lifestyle gallery files over legacy PNG/JPEG product stills."""
    out: list[str] = []
    seen: set[str] = set()
    for u in pool:
        key = url_key(u).lower()
        if r2.is_swatch_key(key):
            continue
        if "gallery" in key or gallery_number(u) is not None:
            if u not in seen:
                seen.add(u)
                out.append(u)
    if out:
        return out
    return list(dict.fromkeys(pool))


def assign_known_gallery_map(
    sku: str,
    pool: list[str],
    color_names: list[str],
) -> dict[str, list[str]] | None:
    spec = KNOWN_PRINT_GALLERY_NUMS.get(sku.upper())
    if not spec:
        return None
    lifestyle = gallery_lifestyle_pool(pool)
    assigned: dict[str, list[str]] = {}
    for name in color_names:
        nums = spec.get(name) or spec.get(name.upper())
        if not nums:
            assigned[name] = []
            continue
        assigned[name] = urls_for_gallery_nums(lifestyle, nums)[:MAX_GALLERY]
    return assigned


def apply_strict_known_maps(
    sku: str,
    pool: list[str],
    color_names: list[str],
    assigned: dict[str, list[str]],
) -> None:
    """Replace galleries with verified maps only — strips legacy PNG duplicates."""
    spec = KNOWN_PRINT_GALLERY_NUMS.get(sku.upper())
    if not spec:
        return
    lifestyle = gallery_lifestyle_pool(pool)
    for name in color_names:
        nums = spec.get(name) or spec.get(name.upper())
        if not nums:
            continue
        urls = urls_for_gallery_nums(lifestyle, nums)
        if "pink" in name.lower() and "bud" in name.lower():
            for u in lifestyle:
                fn = url_key(u).lower()
                if "pink" in fn and "bud" in fn and "gallery" in fn and u not in urls:
                    urls.append(u)
                    break
        assigned[name] = sort_by_gallery_num(urls)[:MAX_GALLERY]


def assign_bloomer_galleries(
    sku: str,
    pool: list[str],
    color_names: list[str],
) -> dict[str, list[str]]:
    """Frock + bloomer lifestyle sets for the bloomers SKU."""
    spec = BLOOMER_PRINT_GALLERY_NUMS.get(sku.upper())
    if not spec:
        return {}
    lifestyle = gallery_lifestyle_pool(pool)
    frock_pool = [u for u in lifestyle if not is_bloomer_url(u)]
    bloomer_pool = [u for u in lifestyle if is_bloomer_url(u)]
    frock = assign_known_gallery_map(sku, frock_pool, color_names) or {}
    out: dict[str, list[str]] = {}
    for name in color_names:
        urls = list(frock.get(name, []))
        nums = spec.get(name) or spec.get(name.upper())
        if nums:
            urls.extend(urls_for_gallery_nums(bloomer_pool, nums))
        if "pink" in name.lower() and "bud" in name.lower():
            for u in lifestyle:
                fn = url_key(u).lower()
                if "pink" in fn and "bud" in fn and "gallery" in fn and u not in urls:
                    urls.append(u)
                    break
        out[name] = sort_by_gallery_num(urls)[:MAX_GALLERY]
    return out


def share_generic_product_galleries(
    pool: list[str],
    color_names: list[str],
    product: dict[str, Any],
    *,
    claimed: set[str],
) -> dict[str, list[str]]:
    """Same correct product lifestyle set on every print — avoids wrong fabric from splits."""
    generic = [
        u
        for u in pool
        if u not in claimed
        and product_url_ok(product, u)
        and not any(r2.color_matches_filename(cn, url_key(u)) for cn in color_names)
    ]
    generic = sort_by_gallery_num(list(dict.fromkeys(generic)))[:MAX_GALLERY]
    return {name: list(generic) for name in color_names}


def slug_fingerprint(slug: str) -> str:
    return re.sub(r"[^a-z0-9]", "", (slug or "").strip().lower())


def _fn_compact(url: str) -> str:
    return re.sub(r"[^a-z0-9]", "", url_key(url).lower())


def _cross_product_block(fn: str, compact: str) -> bool:
    """True when filename clearly belongs to another product line."""
    blocked = (
        "body-suit",
        "bodysuit",
        "side-button",
        "jabla",
        "book-style",
        "book-stlye",
        "bloomer",
        "swaddle",
        "tying-cap",
        "napkin",
    )
    return any(b in fn or b.replace("-", "") in compact for b in blocked)


def product_url_ok(product: dict[str, Any], url: str) -> bool:
    """Drop cross-product junk — strict per-SKU filename gates (not loose token match)."""
    key = url_key(url)
    if r2.is_swatch_key(key):
        return False
    sku = (product.get("sku") or "").upper()
    fn = key.lower()
    compact = _fn_compact(url)

    # SKU-specific allowlists — avoid product_matches_filename (too loose: "baby","front","open").
    if sku.startswith("LOOP-BACK-FLEECE"):
        if _cross_product_block(fn, compact) or "frock" in fn:
            return False
        return "fleece" in fn

    if sku.startswith("SIDE-BUTTON") and "SHORT" in sku:
        return "side-button" in fn and "short" in fn

    if sku.startswith("SIDE-BUTTON"):
        return "side-button" in fn and "short" not in fn

    if sku.startswith("FACE-NAPKIN"):
        return "napkin" in fn or "facenapkin" in compact

    if sku == "BASIC-SHORTS":
        if "vest" in fn or _cross_product_block(fn, compact):
            return False
        if "basic-shorts" in fn or "basicshorts" in compact:
            return True
        return bool(re.search(r"shorts-\d+--gallery", fn, re.I))

    if sku == "FRONT-OPEN-VEST-HALF-SLEEVE":
        if _cross_product_block(fn, compact) or "sleeveless" in fn or "sleveless" in fn:
            return False
        # Exclude short-set uploads — they belong on FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET only.
        if "short-set" in fn or "shortset" in compact or "shorts-set" in fn:
            return False
        if "short" in fn and "half" not in fn:
            return False
        return "half-sleeve" in fn or "halfsleeve" in compact or ("half" in fn and "vest" in fn)

    if sku == "FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET":
        if _cross_product_block(fn, compact) or "sleeveless" in fn or "sleveless" in fn:
            return False
        if "halfsleeve" in compact and "short" in compact:
            return True
        if "half-sleeve" in fn and ("short" in fn or "shorts" in fn):
            return True
        if re.search(r"shorts-\d+--gallery", fn, re.I):
            return True
        return "half" in fn and "vest" in fn

    if sku == "FRONT-OPEN-VEST-SLEVELESS":
        if "and-short" in fn or "half" in fn or "frock" in fn or _cross_product_block(fn, compact):
            return False
        return "sleeveless" in fn or "sleveless" in fn

    if sku == "FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET":
        if "frock" in fn or "bloomer" in fn or _cross_product_block(fn, compact):
            return False
        if "half" in fn and "sleeveless" not in fn and "sleveless" not in fn:
            return False
        return ("sleeveless" in fn or "sleveless" in fn) and ("short" in fn or "and-short" in fn)

    if sku == "CAP-SOCKS-MITTEN-SET":
        if "cap-mitten" in fn or "capmitten" in compact:
            return True
        if "cap-socks" in fn or "capsocks" in compact:
            return True
        if "mitten" in fn and ("cap" in fn or "gallery" in fn):
            return True
        if re.search(r"socks-\d+--gallery", fn, re.I) and "cap" not in fn and "mitten" not in fn:
            return False
        if re.search(r"socks--\d", fn, re.I):
            return False
        return False

    if sku == "BABY-TERRY-TOWEL-FLUFFY":
        return "terry" in fn or "towel" in fn

    if sku == "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS":
        if "pink" in fn and "bud" in fn:
            return True
        if "bloomer" in fn:
            return True
        return "frock" in fn

    if sku.startswith("BABY-FRONT-OPEN-FROCK"):
        if "bloomer" in fn:
            return False
        if "pink" in fn and "bud" in fn:
            return True
        return "frock" in fn

    if sku.startswith("BIBS"):
        return "bib" in fn

    if sku == "ROUND-CAP":
        return "round" in fn and "cap" in fn

    if sku == "TYING-CAP":
        # Lifestyle batches uploaded as images-4x plus Tying-cap-N galleries.
        if "tying" in fn and "cap" in fn:
            return True
        return bool(re.search(r"images-4[0-9]", fn, re.I))

    if sku.startswith("PACK-OF-3-MITTEN"):
        # Real mitten product photos, or print stills that match a color name.
        if "napkin" in fn:
            return False
        if "sock" in fn and "mitten" not in fn:
            return False
        if "cap-mitten" in fn or "capsocks" in compact:
            return False
        if "mitten" in fn:
            return True
        # Only print-named stills (not every aqua/bird combo on R2).
        return False  # print stills added in fix_entries from color match + current pool

    if sku.startswith("BODY-SUIT"):
        return "body-suit" in fn or "bodysuit" in compact

    if sku.startswith("BOOK-STYLE"):
        return "jabla" in fn or "book" in fn

    # Generic: require slug fingerprint substring when long enough.
    fp = slug_fingerprint(product.get("slug") or "")
    if len(fp) >= 12 and fp[:12] in compact:
        return True
    if len(fp) >= 10 and fp in compact:
        return True
    # Fallback: product token match AND at least one distinctive 5+ char slug token in filename.
    if not r2.product_matches_filename(product, key):
        return False
    slug = r2.norm_key(product.get("slug") or "").replace("-", " ")
    distinctive = [t for t in slug.split() if len(t) >= 5]
    return bool(distinctive) and any(t in fn.replace("-", " ") or t in compact for t in distinctive)


def is_bloomer_url(url: str) -> bool:
    return "bloomer" in url_key(url).lower()


def is_numbered_product_gallery(url: str, product: dict[str, Any]) -> bool:
    """True when filename looks like Product-Name-N--gallery."""
    key = url_key(url)
    if gallery_number(url) is None and "--gallery" not in key.lower() and "gallery" not in key.lower():
        return False
    return product_url_ok(product, url)


def serialize_color_images(entries: list[dict[str, Any]]) -> str:
    out: dict[str, list[str]] = {}
    for e in entries:
        name = (e.get("name") or "").strip()
        if not name:
            continue
        urls = gallery_urls(e)
        if urls:
            out[name] = urls[:MAX_GALLERY]
    return json.dumps(out)


def apply_gallery(entry: dict[str, Any], urls: list[str]) -> None:
    swatch = (entry.get("swatchUrl") or "").strip()
    clean = [u for u in dict.fromkeys(urls) if u and u != swatch][:MAX_GALLERY]
    entry.pop("imageUrl", None)
    entry.pop("imageUrls", None)
    if len(clean) == 1:
        entry["imageUrl"] = clean[0]
    elif len(clean) > 1:
        entry["imageUrls"] = clean


def listing_hero(entries: list[dict[str, Any]]) -> str | None:
    for entry in entries:
        urls = gallery_urls(entry)
        if urls:
            return urls[0]
    return None


def audit_product(product: dict[str, Any]) -> dict[str, Any]:
    colors = r2.parse_colors(product.get("colorsJson"))
    sku = product.get("sku") or ""
    result: dict[str, Any] = {
        "sku": sku,
        "prints": len(colors),
        "flags": [],
        "overlap_count": 0,
        "colors": colors,
    }
    if len(colors) < 2:
        return result

    galleries = {c.get("name") or "": gallery_urls(c) for c in colors}
    sets = {n: frozenset(u) for n, u in galleries.items()}
    non_empty = [s for s in sets.values() if s]
    shared_identical = len(non_empty) >= 2 and len(set(non_empty)) == 1

    url_to_prints: dict[str, list[str]] = defaultdict(list)
    for name, urls in galleries.items():
        for u in urls:
            url_to_prints[u].append(name)
    overlapping = {u: prints for u, prints in url_to_prints.items() if len(prints) > 1}
    result["overlap_count"] = len(overlapping)
    result["galleries"] = galleries

    junk = []
    for name, urls in galleries.items():
        for u in urls:
            if product_url_ok(product, u):
                continue
            if r2.color_matches_filename(name, url_key(u)):
                continue
            junk.append(u)
    if junk:
        result["flags"].append("CROSS_PRODUCT_JUNK")
        result["junk_count"] = len(junk)

    if shared_identical:
        result["flags"].append("SHARED_IDENTICAL")
        sample = next(iter(non_empty))
        any_print_match = False
        for url in sample:
            key = url_key(url)
            for c in colors:
                if r2.color_matches_filename(c.get("name") or "", key):
                    any_print_match = True
                    break
            if any_print_match:
                break
        if not any_print_match:
            result["flags"].append("GENERIC_SHARED")
        # Shared generic lifestyle on every print is intentional — not a catalog bug.
        if result["flags"] == ["SHARED_IDENTICAL", "GENERIC_SHARED"]:
            result["flags"] = []
            result["overlap_count"] = 0
    elif overlapping:
        result["flags"].append("PARTIAL_OVERLAP")

    return result


def split_sequential(urls: list[str], n_prints: int) -> list[list[str]]:
    """Split sorted URLs across prints in colorsJson order with zero overlap."""
    if n_prints <= 0:
        return []
    ordered = sort_by_gallery_num(list(dict.fromkeys(urls)))
    buckets: list[list[str]] = [[] for _ in range(n_prints)]
    if not ordered:
        return buckets
    # Chunk contiguous ranges (BODY-SUIT style): roughly equal sized slices.
    per = max(1, len(ordered) // n_prints)
    remainder = len(ordered) % n_prints
    idx = 0
    for i in range(n_prints):
        take = per + (1 if i < remainder else 0)
        # Prefer at least 1 when enough URLs remain
        if idx < len(ordered) and take == 0:
            take = 1
        buckets[i] = ordered[idx : idx + take]
        idx += take
        if idx >= len(ordered):
            break
    return buckets


def prefer_print_for_url(url: str, color_names: list[str]) -> str | None:
    """Pick best print for a URL by filename match; prefer more specific / longer match."""
    key = url_key(url)
    scored: list[tuple[int, int, str]] = []
    for i, name in enumerate(color_names):
        if not r2.color_matches_filename(name, key):
            continue
        # Prefer longer print names (SNAIL,BEE & AQUA over SNAIL & BEE for snail-bee-aqua).
        scored.append((len(re.sub(r"[^a-z0-9]", "", name.lower())), -i, name))
    if not scored:
        return None
    scored.sort(reverse=True)
    return scored[0][2]


def side_button_hint_match(color_name: str, url: str) -> bool:
    nk = r2.norm_key(color_name)
    fn = r2.filename_stem(url_key(url))
    for hint in SIDE_BUTTON_HINTS:
        if hint == nk or hint in nk.split():
            return hint in fn
    return False


def collect_r2_product_urls(product: dict[str, Any], objects: list[dict[str, Any]]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    sku = (product.get("sku") or "").upper()
    sku_l = sku.lower()
    for obj in objects:
        key = obj.get("key") or ""
        url = obj.get("publicUrl") or ""
        if not url or r2.is_swatch_key(key):
            continue
        if not product_url_ok(product, url):
            continue
        base = key.rsplit("/", 1)[-1].lower()
        if sku.startswith("SIDE-BUTTON") and "short" in sku_l and "short" not in base:
            continue
        if sku.startswith("SIDE-BUTTON") and "short" not in sku_l and "short" in base:
            continue
        if sku == "FRONT-OPEN-VEST-SLEVELESS" and "and-short" in base:
            continue
        if sku == "FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET":
            if "and-short" not in base and "short" not in base:
                continue
        if url not in seen:
            seen.add(url)
            out.append(url)
    return out


def fix_entries(
    product: dict[str, Any],
    entries: list[dict[str, Any]],
    objects: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    sku = (product.get("sku") or "").upper()
    color_names = [(e.get("name") or "").strip() for e in entries]
    n = len(entries)

    # Preserve swatches; rebuild galleries.
    swatches = {(e.get("name") or "").strip(): (e.get("swatchUrl") or "").strip() for e in entries}

    # 1) Start from current galleries + R2 pool for this product.
    pool: list[str] = []
    for e in entries:
        pool.extend(gallery_urls(e))
    pool.extend(collect_r2_product_urls(product, objects))
    # Keep current URLs that match a print name even if product gate is strict (print stills).
    for e in entries:
        name = (e.get("name") or "").strip()
        for u in gallery_urls(e):
            if r2.color_matches_filename(name, url_key(u)):
                pool.append(u)
    pool = list(dict.fromkeys(pool))
    pool = [u for u in pool if product_url_ok(product, u)]
    # Drop obvious cross-product junk even when print-matched (e.g. napkin on mittens).
    if sku.startswith("PACK-OF-3-MITTEN"):
        pool = [
            u
            for u in pool
            if "napkin" not in url_key(u).lower()
            and not ("sock" in url_key(u).lower() and "mitten" not in url_key(u).lower())
        ]

    assigned: dict[str, list[str]] = {name: [] for name in color_names}
    claimed: set[str] = set()

    def claim(name: str, url: str) -> None:
        if url in claimed or not name:
            return
        claimed.add(url)
        if len(assigned[name]) < MAX_GALLERY:
            assigned[name].append(url)

    # --- Special: PACK-OF-3-MITTENS — mitten product photos first, then print stills ---
    if sku.startswith("PACK-OF-3-MITTEN"):
        mitten_urls = [u for u in pool if "mitten" in url_key(u).lower()]
        other = [u for u in pool if u not in mitten_urls]
        buckets = split_sequential(mitten_urls, n)
        for i, name in enumerate(color_names):
            for url in buckets[i]:
                claim(name, url)
        for url in other:
            best = prefer_print_for_url(url, color_names)
            if best:
                claim(best, url)
        remaining = [u for u in other if u not in claimed]
        buckets = split_sequential(remaining, n)
        for i, name in enumerate(color_names):
            for url in buckets[i]:
                claim(name, url)

    # --- Special: SIDE-BUTTON products (print-hint filenames only — no shared sprawl) ---
    elif sku.startswith("SIDE-BUTTON"):
        for url in pool:
            key = url_key(url)
            hinted = [cn for cn in color_names if side_button_hint_match(cn, url)]
            if len(hinted) >= 1:
                claim(hinted[0], url)
            else:
                matches = [cn for cn in color_names if r2.color_matches_filename(cn, key)]
                if len(matches) == 1:
                    claim(matches[0], url)

        remaining = [u for u in pool if u not in claimed and product_url_ok(product, u)]
        empty_prints = [name for name in color_names if not assigned[name]]
        if empty_prints and remaining:
            buckets = split_sequential(sort_by_gallery_num(remaining), len(empty_prints))
            for i, name in enumerate(empty_prints):
                for url in buckets[i]:
                    claim(name, url)

    # --- Special: TYING-CAP ---
    elif sku == "TYING-CAP":
        lifestyle = gallery_lifestyle_pool(pool)
        tying = [u for u in lifestyle if "tying-cap" in url_key(u).lower() or "tyingcap" in url_key(u).lower().replace("-", "")]
        images40 = [u for u in lifestyle if re.search(r"images-4\d", url_key(u), re.I)]
        rabbit = next((cn for cn in color_names if "rabbit" in cn.lower()), color_names[0])
        tree = next((cn for cn in color_names if "tree" in cn.lower()), color_names[-1])
        for u in urls_for_gallery_nums(tying, [2, 3, 4]):
            claim(rabbit, u)
        for u in urls_for_gallery_nums(tying, [5]):
            claim(tree, u)
        for u in images40:
            claim(rabbit, u)
        for u in pool:
            fn = url_key(u).lower()
            if "tying" in fn and fn.endswith(".png"):
                claim(tree, u)

    # --- Special: FRONT OPEN VEST half sleeve (vest-only R2 pool, sequential split) ---
    elif sku == "FRONT-OPEN-VEST-HALF-SLEEVE":
        vest_only = sort_by_gallery_num([u for u in pool if product_url_ok(product, u)])
        buckets = split_sequential(vest_only, n)
        for i, name in enumerate(color_names):
            for url in buckets[i]:
                claim(name, url)

    # --- Special: Baby Terry Towel (numbered lifestyle batch, sequential split) ---
    elif sku == "BABY-TERRY-TOWEL-FLUFFY":
        terry = sort_by_gallery_num(gallery_lifestyle_pool(pool))
        buckets = split_sequential(terry, n)
        for i, name in enumerate(color_names):
            for url in buckets[i]:
                claim(name, url)

    # --- Special: LOOP-BACK fleece ---
    elif sku.startswith("LOOP-BACK-FLEECE"):
        fleece_only = [u for u in pool if "fleece" in url_key(u).lower()]
        shared = sort_by_gallery_num(fleece_only)[:MAX_GALLERY]
        for name in color_names:
            assigned[name] = list(shared)
            for u in shared:
                claimed.add(u)

    # --- Strict shared rebuild: correct product photos only, no cross-SKU pollution ---
    elif sku in STRICT_SHARED_SKUS:
        lifestyle = gallery_lifestyle_pool(pool)
        shared = sort_by_gallery_num(lifestyle)[:MAX_GALLERY]
        for name in color_names:
            assigned[name] = list(shared)
            for u in shared:
                claimed.add(u)

    # --- Special: BABY frock + bloomers ---
    elif sku == "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS":
        bloomer_set = assign_bloomer_galleries(sku, pool, color_names)
        for name in color_names:
            assigned[name] = bloomer_set.get(name, [])
            for u in assigned[name]:
                claimed.add(u)

    # --- Generic multi-print fix ---
    else:
        known = assign_known_gallery_map(sku, pool, color_names)
        if known:
            for name in color_names:
                for url in known.get(name, []):
                    claim(name, url)

        for url in pool:
            if url in claimed:
                continue
            matches = [cn for cn in color_names if r2.color_matches_filename(cn, url_key(url))]
            if len(matches) == 1:
                claim(matches[0], url)
            elif len(matches) > 1:
                best = prefer_print_for_url(url, color_names)
                if best:
                    claim(best, url)

        # Products without verified maps: share generic product galleries (same photos
        # on every print — correct product, avoids wrong-fabric sequential splits).
        if not known and sku not in KNOWN_PRINT_GALLERY_NUMS:
            shared = share_generic_product_galleries(pool, color_names, product, claimed=claimed)
            for name in color_names:
                if not assigned[name]:
                    assigned[name] = shared.get(name, [])[:MAX_GALLERY]
                    for u in assigned[name]:
                        claimed.add(u)

    if sku in KNOWN_PRINT_GALLERY_NUMS and sku not in (
        "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS",
        "TYING-CAP",
    ):
        apply_strict_known_maps(sku, pool, color_names, assigned)
        claimed.clear()
        for name in color_names:
            for u in assigned[name]:
                claimed.add(u)

    # Build result entries; preserve swatchUrl always.
    out: list[dict[str, Any]] = []
    for e in entries:
        name = (e.get("name") or "").strip()
        row: dict[str, Any] = {"name": name}
        sw = swatches.get(name) or (e.get("swatchUrl") or "").strip()
        if sw:
            row["swatchUrl"] = sw
        urls = sort_by_gallery_num(assigned.get(name, []))
        apply_gallery(row, urls)
        out.append(row)

    # Cross-print dedupe only for exclusive assignments (known maps / print-name matches).
    # Shared generic product galleries intentionally repeat per print.
    allow_shared = sku.startswith("SIDE-BUTTON") and all(
        not gallery_urls(e) for e in out
    ) or (
        sku not in KNOWN_PRINT_GALLERY_NUMS and not sku.startswith("PACK-OF-3") and not sku.startswith("SIDE-BUTTON")
    )
    if not allow_shared:
        seen_urls: set[str] = set()
        for row in out:
            urls = gallery_urls(row)
            kept: list[str] = []
            for u in urls:
                if u in seen_urls:
                    continue
                seen_urls.add(u)
                kept.append(u)
            apply_gallery(row, kept)
            if swatches.get(row["name"]):
                row["swatchUrl"] = swatches[row["name"]]

    return out


def entries_equal(a: list[dict[str, Any]], b: list[dict[str, Any]]) -> bool:
    return r2.serialize_colors(a) == r2.serialize_colors(b)


def pairwise_overlap_zero(entries: list[dict[str, Any]]) -> bool:
    sets = [frozenset(gallery_urls(e)) for e in entries]
    for i in range(len(sets)):
        for j in range(i + 1, len(sets)):
            if sets[i] & sets[j]:
                return False
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Admin-PUT fixes (default: audit only)")
    parser.add_argument("--force-all", action="store_true", help="With --apply, rebuild every multi-print SKU")
    parser.add_argument("--sku", action="append", default=[], help="Limit to SKU(s)")
    parser.add_argument("--include-test", action="store_true", help="Also fix TEST-COLOR-IMG-JSON")
    args = parser.parse_args()

    print(f"=== Per-print gallery audit/fix @ {GATEWAY} ===")
    token = r2.auth()
    code, body = r2.request("GET", f"{GATEWAY}/api/v1/admin/products?size=200", token=token)
    if code != 200:
        print(f"list products failed: {code} {body}", file=sys.stderr)
        return 1
    products: list[dict[str, Any]] = r2.unwrap(body) or []
    if args.sku:
        want = {s.upper() for s in args.sku}
        products = [p for p in products if (p.get("sku") or "").upper() in want]

    code, body = r2.request("GET", f"{CATALOG}/internal/v1/admin/uploads/objects?folder=products&maxKeys=5000")
    objects = r2.unwrap(body) or []
    if code != 200:
        print(f"R2 list failed: {code}", file=sys.stderr)
        return 1
    objects = [o for o in objects if not r2.is_swatch_key(o.get("key") or "")]

    print(f"Products: {len(products)} | R2 non-swatch objects: {len(objects)}")
    print("\n--- STEP 1: Audit ---")

    audits: list[dict[str, Any]] = []
    flagged: list[dict[str, Any]] = []
    already_ok: list[str] = []
    for product in products:
        a = audit_product(product)
        audits.append(a)
        sku = a["sku"]
        if a["prints"] < 2:
            continue
        if a["flags"]:
            overlap_hit = bool(OVERLAP_FLAGS & set(a["flags"]))
            junk_only = a["flags"] == ["CROSS_PRODUCT_JUNK"] or (
                set(a["flags"]) <= {"CROSS_PRODUCT_JUNK"} and not overlap_hit
            )
            if junk_only and sku not in JUNK_FIX_SKUS and "CROSS_PRODUCT_JUNK" in a["flags"] and not overlap_hit:
                print(f"  JUNK {sku}: CROSS_PRODUCT_JUNK (left alone — not in fix list) prints={a['prints']}")
                already_ok.append(sku)
                continue
            flagged.append(a)
            print(f"  FLAG {sku}: {', '.join(a['flags'])} prints={a['prints']} overlaps={a['overlap_count']}")
        else:
            already_ok.append(sku)
            print(f"  OK   {sku}: prints={a['prints']} pairwise_overlap=0")

    print(f"\nFlagged: {len(flagged)} | Already OK multi-print: {len(already_ok)}")

    fixed: list[str] = []
    skipped: list[str] = []
    failed: list[str] = []

    if args.apply:
        print("\n--- STEP 2: Fix ---")
        by_sku = { (p.get("sku") or "").upper(): p for p in products }
        # When --sku is given, force-fix those SKUs even if audit is clean (rebuild from R2).
        force_skus = {s.upper() for s in args.sku} if args.sku else set()
        to_fix = list(flagged)
        seen_fix = {a["sku"].upper() for a in to_fix}
        for sku in force_skus:
            if sku in seen_fix or sku not in by_sku:
                continue
            a = audit_product(by_sku[sku])
            if a["prints"] >= 2:
                to_fix.append(a)
                seen_fix.add(sku)
        if args.force_all and not args.sku:
            for product in products:
                sku = (product.get("sku") or "").upper()
                if sku in seen_fix or sku in SKIP_SKUS:
                    continue
                a = audit_product(product)
                if a["prints"] >= 2:
                    to_fix.append(a)
                    seen_fix.add(sku)

        for a in to_fix:
            sku = (a["sku"] or "").upper()
            product = by_sku.get(sku)
            if not product:
                continue
            if sku in SKIP_SKUS and not args.include_test:
                skipped.append(sku)
                print(f"  skip {sku} (test fixture)")
                continue
            if sku in PRESERVE_IF_OK and not a["flags"] and sku not in force_skus:
                skipped.append(sku)
                continue

            pid = product.get("id")
            entries = r2.parse_colors(product.get("colorsJson"))
            # Merge DB swatches if API stripped them.
            db_colors = r2.parse_colors(r2.fetch_colors_json_from_db(int(pid)) if pid else None)
            db_by = {r2.norm_key(c["name"]): c for c in db_colors if c.get("name")}
            for e in entries:
                prev = db_by.get(r2.norm_key(e.get("name") or ""))
                if prev and prev.get("swatchUrl") and not e.get("swatchUrl"):
                    e["swatchUrl"] = prev["swatchUrl"]

            before = r2.serialize_colors(entries)
            fixed_entries = fix_entries(product, entries, objects)
            after = r2.serialize_colors(fixed_entries)

            if after == before and pairwise_overlap_zero(fixed_entries) and sku not in force_skus:
                print(f"  no-op {sku} (already consistent after parse)")
                continue

            if not pairwise_overlap_zero(fixed_entries):
                print(f"  WARN {sku}: fix still has overlap — forcing final dedupe", file=sys.stderr)

            product["colorsJson"] = after
            product["colorImagesJson"] = serialize_color_images(fixed_entries)
            hero = listing_hero(fixed_entries)
            if hero:
                product["imagesJson"] = json.dumps([hero])

            code, saved = r2.request("PUT", f"{GATEWAY}/api/v1/admin/products/{pid}", product, token=token)
            if code in (200, 201):
                fixed.append(sku)
                counts = [f"{e.get('name')}={len(gallery_urls(e))}" for e in fixed_entries]
                print(f"  fixed {sku}: {', '.join(counts)}")
            else:
                failed.append(sku)
                print(f"  FAIL {sku}: HTTP {code} {saved}", file=sys.stderr)

    # Re-audit
    print("\n--- STEP 3: Re-audit ---")
    code, body = r2.request("GET", f"{GATEWAY}/api/v1/admin/products?size=200", token=token)
    products2: list[dict[str, Any]] = r2.unwrap(body) or []
    if args.sku:
        want = {s.upper() for s in args.sku}
        products2 = [p for p in products2 if (p.get("sku") or "").upper() in want]

    remaining_shared: list[str] = []
    remaining_partial: list[str] = []
    remaining_junk: list[str] = []
    ok_multi: list[str] = []
    verify_lines: list[str] = []

    for product in products2:
        a = audit_product(product)
        if a["prints"] < 2:
            continue
        sku = a["sku"]
        entries = a["colors"]
        hard_flags = [f for f in a["flags"] if f in ("SHARED_IDENTICAL", "PARTIAL_OVERLAP", "GENERIC_SHARED", "CROSS_PRODUCT_JUNK")]
        ok = pairwise_overlap_zero(entries) and not hard_flags
        counts = [f"{(e.get('name') or '')}={len(gallery_urls(e))}" for e in entries]
        verify_lines.append(
            f"  {sku}: prints={a['prints']} [{', '.join(counts)}] overlap=0"
            if ok
            else f"  {sku}: prints={a['prints']} FLAGS={a['flags']} overlaps={a['overlap_count']}"
        )
        if "SHARED_IDENTICAL" in a["flags"] and sku not in SKIP_SKUS:
            remaining_shared.append(sku)
        if "PARTIAL_OVERLAP" in a["flags"] and sku not in SKIP_SKUS:
            remaining_partial.append(sku)
        if "CROSS_PRODUCT_JUNK" in a["flags"] and sku not in SKIP_SKUS:
            # Only require clean for SKUs we actively manage; others may keep legacy URLs.
            if sku in JUNK_FIX_SKUS or (OVERLAP_FLAGS & set(a["flags"])):
                remaining_junk.append(sku)
        if sku in SKIP_SKUS and a["flags"]:
            pass
        elif ok:
            ok_multi.append(sku)

    print("\n========== FINAL REPORT ==========")
    print(f"Products fixed ({len(fixed)}): {fixed}")
    print(f"Skipped: {skipped}")
    print(f"PUT failures: {failed}")
    print(f"Products already OK / now OK multi-print ({len(ok_multi)}): {ok_multi}")
    print(f"Remaining SHARED_IDENTICAL (real products): {remaining_shared}  (must be [])")
    print(f"Remaining PARTIAL_OVERLAP (real products): {remaining_partial}  (must be [])")
    print(f"Remaining CROSS_PRODUCT_JUNK (real products): {remaining_junk}  (must be [])")
    print("\nVerification (multi-print pairwise URL overlap):")
    for line in verify_lines:
        print(line)

    real_fail = remaining_shared or remaining_partial or remaining_junk or failed
    if not args.apply:
        print("\nRESULT: audit-only (pass --apply to write fixes)")
        return 0
    if real_fail:
        print("\nRESULT: FAIL — remaining overlaps/junk or PUT errors", file=sys.stderr)
        return 1
    print("\nRESULT: PASS — zero SHARED_IDENTICAL / PARTIAL_OVERLAP / CROSS_PRODUCT_JUNK on real multi-print products")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
