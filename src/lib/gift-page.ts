import { optionalPublicUrlFieldError } from "@/lib/utils";
import type { AgeRange } from "@/data/products";

export const GIFT_FEATURE_ICONS = ["sparkles", "flower", "gift"] as const;
export type GiftFeatureIcon = (typeof GIFT_FEATURE_ICONS)[number];

export const GIFT_AGE_VALUES: AgeRange[] = ["0-3m", "3-6m", "6-12m", "12-18m", "18-24m"];

export type GiftBudget = { id: string; label: string; max: number };
export type GiftFeature = { icon: GiftFeatureIcon; title: string; body: string };
export type GiftAgeOption = { label: string; value: AgeRange };

export type GiftPageContent = {
  hero_eyebrow: string;
  hero_title: string;
  hero_subtitle: string;
  hero_image_url: string;
  hero_image_alt: string;
  features: GiftFeature[];
  occasions: string[];
  budgets: GiftBudget[];
  palettes: string[];
  ages: GiftAgeOption[];
  steps: string[];
  inside_title: string;
  inside_bullets: string[];
  highlight_line: string;
  shop_link_label: string;
};

export const DEFAULT_GIFT_PAGE: GiftPageContent = {
  hero_eyebrow: "Baby-first gifting",
  hero_title: "A complete gifting journey, styled for the little one.",
  hero_subtitle: "Choose occasion + budget, pick the pieces, and we'll wrap and ship.",
  hero_image_url: "",
  hero_image_alt: "Smiling baby enjoying a wrapped gift",
  features: [
    {
      icon: "sparkles",
      title: "Occasion-led curation",
      body: "Naming day, welcome home, birthdays and milestone moments.",
    },
    {
      icon: "flower",
      title: "Signature wrapping",
      body: "Cream wrap, silk ribbon, dried botanicals and a tactile keepsake feel.",
    },
    {
      icon: "gift",
      title: "Ready to gift",
      body: "No price slip, direct recipient shipping and a concierge-ready brief.",
    },
  ],
  occasions: ["Naming Ceremony", "First Birthday", "Welcome Baby", "Annaprashan"],
  budgets: [
    { id: "u2k", label: "Under ₹2,000", max: 2000 },
    { id: "2-5k", label: "₹2,000–5,000", max: 5000 },
    { id: "5-10k", label: "₹5,000–10,000", max: 10000 },
  ],
  palettes: ["Cream & Sage", "Blush & Terracotta", "Sky & Sand", "Surprise me"],
  ages: [
    { label: "0–3 m", value: "0-3m" },
    { label: "3–6 m", value: "3-6m" },
    { label: "6–12 m", value: "6-12m" },
    { label: "12–18 m", value: "12-18m" },
    { label: "18–24 m", value: "18-24m" },
  ],
  steps: ["Occasion", "Baby", "Style", "Pick gifts", "Note"],
  inside_title: "What's inside",
  inside_bullets: [
    "Your selected pieces hand-wrapped together",
    "Cream linen-wrapped box with terracotta silk ribbon",
    "Dried baby's-breath sprig & handwritten note card",
    "Optional baby footprint kit (₹450)",
    "Direct ship to recipient with no price slip",
  ],
  highlight_line: "✨ Same-day curation in metros · 48-hr nationwide",
  shop_link_label: "Browse the full shop instead →",
};

const MAX_LIST = 20;
const MAX_BUDGETS = 10;
const MAX_FEATURES = 6;

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function asStringList(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.map((item) => (typeof item === "string" ? item : String(item ?? "")));
}

function asFeatureIcon(value: unknown): GiftFeatureIcon {
  return GIFT_FEATURE_ICONS.includes(value as GiftFeatureIcon) ? (value as GiftFeatureIcon) : "gift";
}

function asFeatures(value: unknown): GiftFeature[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.map((item) => {
    const row = asRecord(item);
    return {
      icon: asFeatureIcon(row.icon),
      title: asString(row.title),
      body: asString(row.body),
    };
  });
}

function asBudgets(value: unknown): GiftBudget[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.map((item) => {
    const row = asRecord(item);
    const maxRaw = row.max;
    const max =
      typeof maxRaw === "number" && Number.isFinite(maxRaw)
        ? maxRaw
        : typeof maxRaw === "string" && maxRaw.trim()
          ? Number(maxRaw)
          : 0;
    return {
      id: asString(row.id),
      label: asString(row.label),
      max: Number.isFinite(max) ? max : 0,
    };
  });
}

function asAgeValue(value: unknown): AgeRange | null {
  return GIFT_AGE_VALUES.includes(value as AgeRange) ? (value as AgeRange) : null;
}

