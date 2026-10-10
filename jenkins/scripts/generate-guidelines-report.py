#!/usr/bin/env python3
"""Brand the Guidelines module (API / database / frontend / backend standards)."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import PROJECT_NAME, esc, link_card, page_shell  # noqa: E402

DOC_GROUPS: list[tuple[str, str, list[tuple[str, str, str]]]] = [
    (
        "API",
        "REST design, auth, security, and production checklist.",
        [
            ("Api_rules/API_STANDARDS.md", "API standards", "Mandatory REST contract, versioning, and error shape"),
            ("Api_rules/AUTH_STANDARDS.md", "Auth standards", "JWT, headers, and RBAC rules"),
            ("Api_rules/SECURITY_HARDENING.md", "Security hardening", "Transport, input validation, and secrets"),
            ("Api_rules/PERFORMANCE_SLA.md", "Performance SLA", "Latency budgets for API responses"),
        ],
    ),
    (
        "Database",
        "PostgreSQL schema, migrations, indexing, and naming.",
        [
            ("Database_rules/DATABASE_STANDARDS.md", "Database standards", "Schema design and non-negotiable integrity rules"),
            ("Database_rules/MIGRATION_STANDARDS.md", "Migration standards", "Versioned, reversible schema changes"),
            ("Database_rules/INDEXING_STANDARDS.md", "Indexing standards", "Hot-path indexes and query performance"),
            ("Database_rules/NAMING_CONVENTIONS.md", "Naming conventions", "Tables, columns, and constraint names"),
        ],
    ),
    (
        "Frontend",
        "React / TypeScript storefront standards.",
        [
            ("Frontend_rules/FRONTEND_STANDARDS.md", "Frontend standards", "Type safety, accessibility, and performance budgets"),
        ],
    ),
    (
        "Backend",
        "Service layering, security, and observability.",
        [
            ("Backend_rules/BACKEND_STANDARDS.md", "Backend standards", "Clean layers, fail-safe defaults, and ops"),
        ],
    ),
]

VIEWER_CSS = """
  .back { margin: 0 0 12px; font-size: 0.92rem; }
  .doc-nav { margin: 0 0 12px; display: flex; gap: 1rem; flex-wrap: wrap; font-size: 0.92rem; }
  .doc-body { line-height: 1.65; font-size: 0.96rem; }
  .doc-body h1, .doc-body h2, .doc-body h3 { margin-top: 1.4rem; margin-bottom: 0.6rem; color: var(--text); }
  .doc-body h1 { font-size: 1.5rem; border-bottom: 1px solid var(--border); padding-bottom: 0.4rem; }
  .doc-body h2 { font-size: 1.2rem; color: var(--brand); }
  .doc-body h3 { font-size: 1.05rem; }
  .doc-body pre {
    background: var(--surface-muted);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 1rem;
    overflow-x: auto;
  }
  .doc-body pre code { background: none; padding: 0; color: var(--text); }
  .doc-body table { margin: 1rem 0; width: 100%; border-collapse: collapse; }
  .doc-body th, .doc-body td { border-bottom: 1px solid var(--border); padding: 8px 10px; text-align: left; }
  .doc-body hr { border: none; border-top: 1px solid var(--border); margin: 1.5rem 0; }
  .doc-body ul, .doc-body ol { padding-left: 1.4rem; }
  .loading { color: var(--text-muted); }
"""


def copy_markdown(src: Path, dest: Path) -> int:
    count = 0
    src = src.resolve()
    dest = dest.resolve()
    for md in src.rglob("*.md"):
        rel = md.relative_to(src)
        out = dest / rel
        out.parent.mkdir(parents=True, exist_ok=True)
        if md.resolve() != out.resolve():
            shutil.copy2(md, out)
        count += 1
    return count


def write_index(dest: Path) -> None:
    groups = []
    for title, blurb, docs in DOC_GROUPS:
        cards = "\n".join(link_card(f"viewer.html?doc={esc(path)}", name, desc) for path, name, desc in docs)
        groups.append(
            f"""
      <section class="panel">
        <h2>{esc(title)}</h2>
        <p class="muted">{esc(blurb)}</p>
        <div class="links">{cards}</div>
      </section>
            """
        )
    body = f"""
      <p class="back"><a href="../index.html">← Reports hub</a></p>
      {"".join(groups)}
    """
    (dest / "index.html").write_text(
        page_shell(
            "Guidelines",
            body,
            subtitle="Mandatory API, database, frontend, and backend standards for AI agents and developers.",
            extra_css=" .back { margin: 0 0 12px; font-size: 0.92rem; } ",
            nested_logo=True,
        ),
        encoding="utf-8",
    )


def write_viewer(dest: Path) -> None:
    body = """
      <div class="doc-nav">
        <a href="../index.html">← Reports hub</a>
        <a href="index.html">← Guidelines</a>
      </div>
      <section class="panel">
        <p id="doc-title" class="muted loading">Loading…</p>
        <div id="doc-content" class="doc-body"></div>
      </section>
      <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
      <script>
        (function () {
          var params = new URLSearchParams(location.search);
          var doc = params.get("doc");
          var titleEl = document.getElementById("doc-title");
          var contentEl = document.getElementById("doc-content");
          if (!doc || doc.indexOf("..") !== -1) {
            titleEl.textContent = "Invalid document path";
            return;
          }
          document.title = doc.split("/").pop().replace(/\\.md$/, "") + " · Guidelines";
          titleEl.textContent = doc;
          fetch(doc)
            .then(function (r) {
              if (!r.ok) throw new Error("Not found");
              return r.text();
            })
            .then(function (md) {
              titleEl.textContent = doc.split("/").pop().replace(/_/g, " ").replace(/\\.md$/, "");
              contentEl.innerHTML = marked.parse(md);
            })
            .catch(function () {
              titleEl.textContent = "Document not found";
              contentEl.innerHTML = "<p class=\\"muted\\">Could not load <code>" + doc + "</code>.</p>";
            });
        })();
      </script>
    """
    html = page_shell(
        "Guidelines",
        body,
        subtitle="Mandatory standards for AI agents and developers.",
        extra_css=VIEWER_CSS,
        nested_logo=True,
    )
    (dest / "viewer.html").write_text(html, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default="artifacts/guidelines")
    parser.add_argument("--output", default="artifacts/guidelines")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[2]
    src = Path(args.source)
    dest = Path(args.output)
    if not src.is_absolute():
        src = root / src
    if not dest.is_absolute():
        dest = root / dest
    dest.mkdir(parents=True, exist_ok=True)

    copied = copy_markdown(src, dest)
    write_index(dest)
    write_viewer(dest)
    print(f"Guidelines: {copied} markdown files → {dest}")


if __name__ == "__main__":
    main()
