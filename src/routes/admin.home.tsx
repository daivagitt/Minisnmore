import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { NumberField } from "@/components/admin/NumberField";
import { HomeByAgeCardsEditor } from "@/components/admin/HomeByAgeCardsEditor";
import { HomeHeroBannersPanel } from "@/components/admin/HomeHeroBannersPanel";
import { HomeBlogStripEditor } from "@/components/admin/HomeBlogStripEditor";
import { HomeReviewsEditor } from "@/components/admin/HomeReviewsEditor";
import { HomeSectionProductsPicker } from "@/components/admin/HomeSectionProductsPicker";
import { HomeWatchShopEditor } from "@/components/admin/HomeWatchShopEditor";
import { toast } from "sonner";
import { ADMIN_NAME_MAX_LEN } from "@/lib/backend-data";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";
import { Save } from "lucide-react";
import {
  listBlogPosts,
  listHomeSections,
  listProducts,
  getHomeSectionValidationErrors,
  saveHomeSection,
  type HomeSectionRow,
} from "@/lib/backend-data";
import { invalidateHomeSections } from "@/hooks/useHomeSections";
import { invalidateProducts } from "@/lib/storefront";
import {
  HOME_SECTION_PRODUCT_KEYS,
  getHomeSectionProductSlugErrors,
  normalizeHomeSectionProductSlugs,
  pruneHomeSectionProductSlugs,
  sectionProductSlugs,
} from "@/lib/home-products";
import {
  homeByAgeDraftsFromPayload,
  homeByAgeDraftsToPayload,
  validateHomeByAgeDrafts,
  type HomeByAgeCardDraft,
} from "@/lib/home-by-age";
import {
  validateWatchReelDrafts,
  watchReelDraftsFromPayload,
  watchReelsToPayload,
  type WatchReelDraft,
} from "@/lib/watch-reels";
import {
  getHomeBlogStripSlugErrors,
  homeBlogCardsFromPayload,
  homeBlogCardsToPayload,
  normalizeHomeBlogCards,
  type HomeBlogCardDraft,
} from "@/lib/home-blog-strip";
import {
  normalizeHomeReviewDrafts,
  validateHomeReviewDrafts,
  homeReviewDraftsForEditor,
  type HomeReviewDraft,
} from "@/lib/home-reviews";

export const Route = createFileRoute("/admin/home")({ component: HomePage });

type Section = HomeSectionRow;

const LABELS: Record<string, string> = {
  hero: "Hero banner",
  by_age: "Shop by age",
  best_sellers: "Best sellers",
  trending: "Trending now",
  new_arrivals: "New arrivals",
  featured: "Featured",
  watch_shop: "Watch & Shop",
  fabric_story: "Fabric story",
  brand_story: "Our story",
  reviews: "Mom reviews",
  write_to_us: "Write to us (moved)",
  journal_strip: "Blog",
  gifts: "Gifts",
  gift_concierge: "Gift (legacy)",
};

/** Admin tooltips — what each homepage block does (from Kasasowjanya storefront). */
const SECTION_HINTS: Record<string, string> = {
  hero: "Top carousel. Slide images and CTAs are managed under Admin → Homepage banners.",
  by_age: "Age category cards shown in a grid. Add cards below or leave empty for built-in defaults.",
  best_sellers: "Shows five products on the homepage. Pick up to five below or leave empty for auto bestsellers.",
  trending: "Shows five trending products. Pick up to five or leave empty for automatic picks.",
  new_arrivals: "Shows five new arrivals. Pick up to five or leave empty for new-arrival flagged items.",
  featured: "Featured tab strip — up to five products per tab when manually picked.",
  watch_shop:
    "Shoppable video reels. Add a public MP4 link for each reel and relate it to a product SKU.",
  fabric_story:
    "Organic fabric video + copy beside it. Optional title/subtitle override the default “Fabric story” heading.",
  brand_story: "Founders / mission block with photo. Title overrides the default “Our story” heading.",
  reviews: "Scrolling mom reviews. Edit review cards in the editor below — at least one row recommended.",
  write_to_us: "Moved to the Contact Us page (/contact-us). This homepage block is no longer shown.",
  journal_strip:
    "Blog teaser strip on the homepage. Add up to five blog cards below or leave empty for the latest posts.",
  gifts: "Shows five gift picks on the homepage. Pick up to five below or leave empty for gift-set category items.",
  gift_concierge: "Legacy concierge block — usually hidden on the live storefront.",
};

