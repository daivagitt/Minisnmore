#!/usr/bin/env python3
"""Branded code coverage summary (Vitest json-summary or JaCoCo XML)."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import esc, link_card, page_shell, stat_block, status_pill  # noqa: E402

COVERAGE_CSS = """
      .coverage-bar {
        margin: 16px 0 4px;
        height: 12px;
        border-radius: 999px;
        background: #fde68a;
        overflow: hidden;
        border: 1px solid #fcd34d;
      }
      .coverage-bar > span {
        display: block;
        height: 100%;
        background: linear-gradient(90deg, #f59e0b, #ea580c);
        border-radius: 999px;
      }
      .coverage-bar-label {
        font-size: 0.92rem;
        color: var(--text-muted);
        margin-bottom: 18px;
      }
      .line-meter {
        display: flex;
        align-items: center;
        gap: 10px;
        min-width: 160px;
      }
      .line-meter-track {
        flex: 1;
        height: 8px;
        border-radius: 999px;
        background: #e2e8f0;
        overflow: hidden;
      }
      .line-meter-fill {
        display: block;
        height: 100%;
        background: #16a34a;
        border-radius: 999px;
      }
      .line-meter-fill.warn { background: #f59e0b; }
      .line-meter-fill.low { background: #dc2626; }
      td.num { text-align: right; white-space: nowrap; }
      .table-note { margin: 0 0 12px; color: var(--text-muted); font-size: 0.95rem; }
"""


def pct(value: float | None) -> str:
    if value is None:
        return "—"
    return f"{value:.1f}%"


def meter_class(line_pct: float) -> str:
    if line_pct >= 95:
        return ""
    if line_pct >= 80:
        return "warn"
    return "low"


def normalize_frontend_path(raw: str) -> str:
    marker = "/src/"
    idx = raw.find(marker)
    if idx >= 0:
        return raw[idx + 1 :]
    return Path(raw).name


def parse_vitest_summary(path: Path) -> tuple[dict, list[dict]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    total = data.get("total") or {}
    rows: list[dict] = []
    for key, metrics in data.items():
        if key == "total" or not isinstance(metrics, dict):
            continue
        lines = metrics.get("lines") or {}
        rows.append(
            {
                "module": normalize_frontend_path(key),
                "lines_pct": float(lines.get("pct") or 0),
                "covered": int(lines.get("covered") or 0),
                "total": int(lines.get("total") or 0),
            }
        )
    rows.sort(key=lambda row: (-row["lines_pct"], row["module"]))
    return total, rows


def counter_totals(node: ET.Element, counter_type: str) -> tuple[int, int]:
    missed = covered = 0
    for counter in node.findall(f"./counter[@type='{counter_type}']"):
        missed += int(counter.attrib.get("missed", 0))
        covered += int(counter.attrib.get("covered", 0))
    return missed, covered


def ratio_pct(missed: int, covered: int) -> float:
    total = missed + covered
    return (100.0 * covered / total) if total else 0.0


def parse_jacoco_xml(path: Path) -> tuple[dict, list[dict]]:
    root = ET.parse(path).getroot()
    line_missed, line_covered = counter_totals(root, "LINE")
    stmt_missed, stmt_covered = counter_totals(root, "INSTRUCTION")
    branch_missed, branch_covered = counter_totals(root, "BRANCH")

    total = {
        "lines": {
            "covered": line_covered,
            "total": line_missed + line_covered,
            "pct": ratio_pct(line_missed, line_covered),
        },
        "statements": {
            "covered": stmt_covered,
            "total": stmt_missed + stmt_covered,
            "pct": ratio_pct(stmt_missed, stmt_covered),
        },
        "branches": {
            "covered": branch_covered,
            "total": branch_missed + branch_covered,
            "pct": ratio_pct(branch_missed, branch_covered),
        },
    }

    rows: list[dict] = []
    for package in root.findall("./package"):
        pkg_name = package.attrib.get("name", "").replace("/", ".")
        for clazz in package.findall("./class"):
            source = clazz.attrib.get("sourcefilename") or clazz.attrib.get("name", "Unknown")
            module = f"{pkg_name}.{source}" if pkg_name else source
            missed, covered = counter_totals(clazz, "LINE")
            if missed + covered == 0:
                continue
            rows.append(
                {
                    "module": module,
                    "lines_pct": ratio_pct(missed, covered),
                    "covered": covered,
                    "total": missed + covered,
                }
            )

    rows.sort(key=lambda row: (-row["lines_pct"], row["module"]))
    return total, rows


def merge_jacoco_xmls(paths: list[Path]) -> tuple[dict, list[dict]]:
    """Merge multiple JaCoCo report roots into one combined total + class rows."""
    line_missed = line_covered = 0
    stmt_missed = stmt_covered = 0
    branch_missed = branch_covered = 0
    merged_rows: dict[str, dict] = {}

    for path in paths:
        if not path.is_file():
            continue
        root = ET.parse(path).getroot()
        lm, lc = counter_totals(root, "LINE")
        sm, sc = counter_totals(root, "INSTRUCTION")
        bm, bc = counter_totals(root, "BRANCH")
        line_missed += lm
        line_covered += lc
        stmt_missed += sm
        stmt_covered += sc
        branch_missed += bm
        branch_covered += bc

        _, rows = parse_jacoco_xml(path)
        for row in rows:
            key = row["module"]
            if key not in merged_rows:
                merged_rows[key] = {"module": key, "covered": 0, "total": 0}
            merged_rows[key]["covered"] += row["covered"]
            merged_rows[key]["total"] += row["total"]

    total = {
        "lines": {
            "covered": line_covered,
            "total": line_missed + line_covered,
            "pct": ratio_pct(line_missed, line_covered),
        },
        "statements": {
            "covered": stmt_covered,
            "total": stmt_missed + stmt_covered,
            "pct": ratio_pct(stmt_missed, stmt_covered),
        },
        "branches": {
            "covered": branch_covered,
            "total": branch_missed + branch_covered,
            "pct": ratio_pct(branch_missed, branch_covered),
        },
    }

    rows: list[dict] = []
    for key, row in merged_rows.items():
        missed = row["total"] - row["covered"]
        rows.append(
            {
                "module": key,
                "lines_pct": ratio_pct(missed, row["covered"]),
                "covered": row["covered"],
                "total": row["total"],
            }
        )
    rows.sort(key=lambda row: (-row["lines_pct"], row["module"]))
    return total, rows


def module_rows_html(rows: list[dict], *, limit: int = 40) -> str:
    if not rows:
        return '<tr><td colspan="4" class="muted">No module-level coverage data found.</td></tr>'

    body = []
    for row in rows[:limit]:
        pct_value = row["lines_pct"]
        fill_class = meter_class(pct_value)
        body.append(
            "<tr>"
            f"<td><code>{esc(row['module'])}</code></td>"
            f'<td><div class="line-meter"><div class="line-meter-track">'
            f'<span class="line-meter-fill {fill_class}" style="width:{pct_value:.1f}%"></span>'
            f"</div><span>{pct(pct_value)}</span></div></td>"
            f'<td class="num">{row["covered"]:,}</td>'
            f'<td class="num">{row["total"]:,}</td>'
            "</tr>"
        )
    return "\n".join(body)


def build_body(
    *,
    kind: str,
    total: dict,
    rows: list[dict],
    detail_href: str,
    line_gate: float,
    branch_gate: float,
) -> str:
    lines = total.get("lines") or {}
    statements = total.get("statements") or {}
    branches = total.get("branches") or {}

    line_pct = float(lines.get("pct") or 0)
    branch_pct = float(branches.get("pct") or 0)
    status = "PASS" if line_pct >= line_gate and branch_pct >= branch_gate else "FAIL"

    line_covered = int(lines.get("covered") or 0)
    line_total = int(lines.get("total") or 0)
    stmt_pct = float(statements.get("pct") or line_pct)

    return f"""
      <section class="panel">
        <h2>Summary</h2>
        <p>Status: {status_pill(status)}</p>
        <div class="stats">
          {stat_block("Line coverage", pct(line_pct))}
          {stat_block("Lines covered", f"{line_covered:,} / {line_total:,}")}
          {stat_block("Statements", pct(stmt_pct))}
          {stat_block("Branches", pct(branch_pct))}
        </div>
        <div class="coverage-bar" aria-hidden="true"><span style="width:{line_pct:.1f}%"></span></div>
        <p class="coverage-bar-label">{pct(line_pct)} line coverage overall</p>
      </section>
      <section class="panel">
        <h2>Detailed report</h2>
        <p class="muted">Line-by-line drill-down (standard generator output, hosted under this release).</p>
        {link_card(detail_href, "Open detailed coverage browser", "Open package and file-level coverage in a new tab")}
      </section>
      <section class="panel">
        <h2>Coverage by module</h2>
        <p class="table-note">Showing top {min(40, len(rows))} of {len(rows)} entries by line coverage.</p>
        <table>
          <thead>
            <tr><th>Module</th><th>Lines</th><th>Covered</th><th>Total</th></tr>
          </thead>
          <tbody>
            {module_rows_html(rows)}
          </tbody>
        </table>
      </section>
    """


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", choices=["frontend", "backend"], required=True)
    parser.add_argument("--input", required=True, help="Primary input file, or comma-separated JaCoCo XML paths for backend merge")
    parser.add_argument("--output", required=True)
    parser.add_argument("--detail-href", required=True, help="Relative link to raw HTML coverage browser")
    parser.add_argument("--line-gate", type=float, default=95.0)
    parser.add_argument("--branch-gate", type=float, default=80.0)
    args = parser.parse_args()

    input_path = Path(args.input)
    if args.kind == "frontend":
        total, rows = parse_vitest_summary(input_path)
        title = "Frontend Code Coverage Report"
    else:
        jacoco_paths = [Path(p.strip()) for p in args.input.split(",") if p.strip()]
        if len(jacoco_paths) > 1:
            total, rows = merge_jacoco_xmls(jacoco_paths)
        else:
            total, rows = parse_jacoco_xml(jacoco_paths[0])
        title = "Backend Code Coverage Report"

    body = build_body(
        kind=args.kind,
        total=total,
        rows=rows,
        detail_href=args.detail_href,
        line_gate=args.line_gate,
        branch_gate=args.branch_gate,
    )

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        page_shell(
            title,
            body,
            subtitle="CI coverage summary",
            extra_css=COVERAGE_CSS,
            nested_logo=True,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
