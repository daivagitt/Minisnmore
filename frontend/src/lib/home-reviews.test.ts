import { describe, expect, it } from "vitest";
import {
  emptyHomeReview,
  defaultHomeReviewDrafts,
  getHomeReviewValidationErrors,
  homeReviewDraftsForEditor,
  homeReviewDraftsFromPayload,
  isBlankHomeReview,
  normalizeHomeReviewDrafts,
  resolveHomeReviews,
  reviewsFromHomePayload,
  validateHomeReviewDrafts,
} from "./home-reviews";

describe("home-reviews", () => {
  it("parses valid reviews from home section payload", () => {
    const parsed = reviewsFromHomePayload({
      reviews: [
        {
          title: "Lovely",
          body: "So soft and gentle on newborn skin.",
          rating: 5,
          author: "Test Mom",
          date: "May 2025",
          photo: "/mom.jpg",
        },
        { title: "Missing body", rating: 5, author: "X" },
      ],
    });
    expect(parsed).toHaveLength(1);
    expect(parsed[0]?.author).toBe("Test Mom");
  });

  it("falls back to defaults when payload is empty", () => {
    expect(reviewsFromHomePayload({})).toEqual([]);
    expect(resolveHomeReviews({}).length).toBeGreaterThan(0);
  });

  it("loads editable drafts from payload", () => {
    const drafts = homeReviewDraftsFromPayload({
      reviews: [{ title: "Draft", body: "Text here", author: "Mom", rating: 4, date: "", photo: "" }],
    });
    expect(drafts).toHaveLength(1);
    expect(drafts[0]?.title).toBe("Draft");
  });

  it("pre-fills admin editor with default mom reviews when payload is empty", () => {
    expect(defaultHomeReviewDrafts().length).toBe(5);
    expect(homeReviewDraftsForEditor({})).toHaveLength(5);
    expect(homeReviewDraftsForEditor({ reviews: [] })).toHaveLength(5);
    expect(homeReviewDraftsForEditor({ reviews: [{ title: "Saved", body: "Already in CMS", author: "Mom", rating: 5, date: "", photo: "" }] })).toHaveLength(1);
  });

  it("validates required review fields", () => {
    expect(isBlankHomeReview(emptyHomeReview())).toBe(true);
    expect(getHomeReviewValidationErrors(emptyHomeReview(), 0)).toEqual(
      expect.arrayContaining([expect.stringMatching(/Headline is required/i)]),
    );
    expect(
      getHomeReviewValidationErrors(
        { ...emptyHomeReview(), title: "Hi", body: "Too short", author: "12", rating: 5 },
        0,
      ),
    ).toEqual(
      expect.arrayContaining([
        expect.stringMatching(/10 characters/i),
        expect.stringMatching(/letter/i),
      ]),
    );
  });

  it("keeps half-star ratings when parsing and saving", () => {
    const parsed = reviewsFromHomePayload({
      reviews: [
        {
          title: "Almost perfect",
          body: "Loved the fabric, just a little long in the sleeves.",
          rating: 4.5,
          author: "Test Mom",
          date: "May 2025",
          photo: "/mom.jpg",
        },
      ],
    });
    expect(parsed[0]?.rating).toBe(4.5);
    expect(
      normalizeHomeReviewDrafts([
        {
          title: "Almost perfect",
          body: "Loved the fabric, just a little long in the sleeves.",
          author: "Test Mom",
          date: "May 2025",
          rating: 4.5,
          photo: "/mom.jpg",
        },
      ])[0]?.rating,
    ).toBe(4.5);
    expect(getHomeReviewValidationErrors({ ...emptyHomeReview(), title: "Hi", body: "Long enough review text", author: "Mom", rating: 4.7 })).toEqual(
      expect.arrayContaining([expect.stringMatching(/half-star/i)]),
    );
  });

  it("skips blank rows and normalizes saved reviews", () => {
    const errors = validateHomeReviewDrafts([
      emptyHomeReview(),
      {
        title: "Great set",
        body: "We loved the fabric quality and fit.",
        author: "Ananya M.",
        date: "Feb 2025",
        rating: 5,
        photo: "",
      },
    ]);
    expect(errors).toEqual([]);
    expect(normalizeHomeReviewDrafts([
      emptyHomeReview(),
      {
        title: "Great set",
        body: "We loved the fabric quality and fit.",
        author: "Ananya M.",
        date: "Feb 2025",
        rating: 5,
        photo: "",
      },
    ])).toHaveLength(1);
  });
});
