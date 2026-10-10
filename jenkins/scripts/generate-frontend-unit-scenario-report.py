#!/usr/bin/env python3
"""Vitest JUnit → scenario-wise HTML/JSON (accordion per suite, one row per test case)."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import PROJECT_NAME, esc, page_shell, stat_block, status_pill  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
LOGO_SRC = ROOT / "jenkins" / "assets" / "daiva-technologies-logo.svg"

MODULE_LABELS = {
    "routes": "Routes",
    "components": "Components",
    "hooks": "Hooks",
    "store": "Store",
    "lib": "Lib / utilities",
}

SCENARIO_EXTRA_CSS = """
      .page { max-width: 1280px; }
      .toc { display:flex; flex-wrap:wrap; gap:8px; margin-top:14px; align-items:center; }
      .toc a, .linkish {
        border:1px solid var(--border); border-radius:999px; padding:4px 12px;
        background:#fff; color:var(--brand-light); font-weight:600; cursor:pointer; font-size:0.9rem;
      }
      .filters { display:flex; flex-wrap:wrap; gap:8px; align-items:center; margin:14px 0 0; }
      .chip {
        border:1px solid var(--border); background:#fff; border-radius:999px; padding:4px 12px;
        cursor:pointer; font-weight:600; color:var(--text-muted);
      }
      .chip.on { background:#eef2ff; color:#3730a3; border-color:#c7d2fe; }
      .screen-card {
        background:#fff; border:1px solid var(--border); border-radius:16px;
        box-shadow:0 1px 2px rgba(15,23,42,0.04); margin-bottom:16px; overflow:hidden;
      }
      .screen-head {
        display:flex; justify-content:space-between; gap:16px; flex-wrap:wrap;
        padding:16px 20px 12px; border-bottom:1px solid var(--border); align-items:flex-start;
      }
      .screen-head h2 { margin:0; font-size:1.15rem; }
      .screen-sub { color:var(--text-muted); font-size:0.9rem; margin-top:2px; }
      .screen-meta { display:flex; gap:12px; align-items:center; flex-wrap:wrap; color:var(--text-muted); font-size:0.92rem; }
      .pass-count { color:#15803d; font-weight:700; }
      .fail-count { color:#b91c1c; font-weight:700; }
      .screen-card.collapsed .screen-body { display:none; }
      .screen-card.collapsed .screen-head { border-bottom:none; }
      .table-wrap { overflow-x:auto; }
      table.cases { width:100%; border-collapse:collapse; font-size:13px; }
      table.cases th, table.cases td {
        text-align:left; vertical-align:top; padding:12px 14px; border-bottom:1px solid var(--border);
      }
      table.cases th { background:#f8fafc; font-size:12px; letter-spacing:0.02em; color:#334155; }
      .tc-id {
        display:inline-block; background:#eef2ff; color:#4338ca; border:1px solid #c7d2fe;
        border-radius:999px; padding:3px 10px; font-size:11px; font-weight:700; white-space:nowrap;
      }
      .type-badge {
        display:inline-block; border:1px solid #cbd5e1; color:#475569; border-radius:999px;
        padding:3px 10px; font-size:12px; background:#f8fafc; white-space:nowrap;
      }
      .steps { margin:0; padding-left:18px; min-width:220px; }
      .steps li + li { margin-top:4px; }
      .row-fail td { background:#fef2f2; }
      .row-skip td { background:#f8fafc; color: var(--text-muted); }
      .small { font-size:12px; margin-top:6px; }
      .module-heading { margin: 28px 0 10px; font-size: 1.05rem; color: var(--brand); letter-spacing: 0.04em; text-transform: uppercase; }
      #screens .module-heading:first-child { margin-top: 8px; }
"""


def slugify(value: str, max_len: int = 36) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return (s[:max_len] or "suite").rstrip("-")


def split_test_name(raw: str) -> tuple[str, str]:
    if " > " in raw:
        describe, test = raw.split(" > ", 1)
        return describe.strip(), test.strip()
    return "", raw.strip()


def infer_type(test_title: str) -> str:
    lower = test_title.lower()
    if any(k in lower for k in ("negative", "reject", "invalid", "wrong", "missing", "cannot", "denied", "fail")):
        return "Negative"
    if any(k in lower for k in ("smoke", "loads", "renders without")):
        return "Smoke"
    if any(k in lower for k in ("responsive", "mobile", "viewport")):
        return "Responsive"
    if "regression" in lower:
        return "Regression"
    return "Unit"


def infer_priority(module: str, suite_file: str, test_title: str) -> str:
    blob = f"{suite_file} {test_title}".lower()
    if module == "Routes" or "login" in blob or "auth" in blob or "checkout" in blob:
        return "P1"
    return "P2"


def suite_context(suite_file: str) -> tuple[str, str, str, str]:
    """module label, display title, path/subtitle hint, id prefix."""
    path = suite_file.replace("\\", "/")
    rel = path.removeprefix("src/")
    parts = rel.split("/")
    area = parts[0] if parts else "other"
    module = MODULE_LABELS.get(area, area.replace("-", " ").title())
    filename = parts[-1] if parts else path
    stem = filename.removesuffix(".test.tsx").removesuffix(".test.ts")

    if area == "routes" and stem.startswith("-"):
        route_stem = stem[1:]
        route_path = "/" + route_stem.replace(".", "/")
        segments = [s for s in route_stem.replace("$", "").split(".") if s]
        title = " ".join(s.replace("-", " ").title() for s in segments[-2:] or segments)
        return module, title or route_stem, route_path, "RTE"

    if area == "components":
        comp = stem.split("/")[-1]
        title = comp.replace("-", " ")
        return module, title, f"src/{rel}", "CMP"

    if area == "hooks":
        name = stem.split("/")[-1]
        return module, name, f"src/{rel}", "HOK"

    if area == "store":
        name = stem.split("/")[-1]
        return module, name, f"src/{rel}", "STR"

    name = stem.split("/")[-1]
    return module, name.replace("-", " "), f"src/{rel}", "LIB"


def parse_junit(path: Path) -> list[dict]:
    root = ET.parse(path).getroot()
    suites = root.findall("testsuite") if root.tag == "testsuites" else [root]
    cases: list[dict] = []
    seq_by_suite: dict[str, int] = {}

    for suite in suites:
        suite_file = suite.attrib.get("name", "unknown")
        module, screen_title, path_hint, id_prefix = suite_context(suite_file)
        suite_slug = slugify(Path(suite_file).stem)

        for tc in suite.findall("testcase"):
            raw_name = tc.attrib.get("name", "unnamed")
            describe, test_title = split_test_name(raw_name)
            scenario = test_title if test_title else raw_name
            if describe and not test_title:
                scenario = describe

            failure = tc.find("failure")
            skipped = tc.find("skipped")
            if failure is not None:
                status = "fail"
                actual = (failure.attrib.get("message") or "").strip()
                if not actual and failure.text:
                    actual = failure.text.strip()[:800]
                if not actual:
                    actual = "Assertion failed"
            elif skipped is not None:
                status = "skip"
                actual = (skipped.attrib.get("message") or "Skipped").strip()
            else:
                status = "pass"
                actual = "All assertions passed"

            seq_by_suite[suite_file] = seq_by_suite.get(suite_file, 0) + 1
            n = seq_by_suite[suite_file]
            case_id = f"UT-{id_prefix}-{suite_slug}-{n:03d}"

            test_type = infer_type(scenario)
            kind = "negative" if test_type == "Negative" else "positive"
            steps = [
                f"Run Vitest suite `{suite_file}`.",
                f"Execute: {raw_name}.",
            ]
            if describe:
                steps.append(f"Describe block: {describe}.")

            cases.append(
                {
                    "id": case_id,
                    "module": module,
                    "screen": screen_title,
                    "path": path_hint,
                    "suite": suite_file,
                    "scenario": scenario,
                    "describe": describe,
                    "rawName": raw_name,
                    "steps": steps,
                    "expected": "Vitest assertions pass without error.",
                    "actual": actual,
                    "priority": infer_priority(module, suite_file, scenario),
                    "type": test_type,
                    "kind": kind,
                    "status": status,
                    "timeSec": tc.attrib.get("time", ""),
                }
            )
    return cases


def summarize(cases: list[dict]) -> dict:
    total = len(cases)
    passed = sum(1 for c in cases if c["status"] == "pass")
    failed = sum(1 for c in cases if c["status"] == "fail")
    skipped = sum(1 for c in cases if c["status"] == "skip")
    positive = [c for c in cases if c["kind"] == "positive"]
    negative = [c for c in cases if c["kind"] == "negative"]
    return {
        "total": total,
        "passed": passed,
        "failed": failed,
        "skipped": skipped,
        "positive": {"total": len(positive), "passed": sum(1 for c in positive if c["status"] == "pass")},
        "negative": {"total": len(negative), "passed": sum(1 for c in negative if c["status"] == "pass")},
    }


def build_html(report: dict) -> str:
    summary = report["summary"]
    cases = report["cases"]
    executed = summary["passed"] + summary["failed"]
    rate = f"{(summary['passed'] / executed * 100):.1f}%" if executed else "—"
    overall = "PASS" if summary["failed"] == 0 else "FAIL"

    suite_order: list[str] = []
    seen: set[str] = set()
    for case in cases:
        if case["suite"] not in seen:
            seen.add(case["suite"])
            suite_order.append(case["suite"])

    def id_pill(value: str) -> str:
        return f'<code class="tc-id">{esc(value)}</code>'

    def type_badge(value: str) -> str:
        return f'<span class="type-badge">{esc(value)}</span>'

    def steps_html(items: list[str]) -> str:
        return '<ol class="steps">' + "".join(f"<li>{esc(step)}</li>" for step in items) + "</ol>"

    def rows_for(suite: str) -> str:
        out = []
        for x in cases:
            if x["suite"] != suite:
                continue
            pill = status_pill(x["status"].upper() if x["status"] != "skip" else "SKIP")
            out.append(
                f'<tr class="row-{esc(x["status"])} kind-{esc(x["kind"])}">'
                f"<td>{id_pill(x['id'])}</td>"
                f"<td>{esc(x['scenario'])}</td>"
                f"<td>{steps_html(x['steps'])}</td>"
                f"<td>{esc(x['expected'])}"
                f'<div class="muted small">{esc(x["actual"])}</div></td>'
                f"<td>{esc(x['priority'])}</td>"
                f"<td>{type_badge(x['type'])}</td>"
                f"<td>{pill}</td>"
                "</tr>"
            )
        return "".join(out)

    cards: list[str] = []
    last_module = None
    for suite in suite_order:
        items = [x for x in cases if x["suite"] == suite]
        if not items:
            continue
        first = items[0]
        module = first["module"]
        passed = sum(1 for x in items if x["status"] == "pass")
        failed = sum(1 for x in items if x["status"] == "fail")
        pass_label = f"{passed} Pass" if failed == 0 else f"{passed} Pass · {failed} Fail"
        pass_class = "pass-count" if failed == 0 else "fail-count"
        slug_bit = first["path"].strip("/").replace("$", "") or first["suite"]
        if module != last_module:
            last_module = module
            cards.append(f'<h2 class="module-heading">{esc(module)}</h2>')
        cards.append(
            f"""
      <article class="screen-card" data-suite="{esc(suite)}">
        <header class="screen-head">
          <div>
            <h2>{esc(first["screen"])}</h2>
            <div class="screen-sub"><span>{esc(slug_bit)}</span> · <code>{esc(first["path"])}</code></div>
          </div>
          <div class="screen-meta">
            <span>{len(items)} cases</span>
            <span class="{pass_class}">{esc(pass_label)}</span>
            <button type="button" class="linkish toggle-one">Expand / collapse</button>
          </div>
        </header>
        <div class="screen-body">
          <div class="table-wrap">
            <table class="cases">
              <thead>
                <tr>
                  <th>Test Case ID</th>
                  <th>Test Scenario</th>
                  <th>Steps (Vitest)</th>
                  <th>Expected Result</th>
                  <th>Priority</th>
                  <th>Type</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>{rows_for(suite)}</tbody>
            </table>
          </div>
        </div>
      </article>"""
        )

    failures = [x for x in cases if x["status"] == "fail"]
    fail_rows = (
        "".join(
            f"<tr><td>{id_pill(x['id'])}</td><td>{esc(x['module'])}</td><td>{esc(x['scenario'])}</td>"
            f"<td class=\"small\">{esc(x['actual'])}</td></tr>"
            for x in failures
        )
        if failures
        else '<p class="muted">No failing cases in the last Vitest run.</p>'
    )

    module_rows = []
    modules: dict[str, list[dict]] = {}
    for case in cases:
        modules.setdefault(case["module"], []).append(case)
    for name, items in modules.items():
        p = [x for x in items if x["kind"] == "positive"]
        n = [x for x in items if x["kind"] == "negative"]
        module_rows.append(
            f"<tr><td>{esc(name)}</td><td>{len(items)}</td>"
            f"<td>{sum(1 for x in p if x['status'] == 'pass')}/{len(p)}</td>"
            f"<td>{sum(1 for x in n if x['status'] == 'pass')}/{len(n)}</td>"
            f"<td>{sum(1 for x in items if x['status'] == 'fail')}</td></tr>"
        )

    body = f"""
      <section class="panel">
        <h2>Summary</h2>
        <p>From Vitest JUnit at <code>{esc(report['source'])}</code> · {esc(report['finishedAt'])}</p>
        <p>Status: {status_pill(overall)} · One row per <code>it()</code> / <code>test()</code> executed in CI.</p>
        <div class="stats">
          {stat_block("Cases", summary["total"])}{stat_block("Passed", summary["passed"])}
          {stat_block("Failed", summary["failed"])}{stat_block("Skipped", summary["skipped"])}
          {stat_block("Pass rate", rate)}
        </div>
        <nav class="toc">
          <a href="#findings">Failures</a>
          <a href="#screens">Suites</a>
          <a href="../index.html">Suite summary (per file)</a>
          <button type="button" class="linkish" id="expand-all">Expand all</button>
          <button type="button" class="linkish" id="collapse-all">Collapse all</button>
        </nav>
        <p class="filters">
          Show:
          <button type="button" class="chip on" data-filter="all">All</button>
          <button type="button" class="chip" data-filter="positive">Positive</button>
          <button type="button" class="chip" data-filter="negative">Negative</button>
          <button type="button" class="chip" data-filter="fail">Failed</button>
        </p>
      </section>
      <section class="panel">
        <h2>Coverage by module</h2>
        <table class="cases">
          <thead><tr><th>Module</th><th>Cases</th><th>Positive pass</th><th>Negative pass</th><th>Failed</th></tr></thead>
          <tbody>{''.join(module_rows)}</tbody>
        </table>
      </section>
      <section class="panel" id="findings">
        <h2>Failures ({len(failures)})</h2>
        {f'<table class="cases"><thead><tr><th>ID</th><th>Module</th><th>Scenario</th><th>Actual</th></tr></thead><tbody>{fail_rows}</tbody></table>' if failures else fail_rows}
      </section>
      <section id="screens">
        {''.join(cards)}
      </section>
      <script>
        (function () {{
          const cards = () => Array.from(document.querySelectorAll(".screen-card"));
          const setOpen = (open) => cards().forEach((c) => c.classList.toggle("collapsed", !open));
          document.getElementById("expand-all")?.addEventListener("click", () => setOpen(true));
          document.getElementById("collapse-all")?.addEventListener("click", () => setOpen(false));
          document.querySelectorAll(".toggle-one").forEach((btn) => {{
            btn.addEventListener("click", () => btn.closest(".screen-card")?.classList.toggle("collapsed"));
          }});
          document.querySelectorAll("[data-filter]").forEach((btn) => {{
            btn.addEventListener("click", () => {{
              document.querySelectorAll("[data-filter]").forEach((b) => b.classList.remove("on"));
              btn.classList.add("on");
              const mode = btn.getAttribute("data-filter");
              document.querySelectorAll("tr.kind-positive, tr.kind-negative").forEach((row) => {{
                const fail = row.classList.contains("row-fail");
                const pos = row.classList.contains("kind-positive");
                let show = true;
                if (mode === "positive") show = pos;
                if (mode === "negative") show = !pos;
                if (mode === "fail") show = fail;
                row.style.display = show ? "" : "none";
              }});
            }});
          }});
        }})();
      </script>
    """
    return page_shell(
        f"{PROJECT_NAME} frontend unit tests (scenarios)",
        body,
        subtitle="Vitest — every test case with scenario, steps, and pass/fail",
        extra_css=SCENARIO_EXTRA_CSS,
        nested_logo=False,
    )


def copy_logo(out_dir: Path) -> None:
    if LOGO_SRC.is_file():
        (out_dir / "daiva-logo.svg").write_bytes(LOGO_SRC.read_bytes())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="coverage/frontend/junit.xml")
    parser.add_argument("--output-dir", default="artifacts/frontend-unit-scenarios")
    args = parser.parse_args()

    junit_path = Path(args.input)
    if not junit_path.is_file():
        print(f"Missing JUnit input: {junit_path}", file=sys.stderr)
        sys.exit(1)

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    copy_logo(out_dir)

    cases = parse_junit(junit_path)
    report = {
        "finishedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        "source": str(junit_path),
        "summary": summarize(cases),
        "cases": cases,
    }

    (out_dir / "frontend-unit-scenarios-report.json").write_text(
        json.dumps(report, indent=2),
        encoding="utf-8",
    )
    (out_dir / "index.html").write_text(build_html(report), encoding="utf-8")

    sm = report["summary"]
    print(
        f"Frontend unit scenarios: {sm['passed']} passed, {sm['failed']} failed, "
        f"{sm['skipped']} skipped / {sm['total']} — {out_dir / 'index.html'}"
    )


if __name__ == "__main__":
    main()
