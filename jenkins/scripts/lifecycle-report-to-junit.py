#!/usr/bin/env python3
"""Convert deploy/postman/api-run-report.json to JUnit for Jenkins."""

from __future__ import annotations

import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--suite-name", default="API lifecycle (all endpoints)")
    args = parser.parse_args()

    data = json.loads(Path(args.input).read_text(encoding="utf-8"))
    results = data.get("results") or []
    suites = ET.Element("testsuites")
    suites.set("name", args.suite_name)

    by_module: dict[str, list[dict]] = {}
    for row in results:
        mod = row.get("module") or "misc"
        by_module.setdefault(mod, []).append(row)

    total_tests = 0
    total_failures = 0
    total_time = 0.0

    for module, rows in sorted(by_module.items()):
        suite = ET.SubElement(suites, "testsuite")
        suite.set("name", module)
        failures = 0
        suite_time = 0.0

        for row in rows:
            case = ET.SubElement(suite, "testcase")
            name = f"{row.get('method', 'GET')} {row.get('path', '/')}"
            case.set("name", name)
            case.set("classname", str(module).replace(" ", ""))
            elapsed = float(row.get("responseTimeMs") or row.get("ms") or 0) / 1000.0
            case.set("time", f"{elapsed:.3f}")
            suite_time += elapsed
            total_tests += 1

            code = int(row.get("code") or 0)
            note = row.get("note") or ""
            if code == 0 and ("skipped" in note.lower() or "payment gateway" in note.lower()):
                skipped = ET.SubElement(case, "skipped")
                skipped.set("message", note.strip() or "skipped")
            elif not row.get("2xx"):
                failures += 1
                total_failures += 1
                failure = ET.SubElement(case, "failure")
                failure.set("message", f"Expected 2xx, got {code}. {note}".strip())

        suite.set("tests", str(len(rows)))
        suite.set("failures", str(failures))
        suite.set("errors", "0")
        suite.set("time", f"{suite_time:.3f}")

    suites.set("tests", str(total_tests))
    suites.set("failures", str(total_failures))
    suites.set("errors", "0")
    suites.set("time", f"{total_time:.3f}")

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(suites).write(out, encoding="utf-8", xml_declaration=True)


if __name__ == "__main__":
    main()
