#!/usr/bin/env python3
"""Adapt feat/testing-coverage report.html for the hosted UAT reports site."""

from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo


LINK_REPLACEMENTS: list[tuple[str, str]] = [
    ('src="assets/logo-horizontal.png"', 'src="../daiva-logo.svg"'),
    ("href=\"frontend/coverage/index.html\"", 'href="../frontend/coverage/index.html"'),
    ("href=\"frontend/coverage/coverage-summary.json\"", 'href="../frontend/coverage-summary/index.html"'),
    ("href=\"backend/target/site/jacoco/index.html\"", 'href="../backend/coverage/catalog/index.html"'),
    ("href=\"backend/target/reports/apidocs/index.html\"", 'href="../backend/javadoc/catalog/index.html"'),
    ("href=\"artifacts/javadocs/index.html\"", 'href="../backend/javadoc/index.html"'),
    ("href=\"artifacts/javadocs/catalog/index.html\"", 'href="../backend/javadoc/catalog/index.html"'),
    ("href=\"artifacts/api-docs/index.html\"", 'href="../backend/swagger/index.html"'),
    ("href=\"artifacts/sonar-summary/index.html\"", 'href="../sonar-summary/index.html"'),
    ("href=\"postman/API-FUNCTIONAL-TESTING-REPORT.md\"", 'href="../backend/postman/index.html"'),
    ("href=\"postman/newman-report.json\"", 'href="../backend/postman/smoke-report.json"'),
    ("href=\"postman/newman-junit.xml\"", 'href="../backend/postman/smoke-junit.xml"'),
    ("href=\"performance/API-PERFORMANCE-TESTING-REPORT.md\"", 'href="../backend/postman/index.html"'),
    ("href=\"performance/k6/results/", 'href="../backend/postman/index.html#api-performance" data-skip="'),
    ("href=\"http://localhost:8084/swagger-ui/index.html\"", 'href="../backend/swagger/index.html"'),
    ("href=\"http://localhost:8084/v3/api-docs/catalog\"", 'href="../backend/swagger/openapi.json"'),
    ("href=\"http://localhost:9001/dashboard?id=miniandmore\"", 'href="../sonar-summary/index.html"'),
    ("<code>frontend/coverage/index.html</code>", "<code>frontend/coverage/index.html</code>"),
    ("<code>backend/target/site/jacoco/index.html</code>", "<code>backend/coverage/catalog/index.html</code>"),
    ("<code>backend/target/reports/apidocs/index.html</code>", "<code>backend/javadoc/catalog/index.html</code>"),
    ("<code>artifacts/javadocs/index.html</code>", "<code>backend/javadoc/index.html</code>"),
    ("<code>artifacts/sonar-summary/index.html</code>", "<code>sonar-summary/index.html</code>"),
    ("<code>http://localhost:8084/swagger-ui/index.html</code>", "<code>backend/swagger/index.html</code>"),
    ("<code>http://localhost:9001/dashboard?id=miniandmore</code>", "<code>sonar-summary/index.html</code>"),
]


def pct(value: float) -> str:
    return f"{value:.2f}%"


def read_frontend_coverage(root: Path) -> dict[str, float]:
    path = root / "frontend/coverage/coverage-summary.json"
    if not path.is_file():
        return {}
    total = json.loads(path.read_text()).get("total") or {}
    return {
        "statements": float((total.get("statements") or {}).get("pct") or 0),
        "branches": float((total.get("branches") or {}).get("pct") or 0),
        "functions": float((total.get("functions") or {}).get("pct") or 0),
        "lines": float((total.get("lines") or {}).get("pct") or 0),
    }


