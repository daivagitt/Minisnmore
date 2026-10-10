#!/usr/bin/env python3
"""Single-page REST overview from OpenAPI JSON (catalog JavaDoc overview)."""

from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from report_theme import PROJECT_NAME, esc, page_shell, stat_block  # noqa: E402

METHODS = ("get", "post", "put", "patch", "delete", "head", "options")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="artifacts/api-docs/openapi.json")
    parser.add_argument("--output", default="artifacts/javadocs/overview.html")
    args = parser.parse_args()

    spec = json.loads(Path(args.input).read_text(encoding="utf-8"))
    paths = spec.get("paths") or {}
    grouped: dict[str, list[tuple[str, str, str]]] = defaultdict(list)
    op_count = 0
    for path, item in sorted(paths.items()):
        if not isinstance(item, dict):
            continue
        for method in METHODS:
            op = item.get(method)
            if not isinstance(op, dict):
                continue
            op_count += 1
            tags = op.get("tags") or ["general"]
            summary = op.get("summary") or op.get("operationId") or ""
            grouped[str(tags[0])].append((method.upper(), path, str(summary)))

    extra_css = """
      .op-table { width:100%; border-collapse:collapse; font-size:0.92rem; }
      .op-table th, .op-table td { text-align:left; padding:8px 10px; border-bottom:1px solid var(--border); vertical-align:top; }
      .op-table th { background: var(--surface-muted); }
      .method { font-weight:700; font-family: ui-monospace, monospace; }
      code { font-size: 0.88rem; }
    """
    sections = []
    for tag in sorted(grouped):
        rows = "".join(
            f"<tr><td class=\"method\">{esc(m)}</td><td><code>{esc(p)}</code></td><td>{esc(s)}</td></tr>"
            for m, p, s in grouped[tag]
        )
        sections.append(
            f"""
      <section class="panel" id="{esc(tag)}">
        <h2>{esc(tag)}</h2>
        <table class="op-table"><thead><tr><th>Method</th><th>Path</th><th>Summary</th></tr></thead>
        <tbody>{rows}</tbody></table>
      </section>
            """
        )

    body = f"""
      <section class="panel">
        <h2>Catalog REST surface</h2>
        <p class="muted">Generated from the live OpenAPI spec (<code>v3/api-docs/catalog</code>).</p>
        <div class="stats">
          {stat_block(str(len(paths)), "Paths")}
          {stat_block(str(op_count), "Operations")}
          {stat_block(str(len(grouped)), "Tags")}
        </div>
      </section>
      {''.join(sections)}
    """
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        page_shell(
            f"{PROJECT_NAME} JavaDoc Overview",
            body,
            subtitle="Single-page REST reference for catalog endpoints",
            nested_logo=True,
            extra_css=extra_css,
        ),
        encoding="utf-8",
    )
    print(f"Wrote {out} ({len(paths)} paths, {op_count} operations)")


if __name__ == "__main__":
    main()
