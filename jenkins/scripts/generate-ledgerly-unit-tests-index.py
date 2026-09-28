#!/usr/bin/env python3
"""Filtered Ledgerly unit-test view from catalog Surefire XML."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import link_card, page_shell, stat_block, status_pill  # noqa: E402

LEDGERLY_PATTERNS = (
    "Ledgerly",
    "ProductDescriptionCodec",
)


def is_ledgerly_test(report_name: str) -> bool:
    return any(p in report_name for p in LEDGERLY_PATTERNS)


def parse_reports(reports_dir: Path) -> list[tuple[str, int, int, int, int]]:
    rows: list[tuple[str, int, int, int, int]] = []
    for report in sorted(reports_dir.glob("TEST-*.xml")):
        if not is_ledgerly_test(report.name):
            continue
        root = ET.parse(report).getroot()
        rows.append(
            (
                root.attrib.get("name", report.stem.removeprefix("TEST-")),
                int(root.attrib.get("tests", 0)),
                int(root.attrib.get("failures", 0)),
                int(root.attrib.get("errors", 0)),
                int(root.attrib.get("skipped", 0)),
            )
        )
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--reports-dir",
        default="backend/target/surefire-reports",
        help="Catalog Surefire output directory",
    )
    parser.add_argument("--output", default="artifacts/reports-site/backend/ledgerly-unit-tests/index.html")
    args = parser.parse_args()

    reports_dir = Path(args.reports_dir)
    rows = parse_reports(reports_dir) if reports_dir.is_dir() else []
    tests = sum(r[1] for r in rows)
    failures = sum(r[2] for r in rows)
    errors = sum(r[3] for r in rows)
    status = "PASS" if failures == 0 and errors == 0 and tests > 0 else ("FAIL" if failures or errors else "SKIP")

    table_rows = "\n".join(
        f"<tr><td><code>{cls}</code></td><td>{t}</td><td>{f}</td><td>{e}</td><td>{sk}</td></tr>"
        for cls, t, f, e, sk in rows
    )
    body = f"""
      <section class="panel">
        <h2>Ledgerly integration — unit tests</h2>
        <p>Status: {status_pill(status)}</p>
        <p class="muted">Maven Surefire in catalog core (<code>backend/</code>) — {len(rows)} classes,
        {tests} tests (Ledgerly sync, client, webhooks, product merge).</p>
        <div class="stats">
          {stat_block("Tests", tests)}
          {stat_block("Failures", failures)}
          {stat_block("Errors", errors)}
          {stat_block("Classes", len(rows))}
        </div>
        <table>
          <thead><tr><th>Class</th><th>Tests</th><th>Failures</th><th>Errors</th><th>Skipped</th></tr></thead>
          <tbody>
            {table_rows or '<tr><td colspan="5" class="muted">No Ledgerly tests found. Run backend Ledgerly Surefire first.</td></tr>'}
          </tbody>
        </table>
      </section>
      <section class="panel">
        <h2>Related</h2>
        <div class="links">
          {link_card('../ledgerly-live/index.html', 'Live API integration tests', 'Admin JWT + live Ledgerly API on UAT')}
          {link_card('../unit-tests/index.html', 'All backend unit tests', 'Gateway, catalog, store, commerce')}
          {link_card('../../index.html', 'Reports home', 'Unified dashboard')}
        </div>
      </section>
    """
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        page_shell("Ledgerly integration unit tests", body, subtitle="catalog core — Surefire", nested_logo=True),
        encoding="utf-8",
    )
    print(f"Wrote {out} ({len(rows)} classes, {tests} tests)")


if __name__ == "__main__":
    main()
