#!/usr/bin/env python3
"""Single branded reports index with dedicated sections per report type."""

from __future__ import annotations

import argparse
import os
import shutil
import sys
from pathlib import Path
from urllib.parse import quote

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import PROJECT_NAME, esc, link_card, page_shell  # noqa: E402

SECTIONS: list[tuple[str, str, list[tuple[str, str, str]]]] = [
    (
        "Frontend unit tests",
        "Vitest — React storefront test suites.",
        [
            ("./frontend/unit-tests/index.html", "Unit test report", "All Vitest suites with pass/fail per file"),
        ],
    ),
    (
        "Frontend code coverage",
        "Line and branch coverage from Vitest (v8).",
        [
            ("./frontend/coverage-summary/index.html", "Coverage summary", "Combined percentages across scoped files"),
            ("./frontend/coverage/index.html", "Coverage browser", "File-level drill-down"),
        ],
    ),
    (
        "Backend unit tests",
        "Maven Surefire — gateway, catalog, store, and commerce.",
        [
            ("./backend/unit-tests/index.html", "All backend unit tests", "Combined Surefire report per microservice"),
        ],
    ),
    (
        "Backend code coverage",
        "JaCoCo — combined and per-service drill-down.",
        [
            ("./backend/coverage-summary/index.html", "Coverage summary", "Merged line coverage across all services"),
            ("./backend/coverage/index.html", "Coverage browser", "Gateway, catalog, store, commerce"),
        ],
    ),
    (
        "Backend JavaDoc",
        "Generated API documentation for all Java microservices.",
        [
            ("./backend/javadoc/index.html", "JavaDoc hub", "Gateway, catalog, store, commerce, and Ledgerly"),
            ("./backend/javadoc/overview.html", "Catalog API overview", "Single-page REST reference for catalog endpoints"),
            ("./backend/javadoc/gateway/index.html", "Gateway JavaDoc", "Routing, security, and gateway REST layer"),
            ("./backend/javadoc/catalog/index.html", "Catalog JavaDoc", "Core catalog service — entities and resources"),
            ("./backend/javadoc/store/index.html", "Store JavaDoc", "Store service API documentation"),
            ("./backend/javadoc/commerce/index.html", "Commerce JavaDoc", "Commerce service API documentation"),
            ("./backend/javadoc/ledgerly/index.html", "Ledgerly integration JavaDoc", "Sync service and Ledgerly commerce API"),
        ],
    ),
    (
        "Postman & OpenAPI",
        "Collections and machine-readable API specification.",
        [
            ("./backend/postman/index.html", "Postman / Newman reports", "Smoke and load test HTML output"),
            ("./backend/swagger/postman-collection.json", "Postman collection (JSON)", "Module-wise requests — import into Postman"),
            ("./backend/swagger/openapi.json", "OpenAPI specification (JSON)", "Machine-readable spec"),
        ],
    ),
    (
        "Swagger UI",
        "Interactive API explorer.",
        [
            ("./backend/swagger/index.html", "Swagger UI", "Try endpoints against UAT from the browser"),
        ],
    ),
    (
        "SonarQube",
        "Frontend (Vitest-scoped) + single backend project (all Java services combined). No Sonar login required.",
        [
            ("./sonar-summary/index.html", "SonarQube summary", "Full metrics, quality gates, and open issues — self-contained"),
        ],
    ),
    (
        "Live Ledgerly integration tests",
        "Real UAT calls to Ledgerly (connection, sync, pull inventory, order retry).",
        [
            ("./backend/ledgerly-live/index.html", "Live API test report", "Admin JWT + catalog core → Ledgerly commerce API"),
            ("./backend/ledgerly-unit-tests/index.html", "Ledgerly unit tests", "Surefire — catalog core (50+ tests)"),
            (
                "./backend/ledgerly-scenario-integration/index.html",
                "Scenario integration tests",
                "Coupon checkout, Ledgerly sync, inventory pull, shipment tracking (UAT flows)",
            ),
        ],
    ),
    (
        "Live Zoho integration tests",
        "Legacy Zoho Books integration (deprecated — replaced by Ledgerly).",
        [
            ("./backend/zoho-live/index.html", "Live API test report", "Admin JWT + legacy integration service → Zoho Books"),
            ("./backend/zoho-unit-tests/index.html", "Zoho unit tests", "Surefire — legacy integration module"),
            ("./backend/coverage/integration/index.html", "Zoho JaCoCo coverage", "Integration service only"),
        ],
    ),
    (
        "API testing",
        "Lifecycle and functional tests against live UAT.",
        [
            ("./backend/api/index.html", "API test dashboard", "Lifecycle runner — all routable endpoints"),
            ("./backend/api/lifecycle-report.html", "Lifecycle HTML report", "Per-endpoint status and response time"),
            ("./backend/scenario-integration/index.html", "Scenario integration tests", "Legacy Zoho + general UAT flows"),
        ],
    ),
    (
        "API performance",
        "Load tests at 15 / 25 / 35 concurrent users.",
        [
            ("./backend/postman/index.html", "Postman load reports", "Newman storefront load at 15/25/35 users"),
        ],
    ),
]

