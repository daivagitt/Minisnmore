import { ReactNode } from "react";
import { marked } from "marked";
import { usePolicy } from "@/hooks/usePolicy";
import { LegalPage } from "@/components/LegalPage";

type Props = {
  slug: string;
  eyebrow: string;
  defaultTitle: string;
  lastUpdated: string;
  fallback: ReactNode;
};

/**
 * Renders policy from Supabase if present; else falls back to static markup.
 * Admin-authored bodies can be written as plain text, Markdown, or HTML —
 * we auto-detect and normalise the content into a clean, responsive layout.
 */

marked.setOptions({ gfm: true, breaks: false });

/**
 * Heuristic: turn a plain-text policy into structured Markdown so the editor
 * doesn't need to know syntax. Rules:
 *  - Numbered headings ("1. Eligibility") become H2s
 *  - ALL CAPS short lines become H2s
 *  - Lines starting with -, *, • become bullets
 *  - "Label:" prefixes get auto-bolded
 *  - Blank lines separate paragraphs
 */
function plainTextToMarkdown(input: string): string {
  const lines = input.replace(/\r\n/g, "\n").split("\n");
  const out: string[] = [];
  for (let raw of lines) {
    const line = raw.trimEnd();
    if (!line.trim()) { out.push(""); continue; }

    // Already markdown heading / list — keep as-is
    if (/^#{1,6}\s/.test(line) || /^([-*+]|\d+\.)\s/.test(line) || /^>\s/.test(line)) {
      out.push(line); continue;
    }
    // Bullet glyphs → markdown bullets
    if (/^\s*[•·●▪]\s+/.test(line)) {
      out.push(line.replace(/^\s*[•·●▪]\s+/, "- ")); continue;
    }
    // Numbered heading "1. Title"  or  "1) Title"   (short, no trailing period sentence)
    const numHead = line.match(/^(\d+)[.)]\s+(.{2,80})$/);
    if (numHead && !/[.!?]$/.test(numHead[2].trim())) {
      out.push(`## ${numHead[1]}. ${numHead[2].trim()}`); continue;
    }
    // Short ALL CAPS line → heading
    const trimmed = line.trim();
    if (trimmed.length <= 80 && /^[A-Z0-9][A-Z0-9 &\-,'/]+$/.test(trimmed) && /[A-Z]/.test(trimmed)) {
      out.push(`## ${trimmed.replace(/\b([A-Z]{2,})\b/g, (w) => w[0] + w.slice(1).toLowerCase())}`);
      continue;
    }
    // "Label: rest" → bold the label
    const labeled = line.match(/^([A-Z][A-Za-z0-9 &/'-]{1,40}):\s+(.+)$/);
    if (labeled) {
      out.push(`**${labeled[1]}:** ${labeled[2]}`); continue;
    }
    out.push(line);
  }
  return out.join("\n");
}

function renderBody(body: string): string {
  const looksLikeHtml = /<\/?(p|div|h[1-6]|ul|ol|li|strong|em|a|br|table|section)\b/i.test(body);
  const md = looksLikeHtml ? body : plainTextToMarkdown(body);
  return marked.parse(md, { async: false }) as string;
}

export function PolicyContent({ slug, eyebrow, defaultTitle, lastUpdated, fallback }: Props) {
  const { policy, loading } = usePolicy(slug);

  if (loading && !policy) {
    return (
      <LegalPage eyebrow={eyebrow} title={defaultTitle} lastUpdated={lastUpdated}>
        <p className="text-muted-foreground">Loading…</p>
      </LegalPage>
    );
  }

  if (policy && policy.body && policy.body.trim().length > 0) {
    const html = renderBody(policy.body);
    return (
      <LegalPage eyebrow={eyebrow} title={policy.title || defaultTitle} lastUpdated={lastUpdated}>
        <div
          className="policy-prose max-w-none text-[15px] md:text-base leading-relaxed text-foreground/90 [&>h2]:font-display [&>h2]:text-2xl [&>h2]:md:text-[1.6rem] [&>h2]:mt-10 [&>h2]:mb-3 [&>h2]:text-foreground [&>h2]:tracking-tight [&>h3]:font-display [&>h3]:text-xl [&>h3]:mt-8 [&>h3]:mb-2 [&>p]:my-4 [&>ul]:my-4 [&>ul]:pl-6 [&>ul]:space-y-1.5 [&>ul>li]:list-disc [&>ol]:my-4 [&>ol]:pl-6 [&>ol]:space-y-1.5 [&>ol>li]:list-decimal [&_strong]:text-foreground [&_strong]:font-semibold [&_a]:text-primary [&_a]:underline [&_a]:underline-offset-4 hover:[&_a]:opacity-80"
          dangerouslySetInnerHTML={{ __html: html }}
        />
      </LegalPage>
    );
  }

  return <>{fallback}</>;
}
