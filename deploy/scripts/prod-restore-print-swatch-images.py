#!/usr/bin/env python3
"""Restore print swatch URLs on catalog products from the PRINT NAMES sheet.

Uploads each unique print image to R2 (via admin upload API) and links swatchUrl on
matching product colours. Uses the same PRINT NAMES artwork as the SKU spreadsheet.

Usage:
  GATEWAY_URL=http://127.0.0.1:9180 ADMIN_USER=admin ADMIN_PASS=admin6 \\
    python3 deploy/scripts/prod-restore-print-swatch-images.py [--dry-run]
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
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

GATEWAY = os.environ.get("GATEWAY_URL", "http://127.0.0.1:9180").rstrip("/")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ADMIN_PASS", "admin6")

_NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
_NS_XDR = "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"
_NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
_NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

# Variant matrix uses short print labels; PRINT NAMES sheet uses full artwork names.
SWATCH_NAME_ALIASES: dict[str, str] = {
    "pink": "pink buds",
    "blue": "baby blue",
    "bee": "happy bee",
    "fish": "aqua",
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


def col_letters_to_num(letters: str) -> int:
    n = 0
    for c in letters:
        n = n * 26 + (ord(c) - 64)
    return n


def _read_sheet_cells(z: zipfile.ZipFile, sheet_path: str, shared: list[str]) -> dict[int, dict[int, str | None]]:
    root = ET.fromstring(z.read(sheet_path))
    rows: dict[int, dict[int, str | None]] = {}
    for row_el in root.findall(f".//{{{_NS_MAIN}}}sheetData/{{{_NS_MAIN}}}row"):
        r = int(row_el.attrib["r"])
        cells: dict[int, str | None] = {}
        for c in row_el.findall(f"{{{_NS_MAIN}}}c"):
            ref = c.attrib.get("r", "")
            m = re.match(r"([A-Z]+)", ref)
            if not m:
                continue
            col = col_letters_to_num(m.group(1)) - 1
            t = c.attrib.get("t")
            v = c.find(f"{{{_NS_MAIN}}}v")
            val = v.text if v is not None else None
            if t == "s" and val is not None:
                val = shared[int(val)]
            cells[col] = val
        rows[r] = cells
    return rows


def _load_rels(z: zipfile.ZipFile, rels_path: str) -> dict[str, str]:
    rels: dict[str, str] = {}
    for rel in ET.fromstring(z.read(rels_path)):
        rels[rel.attrib["Id"]] = rel.attrib["Target"]
    return rels


def _drawing_image_anchors(z: zipfile.ZipFile, drawing_path: str, rels: dict[str, str]) -> list[tuple[int, int, str]]:
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


def build_print_name_images(xlsx_path: Path) -> dict[str, tuple[str, bytes]]:
    """norm_key(print name) -> (display name, raw image bytes from PRINT NAMES sheet)."""
    result: dict[str, tuple[str, bytes]] = {}
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

    for r, cells in names_rows.items():
        name = cells.get(col_letters_to_num("D") - 1)
        target = row_to_target.get(r)
        if not name or not target:
            continue
        display = str(name).strip()
        key = norm_key(display)
        if not key or key in result:
            continue
        try:
            data = _media_bytes(z, target)
        except KeyError:
            continue
        if data:
            result[key] = (display, data)
    return result


def to_jpeg_bytes(raw: bytes, name: str) -> bytes:
    from PIL import Image

    img = Image.open(io.BytesIO(raw))
    if img.mode not in ("RGB", "L"):
        img = img.convert("RGB")
    out = io.BytesIO()
    img.save(out, format="JPEG", quality=92)
    return out.getvalue()


def upload_image(token: str, filename: str, body: bytes) -> str:
    safe = re.sub(r"[^\w.-]+", "-", filename).strip("-")[:80] or "print-swatch"
    url = f"{GATEWAY}/api/v1/admin/uploads"
    boundary = "----WebKitFormBoundaryRestorePrintSwatches"
    parts = [
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"folder\"\r\n\r\nproducts\r\n",
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{safe}-swatch.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n",
    ]
    payload = (
        parts[0].encode()
        + parts[1].encode()
        + body
        + f"\r\n--{boundary}--\r\n".encode()
    )
    req = urllib.request.Request(
        url,
        data=payload,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.loads(resp.read().decode())
    public = unwrap(data)
    if isinstance(public, dict) and public.get("publicUrl"):
        return str(public["publicUrl"])
    raise RuntimeError(f"upload missing publicUrl: {data}")


def parse_colors(colors_json: str | None) -> list[dict[str, Any]]:
    if not colors_json:
        return []
    try:
        parsed = json.loads(colors_json)
    except json.JSONDecodeError:
        return []
    if not isinstance(parsed, list):
        return []
    out: list[dict[str, Any]] = []
    for item in parsed:
        if isinstance(item, str):
            name = item.strip()
            if name:
                out.append({"name": name})
        elif isinstance(item, dict) and item.get("name"):
            out.append(dict(item))
    return out


def serialize_colors(entries: list[dict[str, Any]]) -> str:
    clean: list[Any] = []
    has_rich = any(e.get("swatchUrl") or e.get("imageUrl") or e.get("imageUrls") for e in entries)
    for e in entries:
        name = str(e.get("name", "")).strip()
        if not name:
            continue
        swatch = (e.get("swatchUrl") or "").strip()
        if not has_rich:
            clean.append(name)
            continue
        row: dict[str, Any] = {"name": name}
        if swatch:
            row["swatchUrl"] = swatch
        if e.get("hex"):
            row["hex"] = e["hex"]
        gallery = e.get("imageUrls") or ([] if not e.get("imageUrl") else [e["imageUrl"]])
        if isinstance(gallery, list) and gallery:
            if len(gallery) == 1:
                row["imageUrl"] = gallery[0]
            else:
                row["imageUrls"] = gallery
        clean.append(row)
    return json.dumps(clean)


def fetch_colors_json_from_db(product_id: int) -> str | None:
    """Load rich colours JSON from Postgres — admin list API can omit gallery URLs."""
    password = os.environ.get("SPRING_DATASOURCE_PASSWORD", "")
    user = os.environ.get("SPRING_R2DBC_USERNAME", "minisandmore_user")
    if not password:
        return None
    import subprocess

    sql = f"SELECT colors_json FROM product WHERE id={product_id} LIMIT 1;"
    try:
        out = subprocess.run(
            [
                "psql",
                "-h",
                "127.0.0.1",
                "-U",
                user,
                "-d",
                "miniandmore",
                "-t",
                "-A",
                "-c",
                sql,
            ],
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


def has_gallery_urls(entries: list[dict[str, Any]]) -> bool:
    for entry in entries:
        if entry.get("imageUrl") or entry.get("imageUrls"):
            return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--file", type=str, default=None)
    args = parser.parse_args()

    xlsx_path = Path(args.file) if args.file else next((p for p in XLSX_CANDIDATES if p.is_file()), None)
    if not xlsx_path or not xlsx_path.is_file():
        print("Missing SKU WISE PRINT xlsx in repo root", file=sys.stderr)
        return 1

    print(f"=== Restore print swatches @ {GATEWAY} ===")
    print(f"Spreadsheet: {xlsx_path.name}")

    print_catalog = build_print_name_images(xlsx_path)
    print(f"PRINT NAMES sheet: {len(print_catalog)} unique print images")

    token = auth()
    code, body = request("GET", "/api/v1/admin/products", token=token)
    products = unwrap(body) or []
    if code != 200:
        print(f"Failed to list products: {code} {body}", file=sys.stderr)
        return 1

    needed_keys: set[str] = set()
    for product in products:
        for entry in parse_colors(product.get("colorsJson")):
            key = norm_key(entry["name"])
            needed_keys.add(key)
            alias = SWATCH_NAME_ALIASES.get(key)
            if alias:
                needed_keys.add(norm_key(alias))

    upload_map: dict[str, str] = {}
    for key in sorted(needed_keys):
        if key not in print_catalog:
            continue
        display, raw = print_catalog[key]
        if args.dry_run:
            upload_map[key] = f"https://example.com/uat/products/dry-run/{display}.jpg"
            continue
        jpeg = to_jpeg_bytes(raw, display)
        url = upload_image(token, display, jpeg)
        upload_map[key] = url
        print(f"  uploaded {display} -> {url}")

    print(f"Uploaded / mapped {len(upload_map)} print swatches")

    def swatch_url_for_color(color_name: str) -> str | None:
        key = norm_key(color_name)
        if key in upload_map:
            return upload_map[key]
        alias = SWATCH_NAME_ALIASES.get(key)
        if alias:
            return upload_map.get(norm_key(alias))
        return None

    updated = 0
    skipped = 0
    failed = 0
    for product in products:
        pid = product.get("id")
        db_colors = fetch_colors_json_from_db(int(pid)) if pid else None
        colors_source = db_colors or product.get("colorsJson")
        entries = parse_colors(colors_source)
        if not entries:
            skipped += 1
            continue
        changed = False
        swatch_urls: list[str] = []
        for entry in entries:
            url = swatch_url_for_color(entry["name"])
            if url and entry.get("swatchUrl") != url:
                entry["swatchUrl"] = url
                changed = True
            if entry.get("swatchUrl"):
                swatch_urls.append(entry["swatchUrl"])
        if not changed:
            skipped += 1
            continue

        product["colorsJson"] = serialize_colors(entries)
        # Per-print products keep lifestyle URLs in colorImagesJson / colorsJson — do not
        # overwrite the product-level imagesJson hero with a single print photo.
        if not (product.get("colorImagesJson") or "").strip().strip("{}"):
            if has_gallery_urls(entries):
                for entry in entries:
                    urls = entry.get("imageUrls") or ([] if not entry.get("imageUrl") else [entry["imageUrl"]])
                    swatch = (entry.get("swatchUrl") or "").strip()
                    gallery = [u for u in urls if u != swatch]
                    if gallery:
                        product["imagesJson"] = json.dumps([gallery[0]])
                        break
            elif swatch_urls:
                product["imagesJson"] = json.dumps([swatch_urls[0]])

        name = product.get("name", "")[:50]
        if args.dry_run:
            print(f"  [dry-run] would update {product.get('sku')} — {name}")
            updated += 1
            continue

        pid = product.get("id")
        code, saved = request("PUT", f"/api/v1/admin/products/{pid}", product, token=token)
        if code in (200, 201):
            updated += 1
            print(f"  updated {product.get('sku')} — {name}")
        else:
            failed += 1
            print(f"  FAILED {product.get('sku')}: HTTP {code} {saved}", file=sys.stderr)

    print(f"\nDone: {updated} updated, {skipped} unchanged, {failed} failed")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
