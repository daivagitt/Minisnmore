#!/usr/bin/env python3
"""Upsert UAT catalog products from SKU WISE PRINT spreadsheet.

Usage:
  GATEWAY_URL=http://127.0.0.1:9180 ADMIN_USER=admin ADMIN_PASS=admin6 \\
    python3 deploy/scripts/uat-sku-wise-print-import.py [--dry-run]

Expects repo-root xlsx:
  SKU WISE PRINT  FINAL DETAILS AS ON 21-07-26.xlsx

Print-N columns in the main sheet are embedded swatch photos with no text —
build_print_name_map() resolves the real print name for each SKU/print-slot
by matching that photo (byte-exact, then perceptual hash) against the
swatch photos in the PRINT NAMES sheet.

Existing products are matched by parent SKU and updated; new styles are created.
Products not present in the spreadsheet are left unchanged (no catalog wipe).
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from typing import Any

REPO = Path(__file__).resolve().parents[2]
XLSX_CANDIDATES = [
    REPO / "SKU WISE PRINT  FINAL DETAILS AS ON 25-07-26.xlsx",
    REPO / "SKU WISE PRINT  FINAL DETAILS AS ON 21-07-26 (1).xlsx",
    REPO / "SKU WISE PRINT  FINAL DETAILS AS ON 21-07-26.xlsx",
]

# Print-N columns only ever contain an embedded swatch photo, never text — the
# real name lives in the PRINT NAMES sheet, keyed by matching swatch images
# (see build_print_name_map). These three slots couldn't be resolved from the
# spreadsheet at all (no image, or the image doesn't match anything in the
# PRINT NAMES sheet / sibling rows disagree) and were confirmed by manual
# visual inspection instead.
PRINT_NAME_MANUAL_OVERRIDES = {
    (5, 3): "BABY BLUE",  # Loop Back Fleece Vest 12-18M — 3/3 sibling size-rows agree, no image on this row
    (118, 5): "COMBO 5",  # Face Napkin — auto-match picked the wrong swatch; confirmed visually
    (118, 7): "COMBO 7",  # Face Napkin — no image at all, but this SKU uses COMBO 1-8 sequentially and 1,2,3,4,6,8 are confirmed
    (72, 3): "AQUA",  # Baby Front Open Frock 0-3M — print slot has qty but no swatch image
    # Front Open Vest Half Sleeve — print-1 swatch is pink (not aqua+blue); confirmed visually
    (37, 1): "PINK & BEE",
    (38, 1): "PINK & BEE",
    (39, 1): "PINK & BEE",
    (40, 1): "PINK & BEE",
}
GATEWAY = os.environ.get("GATEWAY_URL", "http://127.0.0.1:9180").rstrip("/")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ADMIN_PASS", "admin6")

DEFAULT_RATE = 799.0
DEFAULT_MRP = 999.0

CATEGORY_MAP = {
    "VEST": 26403,
    "JABLA": 26402,
    "ESSENTIALS": 26407,
    "T SHIRT": 26404,
    "FROCK": 26405,
    "SLEEP WEAR": 28851,  # body-suit
    "ACCESSORIES": 26408,
}

# WHC sheet overrides print-slot colours for these style names (normalized key).
# Book Style Tying Jabla uses main-sheet print columns + quantities (BABY BLUE, BIRD, AQUA).
# WHC bookStyle column mixed BLUE/FISH names with different size keys and caused stock drift.
WHC_STYLE_KEYS = {
    "side button t shirt (half sleeve)": "sideButton",
}

ADDITIONAL_PRINTS_BY_SKU: dict[str, list[str]] = {}

# When spreadsheet print names change, keep uploaded swatches/galleries on the new name.
COLOR_MEDIA_ALIASES: dict[str, list[str]] = {
    "baby blue": ["blue"],
    "aqua": ["fish"],
    "pink & bee": ["aqua & blue"],
}


def request(method: str, path: str, body: Any | None = None, token: str | None = None) -> tuple[int, Any]:
    url = path if path.startswith("http") else f"{GATEWAY}{path}"
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
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
    code, body = request("POST", "/api/authenticate", {"username": ADMIN_USER, "password": ADMIN_PASS, "rememberMe": False})
    token = (body or {}).get("id_token") if isinstance(body, dict) else None
    if code != 200 or not token:
        raise RuntimeError(f"auth failed: {code} {body}")
    return token


def norm_key(s: str) -> str:
    return re.sub(r"\s+", " ", (s or "").strip().lower())


def norm_size(raw: str) -> str:
    s = re.sub(r"\s+", " ", (raw or "").strip())
    if not s:
        return s
    if s.upper() == "COMMON SIZE":
        return "Common"
    s = s.replace("--", "-")
    m = re.match(r"^(\d+)\s*-\s*(\d+)\s*(?:m(?:onths?)?)?\.?$", s, flags=re.I)
    if m:
        return f"{m.group(1)}-{m.group(2)} M"
    if re.match(r"^\d+\s*-\s*\d+\s+M$", s, re.I):
        return re.sub(r"\s+", " ", s)
    return s


def slugify(name: str) -> str:
    s = name.strip().lower()
    s = re.sub(r"[^a-z0-9]+", "-", s)
    return re.sub(r"-+", "-", s).strip("-")[:180]


def parent_sku(name: str) -> str:
    s = re.sub(r"[^A-Z0-9]+", "-", name.strip().upper()).strip("-")
    return (s[:48] if s else "SKU") or "SKU"


def variant_sku(parent: str, size: str, color: str) -> str:
    parts = [parent]
    if size.strip():
        parts.append(re.sub(r"\s+", "-", size.strip().upper()))
    if color.strip():
        parts.append(re.sub(r"\s+", "-", color.strip().upper()))
    return "-".join(parts)[:120]


_NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
_NS_XDR = "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"
_NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
_NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"


def _col_letters_to_num(letters: str) -> int:
    n = 0
    for c in letters:
        n = n * 26 + (ord(c) - ord("A") + 1)
    return n


def _read_sheet_cells(z: zipfile.ZipFile, sheet_path: str, shared: list[str]) -> dict[int, dict[int, str | None]]:
    root = ET.fromstring(z.read(sheet_path))
    rows: dict[int, dict[int, str | None]] = {}
    for row_el in root.findall(f".//{{{_NS_MAIN}}}sheetData/{{{_NS_MAIN}}}row"):
        r = int(row_el.attrib["r"])
        cells: dict[int, str | None] = {}
        for c in row_el.findall(f"{{{_NS_MAIN}}}c"):
            ref = c.attrib.get("r", "")
            col_letters = re.match(r"([A-Z]+)", ref).group(1)
            t = c.attrib.get("t")
            v = c.find(f"{{{_NS_MAIN}}}v")
            val = v.text if v is not None else None
            if t == "s" and val is not None:
                val = shared[int(val)]
            cells[_col_letters_to_num(col_letters) - 1] = val  # 0-based column
        rows[r] = cells
    return rows


def _load_rels(z: zipfile.ZipFile, rels_path: str) -> dict[str, str]:
    rels: dict[str, str] = {}
    for rel in ET.fromstring(z.read(rels_path)):
        rels[rel.attrib["Id"]] = rel.attrib["Target"]
    return rels


def _drawing_image_anchors(z: zipfile.ZipFile, drawing_path: str, rels: dict[str, str]) -> list[tuple[int, int, str]]:
    """Returns (excel_row_1_based, col_0_based, media_target) for every picture anchor."""
    root = ET.fromstring(z.read(drawing_path))
    out = []
    for anchor in root:
        frm = anchor.find(f"{{{_NS_XDR}}}from")
        if frm is None:
            continue
        row = int(frm.find(f"{{{_NS_XDR}}}row").text)
        col = int(frm.find(f"{{{_NS_XDR}}}col").text)
        pic = anchor.find(f".//{{{_NS_XDR}}}pic")
        if pic is None:
            continue
        blip = pic.find(f".//{{{_NS_A}}}blip")
        if blip is None:
            continue
        rid = blip.attrib.get(f"{{{_NS_R}}}embed")
        target = rels.get(rid)
        if target:
            out.append((row + 1, col, target))
    return out


def _media_bytes(z: zipfile.ZipFile, target: str) -> bytes:
    path = ("xl/drawings/" + target).replace("drawings/../media", "media")
    return z.read(path)


def _phash(data: bytes, size: int = 8):
    from PIL import Image

    img = Image.open(io.BytesIO(data)).convert("L").resize((size, size), Image.LANCZOS)
    pixels = list(img.getdata())
    avg = sum(pixels) / len(pixels)
    return "".join("1" if p > avg else "0" for p in pixels)


def _hamming(a: str, b: str) -> int:
    return sum(x != y for x, y in zip(a, b))


def build_print_name_map(xlsx_path: Path) -> dict[tuple[int, int], str]:
    """Maps (excel_row, print_index 1..11) -> real print name for the main sheet.

    Print-N cells hold no text, only an embedded swatch photo. The PRINT NAMES
    sheet has the same photos next to the real name, so each SKU's swatch is
    matched against it by image content (exact bytes, then perceptual hash for
    photos Excel re-compressed slightly differently on re-paste).
    """
    result: dict[tuple[int, int], str] = {}
    try:
        z = zipfile.ZipFile(xlsx_path)
        shared: list[str] = []
        sst = ET.fromstring(z.read("xl/sharedStrings.xml"))
        for si in sst.findall(f"{{{_NS_MAIN}}}si"):
            texts = si.findall(f".//{{{_NS_MAIN}}}t")
            shared.append("".join(t.text or "" for t in texts))

        names_rows = _read_sheet_cells(z, "xl/worksheets/sheet3.xml", shared)
        rels2 = _load_rels(z, "xl/drawings/_rels/drawing2.xml.rels")
        anchors2 = _drawing_image_anchors(z, "xl/drawings/drawing2.xml", rels2)
        row_to_target = {row: target for row, col, target in anchors2 if col == 2}

        md5_to_name: dict[str, str] = {}
        name_entries: list[tuple[str, str]] = []  # (md5-or-none, phash) per named print
        for r, cells in names_rows.items():
            printno = cells.get(_col_letters_to_num("B") - 1)
            name = cells.get(_col_letters_to_num("D") - 1)
            target = row_to_target.get(r)
            if printno is None or not name or not target:
                continue
            data = _media_bytes(z, target)
            h = hashlib.md5(data).hexdigest()
            md5_to_name[h] = name
            try:
                name_entries.append((name, _phash(data)))
            except Exception:
                pass

        main_rows = _read_sheet_cells(z, "xl/worksheets/sheet1.xml", shared)
        rels1 = _load_rels(z, "xl/drawings/_rels/drawing1.xml.rels")
        anchors1 = _drawing_image_anchors(z, "xl/drawings/drawing1.xml", rels1)
        row_col_to_target = {(row, col): target for row, col, target in anchors1}

        for r, cells in main_rows.items():
            style = cells.get(_col_letters_to_num("B") - 1)
            if not style:
                continue
            for i in range(1, 12):
                col = 2 * i + 5  # H=7->Print-1, J=9->Print-2, ...
                target = row_col_to_target.get((r, col))
                if not target:
                    continue
                data = _media_bytes(z, target)
                h = hashlib.md5(data).hexdigest()
                name = md5_to_name.get(h)
                if not name:
                    try:
                        ph = _phash(data)
                        best_name, best_dist = None, 999
                        for name2, ph2 in name_entries:
                            d = _hamming(ph, ph2)
                            if d < best_dist:
                                best_dist, best_name = d, name2
                        if best_name is not None and best_dist <= 10:
                            name = best_name
                    except Exception:
                        name = None
                if name:
                    result[(r, i)] = name
    except Exception as e:
        print(f"WARN: print-name image matching failed ({e}); falling back to placeholder names", file=sys.stderr)

    result.update(PRINT_NAME_MANUAL_OVERRIDES)
    return result


def parse_xlsx(xlsx_path: Path) -> dict[str, Any]:
    node = r"""
