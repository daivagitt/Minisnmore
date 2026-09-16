"""Shared HTML theme for Jenkins-hosted static reports."""

from __future__ import annotations

import html
import os
from datetime import datetime, timezone
from typing import Optional

BRAND_NAME = "Daiva Technologies"
PROJECT_NAME = "Mini's n More"
DEFAULT_LOGO_FILENAME = "daiva-logo.svg"
# Stamp shown on report pages. Override with REPORT_GENERATED_AT=YYYY-MM-DD.
DEFAULT_REPORT_GENERATED_AT = "2026-09-08"


def brand_logo_url(*, nested: bool = False) -> str:
    """Resolve logo URL: env override, then path relative to the HTML page."""
    override = os.environ.get("REPORT_BRAND_LOGO_URL", "").strip()
    if override:
        return override
    prefix = "../" if nested else "./"
    return prefix + DEFAULT_LOGO_FILENAME


def brand_logo_markup(*, nested: bool = False) -> str:
    src = esc(brand_logo_url(nested=nested))
    return (
        f'<img class="brand-logo" src="{src}" alt="{esc(BRAND_NAME)} logo" '
        f'width="240" height="48" decoding="async" />'
    )


def esc(value: object) -> str:
    return html.escape(str(value or ""))


def release_id() -> str:
    return os.environ.get("RELEASE_ID", "local")


def generated_at() -> datetime:
    raw = os.environ.get("REPORT_GENERATED_AT", DEFAULT_REPORT_GENERATED_AT).strip() or DEFAULT_REPORT_GENERATED_AT
    try:
        if len(raw) >= 10 and raw[4] == "-" and raw[7] == "-":
            dt = datetime.strptime(raw[:10], "%Y-%m-%d")
            return dt.replace(tzinfo=timezone.utc)
        return datetime.fromisoformat(raw.replace("Z", "+00:00"))
    except ValueError:
        return datetime.strptime(DEFAULT_REPORT_GENERATED_AT, "%Y-%m-%d").replace(tzinfo=timezone.utc)


def generated_at_label() -> str:
    return generated_at().strftime("%d %B %Y").lstrip("0")


def generated_at_chip() -> str:
    """Header/footer stamp matching the published UAT reports dashboard."""
    override = os.environ.get("REPORT_GENERATED_LABEL", "").strip()
    if override:
        return override
    return "8 Sep 2026, 11:46 AM IST (06:16 UTC)"


def generated_at_iso() -> str:
    return generated_at().strftime("%Y-%m-%dT%H:%M:%S+00:00")


def generated_at_utc_label() -> str:
    return generated_at().strftime("%Y-%m-%d 00:00 UTC")


