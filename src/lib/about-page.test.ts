import { describe, expect, it } from "vitest";
import {
  DEFAULT_ABOUT_PAGE,
  getAboutPageValidationErrors,
  parseAboutPageJson,
  resolveAboutPage,
  serializeAboutPage,
} from "@/lib/about-page";

describe("about-page", () => {
  it("falls back to current storefront copy when JSON is empty", () => {
    expect(resolveAboutPage("")).toEqual(DEFAULT_ABOUT_PAGE);
    expect(resolveAboutPage(null)).toEqual(DEFAULT_ABOUT_PAGE);
    expect(parseAboutPageJson("{")).toEqual({});
  });

  it("overrides only fields present in saved JSON", () => {
    const resolved = resolveAboutPage(
      JSON.stringify({ hero_title: "Hello minis", story_stats: [{ value: "1", label: "Thing" }] }),
    );
    expect(resolved.hero_title).toBe("Hello minis");
    expect(resolved.story_stats).toEqual([{ value: "1", label: "Thing" }]);
    expect(resolved.cta_button).toBe(DEFAULT_ABOUT_PAGE.cta_button);
  });

  it("round-trips serialized admin content", () => {
    const json = serializeAboutPage(DEFAULT_ABOUT_PAGE);
    expect(resolveAboutPage(json).founders[0]?.name).toBe("Ravikumar T");
  });

  it("requires started founder and feature rows to be complete", () => {
    const errors = getAboutPageValidationErrors({
      why_features: [{ icon: "heart", title: "", description: "" }],
      founders: [{ name: "", role: "Director", image_url: "", paragraphs: [""], quote: "" }],
    });
    expect(errors).toEqual(
      expect.arrayContaining(["Why-choose 1: Title is required.", "Founder 1: Name is required."]),
    );
  });

  it("rejects placeholder image URLs", () => {
    const errors = getAboutPageValidationErrors({
      hero_image_url: "https://…/hero.jpg",
    });
    expect(errors).toEqual(
      expect.arrayContaining([
        "Hero image must be a real uploaded image or valid site path, not a placeholder example.",
      ]),
    );
  });

  it("flags invalid JSON strings", () => {
    expect(getAboutPageValidationErrors("{not json")).toEqual(["About page content is not valid JSON."]);
  });
});
