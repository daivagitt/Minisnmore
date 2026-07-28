#!/usr/bin/env python3
"""Delete all UAT catalog products and import from SKU WISE PRINT spreadsheet.

Usage:
  GATEWAY_URL=http://127.0.0.1:9180 ADMIN_USER=admin ADMIN_PASS=admin6 \\
    python3 deploy/scripts/uat-sku-wise-print-import.py [--dry-run]

Expects repo-root xlsx:
  SKU WISE PRINT  FINAL DETAILS AS ON 21-07-26.xlsx
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
from pathlib import Path
from typing import Any

REPO = Path(__file__).resolve().parents[2]
XLSX_CANDIDATES = [
    REPO / "SKU WISE PRINT  FINAL DETAILS AS ON 21-07-26 (1).xlsx",
    REPO / "SKU WISE PRINT  FINAL DETAILS AS ON 21-07-26.xlsx",
]
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
WHC_STYLE_KEYS = {
    "side button t shirt (half sleeve)": "sideButton",
    "book style tying jabla": "bookStyle",
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


def parse_xlsx(xlsx_path: Path) -> dict[str, Any]:
    node = r"""
const XLSX = require('xlsx');
const path = process.argv[1];
const wb = XLSX.readFile(path);
const main = XLSX.utils.sheet_to_json(wb.Sheets['Final updated count - 21-07-26'], { defval: '' });
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


def build_products(parsed: dict[str, Any]) -> list[dict[str, Any]]:
    whc = parsed["whc"]
    products: dict[str, dict[str, Any]] = {}

    for row in parsed["main"]:
        style = str(row.get("Style Name") or "").strip()
        if not style:
            continue
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

        for i in range(1, 8):
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
            color = print_name if print_name and print_name.upper() != "NO STOCK" else f"Print {i}"
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


def delete_all_products(token: str, dry_run: bool) -> list[dict]:
    _, body = request("GET", "/api/v1/admin/products", token=token)
    products = unwrap(body) or []
    print(f"Found {len(products)} existing products to delete")
    if dry_run:
        for p in products:
            print(f"  would delete {p.get('id')} {p.get('sku')} {p.get('name')}")
        return products
    deleted = 0
    for p in products:
        pid = p.get("id")
        if not pid:
            continue
        code, _ = request("DELETE", f"/api/v1/admin/products/{pid}", token=token)
        if code in (200, 204):
            deleted += 1
            print(f"  deleted {pid} {p.get('sku')}")
        else:
            print(f"  FAILED delete {pid}: HTTP {code}", file=sys.stderr)
    print(f"Deleted {deleted}/{len(products)} products")
    return products


def insert_products(token: str, payloads: list[dict], dry_run: bool) -> None:
    print(f"\nInserting {len(payloads)} products from spreadsheet")
    ok = 0
    for i, product in enumerate(payloads, 1):
        variants = json.loads(product["variantsJson"])
        total_stock = product["stock"]
        print(
            f"  [{i}/{len(payloads)}] {product['sku']} — {product['name'][:50]} "
            f"({len(variants)} variants, stock={total_stock})"
        )
        if dry_run:
            ok += 1
            continue
        code, saved = request("POST", "/api/v1/admin/products", product, token=token)
        saved = unwrap(saved)
        if code in (200, 201) and isinstance(saved, dict):
            ok += 1
        else:
            print(f"    FAILED HTTP {code}: {saved}", file=sys.stderr)
    print(f"\nInserted {ok}/{len(payloads)} products")


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
    payloads = build_products(parsed)
    print(f"Parsed {len(payloads)} styles from spreadsheet")

    token = auth()
    delete_all_products(token, args.dry_run)
    insert_products(token, payloads, args.dry_run)

    if not args.dry_run:
        _, body = request("GET", "/api/v1/admin/products", token=token)
        final = unwrap(body) or []
        print(f"\nFinal catalog count: {len(final)}")
        total_stock = sum(int(p.get("stock") or 0) for p in final)
        print(f"Total parent stock sum: {total_stock}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
