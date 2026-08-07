#!/usr/bin/env python3
"""Write docs/sku-wise-print-mapping-verification.md — Excel vs UAT catalog.

Compares each parent SKU's Print-1…Print-N expected names (from the xlsx import
pipeline) against live `colorsJson` / `variantsJson[].color` on UAT.

Usage:
  GATEWAY_URL=http://127.0.0.1:9180 ADMIN_USER=admin ADMIN_PASS=admin6 \\
    python3 deploy/scripts/generate-print-mapping-verification.py
"""
from __future__ import annotations

import importlib.util
import json
import os
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "docs" / "sku-wise-print-mapping-verification.md"

spec = importlib.util.spec_from_file_location(
    "uat_sku_import",
    REPO / "deploy/scripts/uat-sku-wise-print-import.py",
)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def load_expected_from_xlsx() -> tuple[dict[str, dict[int, str]], dict[str, dict], dict[str, list[str]], str]:
    """style -> {print_index: name}, style_meta, style -> import colors list."""
    xlsx = next(p for p in mod.XLSX_CANDIDATES if p.exists())
    print_name_map = mod.build_print_name_map(xlsx)
    parsed = mod.parse_xlsx(xlsx)

    style_prints: dict[str, dict[int, str]] = defaultdict(dict)
    style_meta: dict[str, dict] = {}
    rows_by_style: dict[str, list] = defaultdict(list)

    for row in parsed["main"]:
        style = str(row.get("Style Name") or "").strip()
        if not style:
            continue
        rows_by_style[style].append(row)
        style_meta[style] = {
            "parent_sku": mod.parent_sku(style),
            "category": str(row.get("CATEGORY") or "").strip(),
        }

    for style, rows in rows_by_style.items():
        row_nums = {r.get("_excelRow") for r in rows if r.get("_excelRow")}
        slots_with_qty: set[int] = set()
        for row in rows:
            for i in range(1, 12):
                if row.get(f"Print-{i} QUANTITY") not in ("", None):
                    slots_with_qty.add(i)
        for i in sorted(slots_with_qty):
            resolved: str | None = None
            for excel_row in sorted(row_nums):
                name = print_name_map.get((excel_row, i))
                if name:
                    resolved = name
                    break
            if not resolved:
                for row in rows:
                    if row.get(f"Print-{i} QUANTITY") in ("", None):
                        continue
                    cell = str(row.get(f"Print-{i}") or "").strip()
                    if cell and cell.upper() != "NO STOCK":
                        resolved = cell
                        break
            if resolved:
                style_prints[style][i] = resolved

    for style_key, whc_name in mod.WHC_STYLE_KEYS.items():
        style = next((n for n in style_meta if mod.norm_key(n) == style_key), None)
        if not style:
            continue
        colors: list[str] = []
        seen: set[str] = set()
        for entry in parsed["whc"].get(whc_name, []):
            c = str(entry["color"]).strip()
            if c and c not in seen:
                seen.add(c)
                colors.append(c)
        if colors:
            style_prints[style] = {i + 1: c for i, c in enumerate(colors)}

    import_colors: dict[str, list[str]] = {}
    for product in mod.build_products(parsed, print_name_map):
        import_colors[product["sku"]] = json.loads(product.get("colorsJson") or "[]")

    return dict(style_prints), style_meta, import_colors, xlsx.name


def parse_catalog_colors(raw: str | None) -> list[str]:
    if not raw or not raw.strip():
        return []
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        return []
    names: list[str] = []
    for item in parsed:
        if isinstance(item, str) and item.strip():
            names.append(item.strip())
        elif isinstance(item, dict) and item.get("name"):
            names.append(str(item["name"]).strip())
    return names


def parse_variant_colors(variants_raw: str | None) -> set[str]:
    if not variants_raw:
        return set()
    try:
        variants = json.loads(variants_raw)
    except json.JSONDecodeError:
        return set()
    out: set[str] = set()
    for v in variants:
        if isinstance(v, dict):
            c = v.get("color")
            if c and str(c).strip():
                out.add(str(c).strip())
    return out


def norm_set(names: list[str] | set[str]) -> set[str]:
    return {mod.norm_key(n) for n in names if n}


