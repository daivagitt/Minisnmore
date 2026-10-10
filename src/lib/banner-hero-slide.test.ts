import { describe, expect, it } from "vitest";
import { DEFAULT_HERO_DYES_LABEL } from "./banner-trust";
import { bannerToHeroSlide } from "./banner-hero-slide";

const base = {
  id: "1",
  title: "Baby",
  subtitle: null,
  link_url: "/shop",
  cta_label: "Shop",
  eyebrow: null,
  image_url: "/hero.jpg",
};

const trustDefaults = {
  fabricLabel: "100% Certified Organic Cotton",
  shippingLabel: "Free shipping ₹10+",
};

describe("bannerToHeroSlide", () => {
  it("applies eyebrow fallback by slide index", () => {
    expect(bannerToHeroSlide(base, 0, "focus", trustDefaults).eyebrow).toBe("Featured");
    expect(bannerToHeroSlide(base, 1, "focus", trustDefaults).eyebrow).toBe("Just in");
  });

  it("uses CMS eyebrow when set", () => {
    expect(bannerToHeroSlide({ ...base, eyebrow: "New edit" }, 2, "focus", trustDefaults).eyebrow).toBe(
      "New edit",
    );
  });

  it("falls back to storefront trust lines when banner chips are blank", () => {
    const slide = bannerToHeroSlide(base, 0, "focus", trustDefaults);
    expect(slide.fabricLabel).toBe(trustDefaults.fabricLabel);
    expect(slide.shippingLabel).toBe(trustDefaults.shippingLabel);
    expect(slide.dyesLabel).toBe(DEFAULT_HERO_DYES_LABEL);
  });

  it("uses per-banner trust lines when set", () => {
    const slide = bannerToHeroSlide(
      {
        ...base,
        trust_fabric_label: "GOTS cotton",
        trust_shipping_label: "Free shipping pan-India",
        trust_dyes_label: "Plant dyes",
      },
      0,
      "focus",
      trustDefaults,
    );
    expect(slide.fabricLabel).toBe("GOTS cotton");
    expect(slide.shippingLabel).toBe("Free shipping pan-India");
    expect(slide.dyesLabel).toBe("Plant dyes");
  });
});
