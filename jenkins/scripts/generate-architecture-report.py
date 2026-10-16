#!/usr/bin/env python3
"""Build branded architecture section for the reports site (Mermaid + markdown)."""

from __future__ import annotations

import argparse
import re
import shutil
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import PROJECT_NAME, esc, link_card, page_shell  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]

# (output filename, source path relative to repo root, card title, blurb)
ARCHITECTURE_PAGES: list[tuple[str, str, str, str]] = [
    (
        "production-topology.md",
        "docs/handover/architecture-diagram.md",
        "Production topology",
        "Live minisnmore.com — Nginx, PM2 ports 9180–9184, webhooks, Postgres, R2",
    ),
    (
        "system-architecture.md",
        "docs/system-architecture.md",
        "System architecture",
        "Services, request flow, databases, Ledgerly / Razorpay, dev vs production",
    ),
    (
        "frontend-architecture.md",
        "docs/frontend-architecture.md",
        "Frontend architecture",
        "TanStack Start routes, React Query, lib/ API layer, Vite proxy",
    ),
    (
        "backend-architecture.md",
        "docs/backend-architecture.md",
        "Backend architecture",
        "Gateway, store, commerce, catalog core — auth, proxies, schedulers",
    ),
    (
        "database-er-diagram.md",
        "docs/database-er-diagram.md",
        "Database ER diagram",
        "miniandmore and gateway Postgres schemas (Mermaid ER)",
    ),
    (
        "api-schema.md",
        "docs/api-schema.md",
        "API resource map",
        "REST surface by owning service and auth rules",
    ),
    (
        "architecture-ci-cd.md",
        "docs/architecture-ci-cd.md",
        "CI/CD and reports",
        "Deploy path to production and Jenkins reports pipeline",
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
  .doc-body code {
    background: var(--surface-muted);
    padding: 0.12rem 0.35rem;
    border-radius: 6px;
    font-size: 0.9em;
  }
  .doc-body table { margin: 1rem 0; width: 100%; border-collapse: collapse; font-size: 0.92rem; }
  .doc-body th, .doc-body td { border-bottom: 1px solid var(--border); padding: 8px 10px; text-align: left; vertical-align: top; }
  .doc-body hr { border: none; border-top: 1px solid var(--border); margin: 1.5rem 0; }
  .doc-body ul, .doc-body ol { padding-left: 1.4rem; }
  .doc-body blockquote {
    margin: 1rem 0;
    padding: 0.75rem 1rem;
    border-left: 4px solid var(--brand-light);
    background: var(--surface-muted);
    border-radius: 0 10px 10px 0;
  }
  .mermaid-wrap {
    margin: 1.25rem 0;
    padding: 1rem;
    background: #fff;
    border: 1px solid var(--border);
    border-radius: 12px;
    overflow-x: auto;
  }
  .mermaid-wrap .mermaid { min-height: 80px; }
  .diagram-fallback { color: var(--text-muted); font-size: 0.9rem; }
  .loading { color: var(--text-muted); }
"""


def rewrite_doc_links(md: str) -> str:
    """Turn relative docs/ links into viewer URLs where possible."""

    def repl(match: re.Match[str]) -> str:
        text, target = match.group(1), match.group(2)
        if target.startswith("http://") or target.startswith("https://"):
            return match.group(0)
        clean = target.split("#", 1)[0]
        if clean.startswith("./"):
            clean = clean[2:]
        if clean.startswith("../"):
            clean = clean[3:]
        if clean.startswith("docs/"):
            clean = clean[5:]
        if not clean.endswith(".md"):
            return match.group(0)
        base = Path(clean).name
        for out_name, src_rel, _, _ in ARCHITECTURE_PAGES:
            if Path(src_rel).name == base or out_name == base:
                return f"[{text}](viewer.html?doc={out_name})"
        return match.group(0)

    return re.sub(r"\[([^\]]+)\]\(([^)]+)\)", repl, md)


def copy_sources(dest: Path) -> int:
    count = 0
    for out_name, src_rel, _, _ in ARCHITECTURE_PAGES:
        src = ROOT / src_rel
        if not src.is_file():
            continue
        text = src.read_text(encoding="utf-8")
        text = rewrite_doc_links(text)
        (dest / out_name).write_text(text, encoding="utf-8")
        count += 1
    return count


def write_index(dest: Path) -> None:
    cards = "\n".join(
        link_card(f"viewer.html?doc={esc(out_name)}", title, blurb)
        for out_name, _, title, blurb in ARCHITECTURE_PAGES
        if (dest / out_name).is_file()
    )
    body = f"""
      <p class="back"><a href="../index.html">← Reports hub</a></p>
      <section class="panel panel-highlight">
        <h2>Architecture &amp; technical documentation</h2>
        <p class="muted">Source of truth: <code>docs/</code> in the repo. Diagrams render interactively (Mermaid). Use the hub below or open
        <strong>Production topology</strong> first for the live minisnmore.com layout.</p>
      </section>
      <section class="panel">
        <div class="links">{cards}</div>
      </section>
    """
    extra = """
      .panel-highlight {
        border-color: #93c5fd;
        background: linear-gradient(180deg, #eff6ff 0%, #ffffff 120px);
      }
      .back { margin: 0 0 12px; font-size: 0.92rem; }
    """
    (dest / "index.html").write_text(
        page_shell(
            "Architecture",
            body,
            subtitle=f"{PROJECT_NAME} — system, frontend, backend, database, API, and CI/CD diagrams.",
            extra_css=extra,
            nested_logo=True,
        ),
        encoding="utf-8",
    )


def write_viewer(dest: Path) -> None:
    body = """
      <div class="doc-nav">
        <a href="../index.html">← Reports hub</a>
        <a href="index.html">← Architecture hub</a>
      </div>
      <section class="panel">
        <p id="doc-title" class="muted loading">Loading…</p>
        <div id="doc-content" class="doc-body"></div>
      </section>
      <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
      <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
      <script>
        (function () {
          var params = new URLSearchParams(location.search);
          var doc = params.get("doc");
          var titleEl = document.getElementById("doc-title");
          var contentEl = document.getElementById("doc-content");

          function escapeHtml(s) {
            return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
          }

          function renderMarkdown(md) {
            var re = /```(mermaid)\\n([\\s\\S]*?)```/g;
            var last = 0;
            var m;
            var parts = [];
            while ((m = re.exec(md)) !== null) {
              if (m.index > last) {
                parts.push({ type: "md", text: md.slice(last, m.index) });
              }
              parts.push({ type: "mermaid", text: m[1].trim() });
              last = m.index + m[0].length;
            }
            if (last < md.length) {
              parts.push({ type: "md", text: md.slice(last) });
            }
            var html = "";
            parts.forEach(function (part, i) {
              if (part.type === "md") {
                html += marked.parse(part.text);
              } else {
                var id = "mermaid-" + i;
                html += '<div class="mermaid-wrap"><pre class="diagram-fallback">Diagram loading…</pre>'
                  + '<div class="mermaid" id="' + id + '">' + escapeHtml(part.text) + "</div></div>";
              }
            });
            return html;
          }

          if (!doc || doc.indexOf("..") !== -1 || doc.indexOf("/") !== -1) {
            titleEl.textContent = "Invalid document path";
            return;
          }
          document.title = doc.replace(/\\.md$/, "") + " · Architecture";
          titleEl.textContent = doc;
          fetch(doc)
            .then(function (r) {
              if (!r.ok) throw new Error("Not found");
              return r.text();
            })
            .then(function (md) {
              titleEl.textContent = doc.replace(/_/g, " ").replace(/\\.md$/, "");
              contentEl.innerHTML = renderMarkdown(md);
              mermaid.initialize({
                startOnLoad: false,
                theme: "base",
                themeVariables: {
                  primaryColor: "#dbeafe",
                  primaryTextColor: "#0f172a",
                  primaryBorderColor: "#3b82f6",
                  lineColor: "#64748b",
                  secondaryColor: "#f1f5f9",
                  tertiaryColor: "#fff"
                },
                securityLevel: "loose",
                flowchart: { curve: "basis", padding: 16 }
              });
              return mermaid.run({ querySelector: ".mermaid" });
            })
            .then(function () {
              document.querySelectorAll(".diagram-fallback").forEach(function (el) {
                el.remove();
              });
            })
            .catch(function () {
              titleEl.textContent = "Document not found";
              contentEl.innerHTML = "<p class=\\"muted\\">Could not load <code>" + doc + "</code>.</p>";
            });
        })();
      </script>
    """
    html = page_shell(
        "Architecture",
        body,
        subtitle="Interactive diagrams and technical reference.",
        extra_css=VIEWER_CSS,
        nested_logo=True,
    )
    (dest / "viewer.html").write_text(html, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="artifacts/reports-site/architecture")
    args = parser.parse_args()

    dest = Path(args.output)
    if not dest.is_absolute():
        dest = ROOT / dest
    dest.mkdir(parents=True, exist_ok=True)

    logo_src = Path(__file__).resolve().parents[1] / "assets" / "daiva-technologies-logo.svg"
    if logo_src.is_file():
        shutil.copy2(logo_src, dest.parent / "daiva-logo.svg")

    copied = copy_sources(dest)
    write_index(dest)
    write_viewer(dest)
    print(f"Architecture: {copied} documents → {dest}")


if __name__ == "__main__":
    main()
