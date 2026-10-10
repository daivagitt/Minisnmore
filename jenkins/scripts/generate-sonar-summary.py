#!/usr/bin/env python3
"""Fetch SonarQube metrics and render self-contained summary HTML + JSON."""

from __future__ import annotations

import argparse
import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import PROJECT_NAME, brand_logo_markup, esc, generated_at_iso, generated_at_utc_label, page_shell  # noqa: E402

PROJECTS = [
    ("frontend", "Frontend"),
    ("mnm-backend", "Backend"),
]

METRIC_KEYS = [
    "coverage",
    "line_coverage",
    "branch_coverage",
    "bugs",
    "vulnerabilities",
    "code_smells",
    "duplicated_lines_density",
    "ncloc",
    "reliability_rating",
    "security_rating",
    "sqale_rating",
]


def api_get(base_url: str, token: str, path: str, params: dict[str, str]) -> dict:
    url = f"{base_url.rstrip('/')}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, context=ctx, timeout=60) as resp:
        return json.loads(resp.read().decode())


def fetch_project(base_url: str, token: str, key: str, label: str) -> dict:
    try:
        measures = api_get(
            base_url,
            token,
            "/api/measures/component",
            {"component": key, "metricKeys": ",".join(METRIC_KEYS)},
        )
    except urllib.error.HTTPError:
        return {"key": key, "label": label, "missing": True}

    metric_map = {
        m["metric"]: m.get("value", "")
        for m in measures.get("component", {}).get("measures", [])
    }

    gate = api_get(base_url, token, "/api/qualitygates/project_status", {"projectKey": key})
    status = gate.get("projectStatus", {})
    gate_state = status.get("status", "NONE")
    conditions = status.get("conditions", [])

    issues = api_get(
        base_url,
        token,
        "/api/issues/search",
        {
            "componentKeys": key,
            "resolved": "false",
            "ps": "25",
            "facets": "",
        },
    )
    issue_rows = []
    for issue in issues.get("issues", []):
        comp = issue.get("component", "")
        short = comp.split(":", 1)[-1] if ":" in comp else comp
        issue_rows.append(
            {
                "type": issue.get("type", ""),
                "severity": issue.get("severity", ""),
                "rule": issue.get("rule", ""),
                "file": short,
                "line": issue.get("line", ""),
                "message": issue.get("message", ""),
            }
        )

    def rating(v: str) -> str:
        return {"1.0": "1.0", "2.0": "2.0", "3.0": "3.0", "4.0": "4.0", "5.0": "5.0"}.get(v, v or "—")

    return {
        "key": key,
        "label": label,
        "missing": False,
        "coverage": metric_map.get("coverage", "—"),
        "line_coverage": metric_map.get("line_coverage", "—"),
        "branch_coverage": metric_map.get("branch_coverage", "—"),
        "bugs": metric_map.get("bugs", "0"),
        "vulnerabilities": metric_map.get("vulnerabilities", "0"),
        "code_smells": metric_map.get("code_smells", "0"),
        "duplication": metric_map.get("duplicated_lines_density", "0"),
        "ncloc": metric_map.get("ncloc", "0"),
        "reliability": rating(metric_map.get("reliability_rating", "")),
        "security": rating(metric_map.get("security_rating", "")),
        "maintainability": rating(metric_map.get("sqale_rating", "")),
        "gate": gate_state,
        "gate_conditions": conditions,
        "issues": issue_rows,
    }


def gate_class(gate: str) -> str:
    return "pass" if gate == "OK" else "fail"


def summary_table(rows: list[dict]) -> str:
    body = []
    for row in rows:
        if row.get("missing"):
            continue
        body.append(
            f"<tr><td><a href=\"#{esc(row['key'])}\">{esc(row['label'])}</a></td>"
            f"<td><span class=\"status {gate_class(row['gate'])}\">{esc(row['gate'])}</span></td>"
            f"<td>{esc(row['coverage'])}%</td>"
            f"<td>{esc(row['line_coverage'])}%</td>"
            f"<td>{esc(row['branch_coverage'])}%</td>"
            f"<td>{esc(row['bugs'])}</td>"
            f"<td>{esc(row['vulnerabilities'])}</td>"
            f"<td>{esc(row['ncloc'])}</td></tr>"
        )
    return (
        "<table><thead><tr>"
        "<th>Project</th><th>Gate</th><th>Coverage</th><th>Lines</th><th>Branches</th>"
        "<th>Bugs</th><th>Vulns</th><th>LoC</th></tr></thead><tbody>"
        + "".join(body)
        + "</tbody></table>"
    )