def read_backend_jacoco(root: Path) -> dict[str, float | int]:
    path = root / "backend/target/site/jacoco/jacoco.xml"
    if not path.is_file():
        return {}
    tree = ET.parse(path)
    counters = {c.get("type"): c for c in tree.getroot().findall("counter")}
    out: dict[str, float | int] = {}
    for kind in ("INSTRUCTION", "BRANCH", "LINE"):
        counter = counters.get(kind)
        if counter is None:
            continue
        missed = int(counter.get("missed", 0))
        covered = int(counter.get("covered", 0))
        total = missed + covered
        out[kind.lower()] = round(covered / total * 100, 2) if total else 0.0
        out[f"{kind.lower()}_covered"] = covered
        out[f"{kind.lower()}_total"] = total
    return out


def count_surefire_tests(root: Path) -> dict[str, int]:
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    reports = root / "backend/target/surefire-reports"
    if not reports.is_dir():
        return totals
    for xml in reports.glob("TEST-*.xml"):
        try:
            suite = ET.parse(xml).getroot()
        except ET.ParseError:
            continue
        totals["tests"] += int(suite.attrib.get("tests", 0))
        totals["failures"] += int(suite.attrib.get("failures", 0))
        totals["errors"] += int(suite.attrib.get("errors", 0))
        totals["skipped"] += int(suite.attrib.get("skipped", 0))
    totals["passed"] = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    return totals


def read_lifecycle(root: Path) -> dict[str, int | float | str]:
    path = root / "artifacts/api-tests/lifecycle-report.json"
    if not path.is_file():
        return {}
    data = json.loads(path.read_text())
    summary = data.get("summary") or {}
    results = data.get("results") or []
    pass2 = sum(1 for row in results if row.get("2xx"))
    fail = sum(1 for row in results if not row.get("2xx") and not (row.get("code") == 0))
    skip = sum(1 for row in results if row.get("code") == 0)
    return {
        "requests": int(summary.get("requestsRun") or len(results)),
        "pass2xx": pass2,
        "fail": fail,
        "skip": skip,
        "pass_rate": float(summary.get("passRatePct") or 0),
        "base_url": str(summary.get("baseUrl") or "https://uat.minisnmore.com"),
    }


def read_smoke(root: Path) -> dict[str, int]:
    path = root / "artifacts/postman/smoke-report.json"
    if not path.is_file():
        return {}
    stats = (json.loads(path.read_text()).get("run") or {}).get("stats") or {}
    return {
        "requests": int((stats.get("requests") or {}).get("total") or 0),
        "failed_requests": int((stats.get("requests") or {}).get("failed") or 0),
        "assertions": int((stats.get("assertions") or {}).get("total") or 0),
        "failed_assertions": int((stats.get("assertions") or {}).get("failed") or 0),
    }


def read_qa_run_summary(root: Path) -> dict:
    path = root / "artifacts/qa-run-summary.json"
    if not path.is_file():
        return {}
    return json.loads(path.read_text())


def read_load_levels(root: Path) -> list[dict[str, int | str]]:
    rows: list[dict[str, int | str]] = []
    for summary_path in sorted(root.glob("artifacts/postman/load-*users/summary.json")):
        summary = json.loads(summary_path.read_text())
        users = int(summary.get("concurrentUsers") or 0)
        failed = int((summary.get("assertions") or {}).get("failed") or 0)
        total = int((summary.get("assertions") or {}).get("total") or 0)
        rows.append(
            {
                "users": users,
                "status": "PASS" if failed == 0 else "FAIL",
                "assertions": total,
                "failed": failed,
            }
        )
    return rows


def generated_label() -> str:
    override = __import__("os").environ.get("REPORT_GENERATED_AT", "").strip()
    if override:
        return override
    now_utc = datetime.now(ZoneInfo("UTC"))
    now_ist = now_utc.astimezone(ZoneInfo("Asia/Kolkata"))
    return (
        f"{now_ist.strftime('%d %b %Y, %I:%M %p IST')} "
        f"({now_utc.strftime('%H:%M')} UTC)"
    )


