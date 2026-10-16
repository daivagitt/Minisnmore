import { describe, expect, it } from "vitest";
import {
  HOME_BLOG_STRIP_LIMIT,
  getHomeBlogStripSlugErrors,
  homeBlogCardsFromPayload,
  normalizeHomeBlogCards,
  normalizeHomeBlogStripSlugs,
  resolveHomeBlogStripPosts,
  sectionBlogSlugs,
} from "./home-blog-strip";

const post = (slug: string) => ({
  slug,
  title: slug,
  excerpt: null,
  cover: null,
  date: "May 1",
  tag: "Blog",
});

describe("home-blog-strip", () => {
  it("reads blog slugs from section payload", () => {
    expect(sectionBlogSlugs({ blog_slugs: ["a", "", 1, "b"] })).toEqual(["a", "b"]);
    expect(sectionBlogSlugs({})).toEqual([]);
  });

  it("reads blog cards with sort order", () => {
    expect(
      homeBlogCardsFromPayload({
        blog_cards: [
          { slug: "b", sort_order: 2 },
          { slug: "a", sort_order: 1 },
        ],
      }),
    ).toEqual([
      { slug: "a", sort_order: 1 },
      { slug: "b", sort_order: 2 },
    ]);
    expect(sectionBlogSlugs({ blog_cards: [{ slug: "b", sort_order: 2 }, { slug: "a", sort_order: 1 }] })).toEqual([
      "a",
      "b",
    ]);
  });

  it("caps picked slugs at the homepage limit", () => {
    const slugs = ["a", "b", "c", "d", "e", "f"];
    expect(normalizeHomeBlogStripSlugs(slugs)).toEqual(["a", "b", "c", "d", "e"]);
    expect(
      getHomeBlogStripSlugErrors(slugs.map((slug, sort_order) => ({ slug, sort_order }))),
    ).toEqual([`Pick at most ${HOME_BLOG_STRIP_LIMIT} blog posts for this section.`]);
    expect(normalizeHomeBlogCards(slugs.map((slug, sort_order) => ({ slug, sort_order })))).toHaveLength(
      HOME_BLOG_STRIP_LIMIT,
    );
  });

  it("uses admin-picked posts when provided", () => {
    const all = [post("a"), post("b"), post("c")];
    const picked = resolveHomeBlogStripPosts(all, ["c", "missing", "a"]);
    expect(picked.map((p) => p.slug)).toEqual(["c", "a"]);
  });

  it("shows latest API posts when no slugs are set", () => {
    const all = ["a", "b", "c", "d", "e", "f"].map(post);
    const auto = resolveHomeBlogStripPosts(all, []);
    expect(auto.map((p) => p.slug)).toEqual(["a", "b", "c", "d", "e"]);
  });

  it("shows nothing when the API list is empty", () => {
    expect(resolveHomeBlogStripPosts([], [])).toEqual([]);
  });
});
