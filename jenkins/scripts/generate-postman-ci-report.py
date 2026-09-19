#!/usr/bin/env python3
"""Combined HTML report: Postman smoke + load (all GET endpoints via k6)."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from newman_report_utils import aggregate_load_workers, execution_rows, load_newman_run  # noqa: E402
from report_theme import PROJECT_NAME, esc, page_shell, stat_block, status_pill  # noqa: E402

TABLE_CSS = """
  .data { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 14px; }
  .data th, .data td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); }
  .data th { background: var(--surface-muted); }
  .data tr.fail td { background: #fef2f2; }
  .code-ok { color: var(--pass); font-weight: 600; }
  .code-fail { color: var(--fail); font-weight: 600; }
  .time { font-variant-numeric: tabular-nums; }
  .section-note { margin: 0 0 8px; color: var(--text-muted); font-size: 0.95rem; }
"""


def format_time(ms: object) -> str:
    if ms in (None, "", "—"):
        return "—"
    try:
        return f"{float(ms):.0f} ms"
    except (TypeError, ValueError):
        return str(ms)


def format_code(code: object) -> str:
    if code in (None, "", "—"):
        return "—"
    try:
        value = int(code)
    except (TypeError, ValueError):
        return str(code)
    css = "code-ok" if 200 <= value < 300 else "code-fail"
    return f'<span class="{css}">{value}</span>'


def api_table_rows(rows: list[dict]) -> str:
    if not rows:
        return '<tr><td colspan="3" class="muted">No requests recorded</td></tr>'
    html_rows: list[str] = []
    for row in rows:
        row_cls = "" if row.get("passed", True) else "fail"
        html_rows.append(
            f'<tr class="{row_cls}">'
            f"<td><code>{esc(row['endpoint'])}</code></td>"
            f"<td>{format_code(row.get('code'))}</td>"
            f"<td class=\"time\">{esc(format_time(row.get('responseTimeMs')))}</td>"
            f"</tr>"
        )
    return "\n".join(html_rows)


def k6_table_rows(rows: list[dict]) -> str:
    if not rows:
        return '<tr><td colspan="4" class="muted">No endpoint metrics recorded.</td></tr>'
    html_rows: list[str] = []
    for row in rows:
        fail_rate = row.get("failRatePct") or 0
        ok = fail_rate <= 5
        status = "200" if ok else "ERR"
        css = "code-ok" if ok else "code-fail"
        html_rows.append(
            f'<tr class="{"" if ok else "fail"}">'
            f"<td><code>{esc(row.get('endpoint', '—'))}</code></td>"
            f'<td><span class="{css}">{status}</span></td>'
            f"<td class=\"time\">{esc(format_time(row.get('avgMs')))}</td>"
            f"<td class=\"time\">{esc(format_time(row.get('p95Ms')))}</td>"
            f"</tr>"
        )
    return "\n".join(html_rows)


def smoke_stats(run: dict | None) -> tuple[str, int, int, int, float | None]:
    rows = execution_rows(run)
    if not rows:
        return "not run", 0, 0, 0, None
    failed = sum(1 for r in rows if not r["passed"])
    times = [float(r["responseTimeMs"]) for r in rows if r["responseTimeMs"] != "—"]
    avg = round(sum(times) / len(times)) if times else None
    status = "PASS" if failed == 0 else "FAIL"
    return status, len(rows), len(rows) - failed, failed, avg


def load_k6_endpoints(root: Path, vus: int) -> list[dict]:
    path = root / f"artifacts/load/k6-endpoints-{vus}vu.json"
    if not path.is_file():
        summary_path = root / "artifacts/load/k6-summary.json"
        if summary_path.is_file():
            for run in json.loads(summary_path.read_text()).get("runs") or []:
                if int(run.get("vus") or 0) != vus:
                    continue
                embedded = run.get("endpoints") or {}
                if isinstance(embedded, dict) and embedded.get("endpoints"):
                    return embedded["endpoints"]
    if path.is_file():
        return json.loads(path.read_text(encoding="utf-8")).get("endpoints") or []
    return []


def k6_load_section(vus: str, root: Path, duration: str = "") -> str:
    rows = load_k6_endpoints(root, int(vus))
    if not rows:
        return ""

    fail_count = sum(1 for r in rows if (r.get("failRatePct") or 0) > 5)
    status = "PASS" if fail_count == 0 else "FAIL"
    avg_times = [float(r["avgMs"]) for r in rows if r.get("avgMs") is not None]
    overall_avg = round(sum(avg_times) / len(avg_times)) if avg_times else "—"
    duration_note = f" · {esc(duration)}" if duration else ""

    return f"""
      <section class="panel">
        <h2>Load · {esc(vus)} virtual users (all GET endpoints){duration_note}</h2>
        <p class="section-note">
          Status: {status_pill(status)} · {len(rows)} OpenAPI GET endpoints under concurrent load
        </p>
        <div class="stats">
          {stat_block("Endpoints", len(rows))}
          {stat_block("Failed endpoints", fail_count)}
          {stat_block("Overall avg", f"{overall_avg} ms" if overall_avg != "—" else "—")}
        </div>
        <table class="data">
          <thead>
            <tr><th>Endpoint</th><th>Status</th><th>Avg response</th><th>p95 response</th></tr>
          </thead>
          <tbody>
            {k6_table_rows(rows)}
          </tbody>
        </table>
      </section>
    """


def postman_storefront_section(users: str, load_dir: Path, perf_max_ms: str) -> str:
    summary_path = load_dir / "summary.json"
    if not summary_path.is_file():
        return ""
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    rows = aggregate_load_workers(load_dir)
    failed_assert = int(summary.get("assertions", {}).get("failed", 0))
    total_assert = int(summary.get("assertions", {}).get("total", 0))
    status = "PASS" if failed_assert == 0 else "FAIL"
    times = [float(r["responseTimeMs"]) for r in rows if r["responseTimeMs"] != "—"]
    avg_ms = round(sum(times) / len(times)) if times else "—"

    return f"""
      <section class="panel">
        <h2>Storefront load · {esc(users)} Newman workers</h2>
        <p class="section-note">Status: {status_pill(status)} · 9 public GET routes · max {esc(perf_max_ms)} ms</p>
        <div class="stats">
          {stat_block("Assertions passed", total_assert - failed_assert)}
          {stat_block("Avg response", f"{avg_ms} ms" if avg_ms != "—" else "—")}
        </div>
        <table class="data">
          <thead>
            <tr><th>Endpoint</th><th>Status code</th><th>Response time</th></tr>
          </thead>
          <tbody>
            {api_table_rows(rows)}
          </tbody>
        </table>
      </section>
    """


def k6_duration_for_vus(root: Path, vus: str) -> str:
    summary_path = root / "artifacts/load/k6-summary.json"
    if not summary_path.is_file():
        return ""
    for run in json.loads(summary_path.read_text()).get("runs") or []:
        if str(run.get("vus")) == str(vus):
            return str(run.get("duration") or "")
    return ""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--base-url", default="https://uat.minisnmore.com")
    parser.add_argument("--perf-max-ms", default="3000")
    parser.add_argument("--perf-max-ms-25", default="6000")
    parser.add_argument("--load-users", default="10,25")
    parser.add_argument("--k6-vus-levels", default="")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[2]
    vus_levels = args.k6_vus_levels.strip() or args.load_users

    smoke = load_newman_run(root / "artifacts/postman/smoke-report.json")
    smoke_status, smoke_total, smoke_pass, smoke_fail, smoke_avg = smoke_stats(smoke)
    smoke_rows = execution_rows(smoke)

    load_sections: list[str] = []
    seen_vus: set[str] = set()
    for part in vus_levels.split(","):
        vus = part.strip()
        if not vus or vus == "50" or vus in seen_vus:
            continue
        seen_vus.add(vus)
        k6_section = k6_load_section(vus, root, k6_duration_for_vus(root, vus))
        if k6_section:
            load_sections.append(k6_section)
        else:
            load_dir = root / "artifacts/postman" / f"load-{vus}users"
            perf_cap = args.perf_max_ms_25 if int(vus) > 10 else args.perf_max_ms
            postman_section = postman_storefront_section(vus, load_dir, perf_cap)
            if postman_section:
                load_sections.append(postman_section)

    body = f"""
      <section class="panel">
        <h2>API smoke tests</h2>
        <p class="section-note">Status: {status_pill(smoke_status)} · Target <code>{esc(args.base_url)}</code></p>
        <div class="stats">
          {stat_block("Requests", smoke_total)}
          {stat_block("Passed", smoke_pass)}
          {stat_block("Failed", smoke_fail)}
          {stat_block("Avg response", f"{smoke_avg} ms" if smoke_avg is not None else "—")}
        </div>
        <table class="data">
          <thead>
            <tr><th>Endpoint</th><th>Status code</th><th>Response time</th></tr>
          </thead>
          <tbody>
            {api_table_rows(smoke_rows)}
          </tbody>
        </table>
      </section>
      {''.join(load_sections) if load_sections else '<section class="panel"><h2>Load tests</h2><p class="muted">No load test runs recorded.</p></section>'}
      <p class="muted">Functional coverage for all ~170 endpoints (POST/PUT/DELETE): <a href="../api-tests/lifecycle-report.html">API lifecycle report</a></p>
    """

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        page_shell(
            f"{PROJECT_NAME} API tests",
            body,
            subtitle="Smoke + load (all GET endpoints)",
            extra_css=TABLE_CSS,
            nested_logo=True,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
