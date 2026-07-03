#!/usr/bin/env python3
"""Index page linking per-service JaCoCo HTML trees under backend/coverage/."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import link_card, page_shell  # noqa: E402

SERVICE_LINKS: list[tuple[str, str, str]] = [
    ("gateway", "Gateway", "API gateway service"),
    ("catalog", "Catalog", "Catalog core — Ledgerly sync, products, orders"),
    ("store", "Store", "Store edge service"),
    ("commerce", "Commerce", "Commerce edge service"),
    ("ledgerly", "Ledgerly (catalog)", "Ledgerly integration packages in catalog core"),
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True, help="backend/coverage directory")
    args = parser.parse_args()

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    cards = []
    for subdir, title, description in SERVICE_LINKS:
        index = out_dir / subdir / "index.html"
        if index.is_file():
            cards.append(link_card(f"./{subdir}/index.html", title, description))

    body = f"""
      <section class="panel">
        <h2>Backend services</h2>
        <p class="muted">JaCoCo package and class drill-down per microservice.</p>
        <div class="links">
          {''.join(cards) or '<p class="muted">No JaCoCo reports were packaged for this build.</p>'}
        </div>
      </section>
      <section class="panel">
        <h2>Summary</h2>
        <div class="links">
          {link_card('../coverage-summary/index.html', 'Backend coverage summary', 'Combined line and branch coverage across all services')}
        </div>
      </section>
    """

    (out_dir / "index.html").write_text(
        page_shell(
            "Backend coverage",
            body,
            subtitle="Per-service JaCoCo browsers",
            nested_logo=True,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