function asAges(value: unknown): GiftAgeOption[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const byValue = new Map<AgeRange, string>();
  for (const item of value) {
    const row = asRecord(item);
    const ageValue = asAgeValue(row.value);
    if (!ageValue) continue;
    byValue.set(ageValue, asString(row.label) || DEFAULT_GIFT_PAGE.ages.find((a) => a.value === ageValue)?.label || ageValue);
  }
  // Always return full fixed age set; labels from JSON when present.
  return GIFT_AGE_VALUES.map((ageValue) => ({
    value: ageValue,
    label: byValue.get(ageValue) ?? DEFAULT_GIFT_PAGE.ages.find((a) => a.value === ageValue)!.label,
  }));
}

export function parseGiftPageJson(raw: unknown): Partial<GiftPageContent> {
  if (raw == null) return {};
  if (typeof raw === "object" && !Array.isArray(raw)) {
    return parseGiftPageObject(asRecord(raw));
  }
  if (typeof raw !== "string" || !raw.trim()) return {};
  try {
    const parsed = JSON.parse(raw) as unknown;
    return parseGiftPageObject(asRecord(parsed));
  } catch {
    return {};
  }
}

function parseGiftPageObject(row: Record<string, unknown>): Partial<GiftPageContent> {
  const next: Partial<GiftPageContent> = {};
  const assign = <K extends keyof GiftPageContent>(key: K, value: GiftPageContent[K] | undefined) => {
    if (value !== undefined) next[key] = value;
  };
  assign("hero_eyebrow", typeof row.hero_eyebrow === "string" ? row.hero_eyebrow : undefined);
  assign("hero_title", typeof row.hero_title === "string" ? row.hero_title : undefined);
  assign("hero_subtitle", typeof row.hero_subtitle === "string" ? row.hero_subtitle : undefined);
  assign("hero_image_url", typeof row.hero_image_url === "string" ? row.hero_image_url : undefined);
  assign("hero_image_alt", typeof row.hero_image_alt === "string" ? row.hero_image_alt : undefined);
  assign("features", asFeatures(row.features));
  assign("occasions", asStringList(row.occasions));
  assign("budgets", asBudgets(row.budgets));
  assign("palettes", asStringList(row.palettes));
  assign("ages", asAges(row.ages));
  assign("steps", asStringList(row.steps));
  assign("inside_title", typeof row.inside_title === "string" ? row.inside_title : undefined);
  assign("inside_bullets", asStringList(row.inside_bullets));
  assign("highlight_line", typeof row.highlight_line === "string" ? row.highlight_line : undefined);
  assign("shop_link_label", typeof row.shop_link_label === "string" ? row.shop_link_label : undefined);
  return next;
}

function nonEmptyList<T>(list: T[] | undefined, fallback: T[]): T[] {
  return list && list.length > 0 ? list : fallback;
}

function cleanStringList(list: string[] | undefined): string[] | undefined {
  if (!list) return undefined;
  const cleaned = list.map((item) => item.trim()).filter(Boolean);
  return cleaned.length > 0 ? cleaned : undefined;
}

function cleanBudgets(list: GiftBudget[] | undefined): GiftBudget[] | undefined {
  if (!list) return undefined;
  const cleaned = list
    .map((b) => ({
      id: b.id.trim(),
      label: b.label.trim(),
      max: Number.isFinite(b.max) ? b.max : 0,
    }))
    .filter((b) => b.id && b.label && b.max > 0);
  return cleaned.length > 0 ? cleaned : undefined;
}

export function resolveGiftPage(raw: unknown): GiftPageContent {
  const parsed = parseGiftPageJson(raw);
  const occasions = cleanStringList(parsed.occasions);
  const palettes = cleanStringList(parsed.palettes);
  const inside_bullets = cleanStringList(parsed.inside_bullets);
  const budgets = cleanBudgets(parsed.budgets);
  const features =
    parsed.features && parsed.features.some((f) => f.title.trim() || f.body.trim())
      ? parsed.features
      : undefined;
  const steps =
    parsed.steps &&
    parsed.steps.length === 5 &&
    parsed.steps.every((s) => s.trim())
      ? parsed.steps.map((s) => s.trim())
      : undefined;

  return {
    ...DEFAULT_GIFT_PAGE,
    ...parsed,
    features: nonEmptyList(features, DEFAULT_GIFT_PAGE.features),
    occasions: nonEmptyList(occasions, DEFAULT_GIFT_PAGE.occasions),
    budgets: nonEmptyList(budgets, DEFAULT_GIFT_PAGE.budgets),
    palettes: nonEmptyList(palettes, DEFAULT_GIFT_PAGE.palettes),
    ages: parsed.ages ?? DEFAULT_GIFT_PAGE.ages,
    steps: nonEmptyList(steps, DEFAULT_GIFT_PAGE.steps),
    inside_bullets: nonEmptyList(inside_bullets, DEFAULT_GIFT_PAGE.inside_bullets),
  };
}

