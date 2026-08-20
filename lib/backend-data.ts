import { api, ApiError } from "@/lib/api";
import { numberFieldOr, requireNumberField } from "@/lib/admin-number-field";
import { validateVariantRows } from "@/lib/admin-variants";
import { internalSitePathError } from "@/lib/internal-links";
import {
  isExampleOrPlaceholderUrl,
  isPrivateObjectStorageUrl,
  optionalPublicUrlFieldError,
  requiredPublicUrlFieldError,
  sanitizePublicUrl,
  toDirectDownloadUrl,
} from "@/lib/utils";
import { assertAllowedImageFile, type AdminImageFolder } from "@/lib/admin-image";
import {
  collectStorefrontCardImages,
  buildColorImagesJson,
  mergeColorImagesIntoColors,
  normalizeColorEntries,
  parseProductColors,
  resolveProductImages,
  serializeProductColors,
  type ProductColorEntry,
} from "@/lib/product-colors";
import {
  applyExpectedPrintNamesToColors,
  applyExpectedPrintNamesToVariantsJson,
} from "@/lib/expected-prints";

type Id = string | number;
type JsonRecord = Record<string, unknown>;
const SLUG_RE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const SLUG_HAS_LETTER_RE = /[a-z]/;
const NAME_HAS_LETTER_RE = /\p{L}/u;

const hasAdminPathId = (id?: Id) => id !== undefined && id !== null && id !== "";
const adminPath = (resource: string, id?: Id) =>
  `/api/v1/admin/${resource}${hasAdminPathId(id) ? `/${id}` : ""}`;

