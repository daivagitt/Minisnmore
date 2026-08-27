import { listProducts, listBlogPosts, getPolicy, listCategories } from "@/lib/backend-data";
import { agesFromSizes, sortSizesAsc } from "@/lib/product-ages";
import { normalizeStorefrontColors, resolveStorefrontProductImages, hasPerColorGalleries } from "@/lib/product-colors";
import { type Product, type Fabric } from "@/data/products";
import { posts as seedPosts, type BlogPost } from "@/data/blog";
import { parseVariantsJson, storefrontListPricing } from "@/lib/product-variants";

/**
 * Storefront data layer.
 * Live catalog only — an empty or failed API response must not fall back to mock seed products.
 * Module-level caches dedupe in-flight requests across components.
 */

function fabricFromText(...inputs: (string | null | undefined)[]): Fabric {
  const hay = inputs.filter(Boolean).join(" ").toLowerCase();
  if (hay.includes("muslin") || hay.includes("gauze")) return "Muslin";
  if (hay.includes("linen")) return "Linen";
  if (hay.includes("knit") || hay.includes("wool")) return "Knit";
  if (hay.includes("bamboo")) return "Bamboo";
  if (hay.includes("organic") || hay.includes("cotton")) return "Organic Cotton";
  return "Organic Cotton";
}

function categoryFromAge(min: number | null | undefined): Product["category"] {
  const lo = typeof min === "number" ? min : 0;
  if (lo < 3) return "Newborn";
  if (lo < 12) return "Infant";
  return "Toddler";
}

function normalizeColors(raw: unknown) {
  return normalizeStorefrontColors(raw);
}

function normalizeSizes(raw: unknown): string[] {
  if (!Array.isArray(raw) || raw.length === 0) return ["One Size"];
  return sortSizesAsc(raw.map((s) => String(s)));
}

type DbProductRow = {
  id: string; slug: string; name: string;
  description: string | null; story: string | null;
  price: number; compare_at_price: number | null;
  images: unknown; sizes: unknown; colors: unknown;
  age_min_months: number | null; age_max_months: number | null;
  badge: string | null; featured: boolean | null;
  best_seller: boolean | null; new_arrival: boolean | null; trending: boolean | null;
  category_id: string | null;
  category_slug?: string | null;
  category_name?: string | null;
  fabric_type?: string | null;
  sku?: string | null;
  item_structure?: string | null;
  variants_json?: string | null;
  variants?: unknown;
  stock?: number | null;
};

type CategoryLookup = Map<string, { slug: string; name: string }>;

function buildCategoryLookup(rows: Awaited<ReturnType<typeof listCategories>>): CategoryLookup {
  const map: CategoryLookup = new Map();
  for (const row of rows) {
    const id = row.id ?? row.slug;
    if (!id) continue;
    map.set(String(id), { slug: row.slug.trim().toLowerCase(), name: row.name.trim() });
  }
  return map;
}

function resolveCategoryFields(
  row: DbProductRow,
  lookup: CategoryLookup,
): Pick<DbProductRow, "category_slug" | "category_name"> {
  const fromLookup = row.category_id ? lookup.get(String(row.category_id)) : undefined;
  return {
    category_slug: fromLookup?.slug || row.category_slug?.trim().toLowerCase() || null,
    category_name: fromLookup?.name || row.category_name?.trim() || null,
  };
}

