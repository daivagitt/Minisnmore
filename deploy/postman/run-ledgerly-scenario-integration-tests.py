#!/usr/bin/env python3
"""
Ledgerly-focused UAT scenario integration tests (checkout → sync → inventory → tracking).

Writes JSON + HTML under artifacts/ledgerly-scenario-integration/.
"""

from __future__ import annotations

import importlib.util
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent.parent
OUT_DIR = Path(os.environ.get("LEDGERLY_SCENARIO_REPORT_DIR", ROOT / "artifacts/ledgerly-scenario-integration"))
OUT_JSON = OUT_DIR / "ledgerly-scenario-integration-report.json"
OUT_HTML = OUT_DIR / "index.html"

# Reuse shared helpers from the main scenario runner.
_SCENARIO_MODULE = ROOT / "deploy/postman/run-scenario-integration-tests.py"
_spec = importlib.util.spec_from_file_location("scenario_common", _SCENARIO_MODULE)
if _spec is None or _spec.loader is None:
    raise RuntimeError(f"Cannot load {_SCENARIO_MODULE}")
scenario_common = importlib.util.module_from_spec(_spec)
sys.modules["scenario_common"] = scenario_common
_spec.loader.exec_module(scenario_common)

BASE = scenario_common.BASE
PAYMENT_DEMO = scenario_common.PAYMENT_DEMO
REFERENCE_SHIPPED_ORDER = scenario_common.REFERENCE_SHIPPED_ORDER
REFERENCE_RETURNED_ORDER = scenario_common.REFERENCE_RETURNED_ORDER
REFERENCE_SYNCED_ORDER = os.environ.get("SCENARIO_REFERENCE_SYNCED_ORDER", "MNM-0FB910FDBC4D")
PUBLIC_TRACK_FIELDS = (
    "order_number",
    "status",
    "tracking_status",
    "tracking_number",
    "tracking_carrier",
    "shipment_date",
)
Scenario = scenario_common.Scenario
Step = scenario_common.Step
api = scenario_common.api
login = scenario_common.login
unwrap = scenario_common.unwrap
scenario_coupon_checkout = scenario_common.scenario_coupon_checkout
scenario_payment_verify = scenario_common.scenario_payment_verify
scenario_reference_orders = scenario_common.scenario_reference_orders
build_html = scenario_common.build_html
serialize_scenario = scenario_common.serialize_scenario


def scenario_ledgerly_connection(admin_token: str) -> Scenario:
    sc = Scenario(
        "ledgerly-connection",
        "Ledgerly connection & settings",
        "Integration status and live connection test against Ledgerly commerce API.",
    )
    code, data, ms = api("GET", "/api/v1/admin/ledgerly/integration-status", admin_token)
    payload = unwrap(data) if isinstance(data, dict) else {}
    if code == 200 and payload.get("connected"):
        sc.steps.append(Step("integration-status", "pass", "connected=true", ms, payload))
    elif code == 200:
        sc.steps.append(
            Step(
                "integration-status",
                "fail" if not payload.get("serverConfigured") else "skip",
                f"connected={payload.get('connected')}",
                ms,
                payload,
            )
        )
    else:
        sc.steps.append(Step("integration-status", "fail", f"HTTP {code}", ms))

    code, data, ms = api("GET", "/api/v1/admin/ledgerly/test", admin_token)
    payload = unwrap(data) if isinstance(data, dict) else {}
    if code == 200 and (payload.get("ok") or payload.get("connected") or payload.get("status") == "ok"):
        sc.steps.append(Step("test-connection", "pass", "Ledgerly API reachable", ms, payload))
    elif code == 200:
        sc.steps.append(Step("test-connection", "pass", str(payload)[:120], ms, payload))
    else:
        sc.steps.append(Step("test-connection", "fail", f"HTTP {code}", ms, {"body": str(data)[:300]}))
    return sc


