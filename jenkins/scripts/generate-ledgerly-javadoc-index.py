#!/usr/bin/env python3
"""Curated Ledgerly JavaDoc index — links into catalog core JavaDoc."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import link_card, page_shell  # noqa: E402

# (relative path under catalog javadoc, title, description)
LEDGERLY_CLASSES: list[tuple[str, str, str]] = [
    (
        "../catalog/com/miniandmore/app/service/LedgerlySyncService.html",
        "LedgerlySyncService",
        "Order sync, customer profile, inventory pull, idempotent retries",
    ),
    (
        "../catalog/com/miniandmore/app/client/LedgerlyIntegrationClient.html",
        "LedgerlyIntegrationClient",
        "HTTP client for Ledgerly commerce API",
    ),
    (
        "../catalog/com/miniandmore/app/service/LedgerlyProductMergeService.html",
        "LedgerlyProductMergeService",
        "Pull and merge products from Ledgerly",
    ),
    (
        "../catalog/com/miniandmore/app/service/LedgerlyWebhookHandlerService.html",
        "LedgerlyWebhookHandlerService",
        "Inbound webhook processing",
    ),
    (
        "../catalog/com/miniandmore/app/service/LedgerlyCategoryResolver.html",
        "LedgerlyCategoryResolver",
        "Category mapping for Ledgerly items",
    ),
    (
        "../catalog/com/miniandmore/app/service/LedgerlyItemImageSync.html",
        "LedgerlyItemImageSync",
        "Product image import from Ledgerly",
    ),
    (
        "../catalog/com/miniandmore/app/service/ProductDescriptionCodec.html",
        "ProductDescriptionCodec",
        "Encode/decode product descriptions for sync",
    ),
    (
        "../catalog/com/miniandmore/app/web/rest/admin/LedgerlyAdminResource.html",
        "LedgerlyAdminResource",
        "Admin REST API — settings, sync, retry",
    ),
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="artifacts/reports-site/backend/javadoc/ledgerly/index.html")
    parser.add_argument("--javadocs-dir", default="artifacts/reports-site/backend/javadoc")
    args = parser.parse_args()

    base = Path(args.javadocs_dir)
    cards = []
    for href, title, description in LEDGERLY_CLASSES:
        rel = href.removeprefix("../")
        if (base / rel).is_file() or (base.parent / rel).is_file():
            cards.append(link_card(href, title, description))
        elif (base / "catalog" / Path(rel).relative_to("catalog")).is_file():
            cards.append(link_card(href, title, description))

    # Fallback: always show catalog index if any catalog javadoc exists
    catalog_index = base / "catalog/index.html"
    if not cards and catalog_index.is_file():
        cards.append(
            link_card(
                "../catalog/index.html",
                "Catalog JavaDoc (full)",
                "Search for Ledgerly* classes in catalog core",
            )
        )

    body = f"""
      <section class="panel">
        <h2>Ledgerly integration JavaDoc</h2>
        <p class="muted">Ledgerly runs in <strong>catalog core</strong> (<code>backend/</code>). Classes below are the main sync and admin integration surface.</p>
        <div class="links">
          {''.join(cards) or '<p class="muted">Generate catalog JavaDoc first: <code>cd backend && ./mvnw -Djacoco.skip=true javadoc:javadoc</code></p>'}
          {link_card('../catalog/index.html', 'Full catalog JavaDoc', 'All catalog entities and REST resources')}
          {link_card('../../index.html', 'Reports home', 'Back to main dashboard')}
        </div>
      </section>
    """
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        page_shell("Ledgerly integration JavaDoc", body, subtitle="Catalog core — Ledgerly commerce API", nested_logo=True),
        encoding="utf-8",
    )
    print(f"Wrote {out}")


if __name__ == "__main__":
    main()
