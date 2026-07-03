#!/usr/bin/env python3
"""Build a branded HTML unit-test summary from JUnit XML."""

from __future__ import annotations

import argparse
import html
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import link_card, page_shell, stat_block, status_pill  # noqa: E402


def parse_suites(report_path: Path) -> tuple[list[tuple], int, int, int, int]:
    root = ET.parse(report_path).getroot()
    test_suites = root.findall("testsuite") if root.tag == "testsuites" else [root]

    total = failures = errors = skipped = 0
    rows: list[tuple] = []
    for suite in test_suites:
        tests = int(suite.attrib.get("tests", 0))
        file_failures = int(suite.attrib.get("failures", 0))
        file_errors = int(suite.attrib.get("errors", 0))
        file_skipped = int(suite.attrib.get("skipped", 0))
        time = suite.attrib.get("time", "0")
        name = suite.attrib.get("name", report_path.stem)
        total += tests
        failures += file_failures
        errors += file_errors
        skipped += file_skipped
        rows.append((name, tests, file_failures, file_errors, file_skipped, time))
    return rows, total, failures, errors, skipped


def parse_backend_reports(reports_dir: Path, label: str = "") -> tuple[list[tuple], int, int, int, int]:
    total = failures = errors = skipped = 0
    rows: list[tuple] = []
    prefix = f"[{label}] " if label else ""
    for report in sorted(reports_dir.glob("TEST-*.xml")):
        root = ET.parse(report).getroot()
        tests = int(root.attrib.get("tests", 0))
        file_failures = int(root.attrib.get("failures", 0))
        file_errors = int(root.attrib.get("errors", 0))
        file_skipped = int(root.attrib.get("skipped", 0))
        time = root.attrib.get("time", "0")
        classname = root.attrib.get("name", report.stem.removeprefix("TEST-"))
        total += tests
        failures += file_failures
        errors += file_errors
        skipped += file_skipped
        rows.append((f"{prefix}{classname}", tests, file_failures, file_errors, file_skipped, time))
    return rows, total, failures, errors, skipped


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", choices=["frontend", "backend"], required=True)
    parser.add_argument("--input", help="JUnit XML file or surefire directory")
    parser.add_argument(
        "--module",
        action="append",
        default=[],
        metavar="DIR|LABEL",
        help="Backend only: repeatable surefire dir and service label",
    )
    parser.add_argument("--output", required=True)
    parser.add_argument("--title", default="")
    parser.add_argument("--coverage-href", default="")
    args = parser.parse_args()

    if args.kind == "frontend":
        if not args.input:
            parser.error("--input is required for frontend")
        rows, total, failures, errors, skipped = parse_suites(Path(args.input))
        title = args.title or "Frontend Unit Test Report"
        coverage_href = args.coverage_href or "../coverage-summary/index.html"
    else:
        rows = []
        total = failures = errors = skipped = 0
        if args.module:
            for spec in args.module:
                if "|" not in spec:
                    parser.error(f"--module must be DIR|LABEL, got: {spec}")
                dir_part, label = spec.split("|", 1)
                module_rows, t, f, e, s = parse_backend_reports(Path(dir_part), label)
                rows.extend(module_rows)
                total += t
                failures += f
                errors += e
                skipped += s
        elif args.input:
            module_rows, total, failures, errors, skipped = parse_backend_reports(Path(args.input))
            rows = module_rows
        else:
            parser.error("backend requires --input or --module")
        rows.sort(key=lambda row: row[0].lower())
        title = args.title or "Backend Unit Test Report"
        coverage_href = args.coverage_href or "../coverage-summary/index.html"

    status = "PASS" if failures == 0 and errors == 0 else "FAIL"
    row_html = "\n".join(
        "<tr>"
        f"<td>{html.escape(name)}</td>"
        f"<td>{tests}</td>"
        f"<td>{file_failures}</td>"
        f"<td>{file_errors}</td>"
        f"<td>{file_skipped}</td>"
        f"<td>{html.escape(time)}</td>"
        "</tr>"
        for name, tests, file_failures, file_errors, file_skipped, time in rows
    )

    body = f"""
      <section class="panel">
        <h2>Summary</h2>
        <p>Status: {status_pill(status)}</p>
        <div class="stats">
          {stat_block("Total tests", total)}
          {stat_block("Failures", failures)}
          {stat_block("Errors", errors)}
          {stat_block("Skipped", skipped)}
        </div>
      </section>
      <section class="panel">
        <h2>Test suites</h2>
        <table>
          <thead>
            <tr><th>Test suite</th><th>Tests</th><th>Failures</th><th>Errors</th><th>Skipped</th><th>Time (s)</th></tr>
          </thead>
          <tbody>
            {row_html or '<tr><td colspan="6" class="muted">No test suites found.</td></tr>'}
          </tbody>
        </table>
      </section>
      <section class="panel">
        <h2>Related reports</h2>
        <div class="links">
          {link_card(coverage_href, "Code coverage summary", "Line, branch, and module coverage with drill-down browser")}
        </div>
      </section>
    """

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        page_shell(
            title,
            body,
            subtitle="CI test results",
            nested_logo=True,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
