import type { Review } from "@/data/products";
import { defaultReviews } from "@/data/reviews";
import { HOME_SECTION_CARD_LIMIT } from "@/lib/home-section-limit";
import { optionalPublicUrlFieldError } from "@/lib/utils";

export type HomeReviewDraft = {
  title: string;
  body: string;
  author: string;
  date: string;
  rating: number;
  photo: string;
};

const TITLE_MAX = 120;
const BODY_MAX = 600;
const AUTHOR_MAX = 80;
const DATE_MAX = 40;
const NAME_HAS_LETTER_RE = /\p{L}/u;

export function emptyHomeReview(): HomeReviewDraft {
  return { title: "", body: "", author: "", date: "", rating: 5, photo: "" };
}

function draftFromRaw(raw: unknown): HomeReviewDraft {
  if (!raw || typeof raw !== "object") return emptyHomeReview();
  const row = raw as Record<string, unknown>;
  const rating = Number(row.rating);
  return {
    title: String(row.title ?? ""),
    body: String(row.body ?? ""),
    author: String(row.author ?? ""),
    date: String(row.date ?? "").trim() === "Recent" ? "" : String(row.date ?? ""),
    rating: Number.isFinite(rating) ? Math.min(5, Math.max(1, Math.round(rating))) : 5,
    photo: String(row.photo ?? ""),
  };
}

export function isBlankHomeReview(draft: HomeReviewDraft): boolean {
  return (
    !draft.title.trim() &&
    !draft.body.trim() &&
    !draft.author.trim() &&
    !draft.date.trim() &&
    !draft.photo.trim()
  );
}

export function homeReviewDraftsFromPayload(payload: Record<string, unknown> | undefined): HomeReviewDraft[] {
  const raw = payload?.reviews;
  if (!Array.isArray(raw)) return [];
  return raw.map(draftFromRaw);
}

export function getHomeReviewValidationErrors(draft: HomeReviewDraft, index?: number): string[] {
  const prefix = index != null ? `Review ${index + 1}: ` : "";
  const errors: string[] = [];
  const title = draft.title.trim();
  const body = draft.body.trim();
  const author = draft.author.trim();
  const date = draft.date.trim();
  const photo = draft.photo.trim();

  if (!title) errors.push(`${prefix}Headline is required.`);
  else if (title.length < 2) errors.push(`${prefix}Headline must be at least 2 characters.`);
  else if (title.length > TITLE_MAX) errors.push(`${prefix}Headline must be ${TITLE_MAX} characters or fewer.`);

  if (!body) errors.push(`${prefix}Review text is required.`);
  else if (body.length < 10) errors.push(`${prefix}Review text must be at least 10 characters.`);
  else if (body.length > BODY_MAX) errors.push(`${prefix}Review text must be ${BODY_MAX} characters or fewer.`);

  if (!author) errors.push(`${prefix}Author name is required.`);
  else if (author.length < 2) errors.push(`${prefix}Author name must be at least 2 characters.`);
  else if (!NAME_HAS_LETTER_RE.test(author)) errors.push(`${prefix}Author name must contain at least one letter.`);
  else if (author.length > AUTHOR_MAX) errors.push(`${prefix}Author name must be ${AUTHOR_MAX} characters or fewer.`);

  if (date && date.length > DATE_MAX) errors.push(`${prefix}Date must be ${DATE_MAX} characters or fewer.`);

  if (!Number.isFinite(draft.rating) || draft.rating < 1 || draft.rating > 5) {
    errors.push(`${prefix}Rating must be between 1 and 5 stars.`);
  }

  const photoError = optionalPublicUrlFieldError(photo, "Photo");
  if (photoError) errors.push(`${prefix}${photoError}`);

  return errors;
}

export function getHomeReviewCountErrors(drafts: HomeReviewDraft[]): string[] {
  const filled = drafts.filter((d) => !isBlankHomeReview(d)).length;
  if (filled > HOME_SECTION_CARD_LIMIT) {
    return [`Add at most ${HOME_SECTION_CARD_LIMIT} reviews — the homepage shows five per section.`];
  }
  return [];
}

export function validateHomeReviewDrafts(drafts: HomeReviewDraft[]): string[] {
  const errors: string[] = [];
  errors.push(...getHomeReviewCountErrors(drafts));
  drafts.forEach((draft, index) => {
    if (isBlankHomeReview(draft)) return;
    errors.push(...getHomeReviewValidationErrors(draft, index));
  });
  return errors;
}

export function normalizeHomeReviewDraft(draft: HomeReviewDraft): Review {
  const photo = draft.photo.trim();
  return {
    title: draft.title.trim(),
    body: draft.body.trim(),
    author: draft.author.trim(),
    date: draft.date.trim() || "Recent",
    rating: Math.round(draft.rating),
    photo: photo || (defaultReviews[0]?.photo ?? ""),
  };
}

export function normalizeHomeReviewDrafts(drafts: HomeReviewDraft[]): Review[] {
  return drafts
    .filter((d) => !isBlankHomeReview(d))
    .map(normalizeHomeReviewDraft)
    .slice(0, HOME_SECTION_CARD_LIMIT);
}

function parseReview(raw: unknown): Review | null {
  const draft = draftFromRaw(raw);
  if (isBlankHomeReview(draft)) return null;
  const errors = getHomeReviewValidationErrors(draft);
  if (errors.length) return null;
  return normalizeHomeReviewDraft(draft);
}

export function reviewsFromHomePayload(payload: Record<string, unknown> | undefined): Review[] {
  const raw = payload?.reviews;
  if (!Array.isArray(raw)) return [];
  return raw.map(parseReview).filter((r): r is Review => !!r);
}

export function resolveHomeReviews(payload: Record<string, unknown> | undefined): Review[] {
  const fromCms = reviewsFromHomePayload(payload);
  const list = fromCms.length > 0 ? fromCms : defaultReviews;
  return list.slice(0, HOME_SECTION_CARD_LIMIT);
}