function mapProduct(row: DbProductRow): Product {
  const colors = normalizeColors(row.colors);
  const { image: img, gallery } = resolveStorefrontProductImages(
    Array.isArray(row.images) ? (row.images as string[]) : [],
    colors,
  );
  const heroImage = img || "";
  const perPrint = hasPerColorGalleries(colors);
  const badge = (row.badge ?? "").toLowerCase();
  const variants = parseVariantsJson(row.variants ?? row.variants_json);
  const itemStructure = (row.item_structure ?? (variants.length ? "VARIANTS" : "SIMPLE")).toUpperCase();
  const catalogSizes = normalizeSizes(row.sizes);
  const parentPrice = Number(row.price) || 0;
  const parentCompareAt =
    row.compare_at_price && Number(row.compare_at_price) > parentPrice
      ? Number(row.compare_at_price)
      : undefined;
  const mapped: Product = {
    id: row.id,
    slug: row.slug,
    name: row.name,
    tagline: (row.description ?? row.story ?? "").trim(),
    price: parentPrice,
    compareAt: parentCompareAt,
    image: heroImage,
    gallery: gallery.length ? gallery : perPrint ? [] : heroImage ? [heroImage] : [],
    ages: agesFromSizes(catalogSizes, { min: row.age_min_months, max: row.age_max_months }),
    fabric: fabricFromText(row.fabric_type, row.description, row.story, row.name, row.badge),
    colors: normalizeColors(row.colors),
    sizes: catalogSizes,
    rating: 4.8,
    reviewCount: 0,
    category: categoryFromAge(row.age_min_months),
    categorySlug: row.category_slug?.trim().toLowerCase() || undefined,
    categoryName: row.category_name?.trim() || undefined,
    sku: row.sku?.trim() || undefined,
    itemStructure,
    variants: variants.length ? variants : undefined,
    stock: row.stock ?? null,
    isNew: !!row.new_arrival || badge.includes("new"),
    isBestseller: !!row.best_seller || badge.includes("best"),
    isTrending: !!row.trending,
    description: row.description ?? row.story ?? "",
  };
  // List/card price must match PDP default selection (parent price is min-of-matrix).
  const listPricing = storefrontListPricing(mapped);
  mapped.price = listPricing.price;
  mapped.compareAt = listPricing.compareAt;
  return mapped;
}

// ---------- Products cache ----------
let productsCache: Product[] | null = null;
let productsInflight: Promise<Product[]> | null = null;
const productSubs = new Set<(list: Product[]) => void>();

export function getCachedProducts(): Product[] | null { return productsCache; }

export async function loadProducts(): Promise<Product[]> {
  if (productsCache) return productsCache;
  if (productsInflight) return productsInflight;
  productsInflight = (async () => {
    try {
      const [rows, categoryRows] = await Promise.all([listProducts(), listCategories()]);
      const categoryLookup = buildCategoryLookup(categoryRows);
      const mapped = rows
        .filter((row) => row.active !== false)
        .map((row) => {
          const { category_slug, category_name } = resolveCategoryFields(row, categoryLookup);
          return mapProduct({
            id: String(row.id ?? row.slug),
            slug: row.slug,
            name: row.name,
            description: row.description,
            story: row.story,
            price: Number(row.price) || 0,
            compare_at_price: row.compare_at_price,
            images: row.images,
            sizes: row.sizes,
            colors: row.colors,
            age_min_months: row.age_min_months,
            age_max_months: row.age_max_months,
            badge: row.badge,
            featured: row.featured,
            best_seller: row.best_seller,
            new_arrival: row.new_arrival,
            trending: row.trending,
            category_id: row.category_id,
            category_slug,
            category_name,
            fabric_type: row.fabric_type,
            sku: row.sku,
            item_structure: row.item_structure,
            variants_json: row.variants_json,
            stock: row.stock,
          });
        });
      productsCache = mapped;
      productSubs.forEach((fn) => fn(mapped));
      return mapped;
    } catch (error) {
      if (import.meta.env.DEV) {
        console.error("loadProducts", error);
      }
      const empty: Product[] = [];
      productsCache = empty;
      productSubs.forEach((fn) => fn(empty));
      return empty;
    } finally {
      productsInflight = null;
    }
  })();
  return productsInflight;
}

export function subscribeProducts(fn: (p: Product[]) => void): () => void {
  productSubs.add(fn);
  return () => productSubs.delete(fn);
}

export function invalidateProducts() {
  productsCache = null;
  loadProducts();
}

// ---------- Blog cache ----------
type DbBlogRow = {
  slug: string; title: string; excerpt: string | null;
  content: string | null; cover_url: string | null; published_at: string | null;
};

