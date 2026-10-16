import { HOME_SECTION_CARD_LIMIT } from "@/lib/home-section-limit";
import { optionalPublicUrlFieldError } from "@/lib/utils";

export const HOME_AGE_FILTERS = ["0-3m", "3-6m", "6-12m", "12-18m", "18-24m"] as const;
export type HomeAgeFilter = (typeof HOME_AGE_FILTERS)[number];

export type HomeByAgeCardDraft = {
  label: string;
  note: string;
  image: string;
  age_filter: HomeAgeFilter;
  sort_order: number;
};

export type HomeByAgeCard = {
  label: string;
  note: string;
  img: string;
  to: "/shop";
  search: { age: HomeAgeFilter };
};

export const HOME_AGE_FILTER_OPTIONS: { value: HomeAgeFilter; label: string }[] = [
  { value: "0-3m", label: "Newborn (0–3 months)" },
  { value: "3-6m", label: "Infant (3–6 months)" },
  { value: "6-12m", label: "Crawlers (6–12 months)" },
  { value: "12-18m", label: "Toddlers (12–18 months)" },
  { value: "18-24m", label: "Kidoo (18–24 months)" },
];

const LABEL_MAX = 40;
const NOTE_MAX = 24;

export function emptyHomeByAgeCard(sort_order = 0): HomeByAgeCardDraft {
  return { label: "", note: "", image: "", age_filter: "0-3m", sort_order };
}

function normalizeAgeFilter(value: unknown): HomeAgeFilter {
  const raw = String(value ?? "").trim().toLowerCase();
  return (HOME_AGE_FILTERS as readonly string[]).includes(raw) ? (raw as HomeAgeFilter) : "0-3m";
}

function draftFromRaw(raw: unknown): HomeByAgeCardDraft {
  if (!raw || typeof raw !== "object") return emptyHomeByAgeCard();
  const row = raw as Record<string, unknown>;
  const search =
    row.search && typeof row.search === "object" ? (row.search as Record<string, unknown>) : {};
  const ageRaw = row.age_filter ?? row.age ?? search.age;
  const sortOrder = Number(row.sort_order);
  return {
    label: String(row.label ?? ""),
    note: String(row.note ?? ""),
    image: String(row.image ?? row.img ?? ""),
    age_filter: normalizeAgeFilter(ageRaw),
    sort_order: Number.isFinite(sortOrder) ? Math.max(0, Math.floor(sortOrder)) : 0,
  };
}

export function isBlankHomeByAgeCard(draft: HomeByAgeCardDraft): boolean {
  return !draft.label.trim() && !draft.note.trim() && !draft.image.trim();
}

function sortByCardOrder<T extends { sort_order: number }>(rows: T[]): T[] {
  return [...rows].sort((a, b) => a.sort_order - b.sort_order || 0);
}

export function homeByAgeDraftsFromPayload(
  payload: Record<string, unknown> | undefined,
): HomeByAgeCardDraft[] {
  const raw = payload?.cards;
  if (!Array.isArray(raw)) return [];
  return sortByCardOrder(raw.map(draftFromRaw));
}

export function getHomeByAgeCardValidationErrors(
  draft: HomeByAgeCardDraft,
  index?: number,
): string[] {
  const prefix = index != null ? `Age card ${index + 1}: ` : "";
  const errors: string[] = [];
  const label = draft.label.trim();
  const note = draft.note.trim();
  const image = draft.image.trim();

  if (!label) errors.push(`${prefix}Category name is required.`);
  else if (label.length > LABEL_MAX) {
    errors.push(`${prefix}Category name must be ${LABEL_MAX} characters or fewer.`);
  }

  if (!note) errors.push(`${prefix}Age range label is required (e.g. 0–3M).`);
  else if (note.length > NOTE_MAX) {
    errors.push(`${prefix}Age range label must be ${NOTE_MAX} characters or fewer.`);
  }

  if (!image) errors.push(`${prefix}Card image is required.`);
  else {
    const imageError = optionalPublicUrlFieldError(image, "Card image");
    if (imageError) errors.push(`${prefix}${imageError}`);
  }

  if (!(HOME_AGE_FILTERS as readonly string[]).includes(draft.age_filter)) {
    errors.push(`${prefix}Choose a valid shop age filter.`);
  }

  return errors;
}

export function getHomeByAgeCardCountErrors(drafts: HomeByAgeCardDraft[]): string[] {
  const filled = drafts.filter((d) => !isBlankHomeByAgeCard(d)).length;
  if (filled > HOME_SECTION_CARD_LIMIT) {
    return [`Add at most ${HOME_SECTION_CARD_LIMIT} age cards — the homepage shows five per section.`];
  }
  return [];
}

export function validateHomeByAgeDrafts(drafts: HomeByAgeCardDraft[]): string[] {
  const errors: string[] = [];
  errors.push(...getHomeByAgeCardCountErrors(drafts));
  drafts.forEach((draft, index) => {
    if (isBlankHomeByAgeCard(draft)) return;
    errors.push(...getHomeByAgeCardValidationErrors(draft, index));
  });
  return errors;
}

export function normalizeHomeByAgeDraft(draft: HomeByAgeCardDraft): HomeByAgeCard {
  return {
    label: draft.label.trim(),
    note: draft.note.trim(),
    img: draft.image.trim(),
    to: "/shop",
    search: { age: draft.age_filter },
  };
}

export function normalizeHomeByAgeDrafts(drafts: HomeByAgeCardDraft[]): HomeByAgeCard[] {
  return sortByCardOrder(drafts)
    .filter((d) => !isBlankHomeByAgeCard(d))
    .map(normalizeHomeByAgeDraft)
    .slice(0, HOME_SECTION_CARD_LIMIT);
}

function parseCard(raw: unknown): HomeByAgeCard | null {
  const draft = draftFromRaw(raw);
  if (isBlankHomeByAgeCard(draft)) return null;
  const errors = getHomeByAgeCardValidationErrors(draft);
  if (errors.length) return null;
  return normalizeHomeByAgeDraft(draft);
}

export function cardsFromHomePayload(payload: Record<string, unknown> | undefined): HomeByAgeCard[] {
  return normalizeHomeByAgeDrafts(homeByAgeDraftsFromPayload(payload));
}

export function resolveHomeByAgeCards(payload: Record<string, unknown> | undefined): HomeByAgeCard[] {
  return cardsFromHomePayload(payload).slice(0, HOME_SECTION_CARD_LIMIT);
}

export function homeByAgeCardsToPayload(
  cards: HomeByAgeCard[],
  sortOrders?: number[],
): Record<string, unknown>[] {
  return cards.map((card, index) => ({
    label: card.label,
    note: card.note,
    image: card.img,
    age_filter: card.search.age,
    sort_order: sortOrders?.[index] ?? index,
  }));
}

export function homeByAgeDraftsToPayload(drafts: HomeByAgeCardDraft[]): Record<string, unknown>[] {
  return sortByCardOrder(drafts)
    .filter((d) => !isBlankHomeByAgeCard(d))
    .map((draft) => ({
      label: draft.label.trim(),
      note: draft.note.trim(),
      image: draft.image.trim(),
      age_filter: draft.age_filter,
      sort_order: draft.sort_order,
    }))
    .slice(0, HOME_SECTION_CARD_LIMIT);
}
