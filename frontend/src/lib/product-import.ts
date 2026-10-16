import {
  getProductValidationErrors,
  parseSpreadsheetActive,
  parseSpreadsheetBoolean,
  parseSpreadsheetImageUrls,
  type CategoryRow,
  type ProductRow,
} from "./backend-data";
import { parseAgeRangeField, parseCommaList } from "./comma-list-field";
import { ensureColorEntries } from "./product-colors";

/** Parse and validate a spreadsheet row before any product write is attempted. */
export function parseProductImportRow(raw: Record<string, unknown>, categories: Pick<CategoryRow, "id" | "slug">[], existing?: ProductRow): ProductRow {
  const text = (key: string) => String(raw[key] ?? "").trim();
  const number = (key: string) => text(key) === "" ? null : Number(raw[key]);
  const categorySlug = text("category_slug").toLowerCase();
  const category = categories.find((row) => row.slug.toLowerCase() === categorySlug);
  if (!category?.id) throw new Error(`Unknown category slug: ${categorySlug || "(empty)"}`);
  const age = parseAgeRangeField(text("age_range"));
  const row: ProductRow = {
    slug: text("slug").toLowerCase(),
    name: text("name"),
    sku: text("sku"),
    fabric_type: text("fabric_type"),
    description: text("description"),
    story: text("story"),
    category_id: category.id,
    price: number("price"),
    compare_at_price: number("compare_at_price"),
    currency: text("currency") || "INR",
    age_min_months: number("age_min_months") ?? age.min,
    age_max_months: number("age_max_months") ?? age.max,
    sizes: parseCommaList(text("sizes")),
    colors: ensureColorEntries(parseCommaList(text("colors"))),
    item_structure: "SIMPLE",
    variants_json: null,
    images: parseSpreadsheetImageUrls(raw.images || raw.image_urls || raw.image_url || raw.image),
    details_kicker: text("details_kicker"),
    badge: text("badge"),
    hsn: text("hsn") || null,
    tax_pct: number("tax_pct"),
    units: text("units") || "ea",
    stock: number("stock"),
    sort_order: number("sort_order"),
    featured: parseSpreadsheetBoolean(raw.featured),
    trending: parseSpreadsheetBoolean(raw.trending),
    new_arrival: parseSpreadsheetBoolean(raw.new_arrival),
    best_seller: parseSpreadsheetBoolean(raw.best_seller),
    active: parseSpreadsheetActive(raw.active),
  };
  if (existing) {
    const optionalColumns: (keyof ProductRow)[] = [
      "description", "story", "currency", "sizes", "colors", "details_kicker", "badge", "hsn",
      "tax_pct", "units", "sort_order", "compare_at_price", "featured", "trending", "new_arrival", "best_seller", "active",
    ];
    for (const key of optionalColumns) {
      if (text(key) === "") Object.assign(row, { [key]: existing[key] });
    }
    if (!["images", "image_urls", "image_url", "image"].some((key) => text(key))) row.images = existing.images;
    row.id = existing.id;
    row.item_structure = existing.item_structure;
    row.variants_json = existing.variants_json;
    row.color_images_json = existing.color_images_json;
    row.size_chart = existing.size_chart;
    row.size_notes = existing.size_notes;
  }
  const errors = getProductValidationErrors(row);
  if (errors.length) throw new Error(errors.join("\n"));
  return row;
}
