#!/usr/bin/env python3
"""Single branded reports index with dedicated sections per report type."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path
from urllib.parse import quote

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import PROJECT_NAME, esc, link_card, page_shell  # noqa: E402

SECTIONS: list[tuple[str, str, list[tuple[str, str, str]]]] = [
    (
        "Journey wireframes",
        "Customer, admin, and delivery discussion screens for the call.",
        [
            (
                "./wireframes/index.html",
                "Open wireframes",
                "Shop, checkout, cancel/return/exchange, wrap, book shipment, COD, reverse pickup",
            ),
        ],
    ),
    (
        "Frontend unit tests",
        "Vitest — React storefront test suites.",
        [
            (
                "./frontend/unit-tests/scenarios/index.html",
                "Unit test scenarios",
                "Per-route and per-component accordion — each Vitest case with ID, scenario, and pass/fail",
            ),
            ("./frontend/unit-tests/index.html", "Unit test summary", "Pass/fail totals per Vitest file"),
            (
                "./frontend/unit-tests/scenarios/frontend-unit-scenarios-report.json",
                "Unit scenario results (JSON)",
                "Machine-readable Vitest case results for dashboards",
            ),
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
        "Ledgerly unit tests",
        "Catalog core Surefire suites for Ledgerly sync, merge, and admin APIs.",
        [
            ("./backend/ledgerly-unit-tests/index.html", "Ledgerly unit tests", "Surefire — catalog core"),
        ],
    ),
    (
        "Zoho unit tests",
        "Legacy Zoho Books client and sync unit tests in the catalog service (replaced by Ledgerly).",
        [
            ("./backend/zoho-unit-tests/index.html", "Zoho unit tests", "Surefire — Zoho classes in catalog core"),
        ],
    ),
    (
        "End-to-end test scenarios",
        "Positive and negative scenarios across auth, storefront, account, checkout, cancel/return/exchange, admin, RBAC, webhooks and pages.",
        [
            (
                "./backend/e2e-scenarios/index.html",
                "E2E scenario report",
                "Every scenario with expected vs actual — split into positive and negative, grouped by module",
            ),
            (
                "./backend/e2e-scenarios/e2e-scenarios-report.json",
                "E2E scenario results (JSON)",
                "Machine-readable results for dashboards",
            ),
        ],
    ),
    (
        "Manual UI tests",
        "Positive and negative screen-level cases (smoke, regression, responsive, security) evaluated against the frontend codebase on every reports run.",
        [
            (
                "./manual-ui-tests/index.html",
                "Manual UI test report",
                "Per-screen accordion with Test Case ID, scenario, manual steps, expected result, priority, type, and pass/fail",
            ),
            (
                "./manual-ui-tests/manual-ui-tests-report.json",
                "Manual UI results (JSON)",
                "Machine-readable results for dashboards",
            ),
        ],
    ),
    (
        "API testing",
        "Lifecycle and functional tests against live UAT.",
        [
            ("./backend/api/index.html", "API test dashboard", "Lifecycle runner — all routable endpoints"),
            ("./backend/api/lifecycle-report.html", "Lifecycle HTML report", "Per-endpoint status and response time"),
        ],
    ),
    (
        "API performance",
        "Load tests at 15 / 25 / 35 concurrent users.",
        [
            ("./backend/postman/index.html", "Postman load reports", "Newman storefront load at 15/25/35 users"),
        ],
    ),
    (
        "Guidelines",
        "Mandatory API and database standards for AI agents and developers.",
        [
            ("./guidelines/index.html", "All guidelines", "API, database, frontend, and backend standards"),
            (
                "./guidelines/viewer.html?doc=Api_rules/API_STANDARDS.md",
                "API standards",
                "REST design, auth, security, and production checklist",
            ),
            (
                "./guidelines/viewer.html?doc=Database_rules/DATABASE_STANDARDS.md",
                "Database standards",
                "PostgreSQL schema, migrations, and naming",
            ),
            (
                "./guidelines/viewer.html?doc=Frontend_rules/FRONTEND_STANDARDS.md",
                "Frontend standards",
                "React / TypeScript, accessibility, and performance",
            ),
            (
                "./guidelines/viewer.html?doc=Backend_rules/BACKEND_STANDARDS.md",
                "Backend standards",
                "Service layering, security, and observability",
            ),
        ],
    ),
]

EXTERNAL_SECTIONS: list[tuple[str, str, list[tuple[str, str, str]]]] = [
    (
        "Architecture",
        "Live architecture documentation — https://architecturediagrams.aaushmon.com/",
        [
            (
                "https://architecturediagrams.aaushmon.com/",
                "https://architecturediagrams.aaushmon.com/",
                "Architecture & Technical Documentation — system, frontend, backend, database, API, and CI/CD diagrams",
            ),
            (
                "https://architecturediagrams.aaushmon.com/",
                "Architecture documentation hub",
                "Six interactive diagrams with step-by-step explanations and SVG/PNG downloads",
            ),
            (
                "https://architecturediagrams.aaushmon.com/architecture/system-architecture",
                "System architecture",
                "Nginx, gateway, store/commerce proxies, catalog core, Postgres, Ledgerly, Razorpay",
            ),
            (
                "https://architecturediagrams.aaushmon.com/architecture/frontend-architecture",
                "Frontend architecture",
                "Route groups, components, state management, and typed data layer",
            ),
            (
                "https://architecturediagrams.aaushmon.com/architecture/backend-architecture",
                "Backend architecture",
                "Gateway, store, commerce, and catalog core service boundaries",
            ),
            (
                "https://architecturediagrams.aaushmon.com/architecture/database-er-diagram",
                "Database ER diagram",
                "miniandmore and gateway Postgres schemas",
            ),
            (
                "https://architecturediagrams.aaushmon.com/architecture/api-schema",
                "API resource map",
                "REST surface grouped by owning microservice",
            ),
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
        rel = href.removeprefix("./").split("#", 1)[0].split("?", 1)[0]
        if (out_dir / rel).is_file():
            present.append((href, title, description))
    return present


def section_id(title: str) -> str:
    return title.lower().replace(" ", "-").replace("&", "and")


def quick_access_html(out_dir: Path) -> str:
    """Prominent links — scenario tables live on child pages, not this hub."""
    featured: list[tuple[str, str, str]] = []
    if (out_dir / "frontend/unit-tests/scenarios/index.html").is_file():
        featured.append(
            (
                "./frontend/unit-tests/scenarios/index.html",
                "Frontend unit test scenarios",
                "Vitest — accordion per route/component with test case ID and pass/fail",
            )
        )
    if (out_dir / "manual-ui-tests/index.html").is_file():
        featured.append(
            (
                "./manual-ui-tests/index.html",
                "Manual UI test report",
                "Screen-level positive/negative cases with manual steps (storefront, admin, account)",
            )
        )
    if (out_dir / "backend/e2e-scenarios/index.html").is_file():
        featured.append(
            (
                "./backend/e2e-scenarios/index.html",
                "API E2E scenarios",
                "Live API positive/negative journeys grouped by module",
            )
        )
    if not featured:
        return ""
    cards = "\n".join(link_card(href, title, desc) for href, title, desc in featured)
    return f"""
      <section class="panel panel-highlight" id="scenario-reports">
        <h2>Scenario reports</h2>
        <p class="muted">These open <strong>separate pages</strong> with expandable tables. This dashboard only lists links — scroll to
        <a href="#frontend-unit-tests">Frontend unit tests</a> or <a href="#manual-ui-tests">Manual UI tests</a> for the same entries.</p>
        <div class="links">
          {cards}
        </div>
      </section>
    """


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


def ensure_guidelines(out_dir: Path) -> None:
    src = Path(__file__).resolve().parents[2] / "artifacts" / "guidelines"
    if not any(src.rglob("*.md")):
        return
    script = Path(__file__).resolve().parent / "generate-guidelines-report.py"
    subprocess.run(
        [sys.executable, str(script), "--source", str(src), "--output", str(out_dir / "guidelines")],
        check=False,
    )


def ensure_wireframes(out_dir: Path) -> None:
    src = Path(__file__).resolve().parents[2] / "docs" / "wireframes" / "index.html"
    if not src.is_file():
        return
    dest_dir = out_dir / "wireframes"
    dest_dir.mkdir(parents=True, exist_ok=True)
    html = src.read_text(encoding="utf-8")
    html = html.replace('href="../../report.html"', 'href="../index.html"')
    (dest_dir / "index.html").write_text(html, encoding="utf-8")


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
        os.environ.get("SONAR_REPORT_URL", "https://minisnmore.com/sonar"),
    )
    parser.add_argument("--sonar-url", default=default_sonar)
    args = parser.parse_args()

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    ensure_brand_assets(out_dir)
    ensure_wireframes(out_dir)
    ensure_guidelines(out_dir)
    write_sonar_hub(out_dir, args.sonar_url)

    parts: list[str] = []
    any_section = False
    for title, blurb, links in EXTERNAL_SECTIONS:
        any_section = True
        parts.append(section_html(title, blurb, links))
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
      .panel-highlight {
        border-color: #93c5fd;
        background: linear-gradient(180deg, #eff6ff 0%, #ffffff 120px);
      }
      .panel-highlight .link-card { border-color: #bfdbfe; }
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
    if quick_access_html(out_dir):
        toc_links.append('<a href="#scenario-reports">Scenario reports</a>')
    for title, _, links in EXTERNAL_SECTIONS:
        toc_links.append(f'<a href="#{esc(section_id(title))}">{esc(title)}</a>')
    for title, _, links in SECTIONS:
        if existing_links(links, out_dir):
            toc_links.append(f'<a href="#{esc(section_id(title))}">{esc(title)}</a>')
    toc_html = f'<nav class="toc">{"".join(toc_links)}</nav>' if toc_links else ""

    (out_dir / "index.html").write_text(
        page_shell(
            f"{PROJECT_NAME} Reports",
            toc_html + quick_access_html(out_dir) + index_body,
            subtitle="Unit tests, coverage, JavaDoc, API, Ledgerly, SonarQube, guidelines, and journey wireframes — one dashboard.",
            extra_css=extra_css,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
