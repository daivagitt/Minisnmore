import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
  AdminPage,
  AdminPanelSection,
  AdminSectionToggles,
  Field,
  inputCls,
} from "@/components/admin/AdminPage";
import { useAdminSections } from "@/hooks/useAdminSections";
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
  LOVED_RIGHT_NOW_KEY,
  YOU_MAY_ALSO_LOVE_KEY,
  getHomeSectionProductSlugErrors,
  isMerchandiseProductKey,
  normalizeHomeSectionProductSlugs,
  normalizeMerchandisingProductLabel,
  productLimitForSection,
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
  loved_right_now: "Shop menu · Loved Right Now",
  you_may_also_love: "Product page · Pieces that pair beautifully",
};

/** Admin tooltips — what each homepage block does (from Kasasowjanya storefront). */
const SECTION_HINTS: Record<string, string> = {
  hero: "Top carousel. Slide images, CTAs and the three trust lines under each slide are managed under Admin → Homepage banners.",
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
  loved_right_now:
    "Shop mega-menu column. Title is the heading customers see. Product label replaces the Newborn line above each name. Pick up to three products, or leave empty to auto-pick.",
  you_may_also_love:
    "Product-page “Pieces that pair beautifully” strip. Pick up to four products, or leave empty to auto-pick similar pieces. The product being viewed is always excluded.",
};

function HomePage() {
  const [rows, setRows] = useState<Section[]>([]);
  const [products, setProducts] = useState<{ slug: string; name: string; sku: string }[]>([]);
  const [blogPosts, setBlogPosts] = useState<{ slug: string; title: string }[]>([]);
  const [loading, setLoading] = useState(true);
  const sections = useAdminSections();

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
    if (isMerchandiseProductKey(s.key)) {
      const limit = productLimitForSection(s.key);
      const knownSlugs = products.map((p) => p.slug);
      const slugs = pruneHomeSectionProductSlugs(sectionProductSlugs(s.payload, limit), knownSlugs, limit);
      const productErrors = getHomeSectionProductSlugErrors(slugs, limit);
      if (productErrors.length) {
        showValidationErrors(productErrors);
        return;
      }
      section = {
        ...s,
        payload: {
          ...s.payload,
          product_slugs: normalizeHomeSectionProductSlugs(slugs, limit),
          ...(s.key === LOVED_RIGHT_NOW_KEY
            ? { product_label: normalizeMerchandisingProductLabel(s.payload?.product_label) }
            : {}),
        },
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
      description="Show, hide, reorder and edit homepage blocks, plus Shop menu and product-page pairing picks."
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
          {rows.length > 0 && <AdminSectionToggles sections={sections} />}
          {rows.map((s, i) => {
            const productLimit = productLimitForSection(s.key);
            const slugs = pruneHomeSectionProductSlugs(
              sectionProductSlugs(s.payload, productLimit),
              products.map((p) => p.slug),
              productLimit,
            );
            const enabledLabel =
              s.key === LOVED_RIGHT_NOW_KEY
                ? "Show in shop menu"
                : s.key === YOU_MAY_ALSO_LOVE_KEY
                  ? "Show on product pages"
                  : "Show on homepage";
            const pickerHint =
              s.key === LOVED_RIGHT_NOW_KEY
                ? `Choose up to ${productLimit} products for the Shop mega-menu. Leave empty to auto-pick bestsellers, trending, and new arrivals.`
                : s.key === YOU_MAY_ALSO_LOVE_KEY
                  ? `Choose up to ${productLimit} products for “Pieces that pair beautifully”. Leave empty to auto-pick similar pieces.`
                  : undefined;
            return (
              <AdminPanelSection
                key={s.id}
                title={LABELS[s.key] ?? s.key}
                hint={SECTION_HINTS[s.key]}
                {...sections.panel(s.id)}
                actions={
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={s.enabled}
                      onChange={(e) => {
                        update(i, { enabled: e.target.checked });
                        sections.setOpen(s.id, true);
                      }}
                    />
                    {enabledLabel}
                  </label>
                }
              >
                <div className="grid sm:grid-cols-2 gap-3">
                  <Field
                    label="Title"
                    hint={
                      s.key === LOVED_RIGHT_NOW_KEY
                        ? "Shown as the Shop menu heading. Default is Loved Right Now."
                        : s.key === YOU_MAY_ALSO_LOVE_KEY
                          ? "Shown as the product-page heading. Default is Pieces that pair beautifully."
                          : s.key === "fabric_story"
                            ? "Optional. Leave blank to use the default Fabric story heading on the homepage."
                            : "Optional override — leave blank to use the built-in default for this block."
                    }
                  >
                    <input
                      className={inputCls()}
                      maxLength={ADMIN_NAME_MAX_LEN}
                      value={s.title ?? ""}
                      onChange={(e) => update(i, { title: e.target.value })}
                      placeholder={
                        s.key === LOVED_RIGHT_NOW_KEY
                          ? "Loved Right Now"
                          : s.key === YOU_MAY_ALSO_LOVE_KEY
                            ? "Pieces that pair beautifully"
                            : s.key === "fabric_story"
                              ? "Fabric story"
                              : undefined
                      }
                    />
                  </Field>
                  {s.key !== LOVED_RIGHT_NOW_KEY && (
                  <Field
                    label="Subtitle"
                    hint={
                      s.key === YOU_MAY_ALSO_LOVE_KEY
                        ? "Small line above the heading. Default is You may also love."
                        : "Optional supporting line under the section title on the homepage."
                    }
                  >
                    <input
                      className={inputCls()}
                      value={s.subtitle ?? ""}
                      onChange={(e) => update(i, { subtitle: e.target.value })}
                      placeholder={s.key === YOU_MAY_ALSO_LOVE_KEY ? "You may also love" : undefined}
                    />
                  </Field>
                  )}
                  {s.key === LOVED_RIGHT_NOW_KEY && (
                  <Field
                    label="Product label"
                    hint="Small uppercase line above each product name (the “Newborn” text). Leave blank to keep each product’s category."
                  >
                    <input
                      className={inputCls()}
                      maxLength={ADMIN_NAME_MAX_LEN}
                      value={typeof s.payload?.product_label === "string" ? s.payload.product_label : ""}
                      onChange={(e) =>
                        update(i, { payload: { ...s.payload, product_label: e.target.value } })
                      }
                      placeholder="Newborn"
                    />
                  </Field>
                  )}
                </div>
                <NumberField
                  label="Sort order"
                  value={s.sort_order}
                  onChange={(sort_order) => update(i, { sort_order })}
                  min={0}
                  required
                />

                {isMerchandiseProductKey(s.key) && (
                  <HomeSectionProductsPicker
                    products={products}
                    slugs={slugs}
                    limit={productLimit}
                    hint={pickerHint}
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
              </AdminPanelSection>
            );
          })}
        </div>
      )}
    </AdminPage>
  );
}
