import { ages, fabrics, type AgeRange, type Fabric, type Product } from "@/data/products";
import { productMatchesQuery, normalizeSearchQuery } from "@/lib/product-search";
import { z } from "zod";

const SORT_VALUES = ["new", "lo", "hi"] as const;
export type ShopSort = (typeof SORT_VALUES)[number];

const ageEnum = z.enum(ages as [AgeRange, ...AgeRange[]]);
const fabricEnum = z.enum(fabrics as [Fabric, ...Fabric[]]);
const sortEnum = z.enum(SORT_VALUES);

export type ShopSearch = {
  q?: string;
  category?: string;
  age?: AgeRange;
  fabric?: Fabric;
  sort?: ShopSort;
};

export function parseShopSearch(raw: unknown): ShopSearch {
  const input = raw && typeof raw === "object" ? (raw as Record<string, unknown>) : {};
  const qRaw = typeof input.q === "string" ? normalizeSearchQuery(input.q) : "";
  const category =
    typeof input.category === "string" && input.category.trim() ? input.category.trim() : undefined;
  const age = ageEnum.safeParse(input.age).success ? ageEnum.parse(input.age) : undefined;
  const fabric = fabricEnum.safeParse(input.fabric).success ? fabricEnum.parse(input.fabric) : undefined;
  const sort = sortEnum.safeParse(input.sort).success ? sortEnum.parse(input.sort) : "new";
  const out: ShopSearch = { sort };
  if (qRaw) out.q = qRaw;
  if (category) out.category = category;
  if (age) out.age = age;
  if (fabric) out.fabric = fabric;
  return out;
}

export function slugify(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

/** Collapse hyphens so e.g. `t-shirt` and `tshirt` match the same category filter. */
export function normalizeCategorySlug(value: string): string {
  return slugify(value).replace(/-/g, "");
}

export function productCategorySlug(product: Product): string {
  if (product.categorySlug?.trim()) return slugify(product.categorySlug);
  if (product.categoryName?.trim()) return slugify(product.categoryName);
  return slugify(product.category);
}

export function productMatchesCategory(product: Product, category?: string): boolean {
  if (!category?.trim()) return true;
  const target = normalizeCategorySlug(category);
  const candidates = [product.categorySlug, product.categoryName, product.category].filter(
    (value): value is string => !!value?.trim(),
  );
  return candidates.some((value) => normalizeCategorySlug(value) === target);
}

export function productMatchesAge(product: Product, age?: AgeRange): boolean {
  if (!age) return true;
  return product.ages.includes(age);
}

export function productMatchesFabric(product: Product, fabric?: Fabric): boolean {
  if (!fabric) return true;
  return product.fabric === fabric;
}

export function filterProducts(products: Product[], filters: ShopSearch): Product[] {
  return products.filter(
    (p) =>
      productMatchesQuery(p, filters.q ?? "") &&
      productMatchesCategory(p, filters.category) &&
      productMatchesAge(p, filters.age) &&
      productMatchesFabric(p, filters.fabric),
  );
}

export function sortProducts(products: Product[], sort: ShopSort = "new"): Product[] {
  const list = [...products];
  if (sort === "lo") return list.sort((a, b) => a.price - b.price);
  if (sort === "hi") return list.sort((a, b) => b.price - a.price);
  return list.sort((a, b) => {
    const aNew = a.isNew ? 1 : 0;
    const bNew = b.isNew ? 1 : 0;
    if (bNew !== aNew) return bNew - aNew;
    return a.name.localeCompare(b.name);
  });
}

export function filterAndSortProducts(products: Product[], filters: ShopSearch): Product[] {
  return sortProducts(filterProducts(products, filters), filters.sort ?? "new");
}

/** Build router search object; omits empty values for clean URLs. */
export function buildShopSearch(next: Partial<ShopSearch>): ShopSearch {
  const parsed = parseShopSearch(next);
  const out: ShopSearch = { sort: parsed.sort ?? "new" };
  if (parsed.q) out.q = parsed.q;
  if (parsed.category) out.category = parsed.category;
  if (parsed.age) out.age = parsed.age;
  if (parsed.fabric) out.fabric = parsed.fabric;
  return out;
}

export function hasActiveFilters(filters: ShopSearch): boolean {
  return !!(filters.q || filters.category || filters.age || filters.fabric);
}

/** All catalog fabrics available as shop chips. */
export const SHOP_FILTER_FABRICS: Fabric[] = [...fabrics];

/** Human labels for age filter chips (aligned with homepage age bands). */
export const SHOP_AGE_FILTERS: { value: AgeRange; label: string }[] = [
  { value: "0-3m", label: "0–3M" },
  { value: "3-6m", label: "3–6M" },
  { value: "6-12m", label: "6–12M" },
  { value: "12-18m", label: "12–18M" },
  { value: "18-24m", label: "18–24M" },
];