function HomePage() {
  const [rows, setRows] = useState<Section[]>([]);
  const [products, setProducts] = useState<{ slug: string; name: string; sku: string }[]>([]);
  const [blogPosts, setBlogPosts] = useState<{ slug: string; title: string }[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    const [sectionsResult, productsResult, blogsResult] = await Promise.allSettled([
      listHomeSections(true),
      listProducts(true),
      listBlogPosts(true),
    ]);
    if (sectionsResult.status === "fulfilled") {
      setRows(sectionsResult.value);
    } else {
      showApiError(sectionsResult.reason, "Could not load home sections");
    }
    if (productsResult.status === "fulfilled") {
      setProducts(
        productsResult.value.map(({ slug, name, sku }) => ({ slug, name, sku: sku?.trim() ?? "" })),
      );
    } else {
      showApiError(productsResult.reason, "Could not load products for this page");
    }
    if (blogsResult.status === "fulfilled") {
      setBlogPosts(
        blogsResult.value.map(({ slug, title, published }) => ({
          slug,
          title: published ? title : `${title} (draft)`,
        })),
      );
    } else {
      showApiError(blogsResult.reason, "Could not load blog posts for this page");
    }
    setLoading(false);
  };
  useEffect(() => {
    load();
  }, []);

  const update = (i: number, patch: Partial<Section>) =>
    setRows((r) => r.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  const save = async (s: Section) => {
    let section = s;
    const sectionErrors = getHomeSectionValidationErrors(section);
    if (sectionErrors.length) {
      showValidationErrors(sectionErrors);
      return;
    }
    if ((HOME_SECTION_PRODUCT_KEYS as readonly string[]).includes(s.key)) {
      const knownSlugs = products.map((p) => p.slug);
      const slugs = pruneHomeSectionProductSlugs(sectionProductSlugs(s.payload), knownSlugs);
      const productErrors = getHomeSectionProductSlugErrors(slugs);
      if (productErrors.length) {
        showValidationErrors(productErrors);
        return;
      }
      section = {
        ...s,
        payload: { ...s.payload, product_slugs: normalizeHomeSectionProductSlugs(slugs) },
      };
    }
    if (s.key === "by_age") {
      const drafts = homeByAgeDraftsFromPayload(s.payload);
      const errors = validateHomeByAgeDrafts(drafts);
      if (errors.length) {
        showValidationErrors(errors);
        return;
      }
      section = {
        ...s,
        payload: { ...s.payload, cards: homeByAgeDraftsToPayload(drafts) },
      };
    }
    if (s.key === "journal_strip") {
      const cards = homeBlogCardsFromPayload(s.payload);
      const blogErrors = getHomeBlogStripSlugErrors(cards);
      if (blogErrors.length) {
        showValidationErrors(blogErrors);
        return;
      }
      const normalized = normalizeHomeBlogCards(cards);
      section = {
        ...s,
        payload: {
          ...s.payload,
          blog_cards: homeBlogCardsToPayload(normalized),
          blog_slugs: normalized.map((card) => card.slug),
        },
      };
    }
    if (s.key === "watch_shop") {
      const drafts = watchReelDraftsFromPayload(s.payload);
      const errors = validateWatchReelDrafts(drafts);
      if (errors.length) {
        showValidationErrors(errors);
        return;
      }
      section = {
        ...s,
        payload: { ...s.payload, reels: watchReelsToPayload(drafts) },
      };
    }
    if (s.key === "reviews") {
      const drafts = homeReviewDraftsForEditor(s.payload);
      const errors = validateHomeReviewDrafts(drafts);
      if (errors.length) {
        showValidationErrors(errors);
        return;
      }
      section = {
        ...s,
        payload: { ...s.payload, reviews: normalizeHomeReviewDrafts(drafts) },
      };
    }
    try {
      await saveHomeSection(section);
      invalidateHomeSections();
      invalidateProducts();
      toast.success("Saved");
    } catch (error) {
      showApiError(error, "Could not save section");
    }
  };

  return (
    <AdminPage
      eyebrow="Home page"
      title="Home sections"
      description="Show, hide, reorder and edit each block on your homepage. Pick which products appear in each list."
    >
      {loading ? (
        <div className="text-sm text-muted-foreground">Loading…</div>
      ) : (
        <div className="space-y-3 sm:space-y-4">
          {rows.length === 0 && (
            <div className="glass rounded-2xl p-5 sm:p-6 text-sm text-muted-foreground text-center max-w-lg mx-auto">
              No home sections found. Run the database seed script or contact your developer to
              initialize default homepage blocks.
            </div>
          )}
          {rows.map((s, i) => {
            const slugs = pruneHomeSectionProductSlugs(
              sectionProductSlugs(s.payload),
              products.map((p) => p.slug),
            );
            return (
              <div key={s.id} className="glass-strong rounded-2xl sm:rounded-3xl p-4 sm:p-5 space-y-3 sm:space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="font-display text-base sm:text-lg">{LABELS[s.key] ?? s.key}</div>
                    {SECTION_HINTS[s.key] ? (
                      <p className="text-[11px] text-muted-foreground mt-1.5 max-w-xl leading-snug">
                        {SECTION_HINTS[s.key]}
                      </p>
                    ) : null}
                  </div>
                  <label className="flex items-center gap-2 text-sm shrink-0 self-start sm:self-auto">
                    <input
                      type="checkbox"
                      checked={s.enabled}
                      onChange={(e) => update(i, { enabled: e.target.checked })}
                    />
                    Show on homepage
                  </label>
                </div>
                <div className="grid sm:grid-cols-2 gap-3">
                  <Field
                    label="Title"
                    hint={
                      s.key === "fabric_story"
                        ? "Optional. Leave blank to use the default Fabric story heading on the homepage."
                        : "Optional override — leave blank to use the built-in default for this block."
                    }
                  >
                    <input
                      className={inputCls()}
                      maxLength={ADMIN_NAME_MAX_LEN}
                      value={s.title ?? ""}
                      onChange={(e) => update(i, { title: e.target.value })}
                      placeholder={s.key === "fabric_story" ? "Fabric story" : undefined}
                    />
                  </Field>
                  <Field
                    label="Subtitle"
                    hint="Optional supporting line under the section title on the homepage."
                  >
                    <input
                      className={inputCls()}
                      value={s.subtitle ?? ""}
                      onChange={(e) => update(i, { subtitle: e.target.value })}
                    />
                  </Field>
                </div>
                <NumberField
                  label="Sort order"
                  value={s.sort_order}
                  onChange={(sort_order) => update(i, { sort_order })}
                  min={0}
                  required
                />

                {(HOME_SECTION_PRODUCT_KEYS as readonly string[]).includes(s.key) && (
                  <HomeSectionProductsPicker
                    products={products}
                    slugs={slugs}
                    onChange={(product_slugs) =>
                      update(i, { payload: { ...s.payload, product_slugs } })
                    }
                  />
                )}

                {s.key === "by_age" && (
                  <HomeByAgeCardsEditor
                    payload={s.payload}
                    onChange={(cards: HomeByAgeCardDraft[]) =>
                      update(i, { payload: { ...s.payload, cards } })
                    }
                  />
                )}

                {s.key === "hero" && <HomeHeroBannersPanel />}

                {s.key === "journal_strip" && (
                  <HomeBlogStripEditor
                    posts={blogPosts}
                    payload={s.payload}
                    onChange={(blog_cards: HomeBlogCardDraft[]) =>
                      update(i, {
                        payload: {
                          ...s.payload,
                          blog_cards,
                          blog_slugs: blog_cards.map((card) => card.slug).filter(Boolean),
                        },
                      })
                    }
                  />
                )}

                {s.key === "watch_shop" && (
                  <HomeWatchShopEditor
                    products={products}
                    payload={s.payload}
                    onChange={(reels: WatchReelDraft[]) =>
                      update(i, { payload: { ...s.payload, reels } })
                    }
                  />
                )}

                {s.key === "reviews" && (
                  <HomeReviewsEditor
                    payload={s.payload}
                    onChange={(reviews: HomeReviewDraft[]) =>
                      update(i, { payload: { ...s.payload, reviews } })
                    }
                  />
                )}

                <button
                  onClick={() => save(s)}
                  className="w-full sm:w-auto rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center justify-center gap-2"
                >
                  <Save className="h-4 w-4" /> Save
                </button>
              </div>
            );
          })}
        </div>
      )}
    </AdminPage>
  );
}
