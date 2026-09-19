#!/usr/bin/env python3
"""Combined API + load test dashboard for Jenkins artifacts."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import PROJECT_NAME, esc, link_card, page_shell  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="artifacts/api-tests/index.html")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[2]
    lifecycle = root / "artifacts/api-tests/lifecycle-report.json"
    summary_line = "Lifecycle report not found"
    if lifecycle.is_file():
        s = json.loads(lifecycle.read_text()).get("summary") or {}
        summary_line = (
            f"{s.get('requestsRun', '?')} requests · {s.get('pass2xx', '?')} passed · "
            f"{s.get('fail', '?')} failed"
        )

    links = [
        ("lifecycle-report.html", "API lifecycle (all endpoints)", summary_line),
        ("../postman/index.html", "Postman smoke + load", "Smoke, load at 10/25 VUs — all GET endpoints with response time"),
    ]
    link_html = "\n".join(link_card(h, t, d) for h, t, d in links)
    body = f"""
      <section class="panel">
        <h2>API &amp; performance testing</h2>
        <p class="muted">Open an HTML report below — each shows endpoint, status code, and response time.</p>
        <div class="links">{link_html}</div>
      </section>
    """
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page_shell(f"{PROJECT_NAME} API CI", body, nested_logo=True), encoding="utf-8")


if __name__ == "__main__":
    main()