const XLSX = require('xlsx');
const path = process.argv[1];
const wb = XLSX.readFile(path);
const main = XLSX.utils.sheet_to_json(wb.Sheets['Final updated count - 21-07-26'], { defval: '' }).map(row => ({...row, _excelRow: row.__rowNum__ + 1}));
const whcRows = XLSX.utils.sheet_to_json(wb.Sheets['WHC 21-7-26'], { header: 1, defval: '' });
const whc = { sideButton: [], bookStyle: [] };
for (const row of whcRows.slice(1)) {
  const [p1,s1,q1,_,p2,s2,q2] = row;
  if (p1 && s1 && q1 !== '') whc.sideButton.push({ color: String(p1).trim(), size: String(s1).trim(), stock: Number(q1) });
  if (p2 && s2 && q2 !== '') whc.bookStyle.push({ color: String(p2).trim(), size: String(s2).trim(), stock: Number(q2) });
}
console.log(JSON.stringify({ main, whc }));
"""
    out = subprocess.check_output(["node", "-e", node, str(xlsx_path)], cwd=str(REPO), text=True)
    return json.loads(out)


def build_products(parsed: dict[str, Any], print_name_map: dict[tuple[int, int], str] | None = None) -> list[dict[str, Any]]:
    whc = parsed["whc"]
    print_name_map = print_name_map or {}
    products: dict[str, dict[str, Any]] = {}

    for row in parsed["main"]:
        style = str(row.get("Style Name") or "").strip()
        if not style:
            continue
        excel_row = row.get("_excelRow")
        cat = str(row.get("CATEGORY") or "").strip().upper()
        fabric = str(row.get("Main Fabric") or "").strip()
        size = norm_size(str(row.get("Size") or ""))
        if style not in products:
            products[style] = {
                "name": style,
                "category": cat,
                "fabric": fabric,
                "slots": {},  # (size, color) -> stock
            }
        key = norm_key(style)
        whc_key = WHC_STYLE_KEYS.get(key)
        if whc_key:
            continue  # filled from WHC below

        for i in range(1, 12):
            print_name = str(row.get(f"Print-{i}") or "").strip()
            qty_raw = row.get(f"Print-{i} QUANTITY")
            if qty_raw in ("", None):
                continue
            if str(qty_raw).strip().upper() == "NO STOCK":
                qty = 0
            else:
                try:
                    qty = int(float(qty_raw))
                except (TypeError, ValueError):
                    continue
            resolved_name = print_name_map.get((excel_row, i)) if excel_row else None
            color = resolved_name or (print_name if print_name and print_name.upper() != "NO STOCK" else f"Print {i}")
            products[style]["slots"][(size, color)] = products[style]["slots"].get((size, color), 0) + max(0, qty)

    for style_key, whc_name in WHC_STYLE_KEYS.items():
        style = next((n for n in products if norm_key(n) == style_key), None)
        if not style:
            continue
        products[style]["slots"] = {}
        for entry in whc[whc_name]:
            size = norm_size(entry["size"])
            color = str(entry["color"]).strip()
            stock = max(0, int(entry.get("stock") or 0))
            products[style]["slots"][(size, color)] = products[style]["slots"].get((size, color), 0) + stock

    result: list[dict[str, Any]] = []
    for style, info in products.items():
        slots = info["slots"]
        sizes = sorted({s for s, _ in slots}, key=lambda x: (x != "Common", x))
        colors = sorted({c for _, c in slots})
        if not sizes or not colors:
            continue

        psku = parent_sku(style)
        variants = []
        for size in sizes:
            for color in colors:
                stock = int(slots.get((size, color), 0))
                variants.append(
                    {
                        "sku": variant_sku(psku, size, color),
                        "size": size,
                        "color": color,
                        "rate": DEFAULT_RATE,
                        "mrp": DEFAULT_MRP,
                        "cost": 0,
                        "stock": stock,
                        "active": True,
                    }
                )

        total_stock = sum(v["stock"] for v in variants)
        min_rate = min(v["rate"] for v in variants if v["rate"] > 0) if variants else DEFAULT_RATE
        min_mrp = min(v["mrp"] for v in variants if v["mrp"] > 0) if variants else DEFAULT_MRP
        cat_id = CATEGORY_MAP.get(info["category"])
        if not cat_id:
            raise RuntimeError(f"No category mapping for {info['category']} ({style})")

        age_min, age_max = (0, 24) if "Common" not in sizes else (0, 36)
        extra_prints = ADDITIONAL_PRINTS_BY_SKU.get(psku, [])
        if extra_prints:
            for extra in extra_prints:
                if extra not in colors:
                    colors.append(extra)
                    for size in sizes:
                        variants.append(
                            {
                                "sku": variant_sku(psku, size, extra),
                                "size": size,
                                "color": extra,
                                "rate": DEFAULT_RATE,
                                "mrp": DEFAULT_MRP,
                                "cost": 0,
                                "stock": 0,
                                "active": True,
                            }
                        )
            total_stock = sum(v["stock"] for v in variants)
            colors.sort()
        result.append(
            {
                "slug": slugify(style),
                "name": style,
                "sku": psku,
                "description": f"{style}. {info['fabric'] or 'Premium baby wear'}.",
                "fabricType": info["fabric"] or None,
                "price": min_rate,
                "compareAtPrice": min_mrp,
                "currency": "INR",
                "ageMinMonths": age_min,
                "ageMaxMonths": age_max,
                "sizesJson": json.dumps(sizes),
                "colorsJson": json.dumps(colors),
                "itemStructure": "VARIANTS",
                "variantsJson": json.dumps(variants),
                "imagesJson": "[]",
                "units": "ea",
                "stock": total_stock,
                "sortOrder": 0,
                "featured": False,
                "trending": False,
                "newArrival": False,
                "bestSeller": False,
                "active": True,
                "category": {"id": cat_id},
            }
        )
    result.sort(key=lambda p: p["name"].lower())
    return result


def index_products_by_sku(token: str) -> dict[str, dict]:
    """Map normalized parent SKU → existing product row from admin API."""
    _, body = request("GET", "/api/v1/admin/products", token=token)
    products = unwrap(body) or []
    index: dict[str, dict] = {}
    for p in products:
        sku = str(p.get("sku") or "").strip().upper()
        if sku:
            index[sku] = p
    return index


def merge_preserve_media(existing: dict, product: dict) -> dict:
    """On spreadsheet re-import, keep uploaded print swatches and hero images."""
    ex_images = (existing.get("imagesJson") or "").strip()
    if ex_images and ex_images != "[]":
        product["imagesJson"] = ex_images

    ex_colors_raw = existing.get("colorsJson")
    try:
        new_names = json.loads(product.get("colorsJson") or "[]")
    except json.JSONDecodeError:
        return product
    name_list: list[str] = []
    for item in new_names:
        if isinstance(item, str) and item.strip():
            name_list.append(item.strip())
        elif isinstance(item, dict) and item.get("name"):
            name_list.append(str(item["name"]).strip())

    rich_by_key: dict[str, dict] = {}
    if ex_colors_raw:
        try:
            ex_parsed = json.loads(ex_colors_raw)
        except json.JSONDecodeError:
            ex_parsed = []
        for item in ex_parsed:
            if isinstance(item, dict) and item.get("name"):
                key = norm_key(str(item["name"]))
                if key and key not in rich_by_key:
                    rich_by_key[key] = item

    ex_cij_raw = existing.get("colorImagesJson")
    if ex_cij_raw and name_list:
        try:
            gallery_map = json.loads(ex_cij_raw)
            gallery_keys = list(gallery_map.keys()) if isinstance(gallery_map, dict) else []
        except json.JSONDecodeError:
            gallery_keys = []
        if gallery_keys and len(gallery_keys) == len(name_list):
            overlap = set(norm_key(n) for n in name_list) & set(norm_key(k) for k in gallery_keys)
            if not overlap:
                merged_cij: list[Any] = []
                for i, name in enumerate(name_list):
                    key = gallery_keys[i]
                    row: dict[str, Any] = {"name": key}
                    prev = rich_by_key.get(norm_key(name)) or rich_by_key.get(norm_key(key))
                    if prev:
                        if prev.get("swatchUrl"):
                            row["swatchUrl"] = prev["swatchUrl"]
                        if prev.get("hex"):
                            row["hex"] = prev["hex"]
                    urls = gallery_map.get(key) or []
                    if isinstance(urls, list) and urls:
                        if len(urls) == 1:
                            row["imageUrl"] = urls[0]
                        else:
                            row["imageUrls"] = urls
                    merged_cij.append(row)
                if merged_cij:
                    product["colorsJson"] = json.dumps(merged_cij)
                    return product

    if not ex_colors_raw or "swatchUrl" not in ex_colors_raw and "imageUrl" not in ex_colors_raw:
        return product

    try:
        ex_parsed = json.loads(ex_colors_raw)
    except json.JSONDecodeError:
        return product

    merged: list[Any] = []
    seen: set[str] = set()
    for name in name_list:
        key = norm_key(name)
        if not key or key in seen:
            continue
        seen.add(key)
        prev = None
        for lookup in [key] + COLOR_MEDIA_ALIASES.get(key, []):
            prev = rich_by_key.get(lookup)
            if prev:
                break
        if prev:
            row = {"name": name}
            if prev.get("swatchUrl"):
                row["swatchUrl"] = prev["swatchUrl"]
            if prev.get("hex"):
                row["hex"] = prev["hex"]
            if prev.get("imageUrl"):
                row["imageUrl"] = prev["imageUrl"]
            if prev.get("imageUrls"):
                row["imageUrls"] = prev["imageUrls"]
            merged.append(row)
        else:
            merged.append(name)

    if merged:
        product["colorsJson"] = json.dumps(merged)
    return product


def upsert_products(token: str, payloads: list[dict], dry_run: bool) -> None:
    print(f"\nUpserting {len(payloads)} products from spreadsheet")
    existing_by_sku = index_products_by_sku(token) if not dry_run else {}
    created = 0
    updated = 0
    failed = 0
    for i, product in enumerate(payloads, 1):
        variants = json.loads(product["variantsJson"])
        total_stock = product["stock"]
        parent_sku = str(product.get("sku") or "").strip().upper()
        existing = existing_by_sku.get(parent_sku)
        action = "update" if existing else "create"
        print(
            f"  [{i}/{len(payloads)}] {action} {product['sku']} — {product['name'][:50]} "
            f"({len(variants)} variants, stock={total_stock})"
        )
        if dry_run:
            if action == "create":
                created += 1
            else:
                updated += 1
            continue
        if existing:
            product_id = existing.get("id")
            product = merge_preserve_media(existing, product)
            code, saved = request("PUT", f"/api/v1/admin/products/{product_id}", product, token=token)
        else:
            code, saved = request("POST", "/api/v1/admin/products", product, token=token)
        saved = unwrap(saved)
        if code in (200, 201) and isinstance(saved, dict):
            if action == "create":
                created += 1
                sku_key = str(saved.get("sku") or parent_sku).strip().upper()
                if sku_key:
                    existing_by_sku[sku_key] = saved
            else:
                updated += 1
        else:
            failed += 1
            print(f"    FAILED HTTP {code}: {saved}", file=sys.stderr)
    print(f"\nUpsert complete: {created} created, {updated} updated, {failed} failed")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="Parse and preview only")
    parser.add_argument("--file", type=str, default=None, help="Path to the SKU WISE PRINT xlsx (defaults to repo-root candidates)")
    args = parser.parse_args()

    xlsx_path = Path(args.file) if args.file else next((p for p in XLSX_CANDIDATES if p.is_file()), XLSX_CANDIDATES[-1])
    if not xlsx_path.is_file():
        print(f"Missing spreadsheet: {xlsx_path}", file=sys.stderr)
        return 1

    print(f"=== SKU-wise print import @ {GATEWAY} ===")
    print(f"Spreadsheet: {xlsx_path.name}")

    parsed = parse_xlsx(xlsx_path)
    print_name_map = build_print_name_map(xlsx_path)
    resolved_count = sum(1 for k in print_name_map if k not in PRINT_NAME_MANUAL_OVERRIDES)
    print(f"Resolved {resolved_count} print swatches to real names via image match "
          f"(+{len(PRINT_NAME_MANUAL_OVERRIDES)} manual overrides)")
    payloads = build_products(parsed, print_name_map)
    print(f"Parsed {len(payloads)} styles from spreadsheet")

    token = auth()
    upsert_products(token, payloads, args.dry_run)

    if not args.dry_run:
        _, body = request("GET", "/api/v1/admin/products", token=token)
        final = unwrap(body) or []
        print(f"\nFinal catalog count: {len(final)}")
        total_stock = sum(int(p.get("stock") or 0) for p in final)
        print(f"Total parent stock sum: {total_stock}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