function mapBlogPost(row: DbBlogRow): BlogPost {
  const cover = row.cover_url ?? seedPosts[0]?.cover ?? "";
  const body = (row.content ?? "")
    .split(/\n{2,}/)
    .map((p) => p.trim())
    .filter(Boolean);
  const date = row.published_at
    ? new Date(row.published_at).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" })
    : "";
  const words = (row.content ?? row.excerpt ?? "").split(/\s+/).filter(Boolean).length;
  return {
    slug: row.slug,
    title: row.title,
    excerpt: row.excerpt ?? "",
    cover,
    readMins: Math.max(2, Math.round(words / 200)),
    date,
    tag: "Blogs",
    body: body.length ? body : (row.excerpt ? [row.excerpt] : []),
  };
}

let blogCache: BlogPost[] | null = null;
let blogInflight: Promise<BlogPost[]> | null = null;
const blogSubs = new Set<(p: BlogPost[]) => void>();

export function getCachedBlogPosts(): BlogPost[] | null { return blogCache; }

export async function loadBlogPosts(): Promise<BlogPost[]> {
  if (blogCache) return blogCache;
  if (blogInflight) return blogInflight;
  blogInflight = (async () => {
    try {
      const rows = await listBlogPosts();
      const published = rows.filter((row) => row.published);
      const mapped = published.map((row) =>
        mapBlogPost({
          slug: row.slug,
          title: row.title,
          excerpt: row.excerpt,
          content: row.content,
          cover_url: row.cover_url,
          published_at: row.published_at,
        }),
      );
      const list = mapped.length > 0 ? mapped : seedPosts;
      blogCache = list;
      blogSubs.forEach((fn) => fn(list));
      return list;
    } catch (error) {
      if (import.meta.env.DEV) {
        console.error("loadBlogPosts", error);
      }
      blogCache = seedPosts;
      blogSubs.forEach((fn) => fn(seedPosts));
      return seedPosts;
    } finally {
      blogInflight = null;
    }
  })();
  return blogInflight;
}

export function subscribeBlogPosts(fn: (p: BlogPost[]) => void): () => void {
  blogSubs.add(fn);
  return () => blogSubs.delete(fn);
}

export function invalidateBlogPosts() {
  blogCache = null;
  loadBlogPosts();
}

// ---------- Policies cache (keyed by slug) ----------
export type PolicyRow = { slug: string; title: string; body: string | null };

const policyCache = new Map<string, PolicyRow | null>();
const policyInflight = new Map<string, Promise<PolicyRow | null>>();
const policySubs = new Map<string, Set<(p: PolicyRow | null) => void>>();

export function getCachedPolicy(slug: string): PolicyRow | null | undefined {
  return policyCache.get(slug);
}

export async function loadPolicy(slug: string): Promise<PolicyRow | null> {
  if (policyCache.has(slug)) return policyCache.get(slug)!;
  const inflight = policyInflight.get(slug);
  if (inflight) return inflight;
  const promise = (async () => {
    try {
      const row = await getPolicy(slug);
      const mapped: PolicyRow | null = row
        ? { slug: row.slug, title: row.title, body: row.body }
        : null;
      policyCache.set(slug, mapped);
      policySubs.get(slug)?.forEach((fn) => fn(mapped));
      return mapped;
    } catch (error) {
      if (import.meta.env.DEV) {
        console.error("loadPolicy", error);
      }
      policyCache.set(slug, null);
      policySubs.get(slug)?.forEach((fn) => fn(null));
      return null;
    } finally {
      policyInflight.delete(slug);
    }
  })();
  policyInflight.set(slug, promise);
  return promise;
}

export function subscribePolicy(slug: string, fn: (p: PolicyRow | null) => void): () => void {
  let set = policySubs.get(slug);
  if (!set) { set = new Set(); policySubs.set(slug, set); }
  set.add(fn);
  return () => { set!.delete(fn); };
}

export function invalidatePolicy(slug?: string) {
  if (slug) {
    policyCache.delete(slug);
    loadPolicy(slug);
  } else {
    policyCache.clear();
  }
}