def project_detail(row: dict) -> str:
    if row.get("missing"):
        return ""
    metrics = [
        ("Coverage", f"{row['coverage']}%"),
        ("Line coverage", f"{row['line_coverage']}%"),
        ("Branch coverage", f"{row['branch_coverage']}%"),
        ("Bugs", row["bugs"]),
        ("Vulnerabilities", row["vulnerabilities"]),
        ("Code smells", row["code_smells"]),
        ("Duplication", f"{row['duplication']}%"),
        ("Lines of code", row["ncloc"]),
        ("Reliability", row["reliability"]),
        ("Security", row["security"]),
        ("Maintainability", row["maintainability"]),
    ]
    metric_html = "".join(
        f'<div class="metric"><span class="metric-label">{esc(l)}</span>'
        f'<span class="metric-value">{esc(v)}</span></div>'
        for l, v in metrics
    )
    cond_rows = "".join(
        f"<tr><td>{esc(c.get('metricKey', ''))}</td>"
        f"<td><span class=\"status {gate_class(c.get('status', ''))}\">{esc(c.get('status', ''))}</span></td>"
        f"<td>{esc(c.get('actualValue', ''))}</td>"
        f"<td>{esc(c.get('errorThreshold', ''))}</td></tr>"
        for c in row.get("gate_conditions", [])
    )
    issue_rows = "".join(
        f"<tr><td>{esc(i['type'])}</td><td>{esc(i['severity'])}</td>"
        f"<td><code>{esc(i['file'])}:{esc(i['line'])}</code></td>"
        f"<td>{esc(i['message'])}</td></tr>"
        for i in row.get("issues", [])
    )
    n_issues = len(row.get("issues", []))
    return f"""
      <section class="panel project-detail" id="{esc(row['key'])}">
        <h2>{esc(row['label'])} — full results</h2>
        <p>Quality gate: <span class="status {gate_class(row['gate'])}">{esc(row['gate'])}</span>
        &nbsp;·&nbsp; Project key: <code>{esc(row['key'])}</code></p>
        <div class="metrics-grid">{metric_html}</div>
        <h3>Quality gate conditions</h3>
        <table class="compact"><thead><tr><th>Condition</th><th>Status</th><th>Actual</th><th>Threshold</th></tr></thead>
        <tbody>{cond_rows}</tbody></table>
        <h3>Open issues ({n_issues} shown)</h3>
        <table class="compact issues"><thead><tr><th>Type</th><th>Severity</th><th>Location</th><th>Message</th></tr></thead>
        <tbody>{issue_rows}</tbody></table>
      </section>
    """


def write_summary_artifacts(out: Path, payload: dict) -> None:
    out.mkdir(parents=True, exist_ok=True)
    rows = [payload["frontend"], payload["backend"]]
    (out / "sonar-summary.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")

    refreshed = generated_at_utc_label()
    extra_css = """
      .metrics-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 12px; margin: 16px 0 24px; }
      .metric { background: var(--surface-muted); border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; }
      .metric-label { display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
      .metric-value { font-size: 1.15rem; font-weight: 700; }
      table.compact { font-size: 0.92rem; }
      table.compact code { font-size: 0.85rem; word-break: break-all; }
      table.issues td:nth-child(4) { max-width: 420px; }
      .project-detail { scroll-margin-top: 24px; }
      .notice { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 10px; padding: 14px 16px; margin-bottom: 18px; }
      .status.pass { background: var(--pass); }
      .status.fail { background: var(--fail); }
    """
    body = f"""
      <section class="panel">
        <div class="notice">
          <strong>Self-contained report.</strong> All SonarQube metrics and issues are shown on this page —
          no separate Sonar login is required. Data refreshed at {esc(refreshed)}.
        </div>
        <h2>SonarQube — Frontend &amp; Backend</h2>
        <p class="muted">Backend project <code>mnm-backend</code> combines gateway, catalog, store, and commerce.
        <strong>Lines of code</strong> is Sonar NCLOC (19,501). Coverage uses JaCoCo executable lines
        (currently 28,287 across the four services) — those are different metrics.
        Frontend uses Vitest-scoped coverage.</p>
        {summary_table(rows)}
      </section>
      {''.join(project_detail(r) for r in rows)}
      <section class="panel">
        <h2>Related reports</h2>
        <div class="links">
          <a class="link-card" href="../frontend/coverage-summary/index.html"><strong>Frontend coverage</strong><span>Vitest line and branch coverage</span></a>
          <a class="link-card" href="../backend/coverage-summary/index.html"><strong>Backend coverage</strong><span>JaCoCo combined across all services</span></a>
          <a class="link-card" href="../index.html"><strong>Reports home</strong><span>Unified dashboard</span></a>
        </div>
      </section>
    """
    html = page_shell(
        "SonarQube summary",
        body,
        subtitle="Frontend + Backend — no login required",
        nested_logo=True,
        extra_css=extra_css,
    )
    html = html.replace(f"<h1>{esc(PROJECT_NAME)} Reports</h1>", "<h1>SonarQube summary</h1>", 1)
    (out / "index.html").write_text(html, encoding="utf-8")
    logo_src = Path(__file__).resolve().parents[1] / "assets" / "daiva-technologies-logo.svg"
    if logo_src.is_file():
        import shutil

        shutil.copy2(logo_src, out / "daiva-logo.svg")
    print(f"Wrote {out / 'index.html'}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="artifacts/sonar-summary")
    parser.add_argument("--sonar-url", default=os.environ.get("SONAR_HOST_URL", "http://127.0.0.1:9000/sonar"))
    parser.add_argument(
        "--from-json",
        metavar="PATH",
        help="Render branded HTML from an existing sonar-summary.json (no Sonar API)",
    )
    args = parser.parse_args()

    out = Path(args.output_dir)

    if args.from_json:
        payload = json.loads(Path(args.from_json).read_text(encoding="utf-8"))
        write_summary_artifacts(out, payload)
        return

    token = os.environ.get("SONAR_TOKEN", "")
    if not token:
        print("SONAR_TOKEN not set", file=sys.stderr)
        sys.exit(1)

    rows = [fetch_project(args.sonar_url, token, key, label) for key, label in PROJECTS]
    payload = {
        "generatedAt": generated_at_iso(),
        "frontend": rows[0],
        "backend": rows[1],
    }
    write_summary_artifacts(out, payload)


if __name__ == "__main__":
    main()