export function serializeGiftPage(content: GiftPageContent): string {
  return JSON.stringify(content);
}

function isFeatureStarted(feature: GiftFeature): boolean {
  return !!(feature.title.trim() || feature.body.trim());
}

function isBudgetStarted(budget: GiftBudget): boolean {
  return !!(budget.id.trim() || budget.label.trim() || budget.max > 0);
}

/** Validates gift-page content before save. Empty/invalid JSON falls back to defaults on storefront. */
export function getGiftPageValidationErrors(raw: unknown): string[] {
  const errors: string[] = [];
  if (typeof raw === "string" && raw.trim()) {
    try {
      JSON.parse(raw);
    } catch {
      return ["Gift page content is not valid JSON."];
    }
  }

  // Admin form passes a GiftPageContent object — validate it as-is (do not auto-clean).
  // Storefront JSON strings still go through resolveGiftPage for safety.
  const content: GiftPageContent =
    raw &&
    typeof raw === "object" &&
    !Array.isArray(raw) &&
    Array.isArray((raw as GiftPageContent).budgets) &&
    Array.isArray((raw as GiftPageContent).occasions)
      ? { ...DEFAULT_GIFT_PAGE, ...(raw as GiftPageContent) }
      : resolveGiftPage(raw);

  if (!content.hero_title.trim()) errors.push("Hero title is required.");

  const imageError = optionalPublicUrlFieldError(content.hero_image_url, "Hero image URL");
  if (imageError) errors.push(imageError);

  if (content.features.length > MAX_FEATURES) {
    errors.push(`Feature cards: at most ${MAX_FEATURES} allowed.`);
  }
  content.features.forEach((feature, i) => {
    if (!isFeatureStarted(feature)) return;
    if (!feature.title.trim()) errors.push(`Feature ${i + 1}: Title is required.`);
    if (!feature.body.trim()) errors.push(`Feature ${i + 1}: Body is required.`);
  });

  const occasions = content.occasions.map((o) => o.trim()).filter(Boolean);
  if (occasions.length < 1) errors.push("Add at least one occasion.");
  if (content.occasions.length > MAX_LIST) errors.push(`Occasions: at most ${MAX_LIST} allowed.`);
  if (content.occasions.some((o) => !o.trim())) errors.push("Occasions: remove blank entries.");
  const occasionSeen = new Set<string>();
  for (const o of occasions) {
    const key = o.toLowerCase();
    if (occasionSeen.has(key)) {
      errors.push(`Occasions: "${o}" is duplicated.`);
      break;
    }
    occasionSeen.add(key);
  }

  const budgets = content.budgets.filter(isBudgetStarted);
  if (budgets.length < 1) errors.push("Add at least one budget.");
  if (content.budgets.length > MAX_BUDGETS) errors.push(`Budgets: at most ${MAX_BUDGETS} allowed.`);
  const ids = new Set<string>();
  const labels = new Set<string>();
  budgets.forEach((budget, i) => {
    const prefix = `Budget ${i + 1}: `;
    if (!budget.id.trim()) errors.push(`${prefix}Id is required.`);
    else if (ids.has(budget.id.trim())) errors.push(`${prefix}Id must be unique.`);
    else ids.add(budget.id.trim());
    if (!budget.label.trim()) errors.push(`${prefix}Label is required.`);
    else if (labels.has(budget.label.trim().toLowerCase())) errors.push(`${prefix}Label must be unique.`);
    else labels.add(budget.label.trim().toLowerCase());
    if (!(budget.max > 0)) errors.push(`${prefix}Max must be greater than 0.`);
  });

  const palettes = content.palettes.map((p) => p.trim()).filter(Boolean);
  if (palettes.length < 1) errors.push("Add at least one palette.");
  if (content.palettes.length > MAX_LIST) errors.push(`Palettes: at most ${MAX_LIST} allowed.`);
  if (content.palettes.some((p) => !p.trim())) errors.push("Palettes: remove blank entries.");

  if (content.steps.length !== 5) {
    errors.push("Step labels: exactly 5 steps are required (Occasion, Baby, Style, Pick gifts, Note).");
  }
  if (content.steps.some((s) => !s.trim())) {
    errors.push("Step labels: each step needs a non-empty label.");
  }

  if (content.inside_bullets.length > MAX_LIST) {
    errors.push(`What's inside bullets: at most ${MAX_LIST} allowed.`);
  }
  if (content.inside_bullets.some((b) => !b.trim())) {
    errors.push("What's inside bullets: remove blank entries.");
  }

  content.ages.forEach((age) => {
    if (!GIFT_AGE_VALUES.includes(age.value)) {
      errors.push(`Age value "${age.value}" is not supported.`);
    }
    if (!age.label.trim()) errors.push(`Age ${age.value}: Label is required.`);
  });

  return errors;
}
