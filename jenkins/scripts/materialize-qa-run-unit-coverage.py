#!/usr/bin/env python3
"""Build minimal JUnit / coverage inputs from artifacts/qa-run-summary.json.

qa-final-report does not commit Vitest junit, coverage-summary.json, or JaCoCo XML —
only rollup numbers in qa-run-summary.json. This script materializes small inputs so
package-unit-test-reports.sh and package-coverage-reports.sh can render our branded UI.
"""

from __future__ import annotations

import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.dom import minidom


def write_pretty_xml(path: Path, root: ET.Element) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rough = ET.tostring(root, encoding="unicode")
    pretty = minidom.parseString(rough).toprettyxml(indent="  ")
    path.write_text(pretty, encoding="utf-8")


def pct_to_counts(pct: float, total_units: int = 10_000) -> tuple[int, int]:
    covered = int(round(total_units * pct / 100.0))
    covered = min(max(covered, 0), total_units)
    return covered, total_units - covered


def materialize_frontend(out: Path, summary: dict) -> None:
    unit = summary.get("frontendUnitTests") or {}
    cov = summary.get("frontendCoverage") or {}
    total = int(unit.get("total") or 0)
    failed = int(unit.get("failed") or 0)
    errors = int(unit.get("errors") or 0) if "errors" in unit else 0
    skipped = int(unit.get("skipped") or 0)

    junit_dir = out / "frontend"
    junit_dir.mkdir(parents=True, exist_ok=True)
    suites = ET.Element("testsuites")
    suite = ET.SubElement(
        suites,
        "testsuite",
        {
            "name": "Vitest (QA final report rollup)",
            "tests": str(total),
            "failures": str(failed),
            "errors": str(errors),
            "skipped": str(skipped),
            "time": "0",
        },
    )
    ET.SubElement(
        suite,
        "testcase",
        {"classname": "rollup", "name": "frontend-suite", "time": "0"},
    )
    write_pretty_xml(junit_dir / "junit.xml", suites)

    def metric(key: str) -> dict:
        pct = float(cov.get(key) or 0)
        covered, missed = pct_to_counts(pct)
        total_n = covered + missed
        return {"total": total_n, "covered": covered, "skipped": 0, "pct": pct}

    coverage = {
        "total": {
            "lines": metric("lines"),
            "statements": metric("statements"),
            "branches": metric("branches"),
            "functions": metric("functions"),
        }
    }
    (junit_dir / "coverage-summary.json").write_text(
        json.dumps(coverage, indent=2), encoding="utf-8"
    )


def jacoco_module_xml(path: Path, line_pct: float, instr_pct: float, branch_pct: float) -> None:
    lc, lm = pct_to_counts(line_pct)
    ic, im = pct_to_counts(instr_pct)
    bc, bm = pct_to_counts(branch_pct)
    report = ET.Element("report", {"name": path.stem})
    package = ET.SubElement(report, "package", {"name": "com/miniandmore/rollup"})
    clazz = ET.SubElement(
        package,
        "class",
        {"name": "QaRunSummary", "sourcefilename": "QaRunSummary.java"},
    )
    for ctype, covered, missed in (
        ("INSTRUCTION", ic, im),
        ("BRANCH", bc, bm),
        ("LINE", lc, lm),
    ):
        ET.SubElement(
            clazz,
            "counter",
            {"type": ctype, "missed": str(missed), "covered": str(covered)},
        )
    write_pretty_xml(path, report)


def materialize_backend(out: Path, summary: dict) -> None:
    unit = summary.get("backendUnitTests") or {}
    cov = summary.get("backendCoverage") or {}
    total = int(unit.get("total") or 0)
    failed = int(unit.get("failed") or 0)
    errors = int(unit.get("errors") or 0)
    skipped = int(unit.get("skipped") or 0)

    modules = [
        ("gateway", "Gateway"),
        ("catalog", "Catalog"),
        ("store", "Store"),
        ("commerce", "Commerce"),
    ]
    # Split totals evenly across services for display (rollup only).
    per = max(total // len(modules), 0)
    remainder = total - per * len(modules)

    backend_root = out / "backend"
    for idx, (slug, label) in enumerate(modules):
        mod_total = per + (1 if idx < remainder else 0)
        mod_dir = backend_root / slug / "surefire-reports"
        mod_dir.mkdir(parents=True, exist_ok=True)
        suite = ET.Element(
            "testsuite",
            {
                "name": label,
                "tests": str(mod_total),
                "failures": str(failed // len(modules) if idx == 0 else 0),
                "errors": str(errors // len(modules) if idx == 0 else 0),
                "skipped": str(skipped // len(modules) if idx == 0 else 0),
                "time": "0",
            },
        )
        write_pretty_xml(mod_dir / f"TEST-{label}Rollup.xml", suite)

        jacoco_dir = backend_root / slug / "jacoco"
        jacoco_dir.mkdir(parents=True, exist_ok=True)
        jacoco_module_xml(
            jacoco_dir / "jacoco.xml",
            float(cov.get("line") or 0),
            float(cov.get("instruction") or 0),
            float(cov.get("branch") or 0),
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--summary",
        default="artifacts/qa-run-summary.json",
        help="QA rollup JSON (from qa-final-report)",
    )
    parser.add_argument(
        "--output-dir",
        default="artifacts/qa-import",
        help="Directory for synthetic junit / jacoco / coverage-summary inputs",
    )
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[2]
    summary_path = root / args.summary
    if not summary_path.is_file():
        raise SystemExit(f"Missing {summary_path} — run import-qa-final-report-data.sh first")

    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    out = root / args.output_dir
    materialize_frontend(out, summary)
    materialize_backend(out, summary)
    print(f"Materialized QA rollup inputs under {out}")


if __name__ == "__main__":
    main()