def main() -> int:
    style_prints, style_meta, import_colors, xlsx_name = load_expected_from_xlsx()

    token = mod.auth()
    _, body = mod.request("GET", "/api/v1/admin/products", token=token)
    products = mod.unwrap(body) or []
    by_sku: dict[str, dict] = {}
    for p in products:
        sku = str(p.get("sku") or "").strip().upper()
        if sku:
            by_sku[sku] = p

    ok_slots = 0
    mismatch_slots = 0
    missing_catalog = 0
    extra_in_catalog = 0
    product_rows: list[str] = []

    for style in sorted(style_prints, key=lambda s: s.lower()):
        meta = style_meta.get(style, {})
        psku = meta.get("parent_sku") or mod.parent_sku(style)
        expected_slots = style_prints[style]
        expected_names = list(expected_slots.values())
        expected_set = norm_set(expected_names)

        live = by_sku.get(psku.upper())
        if not live:
            missing_catalog += 1
            product_rows.append(f"## {style}")
            product_rows.append("")
            product_rows.append(f"**Parent SKU:** `{psku}`  ")
            product_rows.append("**Status:** ❌ **SKU not found in UAT catalog**")
            product_rows.append("")
            product_rows.append("| Print slot | Excel expected | In UAT | Result |")
            product_rows.append("|------------|----------------|--------|--------|")
            for i in sorted(expected_slots):
                product_rows.append(f"| Print-{i} | {expected_slots[i]} | — | missing SKU |")
            product_rows.append("")
            continue

        catalog_colors = parse_catalog_colors(live.get("colorsJson"))
        variant_colors = parse_variant_colors(live.get("variantsJson"))
        catalog_set = norm_set(catalog_colors)
        import_list = import_colors.get(psku, [])
        import_set = norm_set(import_list)

        extras = catalog_set - expected_set
        missing = expected_set - catalog_set
        set_match = not extras and not missing

        product_rows.append(f"## {style}")
        product_rows.append("")
        product_rows.append(f"**Parent SKU:** `{psku}`  ")
        product_rows.append(
            f"**Set match (Excel slots ↔ catalog colors):** {'✅' if set_match else '❌'}"
        )
        if extras:
            extra_in_catalog += len(extras)
            product_rows.append(f"- **Extra in catalog (not in Excel slots):** {', '.join(sorted(extras, key=str))}")
        if missing:
            product_rows.append(f"- **Missing in catalog (in Excel slots):** {', '.join(sorted(missing, key=str))}")
        product_rows.append("")
        product_rows.append("| Print slot | Excel expected | In `colorsJson` | In variants | Result |")
        product_rows.append("|------------|----------------|-----------------|-------------|--------|")

        for i in sorted(expected_slots):
            name = expected_slots[i]
            nk = mod.norm_key(name)
            in_colors = nk in catalog_set
            in_variants = nk in norm_set(variant_colors)
            if in_colors and in_variants:
                result = "✅ OK"
                ok_slots += 1
            elif in_colors or in_variants:
                result = "⚠️ partial"
                mismatch_slots += 1
            else:
                result = "❌ missing"
                mismatch_slots += 1
            product_rows.append(
                f"| Print-{i} | {name} | {'yes' if in_colors else 'no'} | {'yes' if in_variants else 'no'} | {result} |"
            )

        product_rows.append("")
        product_rows.append(f"**UAT `colorsJson`:** {', '.join(catalog_colors) if catalog_colors else '*(empty)*'}")
        product_rows.append("")
        product_rows.append(
            f"**Import script would set:** {', '.join(import_list) if import_list else '*(empty)*'}"
        )
        import_match = import_set == catalog_set
        product_rows.append(
            f"**Import `colorsJson` ↔ live catalog:** {'✅ match' if import_match else '❌ differ'}"
        )
        product_rows.append("")

    ts = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    lines = [
        "# SKU-wise print mapping verification (Excel vs UAT)",
        "",
        f"**Generated:** {ts}  ",
        f"**Excel source:** `{xlsx_name}`  ",
        f"**UAT gateway:** `{mod.GATEWAY}`  ",
        "**Script:** `deploy/scripts/generate-print-mapping-verification.py`",
        "",
        "Use this file to confirm each **Print-N → print name** from the spreadsheet is present in the live catalog (`colorsJson` and `variantsJson[].color`).",
        "",
        "Also see the reference mapping (Excel only): [sku-wise-print-slot-mapping.md](./sku-wise-print-slot-mapping.md)",
        "",
        "## Summary",
        "",
        f"| Metric | Count |",
        f"|--------|------:|",
        f"| Products in Excel | {len(style_prints)} |",
        f"| SKU missing from catalog | {missing_catalog} |",
        f"| Print slots ✅ OK | {ok_slots} |",
        f"| Print slots ⚠️/❌ | {mismatch_slots} |",
        "",
        "---",
        "",
    ] + product_rows

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {OUT}")
    print(f"  products={len(style_prints)} ok_slots={ok_slots} mismatch_slots={mismatch_slots} missing_sku={missing_catalog}")
    return 0 if mismatch_slots == 0 and missing_catalog == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