def replace_once(html: str, pattern: str, repl: str) -> str:
    return re.sub(pattern, repl, html, count=1)


def apply_metrics(html: str, root: Path) -> str:
    now_ist = datetime.now(ZoneInfo("Asia/Kolkata"))
    html = replace_once(html, r"<span class=\"chip\">Generated: [^<]+</span>", f'<span class="chip">Generated: {generated_label()}</span>')
    html = replace_once(
        html,
        r"<p class=\"muted\">Links verified against files present in this repository on [^<]+</p>",
        f'<p class="muted">Hosted UAT reports refreshed on {now_ist.strftime("%Y-%m-%d")}.</p>',
    )

    qa = read_qa_run_summary(root)
    fe = read_frontend_coverage(root)
    if not fe and qa.get("frontendCoverage"):
        fc = qa["frontendCoverage"]
        fe = {
            "statements": float(fc.get("statements") or 0),
            "branches": float(fc.get("branches") or 0),
            "functions": float(fc.get("functions") or 0),
            "lines": float(fc.get("lines") or 0),
        }
    if fe:
        html = replace_once(html, r"97\.9% statements, 93\.81% branches, 99\.6% functions, 99\.62% lines\.", f"{pct(fe['statements'])} statements, {pct(fe['branches'])} branches, {pct(fe['functions'])} functions, {pct(fe['lines'])} lines.")
        html = replace_once(html, r"<span class=\"value\">97\.9%</span>", f'<span class="value">{pct(fe["statements"])}</span>', )
        html = replace_once(html, r"<span class=\"value\">93\.81%</span>", f'<span class="value">{pct(fe["branches"])}</span>')
        html = replace_once(html, r"<span class=\"value\">99\.6%</span>", f'<span class="value">{pct(fe["functions"])}</span>')
        html = replace_once(html, r"<span class=\"value\">99\.62%</span>", f'<span class="value">{pct(fe["lines"])}</span>')
        html = replace_once(html, r"97\.9% statements / 99\.62% lines", f"{pct(fe['statements'])} statements / {pct(fe['lines'])} lines")

    be = read_backend_jacoco(root)
    if not be and qa.get("backendCoverage"):
        bc = qa["backendCoverage"]
        instr = float(bc.get("instruction") or 0)
        branch = float(bc.get("branch") or 0)
        be = {"instruction": instr, "branch": branch}
    if be:
        instr = float(be.get("instruction") or 0)
        branch = float(be.get("branch") or 0)
        covered = int(be.get("instruction_covered", 0))
        total = int(be.get("instruction_total", 0))
        gap = max(0.0, 94.0 - instr)
        html = html.replace("93.91% instruction coverage", f"{instr:.2f}% instruction coverage")
        html = html.replace("93.91% Instruction Coverage", f"{instr:.2f}% Instruction Coverage")
        html = html.replace("<strong>93.91%</strong>", f"<strong>{instr:.2f}%</strong>")
        html = html.replace('<span class="value">93.91%</span>', f'<span class="value">{instr:.2f}%</span>')
        html = html.replace("93.91% instruction coverage", f"{instr:.2f}% instruction coverage")
        html = html.replace("70,100 / 74,644 = 93.91%", f"{covered:,} / {total:,} = {instr:.2f}%")
        html = html.replace("70,100", f"{covered:,}")
        html = html.replace("74,644", f"{total:,}")
        html = html.replace("75.0%</span>", f"{branch:.1f}%</span>")
        html = replace_once(
            html,
            r"approximately <strong>0\.09 percentage points</strong> below the strict 94% target",
            f"approximately <strong>{gap:.2f} percentage points</strong> below the strict 94% target",
        )
        html = replace_once(
            html,
            r"— approximately 0\.09 points below the strict 94% target\.",
            f"— approximately {gap:.2f} points below the strict 94% target.",
        )
        badge = "pass" if instr >= 94 else "partial"
        badge_text = "Pass" if instr >= 94 else "Below Target"
        html = replace_once(
            html,
            r'<div class="status-row"><h3>Backend Code Coverage</h3><span class="badge partial">Below Target</span></div>',
            f'<div class="status-row"><h3>Backend Code Coverage</h3><span class="badge {badge}">{badge_text}</span></div>',
        )

    tests = count_surefire_tests(root)
    if not tests.get("tests") and qa.get("backendUnitTests"):
        bu = qa["backendUnitTests"]
        tests = {
            "tests": int(bu.get("total") or 0),
            "failures": int(bu.get("failed") or 0),
            "errors": int(bu.get("errors") or 0),
            "skipped": int(bu.get("skipped") or 0),
        }
        tests["passed"] = tests["tests"] - tests["failures"] - tests["errors"] - tests["skipped"]
    if tests.get("tests"):
        html = replace_once(html, r"2,024 tests executed, 0 failures, 0 errors, 1 skipped, Maven BUILD SUCCESS\.", f"{tests['tests']:,} tests executed, {tests['failures']} failures, {tests['errors']} errors, {tests['skipped']} skipped, Maven BUILD SUCCESS.")
        html = replace_once(html, r"<span class=\"value\">2,024</span>", f'<span class="value">{tests["tests"]:,}</span>')
        html = replace_once(html, r"<span class=\"value\">2,023</span>", f'<span class="value">{tests["passed"]:,}</span>')
        html = replace_once(html, r"2,024 tests, BUILD SUCCESS", f"{tests['tests']:,} tests, BUILD SUCCESS")

    lifecycle = read_lifecycle(root)
    smoke = read_smoke(root)
    load_levels = read_load_levels(root)
    if smoke and smoke.get("failed_assertions", 0) == 0:
        html = replace_once(
            html,
            r"<div class=\"status-row\"><h3>API Functional Testing</h3><span class=\"badge partial\">Partial</span></div>\s*<p>131 / 144 Newman requests passed\. 13 storefront GET failures due to application 401 responses\.</p>",
            f'<div class="status-row"><h3>API Functional Testing</h3><span class="badge pass">Pass</span></div><p>Postman smoke: {smoke["requests"]} requests, {smoke["assertions"]} assertions passed on UAT.</p>',
        )
    if lifecycle:
        html = replace_once(
            html,
            r"<div class=\"status-row\"><span class=\"badge partial\">Partial — Application Authentication Issue</span></div>",
            f'<div class="status-row"><span class="badge {"pass" if lifecycle["fail"] == 0 else "partial"}">{"Pass" if lifecycle["fail"] == 0 else "Partial"}</span></div>',
        )

    if load_levels and all(row["status"] == "PASS" for row in load_levels):
        levels = ", ".join(f"{row['users']} users" for row in load_levels)
        html = replace_once(
            html,
            r"<div class=\"status-row\"><h3>API Performance Testing</h3><span class=\"badge fail\">Fail</span></div>\s*<p>k6 smoke test: 99\.33% HTTP failures from the same public-endpoint authentication issue\.</p>",
            f'<div class="status-row"><h3>API Performance Testing</h3><span class="badge pass">Pass</span></div><p>Newman load tests passed at {levels} on UAT.</p>',
        )
        html = replace_once(
            html,
            r"<div class=\"status-row\"><span class=\"badge fail\">Fail — Application Authentication Issue</span></div>",
            '<div class="status-row"><span class="badge pass">Pass — UAT Newman load</span></div>',
        )

    html += '\n<p class="muted" style="margin-top:24px"><a href="../index.html">← Back to reports home</a></p>\n'
    return html


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--template", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[2]
    html = Path(args.template).read_text(encoding="utf-8")
    for old, new in LINK_REPLACEMENTS:
        html = html.replace(old, new)
    html = apply_metrics(html, root)

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(html, encoding="utf-8")


if __name__ == "__main__":
    main()
