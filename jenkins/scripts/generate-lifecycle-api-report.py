#!/usr/bin/env python3
"""HTML API test report: endpoint, status code, response time."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import PROJECT_NAME, esc, page_shell, stat_block, status_pill  # noqa: E402

TABLE_CSS = """
  .data { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 14px; }
  .data th, .data td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); }
  .data th { background: var(--surface-muted); }
  .data tr.fail td { background: #fef2f2; }
  .data tr.skip td { color: var(--text-muted); }
  .code-ok { color: var(--pass); font-weight: 600; }
  .code-fail { color: var(--fail); font-weight: 600; }
  .time { font-variant-numeric: tabular-nums; }
"""


def format_time(ms: object) -> str:
    if ms in (None, "", 0, "—"):
        return "—"
    try:
        return f"{float(ms):.0f} ms"
    except (TypeError, ValueError):
        return str(ms)


def format_code(code: object) -> str:
    if code in (None, "", 0, "—"):
        return "—"
    try:
        value = int(code)
    except (TypeError, ValueError):
        return str(code)
    css = "code-ok" if 200 <= value < 300 else "code-fail"
    return f'<span class="{css}">{value}</span>'


def is_skipped(row: dict) -> bool:
    code = int(row.get("code") or 0)
    note = (row.get("note") or "").lower()
    return code == 0 and ("skip" in note or "skipped" in note)


def endpoint_cell(row: dict) -> str:
    method = esc((row.get("method") or "—").upper())
    path = esc(row.get("path") or "—")
    return f"<code>{method}</code> {path}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="artifacts/api-tests/lifecycle-report.json")
    parser.add_argument("--output", default="artifacts/api-tests/lifecycle-report.html")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[2]
    report_path = root / args.input
    if not report_path.is_file():
        raise SystemExit(f"Report not found: {report_path}")

    data = json.loads(report_path.read_text(encoding="utf-8"))
    summary = data.get("summary") or {}
    results = data.get("results") or []

    pass_count = sum(1 for r in results if r.get("2xx"))
    fail_count = sum(1 for r in results if not r.get("2xx") and not is_skipped(r))
    skip_count = sum(1 for r in results if is_skipped(r))
    status = "PASS" if fail_count == 0 else "FAIL"

    table_rows: list[str] = []
    for row in results:
        if is_skipped(row):
            row_cls = "skip"
        elif row.get("2xx"):
            row_cls = ""
        else:
            row_cls = "fail"
        table_rows.append(
            f'<tr class="{row_cls}">'
            f"<td>{endpoint_cell(row)}</td>"
            f"<td>{format_code(row.get('code'))}</td>"
            f"<td class=\"time\">{esc(format_time(row.get('responseTimeMs')))}</td>"
            f"</tr>"
        )

    body = f"""
      <section class="panel">
        <h2>Summary</h2>
        <p>Status: {status_pill(status)} · Target <code>{esc(summary.get('baseUrl', '—'))}</code></p>
        <div class="stats">
          {stat_block("Requests", summary.get('requestsRun', len(results)))}
          {stat_block("Passed (2xx)", pass_count)}
          {stat_block("Failed", fail_count)}
          {stat_block("Skipped", skip_count)}
        </div>
      </section>
      <section class="panel">
        <h2>API requests</h2>
        <table class="data">
          <thead>
            <tr><th>Endpoint</th><th>Status code</th><th>Response time</th></tr>
          </thead>
          <tbody>
            {''.join(table_rows) if table_rows else '<tr><td colspan="3" class="muted">No requests recorded</td></tr>'}
          </tbody>
        </table>
      </section>
    """

    out = root / args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        page_shell(
            f"{PROJECT_NAME} API lifecycle",
            body,
            subtitle="All OpenAPI endpoints",
            extra_css=TABLE_CSS,
            nested_logo=True,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