def base_css() -> str:
    return """
      :root {
        --brand: #1e3a8a;
        --brand-light: #3b82f6;
        --brand-accent: #0ea5e9;
        --surface: #ffffff;
        --surface-muted: #f8fafc;
        --border: #e2e8f0;
        --text: #0f172a;
        --text-muted: #64748b;
        --pass: #15803d;
        --fail: #b91c1c;
        --shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
        --radius: 14px;
      }
      * { box-sizing: border-box; }
      body {
        margin: 0;
        font-family: "Segoe UI", system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
        line-height: 1.55;
        color: var(--text);
        background: linear-gradient(180deg, #eff6ff 0%, #f8fafc 220px, #f1f5f9 100%);
        min-height: 100vh;
      }
      .page { max-width: 1100px; margin: 0 auto; padding: 28px 20px 48px; }
      .brand-bar {
        background: linear-gradient(135deg, var(--brand) 0%, var(--brand-light) 55%, var(--brand-accent) 100%);
        color: #fff;
        border-radius: calc(var(--radius) + 4px);
        padding: 28px 30px;
        box-shadow: var(--shadow);
      }
      .brand-top {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 18px;
        margin-bottom: 16px;
      }
      .brand-logo {
        display: block;
        height: 48px;
        width: auto;
        max-width: min(100%, 260px);
      }
      .brand-bar .eyebrow {
        margin: 0 0 6px;
        font-size: 12px;
        letter-spacing: 0.14em;
        text-transform: uppercase;
        opacity: 0.9;
        font-weight: 700;
      }
      .brand-bar h1 { margin: 0 0 8px; font-size: 1.85rem; font-weight: 700; }
      .brand-bar p { margin: 0; opacity: 0.95; }
      .meta {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        margin-top: 18px;
      }
      .meta-chip {
        background: rgba(255, 255, 255, 0.16);
        border: 1px solid rgba(255, 255, 255, 0.22);
        border-radius: 999px;
        padding: 6px 12px;
        font-size: 0.9rem;
      }
      .content { margin-top: 24px; }
      .panel {
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius);
        padding: 22px 24px;
        box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
        margin-bottom: 18px;
      }
      .panel h2 { margin: 0 0 12px; font-size: 1.2rem; }
      .status {
        display: inline-block;
        padding: 5px 12px;
        border-radius: 999px;
        color: #fff;
        font-weight: 700;
        font-size: 0.85rem;
        letter-spacing: 0.04em;
      }
      .status.pass { background: var(--pass); }
      .status.fail { background: var(--fail); }
      table {
        width: 100%;
        border-collapse: collapse;
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius);
        overflow: hidden;
      }
      th, td {
        border-bottom: 1px solid var(--border);
        padding: 10px 12px;
        text-align: left;
        vertical-align: top;
      }
      th { background: var(--surface-muted); font-size: 0.92rem; }
      tr:last-child td { border-bottom: none; }
      code {
        background: #eef2ff;
        color: #312e81;
        padding: 2px 7px;
        border-radius: 6px;
        font-size: 0.92em;
      }
      a { color: var(--brand-light); text-decoration: none; font-weight: 600; }
      a:hover { text-decoration: underline; }
      .links { display: grid; gap: 12px; }
      .link-card {
        display: block;
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius);
        padding: 16px 18px;
        transition: border-color 0.15s ease, box-shadow 0.15s ease;
      }
      .link-card:hover {
        border-color: #93c5fd;
        box-shadow: var(--shadow);
        text-decoration: none;
      }
      .link-card strong { display: block; font-size: 1.05rem; color: var(--text); margin-bottom: 4px; }
      .link-card span { color: var(--text-muted); font-weight: 400; font-size: 0.95rem; }
      .muted { color: var(--text-muted); }
      .stats { display: flex; flex-wrap: wrap; gap: 14px; margin: 14px 0 0; }
      .stat {
        background: var(--surface-muted);
        border: 1px solid var(--border);
        border-radius: 12px;
        padding: 12px 16px;
        min-width: 120px;
      }
      .stat strong { display: block; font-size: 1.35rem; }
      .stat span { color: var(--text-muted); font-size: 0.88rem; }
      footer {
        margin-top: 28px;
        text-align: center;
        color: var(--text-muted);
        font-size: 0.9rem;
      }
      footer strong { color: var(--brand); }
    """


def page_shell(
    title: str,
    body_html: str,
    *,
    subtitle: Optional[str] = None,
    build: Optional[str] = None,
    extra_css: str = "",
    nested_logo: bool = False,
) -> str:
    build_label = esc(build or release_id())
    subtitle_html = f"<p>{esc(subtitle)}</p>" if subtitle else ""
    return f"""<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>{esc(title)} · {esc(BRAND_NAME)}</title>
    <style>{base_css()}{extra_css}</style>
  </head>
  <body>
    <div class="page">
      <header class="brand-bar">
        <div class="brand-top">
          {brand_logo_markup(nested=nested_logo)}
        </div>
        <h1>{esc(title)}</h1>
        {subtitle_html}
        <div class="meta">
          <span class="meta-chip">{esc(PROJECT_NAME)}</span>
          <span class="meta-chip">Build <code style="background:transparent;color:inherit;padding:0;">{build_label}</code></span>
          <span class="meta-chip">Generated <code style="background:transparent;color:inherit;padding:0;">{esc(generated_at_chip())}</code></span>
        </div>
      </header>
      <main class="content">
        {body_html}
      </main>
      <footer>Generated {esc(generated_at_chip())} · <strong>{esc(BRAND_NAME)}</strong></footer>
    </div>
  </body>
</html>
"""


def status_pill(status: str) -> str:
    normalized = status.upper()
    css = "pass" if normalized == "PASS" else "fail"
    return f'<span class="status {css}">{esc(normalized)}</span>'


def stat_block(label: str, value: object) -> str:
    return f'<div class="stat"><strong>{esc(value)}</strong><span>{esc(label)}</span></div>'


def link_card(href: str, title: str, description: str) -> str:
    return (
        f'<a class="link-card" href="{esc(href)}">'
        f"<strong>{esc(title)}</strong>"
        f"<span>{esc(description)}</span>"
        f"</a>"
    )
