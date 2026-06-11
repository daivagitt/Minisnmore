import { HOME_SECTION_CARD_LIMIT } from "@/lib/home-section-limit";

/** Homepage blog strip shows five cards in a row. */
export const HOME_BLOG_STRIP_LIMIT = HOME_SECTION_CARD_LIMIT;

export type HomeBlogStripPost = {
  slug: string;
  title: string;
  excerpt: string | null;
  cover: string | null;
  date: string;
  tag: string;
};

export type HomeBlogCardDraft = {
  slug: string;
  sort_order: number;
};

export function emptyHomeBlogCard(sort_order = 0): HomeBlogCardDraft {
  return { slug: "", sort_order };
}

function sortByCardOrder(cards: HomeBlogCardDraft[]): HomeBlogCardDraft[] {
  return [...cards].sort((a, b) => a.sort_order - b.sort_order || 0);
}

function draftFromRaw(raw: unknown, fallbackOrder: number): HomeBlogCardDraft {
  if (!raw || typeof raw !== "object") return emptyHomeBlogCard(fallbackOrder);
  const row = raw as Record<string, unknown>;
  const sortOrder = Number(row.sort_order);
  return {
    slug: String(row.slug ?? "").trim().toLowerCase(),
    sort_order: Number.isFinite(sortOrder) ? Math.max(0, Math.floor(sortOrder)) : fallbackOrder,
  };
}

export function homeBlogCardsFromPayload(payload: Record<string, unknown> | undefined): HomeBlogCardDraft[] {
  const cards = payload?.blog_cards;
  if (Array.isArray(cards)) {
    return sortByCardOrder(cards.map((row, index) => draftFromRaw(row, index)));
  }
  const slugs = payload?.blog_slugs;
  if (Array.isArray(slugs)) {
    return slugs
      .filter((s): s is string => typeof s === "string" && s.trim().length > 0)
      .map((slug, index) => ({ slug: slug.trim().toLowerCase(), sort_order: index }));
  }
  return [];
}

export function normalizeHomeBlogCards(cards: HomeBlogCardDraft[]): HomeBlogCardDraft[] {
  const seen = new Set<string>();
  const out: HomeBlogCardDraft[] = [];
  for (const card of sortByCardOrder(cards)) {
    const slug = card.slug.trim().toLowerCase();
    if (!slug || seen.has(slug)) continue;
    seen.add(slug);
    out.push({
      slug,
      sort_order: Number.isFinite(card.sort_order) ? Math.max(0, Math.floor(card.sort_order)) : out.length,
    });
    if (out.length >= HOME_BLOG_STRIP_LIMIT) break;
  }
  return out;
}

export function homeBlogCardsToPayload(cards: HomeBlogCardDraft[]): Record<string, unknown>[] {
  return normalizeHomeBlogCards(cards).map((card) => ({
    slug: card.slug,
    sort_order: card.sort_order,
  }));
}

export function sectionBlogSlugs(payload: Record<string, unknown> | undefined): string[] {
  return normalizeHomeBlogCards(homeBlogCardsFromPayload(payload)).map((card) => card.slug);
}

export function normalizeHomeBlogStripSlugs(slugs: string[]): string[] {
  return normalizeHomeBlogCards(slugs.map((slug, index) => ({ slug, sort_order: index }))).map(
    (card) => card.slug,
  );
}

export function getHomeBlogStripSlugErrors(cards: HomeBlogCardDraft[]): string[] {
  const filled = cards.filter((c) => c.slug.trim()).length;
  if (filled > HOME_BLOG_STRIP_LIMIT) {
    return [`Pick at most ${HOME_BLOG_STRIP_LIMIT} blog posts for this section.`];
  }
  return [];
}

/** Admin-picked slugs first; otherwise latest published posts. */
export function resolveHomeBlogStripPosts(
  all: HomeBlogStripPost[],
  slugs: string[],
  fallback: HomeBlogStripPost[],
  limit = HOME_BLOG_STRIP_LIMIT,
): HomeBlogStripPost[] {
  if (slugs.length > 0) {
    const bySlug = new Map(all.map((p) => [p.slug, p]));
    const picked = normalizeHomeBlogStripSlugs(slugs)
      .map((slug) => bySlug.get(slug))
      .filter((p): p is HomeBlogStripPost => !!p);
    if (picked.length > 0) return picked.slice(0, limit);
  }
  if (all.length > 0) return all.slice(0, limit);
  return fallback.slice(0, limit);
}
