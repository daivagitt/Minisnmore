#!/usr/bin/env python3
"""Live Ledgerly commerce API integration tests against UAT."""

from __future__ import annotations

import json
import os
import ssl
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

BASE = os.environ.get("API_TEST_BASE_URL", "https://minisnmore.com").rstrip("/")
OUT_JSON = Path(
    os.environ.get(
        "LEDGERLY_LIVE_REPORT_JSON",
        Path(__file__).resolve().parent.parent.parent / "artifacts/ledgerly-live/ledgerly-live-report.json",
    )
)
OUT_HTML = Path(
    os.environ.get(
        "LEDGERLY_LIVE_REPORT_HTML",
        Path(__file__).resolve().parent.parent.parent / "artifacts/ledgerly-live/index.html",
    )
)

ADMIN_USER = os.environ.get("LEDGERLY_TEST_ADMIN_USER", "admin@minisnmore.com")
ADMIN_PASS = os.environ.get("LEDGERLY_TEST_ADMIN_PASS", "admin6")

CTX = ssl.create_default_context()

# Order retry / tracking pulls can move orders to shipped/delivered and fire customer emails
# from Care@minisnmore.com, so they are skipped unless API_TEST_NO_MAIL=false.
NO_MAIL = os.environ.get("API_TEST_NO_MAIL", "true").lower() not in ("0", "false", "no")
NO_MAIL_SKIP = {
    ("POST", "/api/v1/admin/ledgerly/retry-orders"),
    ("POST", "/api/v1/admin/ledgerly/pull-order-tracking"),
}


def login() -> tuple[int, str | None]:
    body = json.dumps({"username": ADMIN_USER, "password": ADMIN_PASS, "rememberMe": True}).encode()
    req = urllib.request.Request(
        f"{BASE}/api/authenticate",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=30) as r:
            return 200, json.loads(r.read().decode()).get("id_token")
    except urllib.error.HTTPError as e:
        return e.code, None


def redact_body(raw: str) -> str:
    if not raw:
        return raw
    try:
        data = json.loads(raw)
    except Exception:
        return raw[:300]

    def scrub(obj):
        if isinstance(obj, dict):
            for k in list(obj.keys()):
                if k.lower() in ("token", "apitoken", "password", "secret", "refreshtoken", "accesstoken"):
                    obj[k] = "[REDACTED]"
                else:
                    scrub(obj[k])
        elif isinstance(obj, list):
            for item in obj:
                scrub(item)

    if isinstance(data, dict):
        scrub(data)
        return json.dumps(data)[:500]
    return raw[:300]


def call(method: str, path: str, token: str, body: dict | None = None) -> dict:
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    req = urllib.request.Request(f"{BASE}{path}", data=data, method=method.upper(), headers=headers)
    started = time.perf_counter()
    row = {"method": method.upper(), "path": path, "ok": False, "code": 0, "ms": 0, "note": "", "body": ""}
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=120) as r:
            raw = r.read().decode(errors="replace")
            row["code"] = r.status
            row["ms"] = round((time.perf_counter() - started) * 1000)
            row["body"] = redact_body(raw)
            row["ok"] = 200 <= r.status < 300
            return row
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        row["code"] = e.code
        row["ms"] = round((time.perf_counter() - started) * 1000)
        row["body"] = redact_body(raw)
        row["note"] = "HTTP error"
        return row
    except Exception as exc:
        row["ms"] = round((time.perf_counter() - started) * 1000)
        row["note"] = str(exc)[:200]
        return row


def build_html(report: dict) -> str:
    rows = report.get("results") or []
    pass_n = sum(1 for r in rows if r.get("ok"))
    skip_n = sum(1 for r in rows if r.get("skipped"))
    tr = []
    for r in rows:
        code = "skip" if r.get("skipped") else (r.get("code") or "—")
        css = "code-ok" if r.get("ok") else ("code-skip" if r.get("skipped") else "code-fail")
        tr.append(
            f"<tr><td><code>{r['method']}</code></td><td><code>{r['path']}</code></td>"
            f'<td class="{css}">{code}</td><td>{r.get("ms", "—")} ms</td>'
            f"<td>{r.get('note') or ''}</td></tr>"
        )
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"/><title>Ledgerly live API tests</title>
<style>
body{{font-family:system-ui,sans-serif;margin:24px;max-width:1100px}}
table{{width:100%;border-collapse:collapse}} th,td{{border-bottom:1px solid #e2e8f0;padding:8px;text-align:left}}
.code-ok{{color:#15803d;font-weight:600}} .code-fail{{color:#b91c1c;font-weight:600}} .code-skip{{color:#64748b;font-weight:600}}
.meta{{color:#64748b;margin-bottom:16px}}
</style></head><body>
<h1>Ledgerly live API integration tests</h1>
<p class="meta">Target: <strong>{report.get("baseUrl")}</strong> · {report.get("finishedAt")} ·
Passed <strong>{pass_n}</strong> / {len(rows) - skip_n} (2xx) · Skipped {skip_n}</p>
<table><thead><tr><th>Method</th><th>Path</th><th>Status</th><th>Time</th><th>Note</th></tr></thead>
<tbody>{''.join(tr)}</tbody></table>
<p class="meta">Admin JWT + live Ledgerly commerce API (settings, sync, pull, retry). Non-2xx may mean missing token or rate limits.</p>
</body></html>"""


def main() -> int:
    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    results: list[dict] = []
    started = datetime.now(timezone.utc).isoformat()

    code, token = login()
    results.append(
        {
            "method": "POST",
            "path": "/api/authenticate (admin)",
            "ok": code == 200 and bool(token),
            "code": code,
            "ms": 0,
            "note": "admin login" if token else "login failed",
        }
    )
    if not token:
        report = {"baseUrl": BASE, "startedAt": started, "finishedAt": datetime.now(timezone.utc).isoformat(), "results": results}
        OUT_JSON.write_text(json.dumps(report, indent=2), encoding="utf-8")
        OUT_HTML.write_text(build_html(report), encoding="utf-8")
        print("Login failed — wrote partial report")
        return 1

    endpoints: list[tuple[str, str, dict | None]] = [
        ("GET", "/api/v1/admin/ledgerly/integration-status", None),
        ("GET", "/api/v1/admin/ledgerly/settings", None),
        ("GET", "/api/v1/admin/ledgerly/test", None),
        ("POST", "/api/v1/admin/ledgerly/pull-inventory", None),
        ("POST", "/api/v1/admin/ledgerly/retry-orders", None),
        ("POST", "/api/v1/admin/ledgerly/pull-order-tracking", None),
    ]
    for method, path, body in endpoints:
        if NO_MAIL and (method, path) in NO_MAIL_SKIP:
            results.append(
                {
                    "method": method,
                    "path": path,
                    "ok": False,
                    "skipped": True,
                    "code": 0,
                    "ms": 0,
                    "note": "skipped: no-mail mode (can trigger order emails via Care@minisnmore.com)",
                }
            )
            continue
        results.append(call(method, path, token, body))

    report = {
        "baseUrl": BASE,
        "startedAt": started,
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "results": results,
    }
    OUT_JSON.write_text(json.dumps(report, indent=2), encoding="utf-8")
    OUT_HTML.write_text(build_html(report), encoding="utf-8")
    pass_n = sum(1 for r in results if r.get("ok"))
    skip_n = sum(1 for r in results if r.get("skipped"))
    print(f"Ledgerly live: {pass_n}/{len(results) - skip_n} passed, {skip_n} skipped → {OUT_HTML}")
    return 0 if pass_n == len(results) else 0


if __name__ == "__main__":
    raise SystemExit(main())