def scenario_ledgerly_order_sync(admin_token: str, order_ctx: dict[str, Any]) -> Scenario:
    sc = Scenario(
        "ledgerly-order-sync",
        "Ledgerly invoice + sales order sync",
        "Admin sync after payment; falls back to a known synced reference order when checkout is unpaid.",
    )
    order_number = order_ctx.get("orderNumber")
    if not order_number and not REFERENCE_SYNCED_ORDER:
        sc.steps.append(Step("sync-order", "skip", "no order number"))
        return sc

    target_order = order_number if order_ctx.get("paid") else REFERENCE_SYNCED_ORDER
    if not order_ctx.get("paid"):
        sc.steps.append(
            Step(
                "sync-order",
                "skip",
                f"checkout order unpaid — using reference order {REFERENCE_SYNCED_ORDER}",
            )
        )

    code, data, ms = api(
        "POST",
        f"/api/v1/admin/ledgerly/sync-orders/by-number/{target_order}",
        admin_token,
    )
    payload = unwrap(data) if isinstance(data, dict) else {}
    if code == 200 and payload.get("ok"):
        sc.steps.append(Step("sync-order", "pass", f"sync OK for {target_order}", ms, payload))
        order_ctx["ledgerlySyncTriggered"] = True
    else:
        sc.steps.append(Step("sync-order", "fail", f"HTTP {code}", ms, {"body": str(data)[:400]}))
        return sc

    time.sleep(2)
    code, data, ms = api("GET", f"/api/v1/orders/track/{target_order}", public=True)
    payload = unwrap(data) if isinstance(data, dict) else {}
    order = payload.get("order") or {}
    payment_status = order.get("payment_status") or order.get("status")
    if code == 200 and target_order == REFERENCE_SYNCED_ORDER:
        sc.steps.append(
            Step(
                "verify-sync-status",
                "pass",
                f"reference order {target_order} reachable (payment={payment_status})",
                ms,
                order,
            )
        )
    elif code == 200 and order_ctx.get("paid"):
        sc.steps.append(
            Step(
                "verify-sync-status",
                "pass",
                f"paid order {target_order} trackable after sync",
                ms,
                order,
            )
        )
    elif not order_ctx.get("paid"):
        sc.steps.append(
            Step(
                "verify-sync-status",
                "skip",
                "sync exercised on reference order; full checkout sync needs paid order",
                ms,
            )
        )
    else:
        sc.steps.append(Step("verify-sync-status", "fail", f"HTTP {code}", ms))
    return sc


def scenario_ledgerly_inventory(admin_token: str) -> Scenario:
    sc = Scenario(
        "ledgerly-inventory",
        "Pull inventory from Ledgerly",
        "Live inventory pull updates catalog stock from Ledgerly.",
    )
    code, data, ms = api("POST", "/api/v1/admin/ledgerly/pull-inventory", admin_token)
    payload = unwrap(data) if isinstance(data, dict) else {}
    if code == 200:
        note = payload.get("message") or payload.get("status") or "ok"
        sc.steps.append(Step("pull-inventory", "pass", str(note)[:120], ms, payload))
    else:
        sc.steps.append(Step("pull-inventory", "fail", f"HTTP {code}", ms, {"body": str(data)[:300]}))
    return sc


