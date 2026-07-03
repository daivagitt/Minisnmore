#!/usr/bin/env python3
"""Single backend JavaDoc index — all microservices in one place."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import PROJECT_NAME, link_card, page_shell  # noqa: E402

MODULES = [
    ("catalog", "Catalog"),
    ("gateway", "Gateway"),
    ("store", "Store"),
    ("commerce", "Commerce"),
    ("ledgerly", "Ledgerly integration"),
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="artifacts/reports-site/backend/javadoc/index.html")
    parser.add_argument("--javadocs-dir", default="artifacts/reports-site/backend/javadoc")
    args = parser.parse_args()

    base = Path(args.javadocs_dir)
    links: list[tuple[str, str, str]] = []

    overview = base / "overview.html"
    if overview.is_file():
        links.append(("./overview.html", "Catalog API overview", "Single-page REST reference"))

    for slug, title in MODULES:
        index = base / slug / "index.html"
        if index.is_file():
            links.append((f"./{slug}/index.html", title, f"Searchable JavaDoc — {title}"))

    link_html = "\n".join(link_card(href, title, desc) for href, title, desc in links)
    body = f"""
      <section class="panel">
        <h2>Backend JavaDoc</h2>
        <p class="muted">Gateway, catalog, store, commerce, and Ledgerly integration (catalog core).</p>
        <div class="links">
          {link_html or '<p class="muted">No JavaDoc modules were generated in this build.</p>'}
          {link_card('../../index.html', 'Reports home', 'Back to main dashboard')}
        </div>
      </section>
    """
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        page_shell(
            f"{PROJECT_NAME} Backend JavaDoc",
            body,
            subtitle="Merged microservice API documentation",
            nested_logo=True,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
