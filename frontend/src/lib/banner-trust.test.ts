import { describe, expect, it } from "vitest";
import {
  BANNER_LABEL_MAX,
  BANNER_TRUST_LABEL_MAX,
  DEFAULT_HERO_DYES_LABEL,
  getBannerEyebrowValidationErrors,
  getBannerTrustValidationErrors,
  mergeHeroSectionTrustDefaults,
  resolveBannerTrustLabels,
} from "./banner-trust";

const defaults = {
  fabricLabel: "100% Certified Organic Cotton",
  shippingLabel: "Free shipping ₹10+",
};

describe("banner-trust", () => {
  it("falls back to storefront defaults when banner chips are blank", () => {
    expect(resolveBannerTrustLabels({}, defaults)).toEqual({
      fabricLabel: defaults.fabricLabel,
      shippingLabel: defaults.shippingLabel,
      dyesLabel: DEFAULT_HERO_DYES_LABEL,
    });
  });

  it("uses per-banner overrides when set", () => {
    expect(
      resolveBannerTrustLabels(
        {
          trust_fabric_label: "Handloom cotton",
          trust_shipping_label: "Free shipping pan-India",
          trust_dyes_label: "Plant dyes",
        },
        defaults,
      ),
    ).toEqual({
      fabricLabel: "Handloom cotton",
      shippingLabel: "Free shipping pan-India",
      dyesLabel: "Plant dyes",
    });
  });

  it("merges legacy hero-section payload into storefront defaults", () => {
    expect(
      mergeHeroSectionTrustDefaults(
        {
          trust_fabric_label: "GOTS cotton",
          trust_shipping_label: "Free shipping pan-India",
        },
        defaults,
      ),
    ).toEqual({
      fabricLabel: "GOTS cotton",
      shippingLabel: "Free shipping pan-India",
      dyesLabel: DEFAULT_HERO_DYES_LABEL,
    });
  });

  it("rejects eyebrow labels over the character limit", () => {
    expect(getBannerEyebrowValidationErrors("A".repeat(BANNER_LABEL_MAX + 1))).toEqual([
      `Eyebrow label must be ${BANNER_LABEL_MAX} characters or fewer.`,
    ]);
    expect(BANNER_TRUST_LABEL_MAX).toBe(BANNER_LABEL_MAX);
  });

  it("rejects labels over the character limit", () => {
    expect(
      getBannerTrustValidationErrors({
        trust_fabric_label: "A".repeat(BANNER_TRUST_LABEL_MAX + 1),
        trust_shipping_label: "",
        trust_dyes_label: null,
      }),
    ).toEqual([`Organic / fabric line must be ${BANNER_TRUST_LABEL_MAX} characters or fewer.`]);
  });
});
