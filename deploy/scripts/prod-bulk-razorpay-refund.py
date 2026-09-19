#!/usr/bin/env python3
"""Issue Razorpay refunds for online orders marked REFUNDED in the DB."""

from __future__ import annotations

import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass


@dataclass
class OrderRow:
    order_id: int
    order_number: str
    total: str
    payment_id: str
    existing_refund: str | None


def require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        print(f"ERROR: {name} is not set", file=sys.stderr)
        sys.exit(1)
    return value


def auth_header(key_id: str, key_secret: str) -> dict[str, str]:
    token = base64.b64encode(f"{key_id}:{key_secret}".encode()).decode()
    return {"Authorization": f"Basic {token}", "Content-Type": "application/json"}


def fetch_orders() -> list[OrderRow]:
    user = require_env("SPRING_DATASOURCE_USERNAME")
    password = require_env("SPRING_DATASOURCE_PASSWORD")
    sql = """
SELECT o.id,
       o.order_number,
       o.total::text,
       o.razorpay_payment_id,
       (
         SELECT r.razorpay_refund_id
         FROM order_return r
         WHERE r.customer_order_id = o.id
           AND r.razorpay_refund_id IS NOT NULL
           AND r.razorpay_refund_id NOT LIKE 'rfnd_demo%'
         LIMIT 1
       )
FROM customer_order o
WHERE o.payment_status = 'REFUNDED'
  AND o.payment_method = 'online'
  AND o.razorpay_payment_id IS NOT NULL
  AND o.razorpay_payment_id <> ''
  AND o.razorpay_payment_id NOT LIKE 'pay_demo%'
  AND o.razorpay_payment_id NOT LIKE 'pay_flow%'
  AND o.total > 0
ORDER BY o.id;
"""
    env = {**os.environ, "PGPASSWORD": password}
    proc = subprocess.run(
        ["psql", "-h", "127.0.0.1", "-U", user, "-d", "miniandmore", "-tA", "-F", "\t", "-c", sql],
        capture_output=True,
        text=True,
        env=env,
        check=False,
    )
    if proc.returncode != 0:
        print(proc.stderr or proc.stdout, file=sys.stderr)
        sys.exit(1)

    rows: list[OrderRow] = []
    for line in proc.stdout.splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 4:
            continue
        existing = parts[4].strip() if len(parts) > 4 and parts[4].strip() else None
        rows.append(
            OrderRow(
                order_id=int(parts[0]),
                order_number=parts[1],
                total=parts[2],
                payment_id=parts[3],
                existing_refund=existing,
            )
        )
    return rows


def list_refunds(key_id: str, key_secret: str, payment_id: str) -> list[dict]:
    req = urllib.request.Request(
        f"https://api.razorpay.com/v1/payments/{payment_id}/refunds",
        headers=auth_header(key_id, key_secret),
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode())
    return data.get("items") or []


def refund_payment(key_id: str, key_secret: str, row: OrderRow) -> tuple[str, str | None, str | None]:
    amount_paise = int(round(float(row.total) * 100))
    body = json.dumps({"amount": amount_paise, "notes": {"order_number": row.order_number}}).encode()
    req = urllib.request.Request(
        f"https://api.razorpay.com/v1/payments/{row.payment_id}/refund",
        data=body,
        headers=auth_header(key_id, key_secret),
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode())
            return "refunded", data.get("id"), None
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode()
        description = raw
        try:
            parsed = json.loads(raw)
            description = parsed.get("error", {}).get("description", raw)
        except json.JSONDecodeError:
            pass
        lowered = description.lower()
        if "already" in lowered and "refund" in lowered:
            items = list_refunds(key_id, key_secret, row.payment_id)
            if items:
                return "already_refunded", items[-1].get("id"), description
            return "already_refunded", None, description
        return "error", None, f"HTTP {exc.code}: {description}"


def main() -> int:
    key_id = require_env("RAZORPAY_KEY_ID")
    key_secret = require_env("RAZORPAY_KEY_SECRET")
    rows = fetch_orders()

    print(f"Found {len(rows)} live online orders to process")
    ok = 0
    skipped = 0
    failed = 0

    for row in rows:
        if row.existing_refund:
            print(f"SKIP {row.order_number} existing_refund={row.existing_refund}")
            skipped += 1
            continue

        status, refund_id, detail = refund_payment(key_id, key_secret, row)
        if status == "refunded":
            print(f"OK   {row.order_number} ₹{row.total} -> {refund_id}")
            ok += 1
        elif status == "already_refunded":
            print(f"OK   {row.order_number} already refunded -> {refund_id or 'unknown'}")
            ok += 1
        else:
            print(f"FAIL {row.order_number} {detail}")
            failed += 1
        time.sleep(0.35)

    print(f"\nSummary: refunded/already={ok}, skipped={skipped}, failed={failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
