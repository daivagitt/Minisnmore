/** Trust chips shown under a homepage hero slide. Blank banner fields use these defaults. */

export const DEFAULT_HERO_DYES_LABEL = "Azo-free dyes";
/** Max length for banner eyebrow and trust chip labels (matches backend @Size). */
export const BANNER_LABEL_MAX = 80;
export const BANNER_TRUST_LABEL_MAX = BANNER_LABEL_MAX;

export type BannerTrustFields = {
  trust_fabric_label?: string | null;
  trust_shipping_label?: string | null;
  trust_dyes_label?: string | null;
};

export type BannerTrustLabels = {
  fabricLabel: string;
  shippingLabel: string;
  dyesLabel: string;
};

export type BannerTrustDefaults = {
  fabricLabel: string;
  shippingLabel: string;
  dyesLabel?: string;
};

function trimmed(value: string | null | undefined): string {
  return (value ?? "").trim();
}

function labelTooLongError(value: string | null | undefined, label: string): string | null {
  if (trimmed(value).length > BANNER_LABEL_MAX) {
    return `${label} must be ${BANNER_LABEL_MAX} characters or fewer.`;
  }
  return null;
}

export function getBannerEyebrowValidationErrors(eyebrow: string | null | undefined): string[] {
  const error = labelTooLongError(eyebrow, "Eyebrow label");
  return error ? [error] : [];
}

export function getBannerTrustValidationErrors(row: BannerTrustFields): string[] {
  return [
    labelTooLongError(row.trust_fabric_label, "Organic / fabric line"),
    labelTooLongError(row.trust_shipping_label, "Shipping line"),
    labelTooLongError(row.trust_dyes_label, "Dyes line"),
  ].filter((error): error is string => !!error);
}

export function resolveBannerTrustLabels(
  banner: BannerTrustFields,
  defaults: BannerTrustDefaults,
): BannerTrustLabels {
  return {
    fabricLabel: trimmed(banner.trust_fabric_label) || defaults.fabricLabel,
    shippingLabel: trimmed(banner.trust_shipping_label) || defaults.shippingLabel,
    dyesLabel: trimmed(banner.trust_dyes_label) || defaults.dyesLabel || DEFAULT_HERO_DYES_LABEL,
  };
}

export function homeSectionPayloadRecord(payload: unknown): Record<string, unknown> | undefined {
  return payload && typeof payload === "object" && !Array.isArray(payload)
    ? (payload as Record<string, unknown>)
    : undefined;
}

/** Legacy hero-section payload keys still apply as defaults when a banner chip is blank. */
export function mergeHeroSectionTrustDefaults(
  homeSectionPayload: Record<string, unknown> | undefined,
  storefront: BannerTrustDefaults,
): BannerTrustDefaults {
  return resolveBannerTrustLabels(
    {
      trust_fabric_label:
        typeof homeSectionPayload?.trust_fabric_label === "string"
          ? homeSectionPayload.trust_fabric_label
          : null,
      trust_shipping_label:
        typeof homeSectionPayload?.trust_shipping_label === "string"
          ? homeSectionPayload.trust_shipping_label
          : null,
      trust_dyes_label:
        typeof homeSectionPayload?.trust_dyes_label === "string"
          ? homeSectionPayload.trust_dyes_label
          : null,
    },
    storefront,
  );
}
