#!/usr/bin/env python3
"""Backfill product MRP (compare_at_price) so the global store discount applies.

Legacy catalog rows often have only `price` (or variant `rate`) with no MRP. The storefront
derives selling price from compare_at_price at runtime; without MRP, no discount is shown.

Assumption: stored price / rate values are list/MRP, not already-discounted selling prices.

Usage:
  python3 deploy/scripts/backfill-product-mrp.py --dry-run
  python3 deploy/scripts/backfill-product-mrp.py --apply

Environment (optional):
  PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from decimal import Decimal, ROUND_HALF_UP
from typing import Any


def psql_rows(sql: str) -> list[tuple[str, ...]]:
    cmd = [
        "psql",
        "-h",
        os.environ.get("PGHOST", "127.0.0.1"),
        "-p",
        os.environ.get("PGPORT", "5432"),
        "-U",
        os.environ.get("PGUSER", "minisandmore_user"),
        "-d",
        os.environ.get("PGDATABASE", "miniandmore"),
        "-tA",
        "-F",
        "\t",
        "-c",
        sql,
    ]
    env = {**os.environ}
    if "PGPASSWORD" not in env:
        env["PGPASSWORD"] = os.environ.get("PGPASSWORD", "Kzer!@A123")
    out = subprocess.check_output(cmd, env=env, text=True)
    rows: list[tuple[str, ...]] = []
    for line in out.strip().split("\n"):
        if line.strip():
            rows.append(tuple(line.split("\t")))
    return rows


def psql_exec(sql: str) -> None:
    cmd = [
        "psql",
        "-h",
        os.environ.get("PGHOST", "127.0.0.1"),
        "-p",
        os.environ.get("PGPORT", "5432"),
        "-U",
        os.environ.get("PGUSER", "minisandmore_user"),
        "-d",
        os.environ.get("PGDATABASE", "miniandmore"),
        "-v",
        "ON_ERROR_STOP=1",
        "-c",
        sql,
    ]
    env = {**os.environ}
    if "PGPASSWORD" not in env:
        env["PGPASSWORD"] = os.environ.get("PGPASSWORD", "Kzer!@A123")
    subprocess.check_call(cmd, env=env)


def to_amount(raw: Any) -> Decimal | None:
    if raw is None or raw == "":
        return None
    try:
        value = Decimal(str(raw))
    except Exception:
        return None
    if value <= 0:
        return None
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def is_variants(item_structure: str, variants_json: str) -> bool:
    structure = (item_structure or "SIMPLE").strip().upper()
    if structure == "VARIANTS":
        return True
    trimmed = (variants_json or "").strip()
    return bool(trimmed and trimmed not in ("[]", ""))


def variant_row_needs_mrp(row: dict[str, Any]) -> bool:
    mrp = to_amount(row.get("mrp") if row.get("mrp") is not None else row.get("compareAt"))
    if mrp is not None:
        return False
    return to_amount(row.get("price")) is not None or to_amount(row.get("rate")) is not None


def backfill_variant_row(row: dict[str, Any]) -> tuple[dict[str, Any], bool]:
    mrp = to_amount(row.get("mrp") if row.get("mrp") is not None else row.get("compareAt"))
    if mrp is not None:
        return row, False

    price = to_amount(row.get("price"))
    rate = to_amount(row.get("rate"))
    next_row = dict(row)
    if price is not None:
        next_row["mrp"] = float(price)
        return next_row, True
    if rate is not None:
        next_row["mrp"] = float(rate)
        return next_row, True
    return row, False


def min_active_variant_mrp(rows: list[dict[str, Any]]) -> Decimal | None:
    min_mrp: Decimal | None = None
    for row in rows:
        active = row.get("active")
        if active is False or active == "false" or active == 0:
            continue
        mrp = to_amount(row.get("mrp") if row.get("mrp") is not None else row.get("compareAt"))
        if mrp is None:
            continue
        min_mrp = mrp if min_mrp is None else min(min_mrp, mrp)
    return min_mrp


def load_products() -> list[dict[str, Any]]:
    sql = """
      SELECT id::text, sku, coalesce(item_structure, ''), coalesce(price::text, ''), coalesce(compare_at_price::text, ''), coalesce(variants_json, '')
      FROM product
      WHERE active = true
      ORDER BY id
    """
    products: list[dict[str, Any]] = []
    for pid, sku, structure, price, compare_at, variants_json in psql_rows(sql):
        products.append(
            {
                "id": pid,
                "sku": sku,
                "item_structure": structure,
                "price": price,
                "compare_at_price": compare_at,
                "variants_json": variants_json,
            }
        )
    return products


def plan_updates(products: list[dict[str, Any]]) -> list[dict[str, Any]]:
    planned: list[dict[str, Any]] = []
    for product in products:
        pid = product["id"]
        sku = product["sku"]
        structure = product["item_structure"]
        variants_json = product["variants_json"]
        price = to_amount(product["price"])
        compare_at = to_amount(product["compare_at_price"])

        if compare_at is not None:
            continue

        if not is_variants(structure, variants_json):
            if price is None:
                continue
            planned.append(
                {
                    "id": pid,
                    "sku": sku,
                    "kind": "simple",
                    "compare_at_price": str(price),
                    "variants_json": None,
                }
            )
            continue

        try:
            parsed = json.loads(variants_json)
        except json.JSONDecodeError:
            print(f"WARN skip {sku}: invalid variants_json", file=sys.stderr)
            continue
        if not isinstance(parsed, list):
            continue

        changed_rows = False
        next_rows: list[dict[str, Any]] = []
        for row in parsed:
            if not isinstance(row, dict):
                next_rows.append(row)
                continue
            next_row, changed = backfill_variant_row(row)
            changed_rows = changed_rows or changed
            next_rows.append(next_row)

        min_mrp = min_active_variant_mrp(next_rows)
        next_compare = min_mrp if min_mrp is not None else price
        if next_compare is None:
            continue

        planned.append(
            {
                "id": pid,
                "sku": sku,
                "kind": "variants",
                "compare_at_price": str(next_compare),
                "variants_json": json.dumps(next_rows, separators=(",", ":")) if changed_rows else None,
            }
        )
    return planned


def apply_updates(planned: list[dict[str, Any]]) -> None:
    for item in planned:
        pid = item["id"]
        compare_at = item["compare_at_price"]
        if item["variants_json"] is not None:
            escaped = item["variants_json"].replace("'", "''")
            psql_exec(
                f"UPDATE product SET compare_at_price = {compare_at}, variants_json = '{escaped}' WHERE id = {pid};"
            )
        else:
            psql_exec(f"UPDATE product SET compare_at_price = {compare_at} WHERE id = {pid};")


def main() -> int:
    parser = argparse.ArgumentParser(description="Backfill product MRP for store discount")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--dry-run", action="store_true", help="Show planned updates only")
    group.add_argument("--apply", action="store_true", help="Write updates to the database")
    args = parser.parse_args()

    products = load_products()
    missing = sum(1 for p in products if to_amount(p["compare_at_price"]) is None)
    planned = plan_updates(products)

    print(f"Active products: {len(products)}")
    print(f"Missing compare_at_price before: {missing}")
    print(f"Planned updates: {len(planned)}")
    for item in planned[:20]:
        extra = " + variants_json" if item["variants_json"] else ""
        print(f"  [{item['kind']}] {item['sku']}: compare_at_price={item['compare_at_price']}{extra}")
    if len(planned) > 20:
        print(f"  ... and {len(planned) - 20} more")

    if args.apply:
        if not planned:
            print("Nothing to apply.")
            return 0
        apply_updates(planned)
        after_missing = int(psql_rows("SELECT count(*) FROM product WHERE active = true AND (compare_at_price IS NULL OR compare_at_price <= 0) AND price > 0")[0][0])
        print(f"Applied {len(planned)} updates. Active products still missing MRP (with price): {after_missing}")
    else:
        print("Dry run only — re-run with --apply to write changes.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
