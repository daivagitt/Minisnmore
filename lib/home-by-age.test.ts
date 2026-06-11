import { describe, expect, it } from "vitest";
import {
  emptyHomeByAgeCard,
  getHomeByAgeCardValidationErrors,
  homeByAgeDraftsFromPayload,
  isBlankHomeByAgeCard,
  normalizeHomeByAgeDrafts,
  resolveHomeByAgeCards,
  validateHomeByAgeDrafts,
} from "./home-by-age";

describe("home-by-age", () => {
  it("parses legacy card shape with img and search.age", () => {
    const drafts = homeByAgeDraftsFromPayload({
      cards: [{ label: "Infant", note: "3–6M", img: "/infant.webp", search: { age: "3-6m" } }],
    });
    expect(drafts).toHaveLength(1);
    expect(drafts[0]?.age_filter).toBe("3-6m");
    expect(drafts[0]?.image).toBe("/infant.webp");
  });

  it("returns no cards when payload is empty", () => {
    expect(resolveHomeByAgeCards({})).toEqual([]);
    expect(resolveHomeByAgeCards({ cards: [] })).toEqual([]);
  });

  it("validates required card fields", () => {
    expect(isBlankHomeByAgeCard(emptyHomeByAgeCard())).toBe(true);
    expect(getHomeByAgeCardValidationErrors(emptyHomeByAgeCard(), 0)).toEqual(
      expect.arrayContaining([expect.stringMatching(/Category name is required/i)]),
    );
  });

  it("skips blank rows and normalizes saved cards", () => {
    const errors = validateHomeByAgeDrafts([
      emptyHomeByAgeCard(),
      {
        label: "Toddlers",
        note: "12–18M",
        image: "/toddler.webp",
        age_filter: "12-18m",
        sort_order: 0,
      },
    ]);
    expect(errors).toEqual([]);
    expect(normalizeHomeByAgeDrafts([
      emptyHomeByAgeCard(),
      {
        label: "Toddlers",
        note: "12–18M",
        image: "/toddler.webp",
        age_filter: "12-18m",
        sort_order: 3,
      },
    ])).toEqual([
      {
        label: "Toddlers",
        note: "12–18M",
        img: "/toddler.webp",
        to: "/shop",
        search: { age: "12-18m" },
      },
    ]);
  });

  it("sorts age cards by sort_order on the storefront", () => {
    const resolved = resolveHomeByAgeCards({
      cards: [
        { label: "Kidoo", note: "18–24M", image: "/kidoo.webp", age_filter: "18-24m", sort_order: 2 },
        { label: "Newborn", note: "0–3M", image: "/newborn.webp", age_filter: "0-3m", sort_order: 0 },
      ],
    });
    expect(resolved.map((c) => c.label)).toEqual(["Newborn", "Kidoo"]);
  });
});