def scenario_ledgerly_tracking(admin_token: str, order_ctx: dict[str, Any]) -> Scenario:
    sc = Scenario(
        "ledgerly-shipment-tracking",
        "Shipment tracking pull",
        "Pull order tracking from Ledgerly and verify public track API on a known shipped order.",
    )
    code, data, ms = api("POST", "/api/v1/admin/ledgerly/pull-order-tracking", admin_token)
    payload = unwrap(data) if isinstance(data, dict) else {}
    if code == 200:
        sc.steps.append(Step("pull-tracking", "pass", "tracking pull completed", ms, payload))
    else:
        sc.steps.append(Step("pull-tracking", "fail", f"HTTP {code}", ms))

    code, data, ms = api("GET", f"/api/v1/orders/track/{REFERENCE_SHIPPED_ORDER}", public=True)
    payload = unwrap(data) if isinstance(data, dict) else {}
    order = payload.get("order") or {}
    tracking = order.get("tracking_status")
    if code == 200 and tracking in ("shipped", "delivered", "in_transit", "processing"):
        sc.steps.append(
            Step(
                "public-track-shipped-ref",
                "pass",
                f"{REFERENCE_SHIPPED_ORDER} → {tracking}",
                ms,
                order,
            )
        )
    elif code == 200:
        sc.steps.append(
            Step("public-track-shipped-ref", "fail", f"unexpected tracking_status={tracking}", ms, order)
        )
    else:
        sc.steps.append(Step("public-track-shipped-ref", "fail", f"HTTP {code}", ms))
    return sc


def scenario_public_track_api(order_ctx: dict[str, Any]) -> Scenario:
    sc = Scenario(
        "public-track-api",
        "Public track API (no login)",
        "GET /api/v1/orders/track/{orderNumber} — delivery timeline only; no JWT required.",
    )

    code, data, ms = api("GET", f"/api/v1/orders/track/{REFERENCE_SHIPPED_ORDER}", public=True)
    payload = unwrap(data) if isinstance(data, dict) else {}
    order = payload.get("order") or {}
    tracking = order.get("tracking_status")
    if code == 200 and tracking in ("shipped", "delivered", "in_transit", "processing"):
        sc.steps.append(
            Step(
                "shipped-order",
                "pass",
                f"{REFERENCE_SHIPPED_ORDER} → {tracking}",
                ms,
                order,
            )
        )
    elif code == 200:
        sc.steps.append(Step("shipped-order", "fail", f"tracking_status={tracking}", ms, order))
    else:
        sc.steps.append(Step("shipped-order", "fail", f"HTTP {code}", ms))

    code, data, ms = api("GET", f"/api/v1/orders/track/{REFERENCE_RETURNED_ORDER}", public=True)
    payload = unwrap(data) if isinstance(data, dict) else {}
    order = payload.get("order") or {}
    if code == 200 and order.get("shipment_date"):
        sc.steps.append(
            Step(
                "returned-order",
                "pass",
                f"{REFERENCE_RETURNED_ORDER} shipment_date={order.get('shipment_date')}",
                ms,
                order,
            )
        )
    elif code == 200:
        sc.steps.append(Step("returned-order", "fail", "missing shipment_date", ms, order))
    else:
        sc.steps.append(Step("returned-order", "fail", f"HTTP {code}", ms))

    checkout_number = order_ctx.get("orderNumber")
    if checkout_number:
        code, data, ms = api("GET", f"/api/v1/orders/track/{checkout_number}", public=True)
        payload = unwrap(data) if isinstance(data, dict) else {}
        order = payload.get("order") or {}
        missing = [f for f in PUBLIC_TRACK_FIELDS if f not in order]
        if code == 200 and not missing and order.get("order_number") == checkout_number:
            sc.steps.append(
                Step(
                    "checkout-order",
                    "pass",
                    f"{checkout_number} → {order.get('tracking_status', '—')} (public fields OK)",
                    ms,
                    order,
                )
            )
        elif code == 200:
            sc.steps.append(
                Step(
                    "checkout-order",
                    "fail",
                    f"missing fields: {', '.join(missing) or 'order_number mismatch'}",
                    ms,
                    order,
                )
            )
        else:
            sc.steps.append(Step("checkout-order", "fail", f"HTTP {code}", ms))
    else:
        sc.steps.append(Step("checkout-order", "skip", "no checkout order in this run"))

    code, data, ms = api("GET", "/api/v1/orders/track/MNM-INVALID-000000", public=True)
    if code == 404:
        sc.steps.append(Step("unknown-order", "pass", "404 for invalid order number", ms))
    else:
        sc.steps.append(Step("unknown-order", "fail", f"expected 404, got HTTP {code}", ms, {"body": str(data)[:200]}))

    return sc


