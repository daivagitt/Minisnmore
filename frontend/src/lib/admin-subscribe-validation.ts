import { optionalNumberFromUnknown, requireNumberField } from "@/lib/admin-number-field";
import type { SiteSettingsRow } from "@/lib/backend-data";

type SubscribePlan = {
  id: string;
  name: string;
  price: number | null;
  tagline: string;
  perks: string[];
  image: string;
};

type SubscribeBadge = { icon: string; text: string };
type SubscribeFeature = { title: string; description: string };

function parseJsonArray(raw: unknown): unknown[] {
  if (Array.isArray(raw)) return raw;
  if (typeof raw !== "string" || !raw.trim()) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function asPlans(raw: unknown): SubscribePlan[] {
  return parseJsonArray(raw).map((p) => {
    const row = (p && typeof p === "object" ? p : {}) as Record<string, unknown>;
    return {
      id: typeof row.id === "string" ? row.id : "",
      name: typeof row.name === "string" ? row.name : "",
      price: optionalNumberFromUnknown(row.price),
      tagline: typeof row.tagline === "string" ? row.tagline : "",
      perks: Array.isArray(row.perks) ? row.perks.map(String) : [],
      image: typeof row.image === "string" ? row.image : "",
    };
  });
}

function asBadges(raw: unknown): SubscribeBadge[] {
  return parseJsonArray(raw).map((b) => {
    const row = (b && typeof b === "object" ? b : {}) as Record<string, unknown>;
    return {
      icon: typeof row.icon === "string" ? row.icon : "sparkles",
      text: typeof row.text === "string" ? row.text : "",
    };
  });
}

function asFeatures(raw: unknown): SubscribeFeature[] {
  return parseJsonArray(raw).map((f) => {
    const row = (f && typeof f === "object" ? f : {}) as Record<string, unknown>;
    return {
      title: typeof row.title === "string" ? row.title : "",
      description: typeof row.description === "string" ? row.description : "",
    };
  });
}

function isPlanStarted(plan: SubscribePlan): boolean {
  return !!(
    plan.id.trim() ||
    plan.name.trim() ||
    plan.price != null ||
    plan.tagline.trim() ||
    plan.image.trim() ||
    plan.perks.some((p) => p.trim())
  );
}

function isFeatureStarted(feature: SubscribeFeature): boolean {
  return !!(feature.title.trim() || feature.description.trim());
}

function isBadgeStarted(badge: SubscribeBadge): boolean {
  return !!(badge.text.trim() || (badge.icon.trim() && badge.icon !== "sparkles"));
}

/** Validates subscription page JSON editors before save. */
export function getSubscribePageValidationErrors(row: SiteSettingsRow): string[] {
  const errors: string[] = [];

  asBadges(row.subscribe_badges_json).forEach((badge, i) => {
    if (!isBadgeStarted(badge)) return;
    if (!badge.text.trim()) errors.push(`Badge ${i + 1}: Text is required.`);
  });

  asPlans(row.subscribe_plans_json).forEach((plan, i) => {
    if (!isPlanStarted(plan)) return;
    const prefix = `Plan ${i + 1}: `;
    if (!plan.id.trim()) errors.push(`${prefix}Plan ID is required.`);
    if (!plan.name.trim()) errors.push(`${prefix}Name is required.`);
    const priceError = requireNumberField(plan.price, "Price (INR)", { allowZero: true });
    if (priceError) errors.push(`${prefix}${priceError}`);
  });

  asFeatures(row.subscribe_features_json).forEach((feature, i) => {
    if (!isFeatureStarted(feature)) return;
    const prefix = `Feature ${i + 1}: `;
    if (!feature.title.trim()) errors.push(`${prefix}Title is required.`);
    if (!feature.description.trim()) errors.push(`${prefix}Description is required.`);
  });

  return errors;
}