function parseJson<T>(value: unknown, fallback: T): T {
  if (Array.isArray(value) || (value && typeof value === "object")) return value as T;
  if (typeof value !== "string" || !value.trim()) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

function nullable(value: unknown) {
  return value === "" || value === undefined ? null : value;
}

function idValue(id?: Id) {
  return id == null || id === "" ? undefined : Number(id);
}

export const ADMIN_NAME_MAX_LEN = 50;
export const PRODUCT_NAME_MAX_LEN = 200;

const LABEL_MIN_LEN = 2;
const LABEL_MAX_LEN = ADMIN_NAME_MAX_LEN;
const BODY_MIN_LEN = 20;

/** Shared slug rules for categories, products, blogs, policies. */
function slugFieldError(slug: string, label = "Slug"): string | null {
  const s = slug.trim().toLowerCase();
  if (!s) return `${label} is required.`;
  if (!SLUG_RE.test(s)) return `${label} must be lowercase with hyphens only.`;
  if (!SLUG_HAS_LETTER_RE.test(s)) {
    return `${label} must include at least one letter (e.g. newborn-essentials).`;
  }
  return null;
}

/** Shared name/title rules for categories, products, blogs, banners, policies. */
function labelFieldError(
  value: string,
  label: string,
  minLen = LABEL_MIN_LEN,
  maxLen = LABEL_MAX_LEN,
): string | null {
  const text = value.trim();
  if (!text) return `${label} is required.`;
  if (text.length < minLen) return `${label} must be at least ${minLen} characters.`;
  if (text.length > maxLen) return `${label} must be ${maxLen} characters or fewer.`;
  if (!NAME_HAS_LETTER_RE.test(text)) return `${label} must contain at least one letter.`;
  return null;
}

/** Shared body/content rules for blogs and policies. */
function bodyFieldError(body: string, label: string, minLen = BODY_MIN_LEN): string | null {
  const text = body.trim();
  if (!text) return `${label} is required.`;
  if (text.length < minLen) return `${label} must be at least ${minLen} characters.`;
  if (!NAME_HAS_LETTER_RE.test(text)) return `${label} must include readable text (at least one letter).`;
  return null;
}

export function getCategoryValidationErrors(row: CategoryRow): string[] {
  const errors: string[] = [];
  const name = row.name?.trim() ?? "";
  const slug = row.slug?.trim().toLowerCase() ?? "";
  const nameError = labelFieldError(name, "Category name");
  if (nameError) errors.push(nameError);
  const slugError = slugFieldError(slug, "Category slug");
  if (slugError) errors.push(slugError);
  const sortError = requireNumberField(row.sort_order, "Sort order", { allowZero: true });
  if (sortError) errors.push(sortError);
  const imageError = optionalPublicUrlFieldError(row.image_url, "Category image");
  if (imageError) errors.push(imageError);
  return errors;
}

function assertValidCategory(row: CategoryRow) {
  const errors = getCategoryValidationErrors(row);
  if (errors.length) throw new Error(errors.join("\n"));
}

function normalizeProductImageUrl(raw: unknown) {
  let trimmed = String(raw ?? "").trim();
  if (!trimmed) return null;
  trimmed = trimmed.replace(/^["']+|["']+$/g, "").trim();
  if (!trimmed) return null;
  if (isPrivateObjectStorageUrl(trimmed)) return null;

  const withProtocol =
    /^https?:\/\//i.test(trimmed) || trimmed.startsWith("/") ? trimmed : `https://${trimmed}`;
  const direct = toDirectDownloadUrl(withProtocol);
  if (isExampleOrPlaceholderUrl(direct)) return null;

  const sanitized = sanitizePublicUrl(direct);
  if (sanitized) return sanitized;

  if (direct.startsWith("/")) return direct;
  try {
    const url = new URL(direct);
    if (["localhost", "127.0.0.1", "0.0.0.0"].includes(url.hostname)) return null;
    if (!/^https?:$/i.test(url.protocol)) return null;
    return direct;
  } catch {
    return null;
  }
}

/** Parse pipe/comma/semicolon/newline-separated image URLs from spreadsheet cells. */
export function parseSpreadsheetImageUrls(raw: unknown): string[] {
  const text = String(raw ?? "").trim();
  if (!text) return [];
  const parts = text
    .split(/[|,;\n]+/)
    .map((part) => part.trim())
    .filter(Boolean);
  const out: string[] = [];
  const seen = new Set<string>();
  for (const part of parts) {
    const url = normalizeProductImageUrl(part);
    if (url && !seen.has(url)) {
      seen.add(url);
      out.push(url);
    }
  }
  return out;
}

export { normalizeProductImageUrl };

export function getProductValidationErrors(row: ProductRow): string[] {
  const errors: string[] = [];
  const name = row.name?.trim() ?? "";
  const slug = row.slug?.trim().toLowerCase() ?? "";
  const sku = row.sku?.trim() ?? "";

  const nameError = labelFieldError(name, "Product name", LABEL_MIN_LEN, PRODUCT_NAME_MAX_LEN);
  if (nameError) errors.push(nameError);
  const slugError = slugFieldError(slug, "Product slug");
  if (slugError) errors.push(slugError);

  if (!sku) errors.push("SKU is required.");
  if (!row.category_id) errors.push("Category is required.");
  const priceError = requireNumberField(row.price, "Price");
  if (priceError) errors.push(priceError);
  const stockError = requireNumberField(row.stock, "Stock", { allowZero: true });
  if (stockError) errors.push(stockError);
  const ageMinError = requireNumberField(row.age_min_months, "Age min (months)", { allowZero: true });
  if (ageMinError) errors.push(ageMinError);
  const ageMaxError = requireNumberField(row.age_max_months, "Age max (months)", { allowZero: true });
  if (ageMaxError) errors.push(ageMaxError);
  else if (
    row.age_min_months != null &&
    row.age_max_months != null &&
    Number(row.age_max_months) < Number(row.age_min_months)
  ) {
    errors.push("Age max (months) must be greater than or equal to age min.");
  }

  const fabric = row.fabric_type?.trim() ?? "";
  if (!fabric) errors.push("Fabric type is required.");

  if ((row.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS") {
    errors.push(...validateVariantRows(row.variants_json));
  }

  // Product description is optional (CSV import + Ledgerly pull may omit it).

  const images = Array.isArray(row.images) ? row.images : [];
  if (images.length > 0) {
    const privateStorage = images.filter(
      (raw) => typeof raw === "string" && isPrivateObjectStorageUrl(raw.trim()),
    );
    if (privateStorage.length > 0) {
      errors.push(
        "One or more image URLs use the private storage API host. Re-upload images after S3_PUBLIC_BASE_URL is set to your public R2 URL (r2.dev or CDN).",
      );
    }
    const validImageCount = images.filter((raw) => !!normalizeProductImageUrl(raw)).length;
    if (validImageCount === 0 && privateStorage.length === 0) {
      errors.push("Product images must be valid URLs when provided.");
    }
  }

  return errors;
}

function assertValidProduct(row: ProductRow) {
  const errors = getProductValidationErrors(row);
  if (errors.length) throw new Error(errors.join("\n"));
}

export function getBlogPostValidationErrors(row: BlogPostRow): string[] {
  const errors: string[] = [];
  const title = (row.title ?? "").trim();
  const slug = (row.slug ?? "").trim().toLowerCase();
  const content = (row.content ?? "").trim();
  const cover = (row.cover_url ?? "").trim();

  const titleError = labelFieldError(title, "Blog title");
  if (titleError) errors.push(titleError);
  const slugError = slugFieldError(slug, "Blog slug");
  if (slugError) errors.push(slugError);
  const contentError = bodyFieldError(content, "Blog content");
  if (contentError) errors.push(contentError);

  if (cover) {
    const coverError = optionalPublicUrlFieldError(cover, "Cover image URL");
    if (coverError) errors.push(coverError);
  }
  return errors;
}

function assertValidBlogPost(row: BlogPostRow) {
  const errors = getBlogPostValidationErrors(row);
  if (errors.length) throw new Error(errors.join("\n"));
}

export function getBannerValidationErrors(row: BannerRow): string[] {
  const errors: string[] = [];
  const titleError = labelFieldError(row.title ?? "", "Banner title");
  if (titleError) errors.push(titleError);
  const imageError = requiredPublicUrlFieldError(row.image_url, "Banner image");
  if (imageError) errors.push(imageError);
  const linkError = internalSitePathError(row.link_url);
  if (linkError) errors.push(linkError);
  const hasLink = !!(row.link_url ?? "").trim();
  const hasCta = !!(row.cta_label ?? "").trim();
  if (hasLink && !hasCta) errors.push("CTA label is required when Link URL is set.");
  if (hasCta && !hasLink) errors.push("Link URL is required when CTA label is set.");
  const sortError = requireNumberField(row.sort_order, "Sort order", { allowZero: true });
  if (sortError) errors.push(sortError);
  return errors;
}

function assertValidBanner(row: BannerRow) {
  const errors = getBannerValidationErrors(row);
  if (errors.length) throw new Error(errors.join("\n"));
}

const HOME_SECTION_SUBTITLE_MAX = 260;

export function getHomeSectionValidationErrors(row: HomeSectionRow): string[] {
  const errors: string[] = [];
  if (!row.key?.trim()) errors.push("Section key is required.");
  const title = (row.title ?? "").trim();
  const subtitle = (row.subtitle ?? "").trim();
  if (title.length > LABEL_MAX_LEN) {
    errors.push(`Section title must be ${LABEL_MAX_LEN} characters or fewer.`);
  }
  if (subtitle.length > HOME_SECTION_SUBTITLE_MAX) {
    errors.push(`Subtitle must be ${HOME_SECTION_SUBTITLE_MAX} characters or fewer.`);
  }
  if (row.enabled) {
    const titleError = labelFieldError(title, "Section title");
    if (titleError) errors.push(titleError);
  }
  const sortError = requireNumberField(row.sort_order, "Sort order", { allowZero: true });
  if (sortError) errors.push(sortError);
  return errors;
}

function assertValidHomeSection(row: HomeSectionRow) {
  const errors = getHomeSectionValidationErrors(row);
  if (errors.length) throw new Error(errors.join("\n"));
}

export function getPolicyValidationErrors(row: PolicyRow): string[] {
  const errors: string[] = [];
  const slug = (row.slug ?? "").trim().toLowerCase();
  const title = (row.title ?? "").trim();
  const body = (row.body ?? "").trim();

  const slugError = slugFieldError(slug, "Policy slug");
  if (slugError) errors.push(slugError);
  const titleError = labelFieldError(title, "Policy title");
  if (titleError) errors.push(titleError);
  const bodyError = bodyFieldError(body, "Policy body");
  if (bodyError) errors.push(bodyError);
  return errors;
}

function assertValidPolicy(row: PolicyRow) {
  const errors = getPolicyValidationErrors(row);
  if (errors.length) throw new Error(errors.join("\n"));
}

export function getCouponValidationErrors(row: CouponRow): string[] {
  const errors: string[] = [];
  const code = row.code?.trim() ?? "";
  if (!code) errors.push("Coupon code is required.");
  else if (!/^[A-Z0-9_-]+$/.test(code.toUpperCase())) {
    errors.push("Coupon code can contain only letters, numbers, _ and -.");
  }
  if (!["flat", "percent"].includes(row.discount_type)) {
    errors.push("Discount type must be percent or flat.");
  }
  const discountError = requireNumberField(row.discount_value, "Discount value");
  if (discountError) errors.push(discountError);
  return errors;
}

function assertValidCoupon(row: CouponRow) {
  const errors = getCouponValidationErrors(row);
  if (errors.length) throw new Error(errors.join("\n"));
}

const GST_RATE_MAX = 100;
const SHIPPING_INR_MAX = 10_000;
const FREE_SHIPPING_MIN_INR_MAX = 1_000_000;

export function getSiteSettingsValidationErrors(row: SiteSettingsRow): string[] {
  const errors: string[] = [];
  const brand = String(row.brand_name ?? "").trim();
  if (!brand) errors.push("Brand name is required.");
  const email = String(row.contact_email ?? "").trim();
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.push("Contact email format is invalid.");
  }
  const ecatalogue = String(row.ecatalogue_url ?? "").trim();
  const ecatalogueError = optionalPublicUrlFieldError(ecatalogue, "E-catalogue URL");
  if (ecatalogueError) errors.push(ecatalogueError);

  const gstError = requireNumberField(row.gst_rate, "GST rate", { allowZero: true, max: GST_RATE_MAX });
  if (gstError) errors.push(gstError);
  const standardShippingError = requireNumberField(row.shipping_standard_inr, "Standard shipping", {
    allowZero: true,
    max: SHIPPING_INR_MAX,
  });
  if (standardShippingError) errors.push(standardShippingError);
  const expressShippingError = requireNumberField(row.shipping_express_inr, "Express shipping", {
    allowZero: true,
    max: SHIPPING_INR_MAX,
  });
  if (expressShippingError) errors.push(expressShippingError);
  const freeShippingError = requireNumberField(row.free_shipping_min_inr, "Free shipping threshold", {
    allowZero: true,
    max: FREE_SHIPPING_MIN_INR_MAX,
  });
  if (freeShippingError) errors.push(freeShippingError);
  return errors;
}

function assertValidSiteSettings(row: SiteSettingsRow) {
  const errors = getSiteSettingsValidationErrors(row);
  if (errors.length) throw new Error(errors.join("\n"));
}

function ensureArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value : [];
}

async function listAdmin<T>(resource: string) {
  return ensureArray<T>(await api.get<T[]>(adminPath(resource), { auth: true }));
}

async function createAdmin<T>(resource: string, payload: unknown) {
  return api.post<T>(adminPath(resource), payload, { auth: true });
}

async function updateAdmin<T>(resource: string, id: Id, payload: unknown) {
  return api.put<T>(adminPath(resource, id), payload, { auth: true });
}

async function deleteAdmin(resource: string, id: Id) {
  return api.delete<{ deleted: boolean }>(adminPath(resource, id), { auth: true });
}

export type CategoryRow = {
  id?: string;
  slug: string;
  name: string;
  description: string | null;
  image_url: string | null;
  sort_order: number | null;
  active: boolean;
};

type CategoryDto = {
  id?: number;
  slug: string;
  name: string;
  description?: string | null;
  imageUrl?: string | null;
  sortOrder?: number;
  active?: boolean;
};

export const fromCategoryDto = (dto: CategoryDto): CategoryRow => ({
  id: dto.id == null ? undefined : String(dto.id),
  slug: dto.slug?.trim() || slugifyProductSlug(dto.name),
  name: dto.name,
  description: dto.description ?? null,
  image_url: dto.imageUrl ?? null,
  sort_order: dto.sortOrder != null ? Number(dto.sortOrder) : null,
  active: dto.active ?? true,
});

const toCategoryDto = (row: CategoryRow): CategoryDto => ({
  id: idValue(row.id),
  slug: row.slug,
  name: row.name,
  description: nullable(row.description) as string | null,
  imageUrl: nullable(row.image_url) as string | null,
  sortOrder: Number(row.sort_order) || 0,
  active: !!row.active,
});

export const listCategories = (admin = false) =>
  admin
    ? listAdmin<CategoryDto>("categories").then((rows) => rows.map(fromCategoryDto))
    : api
        .get<CategoryDto[]>("/api/v1/categories")
        .then((rows) => ensureArray<CategoryDto>(rows).map(fromCategoryDto));

export const saveCategory = (row: CategoryRow) => {
  assertValidCategory(row);
  const normalized: CategoryRow = {
    ...row,
    name: row.name?.trim() ?? "",
    slug: row.slug?.trim().toLowerCase() ?? "",
    sort_order: Math.max(0, Math.floor(Number(row.sort_order) || 0)),
  };
  return normalized.id
    ? updateAdmin<CategoryDto>("categories", normalized.id, toCategoryDto(normalized)).then(fromCategoryDto)
    : createAdmin<CategoryDto>("categories", toCategoryDto(normalized)).then(fromCategoryDto);
};

export const deleteCategory = (id: Id) => deleteAdmin("categories", id);

export type ProductRow = {
  id?: string;
  slug: string;
  name: string;
  sku: string | null;
  ledgerly_sync_status?: string | null;
  ledgerly_sync_error?: string | null;
  ledgerly_item_id?: string | null;
  zoho_sync_status?: string | null;
  zoho_sync_error?: string | null;
  zoho_item_id?: string | null;
  fabric_type: string | null;
  description: string | null;
  story: string | null;
  category_id: string | null;
  category_slug?: string | null;
  category_name?: string | null;
  price: number | null;
  compare_at_price: number | null;
  currency: string;
  age_min_months: number | null;
  age_max_months: number | null;
  sizes: string[];
  colors: ProductColorEntry[];
  color_images_json?: string | null;
  item_structure: string | null;
  variants_json: string | null;
  images: string[];
  badge: string | null;
  hsn: string | null;
  tax_pct: number | null;
  units: string | null;
  stock: number | null;
  sort_order: number | null;
  featured: boolean;
  trending: boolean;
  new_arrival: boolean;
  best_seller: boolean;
  active: boolean;
};

type ProductDto = {
  id?: number;
  slug: string;
  name: string;
  sku?: string | null;
  fabricType?: string | null;
  description?: string | null;
  story?: string | null;
  price: number;
  compareAtPrice?: number | null;
  currency?: string;
  ageMinMonths?: number | null;
  ageMaxMonths?: number | null;
  sizesJson?: string | null;
  colorsJson?: string | null;
  colorImagesJson?: string | null;
  itemStructure?: string | null;
  variantsJson?: string | null;
  imagesJson?: string | null;
  badge?: string | null;
  hsn?: string | null;
  taxPct?: number | null;
  units?: string | null;
  stock?: number;
  sortOrder?: number;
  featured?: boolean;
  trending?: boolean;
  newArrival?: boolean;
  bestSeller?: boolean;
  active?: boolean;
  category?: CategoryDto | null;
  ledgerlySyncStatus?: string | null;
  ledgerlySyncError?: string | null;
  ledgerlyItemId?: string | null;
  zohoSyncStatus?: string | null;
  zohoSyncError?: string | null;
  zohoItemId?: string | null;
};

export const fromProductDto = (dto: ProductDto): ProductRow => ({
  id: dto.id == null ? undefined : String(dto.id),
  slug: dto.slug,
  name: dto.name,
  sku: dto.sku ?? null,
  fabric_type: dto.fabricType ?? null,
  description: dto.description ?? null,
  story: dto.story ?? null,
  category_id: dto.category?.id == null ? null : String(dto.category.id),
  category_slug:
    dto.category?.slug?.trim() ||
    (dto.category?.name ? slugifyProductSlug(dto.category.name) : null),
  category_name: dto.category?.name ?? null,
  price: dto.price == null ? null : Number(dto.price),
  compare_at_price: dto.compareAtPrice == null ? null : Number(dto.compareAtPrice),
  currency: dto.currency ?? "INR",
  age_min_months: dto.ageMinMonths ?? null,
  age_max_months: dto.ageMaxMonths ?? null,
  sizes: parseJson<string[]>(dto.sizesJson, []),
  colors: normalizeColorEntries(
    applyExpectedPrintNamesToColors(
      mergeColorImagesIntoColors(
        parseProductColors(parseJson(dto.colorsJson, [])),
        dto.colorImagesJson,
      ),
      dto.sku,
    ),
  ),
  color_images_json: dto.colorImagesJson ?? null,
  item_structure: dto.itemStructure ?? null,
  variants_json: applyExpectedPrintNamesToVariantsJson(dto.variantsJson, dto.sku),
  images: parseJson<string[]>(dto.imagesJson, []),
  badge: dto.badge ?? null,
  hsn: dto.hsn ?? null,
  tax_pct: dto.taxPct == null ? null : Number(dto.taxPct),
  units: dto.units?.trim() || "ea",
  stock: dto.stock == null ? null : Number(dto.stock),
  sort_order: dto.sortOrder != null ? Number(dto.sortOrder) : null,
  featured: !!dto.featured,
  trending: !!dto.trending,
  new_arrival: !!dto.newArrival,
  best_seller: !!dto.bestSeller,
  active: dto.active ?? true,
  ledgerly_sync_status: dto.ledgerlySyncStatus ?? null,
  ledgerly_sync_error: dto.ledgerlySyncError ?? null,
  ledgerly_item_id: dto.ledgerlyItemId ?? null,
  zoho_sync_status: dto.zohoSyncStatus ?? null,
  zoho_sync_error: dto.zohoSyncError ?? null,
  zoho_item_id: dto.zohoItemId ?? null,
});

const toProductDto = (row: ProductRow): ProductDto => ({
  id: idValue(row.id),
  slug: row.slug,
  name: row.name,
  sku: nullable(row.sku) as string | null,
  fabricType: nullable(row.fabric_type) as string | null,
  description: nullable(row.description) as string | null,
  story: nullable(row.story) as string | null,
  price: Number(row.price) || 0,
  compareAtPrice: row.compare_at_price == null ? null : Number(row.compare_at_price),
  currency: row.currency || "INR",
  ageMinMonths: row.age_min_months,
  ageMaxMonths: row.age_max_months,
  sizesJson: JSON.stringify(row.sizes ?? []),
  colorsJson: JSON.stringify(serializeProductColors(row.colors ?? [])),
  itemStructure: row.item_structure ?? "SIMPLE",
  variantsJson: row.variants_json ?? null,
  imagesJson: JSON.stringify(row.images ?? []),
  colorImagesJson: row.color_images_json ?? buildColorImagesJson(row.colors ?? []),
  badge: nullable(row.badge) as string | null,
  hsn: nullable(row.hsn) as string | null,
  taxPct: row.tax_pct == null ? undefined : Number(row.tax_pct),
  units: (nullable(row.units) as string | null)?.trim() || "ea",
  stock: Number(row.stock) || 0,
  sortOrder: Number(row.sort_order) || 0,
  featured: !!row.featured,
  trending: !!row.trending,
  newArrival: !!row.new_arrival,
  bestSeller: !!row.best_seller,
  active: !!row.active,
  category: row.category_id ? ({ id: Number(row.category_id) } as CategoryDto) : null,
});

export const listProducts = (admin = false) =>
  (admin ? listAdmin<ProductDto>("products") : api.get<ProductDto[]>("/api/v1/products")).then(
    (rows) => ensureArray<ProductDto>(rows).map(fromProductDto),
  );

export const saveProduct = (row: ProductRow) => {
  const normalizedColors = normalizeColorEntries(parseProductColors(row.colors)).map((entry) => ({
    ...entry,
    name: entry.name.trim(),
    swatchUrl: entry.swatchUrl?.trim() || undefined,
  }));
  const explicitImages = (row.images ?? [])
    .map((url) => normalizeProductImageUrl(url))
    .filter((url): url is string => !!url);
  const images = explicitImages.length ? explicitImages : collectStorefrontCardImages(normalizedColors);
  const prepared: ProductRow = {
    ...row,
    name: row.name?.trim() ?? "",
    slug: row.slug?.trim().toLowerCase() ?? "",
    sku: row.sku?.trim() ?? "",
    colors: normalizedColors,
    color_images_json: buildColorImagesJson(normalizedColors),
    images,
  };
  assertValidProduct(prepared);
  const normalized: ProductRow = {
    ...prepared,
    price: Number(prepared.price) || 0,
    stock: Math.max(0, Math.floor(Number(prepared.stock) || 0)),
    sort_order: Math.max(0, Math.floor(Number(prepared.sort_order) || 0)),
  };
  return normalized.id
    ? updateAdmin<ProductDto>("products", normalized.id, toProductDto(normalized)).then(fromProductDto)
    : createAdmin<ProductDto>("products", toProductDto(normalized)).then(fromProductDto);
};

export const deleteProduct = (id: Id) => deleteAdmin("products", id);

export const clearVariantPhotos = (id: Id) =>
  api.post<ProductDto>(adminPath("products", id) + "/clear-variant-photos", undefined, { auth: true }).then(
    fromProductDto,
  );

export type BlogPostRow = {
  id?: string;
  slug: string;
  title: string;
  excerpt: string | null;
  content: string | null;
  cover_url: string | null;
  published: boolean;
  published_at: string | null;
};

type BlogPostDto = {
  id?: number;
  slug: string;
  title: string;
  excerpt?: string | null;
  content?: string | null;
  coverUrl?: string | null;
  published?: boolean;
  publishedAt?: string | null;
};

function normalizeBlogPostRow(row: BlogPostRow): BlogPostRow {
  const slug = (row.slug ?? "").trim().toLowerCase();
  const title = (row.title ?? "").trim();
  const excerpt = row.excerpt?.trim() || null;
  const content = row.content?.trim() || null;
  const cover_url = row.cover_url?.trim() || null;
  return { ...row, slug, title, excerpt, content, cover_url };
}

export const fromBlogPostDto = (dto: BlogPostDto): BlogPostRow =>
  normalizeBlogPostRow({
    id: dto.id == null ? undefined : String(dto.id),
    slug: dto.slug,
    title: dto.title,
    excerpt: dto.excerpt ?? null,
    content: dto.content ?? null,
    cover_url: dto.coverUrl ?? null,
    published: !!dto.published,
    published_at: dto.publishedAt ?? null,
  });

const toBlogPostDto = (row: BlogPostRow): BlogPostDto => {
  const normalized = normalizeBlogPostRow(row);
  return {
    id: idValue(normalized.id),
    slug: normalized.slug,
    title: normalized.title,
    excerpt: nullable(normalized.excerpt) as string | null,
    content: nullable(normalized.content) as string | null,
    coverUrl: nullable(normalized.cover_url) as string | null,
    published: !!normalized.published,
    publishedAt: normalized.published_at,
  };
};

export const listBlogPosts = (admin = false) =>
  (admin
    ? listAdmin<BlogPostDto>("blog-posts")
    : api.get<BlogPostDto[]>("/api/v1/blog-posts")
  ).then((rows) => ensureArray<BlogPostDto>(rows).map(fromBlogPostDto));

export const getBlogPost = (slug: string) =>
  api
    .get<BlogPostDto>(`/api/v1/blog-posts/${encodeURIComponent(slug)}`)
    .then(fromBlogPostDto);

export const saveBlogPost = (row: BlogPostRow) => {
  const normalized: BlogPostRow = {
    ...row,
    title: row.title?.trim() ?? "",
    slug: row.slug?.trim().toLowerCase() ?? "",
    content: row.content?.trim() ?? "",
    excerpt: row.excerpt?.trim() || null,
    cover_url: row.cover_url?.trim() || null,
  };
  assertValidBlogPost(normalized);
  return normalized.id
    ? updateAdmin<BlogPostDto>("blog-posts", normalized.id, toBlogPostDto(normalized)).then(
        fromBlogPostDto,
      )
    : createAdmin<BlogPostDto>("blog-posts", toBlogPostDto(normalized)).then(fromBlogPostDto);
};

export const deleteBlogPost = (id: Id) => deleteAdmin("blog-posts", id);

export type BannerRow = {
  id?: string;
  title: string;
  subtitle: string | null;
  image_url: string | null;
  link_url: string | null;
  cta_label: string | null;
  sort_order: number | null;
  active: boolean;
};

type BannerDto = {
  id?: number;
  title: string;
  subtitle?: string | null;
  imageUrl?: string | null;
  linkUrl?: string | null;
  ctaLabel?: string | null;
  sortOrder?: number;
  active?: boolean;
};

export const fromBannerDto = (dto: BannerDto): BannerRow => ({
  id: dto.id == null ? undefined : String(dto.id),
  title: dto.title,
  subtitle: dto.subtitle ?? null,
  image_url: dto.imageUrl ?? null,
  link_url: dto.linkUrl ?? null,
  cta_label: dto.ctaLabel ?? null,
  sort_order: dto.sortOrder != null ? Number(dto.sortOrder) : null,
  active: dto.active ?? true,
});

const toBannerDto = (row: BannerRow): BannerDto => ({
  id: idValue(row.id),
  title: row.title,
  subtitle: nullable(row.subtitle) as string | null,
  imageUrl: nullable(row.image_url) as string | null,
  linkUrl: nullable(row.link_url) as string | null,
  ctaLabel: nullable(row.cta_label) as string | null,
  sortOrder: Number(row.sort_order) || 0,
  active: !!row.active,
});

export const listBanners = (admin = false) =>
  (admin ? listAdmin<BannerDto>("banners") : api.get<BannerDto[]>("/api/v1/banners")).then((rows) =>
    ensureArray<BannerDto>(rows).map(fromBannerDto),
  );

export const saveBanner = (row: BannerRow) => {
  const normalized: BannerRow = {
    ...row,
    title: row.title?.trim() ?? "",
    subtitle: row.subtitle?.trim() || null,
    image_url: row.image_url?.trim() ?? "",
    link_url: row.link_url?.trim() || null,
    cta_label: row.cta_label?.trim() || null,
  };
  assertValidBanner(normalized);
  return normalized.id
    ? updateAdmin<BannerDto>("banners", normalized.id, toBannerDto(normalized)).then(fromBannerDto)
    : createAdmin<BannerDto>("banners", toBannerDto(normalized)).then(fromBannerDto);
};

export const deleteBanner = (id: Id) => deleteAdmin("banners", id);

export type HomeSectionRow = {
  id: string;
  key: string;
  title: string | null;
  subtitle: string | null;
  enabled: boolean;
  sort_order: number | null;
  payload: JsonRecord;
};

type HomeSectionDto = {
  id: number;
  sectionKey: string;
  title?: string | null;
  subtitle?: string | null;
  enabled?: boolean;
  sortOrder?: number;
  payloadJson?: string | null;
};

export const fromHomeSectionDto = (dto: HomeSectionDto): HomeSectionRow => ({
  id: String(dto.id),
  key: dto.sectionKey,
  title: dto.title ?? null,
  subtitle: dto.subtitle ?? null,
  enabled: dto.enabled ?? true,
  sort_order: dto.sortOrder != null ? Number(dto.sortOrder) : null,
  payload: parseJson<JsonRecord>(dto.payloadJson, {}),
});

const toHomeSectionDto = (row: HomeSectionRow): HomeSectionDto => ({
  id: Number(row.id),
  sectionKey: row.key,
  title: nullable(row.title) as string | null,
  subtitle: nullable(row.subtitle) as string | null,
  enabled: !!row.enabled,
  sortOrder: Number(row.sort_order) || 0,
  payloadJson: JSON.stringify(row.payload ?? {}),
});

export const listHomeSections = (admin = false) =>
  (admin
    ? listAdmin<HomeSectionDto>("home-sections")
    : api.get<HomeSectionDto[]>("/api/v1/home-sections")
  ).then((rows) => ensureArray<HomeSectionDto>(rows).map(fromHomeSectionDto));

export const saveHomeSection = (row: HomeSectionRow) => {
  assertValidHomeSection(row);
  const id = Number(row.id);
  if (!Number.isFinite(id) || id <= 0) {
    throw new Error("This home section has no valid id. Refresh the page and try again.");
  }
  return updateAdmin<HomeSectionDto>("home-sections", id, toHomeSectionDto(row)).then(
    fromHomeSectionDto,
  );
};

export type PageVisibilityRow = { id: string; page_key: string; visible: boolean };
type PageVisibilityDto = { id: number; pageKey: string; visible: boolean };

export const fromPageVisibilityDto = (dto: PageVisibilityDto): PageVisibilityRow => ({
  id: String(dto.id),
  page_key: dto.pageKey,
  visible: !!dto.visible,
});

const toPageVisibilityDto = (row: PageVisibilityRow): PageVisibilityDto => ({
  id: Number(row.id),
  pageKey: row.page_key,
  visible: !!row.visible,
});

export const listPageVisibility = (admin = false) =>
  (admin
    ? listAdmin<PageVisibilityDto>("page-visibility")
    : api.get<PageVisibilityDto[]>("/api/v1/page-visibility")
  ).then((rows) => ensureArray<PageVisibilityDto>(rows).map(fromPageVisibilityDto));

export const savePageVisibility = (row: PageVisibilityRow) =>
  updateAdmin<PageVisibilityDto>("page-visibility", row.id, toPageVisibilityDto(row)).then(
    fromPageVisibilityDto,
  );

export type PolicyRow = { id?: string; slug: string; title: string; body: string | null };
type PolicyDto = { id?: number; slug: string; title: string; body?: string | null };

export const fromPolicyDto = (dto: PolicyDto): PolicyRow => ({
  id: dto.id == null ? undefined : String(dto.id),
  slug: dto.slug,
  title: dto.title,
  body: dto.body ?? null,
});

const toPolicyDto = (row: PolicyRow): PolicyDto => ({
  id: idValue(row.id),
  slug: row.slug,
  title: row.title,
  body: (row.body ?? "").trim(),
});

export const listPolicies = () =>
  listAdmin<PolicyDto>("policies").then((rows) => rows.map(fromPolicyDto));
export const getPolicy = (slug: string) =>
  api.get<PolicyDto>(`/api/v1/policies/${encodeURIComponent(slug)}`).then(fromPolicyDto);
export const savePolicy = async (row: PolicyRow) => {
  const normalized: PolicyRow = {
    ...row,
    slug: row.slug?.trim().toLowerCase() ?? "",
    title: row.title?.trim() ?? "",
    body: row.body?.trim() ?? "",
  };
  assertValidPolicy(normalized);
  let policyId = normalized.id;
  if (!policyId && normalized.slug) {
    const existing = await listPolicies().then((rows) =>
      rows.find((p) => p.slug === normalized.slug),
    );
    if (existing?.id) policyId = existing.id;
  }
  const saved = await (policyId
    ? updateAdmin<PolicyDto>("policies", policyId, toPolicyDto({ ...normalized, id: policyId }))
    : createAdmin<PolicyDto>("policies", toPolicyDto(normalized))
  ).then(fromPolicyDto);
  const { invalidatePolicy } = await import("@/lib/storefront");
  invalidatePolicy(saved.slug);
  return saved;
};
export const deletePolicy = async (id: Id, slug?: string) => {
  await deleteAdmin("policies", id);
  if (slug?.trim()) {
    const { invalidatePolicy } = await import("@/lib/storefront");
    invalidatePolicy(slug.trim().toLowerCase());
  }
};

export type CouponRow = {
  id?: string;
  code: string;
  discount_type: string;
  discount_value: number | null;
  min_order: number | null;
  max_discount: number | null;
  expires_at: string | null;
  active: boolean;
};
type CouponDto = {
  id?: number;
  code: string;
  discountType: "PERCENT" | "FLAT";
  discountValue: number;
  minOrder: number;
  maxDiscount?: number | null;
  expiresAt?: string | null;
  active: boolean;
};

export const fromCouponDto = (dto: CouponDto): CouponRow => ({
  id: dto.id == null ? undefined : String(dto.id),
  code: dto.code,
  discount_type: dto.discountType === "FLAT" ? "flat" : "percent",
  discount_value: dto.discountValue == null ? null : Number(dto.discountValue),
  min_order: dto.minOrder == null ? null : Number(dto.minOrder),
  max_discount: dto.maxDiscount == null ? null : Number(dto.maxDiscount),
  expires_at: dto.expiresAt ?? null,
  active: !!dto.active,
});

const toCouponDto = (row: CouponRow): CouponDto => ({
  id: idValue(row.id),
  code: row.code,
  discountType: row.discount_type === "flat" ? "FLAT" : "PERCENT",
  discountValue: row.discount_value as number,
  minOrder: numberFieldOr(row.min_order, 0),
  maxDiscount: row.max_discount == null ? null : Number(row.max_discount),
  expiresAt: row.expires_at,
  active: !!row.active,
});

export const listCoupons = () =>
  listAdmin<CouponDto>("coupons").then((rows) => rows.map(fromCouponDto));
export const saveCoupon = (row: CouponRow) => {
  assertValidCoupon(row);
  return row.id
    ? updateAdmin<CouponDto>("coupons", row.id, toCouponDto(row)).then(fromCouponDto)
    : createAdmin<CouponDto>("coupons", toCouponDto(row)).then(fromCouponDto);
};
export const deleteCoupon = (id: Id) => deleteAdmin("coupons", id);

export type SiteSettingsRow = Record<string, string | number | boolean | null | undefined>;

const siteStringMap: Record<string, string> = {
  brand_name: "brandName",
  legal_entity_name: "legalEntityName",
  logo_url: "logoUrl",
  logo_horizontal_url: "logoHorizontalUrl",
  contact_email: "contactEmail",
  contact_phone: "contactPhone",
  care_label: "careLabel",
  whatsapp_number: "whatsappNumber",
  instagram_url: "instagramUrl",
  facebook_url: "facebookUrl",
  youtube_url: "youtubeUrl",
  twitter_url: "twitterUrl",
  footer_tagline: "footerTagline",
  footer_copyright: "footerCopyright",
  ecatalogue_url: "ecatalogueUrl",
  ecatalogue_label: "ecatalogueLabel",
  subscribe_hero_title: "subscribeHeroTitle",
  subscribe_hero_subtitle: "subscribeHeroSubtitle",
  subscribe_testimonial: "subscribeTestimonial",
  subscribe_badges_json: "subscribeBadgesJson",
  subscribe_plans_json: "subscribePlansJson",
  subscribe_features_json: "subscribeFeaturesJson",
};

function fromSiteSettingDto(dto: Record<string, unknown> | null): SiteSettingsRow | null {
  if (!dto) return null;
  const row = Object.fromEntries(
    Object.entries(siteStringMap).map(([snake, camel]) => [snake, dto[camel] ?? ""]),
  ) as SiteSettingsRow;
  row.gst_rate = dto.gstRate as number | undefined;
  row.prices_include_gst = dto.pricesIncludeGst as boolean | undefined;
  row.shipping_standard_inr = dto.shippingStandardInr as number | undefined;
  row.shipping_express_inr = dto.shippingExpressInr as number | undefined;
  row.free_shipping_min_inr = dto.freeShippingMinInr as number | undefined;
  return row;
}

function toSiteSettingDto(row: SiteSettingsRow) {
  const payload = Object.fromEntries(
    Object.entries(siteStringMap).map(([snake, camel]) => [camel, row[snake] ?? null]),
  ) as Record<string, unknown>;
  payload.gstRate = row.gst_rate ?? null;
  payload.pricesIncludeGst = row.prices_include_gst ?? null;
  payload.shippingStandardInr = row.shipping_standard_inr ?? null;
  payload.shippingExpressInr = row.shipping_express_inr ?? null;
  payload.freeShippingMinInr = row.free_shipping_min_inr ?? null;
  return payload;
}

function fromCamelMap<T>(
  dto: Record<string, unknown> | null,
  map: Record<string, string>,
): T | null {
  if (!dto) return null;
  return Object.fromEntries(
    Object.entries(map).map(([snake, camel]) => [snake, dto[camel] ?? ""]),
  ) as T;
}

function toCamelMap(row: Record<string, unknown>, map: Record<string, string>) {
  return Object.fromEntries(
    Object.entries(map).map(([snake, camel]) => [camel, row[snake] ?? null]),
  );
}

export const getSiteSettings = () =>
  api.get<Record<string, unknown> | null>("/api/v1/site-settings").then(fromSiteSettingDto);

export type SubscribePageContent = {
  subscribe_hero_title?: string;
  subscribe_hero_subtitle?: string;
  subscribe_testimonial?: string;
  subscribe_badges_json?: string;
  subscribe_plans_json?: string;
  subscribe_features_json?: string;
};

export const getSubscribePage = () =>
  api.get<SubscribePageContent | null>("/api/v1/subscribe-page").then((row) => row ?? {});
export const getAdminSiteSettings = () =>
  api
    .get<Record<string, unknown> | null>("/api/v1/admin/site-settings", { auth: true })
    .then(fromSiteSettingDto);
export const saveSiteSettings = (row: SiteSettingsRow) => {
  assertValidSiteSettings(row);
  return api.put<Record<string, unknown>>("/api/v1/admin/site-settings", toSiteSettingDto(row), {
    auth: true,
  });
};

export const getAdminDashboard = () =>
  api.get<Record<string, unknown>>("/api/v1/admin/dashboard", { auth: true });

type PresignedUpload = {
  uploadUrl: string;
  publicUrl: string;
  method: string;
  headers: Record<string, string>;
  maxUploadBytes: number;
};

async function uploadAdminImageViaBackend(folder: AdminImageFolder, file: File) {
  const form = new FormData();
  form.append("folder", folder);
  const filename =
    file.name?.trim() ||
    (file.type === "image/png"
      ? "upload.png"
      : file.type === "image/webp"
        ? "upload.webp"
        : file.type === "image/gif"
          ? "upload.gif"
          : "upload.jpg");
  form.append("file", file, filename);
  const uploaded = await api.post<unknown>("/api/v1/admin/uploads", form, { auth: true });
  if (
    !uploaded ||
    typeof uploaded !== "object" ||
    typeof (uploaded as { publicUrl?: unknown }).publicUrl !== "string"
  ) {
    throw new Error("Upload response missing publicUrl.");
  }
  return (uploaded as { publicUrl: string }).publicUrl;
}

async function uploadAdminImageViaPresign(folder: AdminImageFolder, file: File) {
  const presigned = await api.post<PresignedUpload>(
    "/api/v1/admin/uploads/presign",
    {
      folder,
      filename: file.name,
      contentType: file.type || undefined,
    },
    { auth: true },
  );

  if (!presigned?.uploadUrl || !presigned.publicUrl) {
    throw new Error("Upload could not be prepared. Please try again.");
  }
  if (typeof presigned.maxUploadBytes !== "number" || presigned.maxUploadBytes <= 0) {
    throw new Error("Invalid or missing maxUploadBytes from presign response");
  }
  if (file.size > presigned.maxUploadBytes) {
    const maxMb = Math.max(1, Math.round(presigned.maxUploadBytes / (1024 * 1024)));
    throw new Error(`Image is too large. Maximum size is about ${maxMb} MB.`);
  }

  const uploadHeaders = new Headers();
  for (const [name, value] of Object.entries(presigned.headers ?? {})) {
    const lower = name.toLowerCase();
    if (lower === "host" || lower === "content-length") continue;
    uploadHeaders.set(name, value);
  }

  let storageResponse: Response;
  try {
    storageResponse = await fetch(presigned.uploadUrl, {
      method: presigned.method || "PUT",
      headers: uploadHeaders,
      body: file,
    });
  } catch {
    throw new Error(
      "Upload to storage was blocked (often missing R2 CORS for https://uat.minisnmore.com).",
    );
  }
  if (!storageResponse.ok) {
    if (storageResponse.status === 403) {
      throw new Error(
        "R2 storage rejected the upload (403). The S3 API token likely lacks Object Read & Write on bucket miniandmore — update S3_ACCESS_KEY and S3_SECRET_KEY in UAT env, then restart catalog.",
      );
    }
    throw new Error(`Upload to storage failed (${storageResponse.status}).`);
  }

  return presigned.publicUrl;
}

function shouldFallbackToPresign(error: unknown) {
  if (error instanceof ApiError) {
    // Size/proxy failures won't be fixed by browser PUT to R2.
    if (error.status === 413 || error.status === 400 || error.status === 401 || error.status === 403) {
      return false;
    }
  }
  return true;
}

/** Server-side upload first (reliable on R2); presign only as fallback. */
export async function uploadAdminImage(folder: AdminImageFolder, file: File) {
  assertAllowedImageFile(file);
  if (file.size <= 0) {
    throw new Error("File is empty.");
  }

  try {
    return await uploadAdminImageViaBackend(folder, file);
  } catch (backendError) {
    if (backendError instanceof ApiError && backendError.status === 413) {
      throw new Error("Image is too large. Maximum size is about 8 MB.");
    }
    if (!shouldFallbackToPresign(backendError)) {
      throw backendError instanceof Error ? backendError : new Error("Upload failed.");
    }
    try {
      return await uploadAdminImageViaPresign(folder, file);
    } catch (presignError) {
      throw presignError instanceof Error ? presignError : backendError;
    }
  }
}

export function slugifyProductSlug(value: string) {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

/**
 * When a spreadsheet reuses the same slug for different SKUs, append the SKU so
 * each row becomes a distinct product instead of tripping duplicate-slug errors.
 */
export function disambiguateProductImportSlugs<
  T extends { slug: string; sku: string | null; name: string },
>(payloads: T[], existing: T[]): { rows: T[]; warnings: string[] } {
  const warnings: string[] = [];
  const slugOwner = new Map<string, string>();

  for (const product of existing) {
    const slug = slugifyProductSlug(product.slug);
    if (!slug) continue;
    slugOwner.set(slug, (product.sku ?? "").trim().toLowerCase());
  }

  const rows: T[] = [];

  for (const raw of payloads) {
    const sku = (raw.sku ?? "").trim().toLowerCase();
    let slug = slugifyProductSlug(raw.slug) || slugifyProductSlug(raw.name);
    if (!slug) {
      rows.push(raw);
      continue;
    }

    const ownerSku = slugOwner.get(slug);
    if (ownerSku !== undefined && ownerSku !== sku) {
      const adjusted = slugifyProductSlug(`${slug}-${sku}`);
      warnings.push(
        `"${raw.name}": slug "${slug}" was already used — imported as "${adjusted}".`,
      );
      slug = adjusted;
    }

    let candidate = slug;
    let suffix = 2;
    while (slugOwner.has(candidate) && slugOwner.get(candidate) !== sku) {
      candidate = slugifyProductSlug(`${slug}-${suffix}`);
      suffix++;
    }
    slug = candidate;

    slugOwner.set(slug, sku);
    rows.push({ ...raw, slug });
  }

  return { rows, warnings };
}

export function categoryNameFromSlug(slug: string) {
  return slug
    .split("-")
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function parseSpreadsheetActive(value: unknown, defaultValue = true) {
  const normalized = String(value ?? "")
    .trim()
    .toLowerCase();
  if (!normalized) return defaultValue;
  return !["false", "0", "no", "n"].includes(normalized);
}

export function parseSpreadsheetBoolean(value: unknown, defaultValue = false) {
  const normalized = String(value ?? "")
    .trim()
    .toLowerCase();
  if (!normalized) return defaultValue;
  return ["true", "1", "yes", "y"].includes(normalized);
}

export async function bulkSave<T extends { id?: string }>(
  rows: T[],
  existing: T[],
  uniqueKey: keyof T,
  save: (row: T) => Promise<T>,
): Promise<{ inserted: number; errors: string[] }> {
  const errors: string[] = [];
  let inserted = 0;
  const savedInBatch = new Map<string, T>();

  for (const row of rows) {
    const key = String(row[uniqueKey] ?? "");
    const existingRow =
      existing.find((candidate) => String(candidate[uniqueKey] ?? "") === key) ??
      savedInBatch.get(key);
    try {
      const saved = await save(existingRow?.id ? { ...row, id: existingRow.id } : row);
      savedInBatch.set(key, saved);
      inserted++;
    } catch (error) {
      const message = error instanceof Error ? error.message : "Save failed";
      errors.push(key ? `"${key}": ${message}` : message);
    }
  }

  return { inserted, errors };
}

// --- Ledgerly integration ---

export type LedgerlySettingsRow = {
  api_base_url?: string | null;
  apiBaseUrl?: string | null;
  api_token?: string | null;
  org_id?: string | null;
  orgId?: string | null;
  connected?: boolean;
  hasToken?: boolean;
  hasWebhookSecret?: boolean;
  pullProductsAutoCreateCategory?: boolean;
};

export type LedgerlyIntegrationStatus = {
  connected?: boolean;
  serverConfigured?: boolean;
  pullProductsAutoCreateCategory?: boolean;
};

export type LedgerlySyncFailure = {
  id?: number;
  productId?: number;
  productName?: string;
  orderNumber?: string;
  sku?: string;
  message?: string;
};

export type LedgerlySyncSummary = {
  successCount?: number;
  failureCount?: number;
  skippedCount?: number;
  failures?: LedgerlySyncFailure[];
  notices?: LedgerlySyncFailure[];
  skipped?: LedgerlySyncFailure[];
};

export type LedgerlyProductSyncResult = {
  ledgerlyItemId?: string | null;
  ledgerlySyncStatus?: string | null;
  ledgerlyLastSyncedAt?: string | null;
  ledgerlySyncError?: string | null;
};

export const getLedgerlyIntegrationStatus = () =>
  api.get<LedgerlyIntegrationStatus>("/api/v1/admin/ledgerly/integration-status", { auth: true });

export const getLedgerlySettings = () =>
  api.get<LedgerlySettingsRow | null>("/api/v1/admin/ledgerly/settings", { auth: true });

export const saveLedgerlySettings = (row: LedgerlySettingsRow) =>
  api.put("/api/v1/admin/ledgerly/settings", row, { auth: true });

export const setLedgerlyPullProductsAutoCreateCategory = (enabled: boolean) =>
  api.put<LedgerlyIntegrationStatus>(
    "/api/v1/admin/ledgerly/pull-products-auto-create-category",
    { enabled },
    { auth: true },
  );

export const testLedgerlyConnection = () =>
  api.get<Record<string, unknown>>("/api/v1/admin/ledgerly/test", { auth: true });

export const syncAllLedgerlyProducts = () =>
  api.post<LedgerlySyncSummary>("/api/v1/admin/ledgerly/sync-products", undefined, { auth: true });

export const syncLedgerlyProduct = (productId: number) =>
  api.post<LedgerlyProductSyncResult>(`/api/v1/admin/ledgerly/sync-products/${productId}`, undefined, {
    auth: true,
  });

export const pullLedgerlyInventory = () =>
  api.post<LedgerlySyncSummary>("/api/v1/admin/ledgerly/pull-inventory", undefined, { auth: true });

export const pullLedgerlyProducts = () =>
  api.post<LedgerlySyncSummary>("/api/v1/admin/ledgerly/pull-products", undefined, { auth: true });

export const retryLedgerlyOrders = () =>
  api.post<LedgerlySyncSummary>("/api/v1/admin/ledgerly/retry-orders", undefined, { auth: true });

export const syncLedgerlyOrderByNumber = (orderNumber: string) =>
  api.post<Record<string, unknown>>(
    `/api/v1/admin/ledgerly/sync-orders/by-number/${encodeURIComponent(orderNumber.trim())}`,
    undefined,
    { auth: true },
  );

export const pullLedgerlyOrderTracking = () =>
  api.post<LedgerlySyncSummary>("/api/v1/admin/ledgerly/pull-order-tracking", undefined, { auth: true });

export function formatLedgerlySyncSummary(summary: LedgerlySyncSummary, label: string) {
  const success = summary.successCount ?? 0;
  const failed = summary.failureCount ?? 0;
  const skipped = summary.skippedCount ?? 0;
  const noticeCount = (summary.notices ?? []).length;
  const parts = [`${success} succeeded`];
  if (failed > 0) parts.push(`${failed} failed`);
  if (skipped > 0) parts.push(`${skipped} skipped`);
  if (failed === 0 && skipped === 0 && noticeCount > 0) parts.push(`${noticeCount} notes`);
  return `${label}: ${parts.join(", ")}`;
}

export function formatLedgerlySyncFailures(summary: LedgerlySyncSummary): string[] {
  const label = (f: LedgerlySyncFailure) =>
    f.productName ?? f.orderNumber ?? f.sku ?? (f.id != null ? String(f.id) : "Item");
  const failures = (summary.failures ?? []).map((f) => `${label(f)}: ${f.message ?? "Failed"}`);
  const skipped = (summary.skipped ?? []).map((f) => `Skipped — ${label(f)}: ${f.message ?? ""}`);
  const notices = (summary.notices ?? []).map((f) => `Note — ${label(f)}: ${f.message ?? ""}`);
  return [...failures, ...skipped, ...notices];
}

export type ZohoSettingsRow = {
  org_id: string | null;
  client_id: string | null;
  client_secret: string | null;
  refresh_token: string | null;
  data_center: string | null;
  connected: boolean;
  hasClientSecret?: boolean;
  hasRefreshToken?: boolean;
};

const zohoMap = {
  org_id: "orgId",
  client_id: "clientId",
  client_secret: "clientSecret",
  refresh_token: "refreshToken",
  data_center: "dataCenter",
};

export function getZohoSettingsValidationErrors(
  row: ZohoSettingsRow,
  options?: { hasClientSecret?: boolean; requireRefreshToken?: boolean },
): string[] {
  const errors: string[] = [];
  if (!(row.org_id ?? "").trim()) errors.push("Zoho organisation ID is required.");
  if (!(row.client_id ?? "").trim()) errors.push("Zoho client ID is required.");
  if (!options?.hasClientSecret && !(row.client_secret ?? "").trim()) {
    errors.push("Zoho client secret is required.");
  }
  if (options?.requireRefreshToken && !(row.refresh_token ?? "").trim()) {
    errors.push("Zoho refresh token is required.");
  }
  return errors;
}

function assertValidZohoSettings(
  row: ZohoSettingsRow,
  options?: { hasClientSecret?: boolean; requireRefreshToken?: boolean },
) {
  const errors = getZohoSettingsValidationErrors(row, options);
  if (errors.length) throw new Error(errors.join("\n"));
}

export type ZohoOAuthSetup = {
  clientType: string;
  apiConsoleUrl: string;
  homepageUrl: string;
  redirectUris: string[];
  javascriptDomains: string[];
  scopes: string;
  primaryRedirectUri: string;
  authorizationUrlTemplate: string;
  dataCenter: string;
};

export const getZohoOAuthSetup = () =>
  api.get<ZohoOAuthSetup>("/api/v1/admin/zoho/oauth-setup", { auth: true });

export type ZohoIntegrationStatus = {
  serverConfigured: boolean;
  connected: boolean;
  dataCenter: string;
  organizationId: string | null;
  pullProductsAutoCreateCategory: boolean;
};

export const getZohoIntegrationStatus = () =>
  api.get<ZohoIntegrationStatus>("/api/v1/admin/zoho/integration-status", { auth: true });

export const setZohoPullProductsAutoCreateCategory = (enabled: boolean) =>
  api.put<ZohoIntegrationStatus>(
    "/api/v1/admin/zoho/pull-products-auto-create-category",
    { enabled },
    { auth: true },
  );

export const getZohoConnectUrl = () =>
  api.get<{ url: string }>("/api/v1/admin/zoho/connect-url", { auth: true });

export const completeZohoOAuth = (code: string) =>
  api.post<ZohoIntegrationStatus>(
    "/api/v1/admin/zoho/oauth/complete",
    { code },
    { auth: true },
  );

export type ZohoConnectionStatus = {
  connected: boolean;
  message: string;
  organizationName: string | null;
};

export const testZohoConnection = () =>
  api.get<ZohoConnectionStatus>("/api/v1/admin/zoho/test", { auth: true });

export type ZohoSyncFailure = {
  productId: number;
  productName: string;
  message: string;
};

export type ZohoSyncSummary = {
  success: number;
  failed: number;
  failures?: ZohoSyncFailure[];
  notices?: ZohoSyncFailure[];
};

export const syncAllZohoProducts = () =>
  api.post<ZohoSyncSummary>("/api/v1/admin/zoho/sync-products", undefined, { auth: true });

export type ZohoProductSyncResult = {
  id: number;
  zohoItemId?: string | null;
  zohoSyncStatus?: string | null;
  zohoSyncError?: string | null;
  zohoLastSyncedAt?: string | null;
  variantsJson?: string | null;
  itemStructure?: string | null;
};

export const syncZohoProduct = (productId: number) =>
  api.post<ZohoProductSyncResult>(`/api/v1/admin/zoho/sync-products/${productId}`, undefined, {
    auth: true,
  });

export const pullZohoInventory = () =>
  api.post<ZohoSyncSummary>("/api/v1/admin/zoho/pull-inventory", undefined, { auth: true });

export const pullZohoProducts = () =>
  api.post<ZohoSyncSummary>("/api/v1/admin/zoho/pull-products", undefined, { auth: true });

export const retryZohoOrders = () =>
  api.post<ZohoSyncSummary>("/api/v1/admin/zoho/retry-orders", undefined, { auth: true });

export const syncZohoOrders = retryZohoOrders;

export type ZohoOrderSyncResult = {
  id: number;
  orderNumber?: string | null;
  zohoSalesOrderId?: string | null;
  zohoInvoiceId?: string | null;
  zohoSyncStatus?: string | null;
  zohoSyncError?: string | null;
  zohoLastSyncedAt?: string | null;
};

export const syncZohoOrderByNumber = (orderNumber: string) =>
  api.post<ZohoOrderSyncResult>(
    `/api/v1/admin/zoho/sync-orders/by-number/${encodeURIComponent(orderNumber.trim())}`,
    undefined,
    { auth: true },
  );

export const pullZohoOrderTracking = () =>
  api.post<ZohoSyncSummary>("/api/v1/admin/zoho/pull-order-tracking", undefined, { auth: true });

export function formatZohoSyncSummary(summary: ZohoSyncSummary, label: string) {
  const noticeCount = (summary.notices ?? []).length;
  if (summary.failed === 0) {
    if (summary.success > 0) {
      return noticeCount > 0
        ? `${label} completed: ${summary.success} succeeded, ${noticeCount} notes`
        : `${label} completed: ${summary.success} succeeded`;
    }
    if (noticeCount > 0) {
      return `${label}: ${noticeCount} notes`;
    }
  }
  return `${label}: ${summary.success} succeeded, ${summary.failed} failed`;
}

export function formatZohoSyncFailures(summary: ZohoSyncSummary): string[] {
  const failures = (summary.failures ?? []).map((f) => `${f.productName}: ${f.message}`);
  const notices = (summary.notices ?? []).map((f) => `Note — ${f.productName}: ${f.message}`);
  return [...failures, ...notices];
}

export const getZohoSettings = async (): Promise<ZohoSettingsRow | null> => {
  const dto = await api.get<Record<string, unknown> | null>("/api/v1/admin/zoho-settings", {
    auth: true,
  });
  if (!dto) return null;
  return {
    ...(fromCamelMap<Omit<ZohoSettingsRow, "connected" | "hasClientSecret" | "hasRefreshToken">>(
      dto,
      zohoMap,
    ) as Omit<ZohoSettingsRow, "connected" | "hasClientSecret" | "hasRefreshToken">),
    connected: !!dto.connected,
    hasClientSecret: !!dto.hasClientSecret,
    hasRefreshToken: !!dto.hasRefreshToken,
  };
};

export const saveZohoSettings = (
  row: ZohoSettingsRow,
  options?: { hasClientSecret?: boolean; requireRefreshToken?: boolean },
) => {
  assertValidZohoSettings(row, options);
  const payload: Record<string, unknown> = { ...toCamelMap(row, zohoMap), connected: row.connected };
  if (!row.client_secret?.trim()) {
    delete payload.clientSecret;
  }
  if (!row.refresh_token?.trim()) {
    delete payload.refreshToken;
  }
  return api.put<Record<string, unknown>>("/api/v1/admin/zoho-settings", payload, { auth: true });
};