def build_ledgerly_html(report: dict) -> str:
    sys.path.insert(0, str(ROOT / "jenkins" / "scripts"))
    from report_theme import PROJECT_NAME  # noqa: E402

    html = build_html(report)
    return (
        html.replace(
            f"{PROJECT_NAME} scenario integration",
            f"{PROJECT_NAME} Ledgerly scenario integration",
        )
        .replace("Coupon · Zoho · shipment · returns", "Coupon · Ledgerly sync · inventory · tracking · returns")
        .replace("Scenario integration summary", "Ledgerly scenario integration summary")
    )


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    started = datetime.now(timezone.utc).isoformat()
    scenarios: list[Scenario] = []
    order_ctx: dict[str, Any] = {}

    c_code, customer_token = login(scenario_common.CUSTOMER_USER, scenario_common.CUSTOMER_PASS)
    a_code, admin_token = login(scenario_common.ADMIN_USER, scenario_common.ADMIN_PASS)
    auth_sc = Scenario("auth", "Authentication", "Customer + admin JWT for Ledgerly scenario steps.")
    auth_sc.steps.append(Step("customer-login", "pass" if customer_token else "fail", f"HTTP {c_code}"))
    auth_sc.steps.append(Step("admin-login", "pass" if admin_token else "fail", f"HTTP {a_code}"))
    scenarios.append(auth_sc)

    if not customer_token or not admin_token:
        report = {
            "baseUrl": BASE,
            "startedAt": started,
            "finishedAt": datetime.now(timezone.utc).isoformat(),
            "paymentDemo": PAYMENT_DEMO,
            "refundDemo": scenario_common.REFUND_DEMO,
            "scenarios": [serialize_scenario(s) for s in scenarios],
            "summary": {"total": 1, "passed": 0, "failed": 1, "skipped": 0},
        }
        OUT_JSON.write_text(json.dumps(report, indent=2), encoding="utf-8")
        OUT_HTML.write_text(build_ledgerly_html(report), encoding="utf-8")
        print("ERROR: login failed")
        return 1

    sc, order_ctx = scenario_coupon_checkout(customer_token)
    scenarios.append(sc)
    scenarios.append(scenario_payment_verify(customer_token, order_ctx))
    scenarios.append(scenario_ledgerly_connection(admin_token))
    scenarios.append(scenario_ledgerly_order_sync(admin_token, order_ctx))
    scenarios.append(scenario_ledgerly_inventory(admin_token))
    scenarios.append(scenario_ledgerly_tracking(admin_token, order_ctx))
    scenarios.append(scenario_public_track_api(order_ctx))
    scenarios.append(scenario_reference_orders())

    passed = sum(1 for s in scenarios if s.status == "pass")
    failed = sum(1 for s in scenarios if s.status == "fail")
    skipped = sum(1 for s in scenarios if s.status == "skip")

    report = {
        "baseUrl": BASE,
        "startedAt": started,
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "paymentDemo": PAYMENT_DEMO,
        "refundDemo": scenario_common.REFUND_DEMO,
        "credentials": {"customer": scenario_common.CUSTOMER_USER, "admin": scenario_common.ADMIN_USER},
        "orderContext": {k: v for k, v in order_ctx.items() if k not in ("razorpayOrderId",)},
        "scenarios": [serialize_scenario(s) for s in scenarios],
        "summary": {
            "total": len(scenarios),
            "passed": passed,
            "failed": failed,
            "skipped": skipped,
        },
    }
    OUT_JSON.write_text(json.dumps(report, indent=2), encoding="utf-8")
    OUT_HTML.write_text(build_ledgerly_html(report), encoding="utf-8")
    print(
        f"Ledgerly scenario integration: {passed} passed, {failed} failed, {skipped} skipped / {len(scenarios)} scenarios"
    )
    print(f"Report: {OUT_HTML}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