SONAR_PROJECTS: list[tuple[str, str, str]] = [
    ("frontend", "Frontend", "React storefront — Vitest coverage"),
    ("miniandmore", "Catalog (backend)", "JHipster catalog core service"),
    ("gateway", "Gateway", "API gateway and routing"),
]


def existing_links(links: list[tuple[str, str, str]], out_dir: Path) -> list[tuple[str, str, str]]:
    present: list[tuple[str, str, str]] = []
    for href, title, description in links:
        rel = href.removeprefix("./")
        if (out_dir / rel).is_file():
            present.append((href, title, description))
    return present


def section_id(title: str) -> str:
    return title.lower().replace(" ", "-").replace("&", "and")


def section_html(title: str, blurb: str, links: list[tuple[str, str, str]]) -> str:
    if not links:
        return ""
    cards = "\n".join(link_card(href, t, d) for href, t, d in links)
    return f"""
      <section class="panel" id="{esc(section_id(title))}">
        <h2>{esc(title)}</h2>
        <p class="muted">{esc(blurb)}</p>
        <div class="links">
          {cards}
        </div>
      </section>
    """


def ensure_brand_assets(out_dir: Path) -> None:
    logo_src = Path(__file__).resolve().parents[1] / "assets" / "daiva-technologies-logo.svg"
    if logo_src.is_file():
        shutil.copy2(logo_src, out_dir / "daiva-logo.svg")


def sonar_dashboard_url(base_url: str, project_key: str) -> str:
    return f"{base_url.rstrip('/')}/dashboard?id={quote(project_key, safe='')}"


def write_sonar_hub(out_dir: Path, sonar_url: str) -> None:
    cards = "\n".join(
        link_card(sonar_dashboard_url(sonar_url, key), title, description)
        for key, title, description in SONAR_PROJECTS
    )
    body = f"""
      <section class="panel">
        <h2>SonarQube projects</h2>
        <p class="muted">Live server: <a href="{esc(sonar_url)}">{esc(sonar_url)}</a></p>
        <div class="links">{cards}</div>
      </section>
      <section class="panel">
        <div class="links">
          {link_card("./sonar-summary/index.html", "Overall summary", "Static metrics table")}
          {link_card("./index.html", "Reports home", "Back to dashboard")}
        </div>
      </section>
    """
    (out_dir / "sonar.html").write_text(
        page_shell("SonarQube", body, subtitle="Code quality — all modules.", nested_logo=True),
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    default_sonar = os.environ.get(
        "SONAR_PUBLIC_URL",
        os.environ.get("SONAR_REPORT_URL", "https://uat.minisnmore.com/sonar"),
    )
    parser.add_argument("--sonar-url", default=default_sonar)
    args = parser.parse_args()

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    ensure_brand_assets(out_dir)
    write_sonar_hub(out_dir, args.sonar_url)

    parts: list[str] = []
    any_section = False
    for title, blurb, links in SECTIONS:
        present = existing_links(links, out_dir)
        if present:
            any_section = True
        parts.append(section_html(title, blurb, present))

    index_body = "".join(parts)
    if not any_section:
        index_body += """
      <section class="panel">
        <p class="muted">No report artifacts found. Run
        <code>bash jenkins/scripts/publish-ledgerly-reports.sh</code></p>
      </section>
    """

    extra_css = """
      .content .panel { scroll-margin-top: 24px; }
      .toc { display:flex; flex-wrap:wrap; gap:8px; margin:0 0 20px; }
      .toc a {
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: 999px;
        padding: 6px 14px;
        font-size: 0.9rem;
        font-weight: 600;
      }
    """
    toc_links = []
    for title, _, links in SECTIONS:
        if existing_links(links, out_dir):
            toc_links.append(f'<a href="#{esc(section_id(title))}">{esc(title)}</a>')
    toc_html = f'<nav class="toc">{"".join(toc_links)}</nav>' if toc_links else ""

    (out_dir / "index.html").write_text(
        page_shell(
            f"{PROJECT_NAME} Reports",
            toc_html + index_body,
            subtitle="Unit tests, coverage, JavaDoc, API, Ledgerly, and SonarQube — one dashboard.",
            extra_css=extra_css,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
