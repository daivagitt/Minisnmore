import { describe, expect, it } from "vitest";
import {
  DEFAULT_GIFT_PAGE,
  getGiftPageValidationErrors,
  resolveGiftPage,
  serializeGiftPage,
} from "./gift-page";

describe("gift-page", () => {
  it("falls back to defaults for empty or invalid JSON", () => {
    expect(resolveGiftPage(null)).toEqual(DEFAULT_GIFT_PAGE);
    expect(resolveGiftPage("")).toEqual(DEFAULT_GIFT_PAGE);
    expect(resolveGiftPage("{not-json")).toEqual(DEFAULT_GIFT_PAGE);
    expect(resolveGiftPage("{}").hero_title).toBe(DEFAULT_GIFT_PAGE.hero_title);
  });

  it("merges partial JSON over defaults", () => {
    const resolved = resolveGiftPage(
      JSON.stringify({
        hero_title: "Custom gift title",
        occasions: ["Baby Shower"],
        budgets: [{ id: "u1k", label: "Under ₹1,000", max: 1000 }],
      }),
    );
    expect(resolved.hero_title).toBe("Custom gift title");
    expect(resolved.occasions).toEqual(["Baby Shower"]);
    expect(resolved.budgets).toEqual([{ id: "u1k", label: "Under ₹1,000", max: 1000 }]);
    expect(resolved.hero_subtitle).toBe(DEFAULT_GIFT_PAGE.hero_subtitle);
    expect(resolved.palettes).toEqual(DEFAULT_GIFT_PAGE.palettes);
  });

  it("keeps fixed age values and applies custom labels", () => {
    const resolved = resolveGiftPage(
      JSON.stringify({
        ages: [
          { value: "0-3m", label: "Newborn" },
          { value: "bogus", label: "Nope" },
        ],
      }),
    );
    expect(resolved.ages).toHaveLength(5);
    expect(resolved.ages[0]).toEqual({ value: "0-3m", label: "Newborn" });
    expect(resolved.ages.find((a) => a.value === "3-6m")?.label).toBe("3–6 m");
  });

  it("restores defaults when list fields are empty arrays", () => {
    const resolved = resolveGiftPage(
      JSON.stringify({ occasions: [], budgets: [], palettes: [], features: [] }),
    );
    expect(resolved.occasions).toEqual(DEFAULT_GIFT_PAGE.occasions);
    expect(resolved.budgets).toEqual(DEFAULT_GIFT_PAGE.budgets);
    expect(resolved.palettes).toEqual(DEFAULT_GIFT_PAGE.palettes);
    expect(resolved.features).toEqual(DEFAULT_GIFT_PAGE.features);
  });

  it("drops blank list entries and invalid budgets", () => {
    const resolved = resolveGiftPage(
      JSON.stringify({
        occasions: ["Welcome Baby", "  ", ""],
        palettes: ["", "Cream & Sage"],
        budgets: [
          { id: "ok", label: "Under ₹1,000", max: 1000 },
          { id: "", label: "Broken", max: 500 },
          { id: "zero", label: "Zero", max: 0 },
        ],
        steps: ["Only", "Three", "Steps"],
        inside_bullets: ["Box", ""],
      }),
    );
    expect(resolved.occasions).toEqual(["Welcome Baby"]);
    expect(resolved.palettes).toEqual(["Cream & Sage"]);
    expect(resolved.budgets).toEqual([{ id: "ok", label: "Under ₹1,000", max: 1000 }]);
    expect(resolved.steps).toEqual(DEFAULT_GIFT_PAGE.steps);
    expect(resolved.inside_bullets).toEqual(["Box"]);
  });

  it("round-trips serialize and resolve", () => {
    const json = serializeGiftPage(DEFAULT_GIFT_PAGE);
    expect(resolveGiftPage(json)).toEqual(DEFAULT_GIFT_PAGE);
  });

  it("validates required fields and unique budget ids", () => {
    expect(getGiftPageValidationErrors({ ...DEFAULT_GIFT_PAGE, hero_title: "" })).toContain(
      "Hero title is required.",
    );
    expect(
      getGiftPageValidationErrors({
        ...DEFAULT_GIFT_PAGE,
        budgets: [
          { id: "u2k", label: "A", max: 2000 },
          { id: "u2k", label: "B", max: 3000 },
        ],
      }),
    ).toEqual(expect.arrayContaining([expect.stringMatching(/unique/i)]));
    expect(
      getGiftPageValidationErrors({
        ...DEFAULT_GIFT_PAGE,
        budgets: [{ id: "bad", label: "Zero", max: 0 }],
      }),
    ).toEqual(expect.arrayContaining([expect.stringMatching(/greater than 0/i)]));
    expect(getGiftPageValidationErrors(DEFAULT_GIFT_PAGE)).toEqual([]);
    expect(
      getGiftPageValidationErrors({
        ...DEFAULT_GIFT_PAGE,
        steps: ["Only", "Four", "Steps", "Here"],
      }),
    ).toEqual(expect.arrayContaining([expect.stringMatching(/exactly 5/i)]));
    expect(
      getGiftPageValidationErrors({
        ...DEFAULT_GIFT_PAGE,
        occasions: ["Naming Ceremony", ""],
      }),
    ).toEqual(expect.arrayContaining([expect.stringMatching(/blank/i)]));
    expect(
      getGiftPageValidationErrors({
        ...DEFAULT_GIFT_PAGE,
        budgets: [
          { id: "a", label: "Same", max: 1000 },
          { id: "b", label: "Same", max: 2000 },
        ],
      }),
    ).toEqual(expect.arrayContaining([expect.stringMatching(/Label must be unique/i)]));
  });

  it("rejects invalid stored JSON strings", () => {
    expect(getGiftPageValidationErrors("{bad")).toEqual(["Gift page content is not valid JSON."]);
  });
});
