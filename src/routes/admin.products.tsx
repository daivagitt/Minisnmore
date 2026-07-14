import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { AgeRangeField } from "@/components/admin/AgeRangeField";
import { CommaListField } from "@/components/admin/CommaListField";
import { NumberField } from "@/components/admin/NumberField";
import { VariantsMatrixEditor, validateVariantRows } from "@/components/admin/VariantsMatrixEditor";
import { parseAgeRangeField } from "@/lib/comma-list-field";
import { ImageSpecHint } from "@/components/admin/ImageSpecHint";
import { ADMIN_IMAGE_SPECS } from "@/lib/admin-image-specs";
import { BulkImport } from "@/components/admin/BulkImport";
import { toast } from "sonner";
import {
  Plus,
  Trash2,
  Save,
  Upload,
  X,
  Star,
  Flame,
  Sparkles,
  Award,
  Loader2,
  CloudUpload,
} from "lucide-react";
import { getUserErrorMessage, showApiError, showValidationErrors } from "@/lib/ui-errors";
import { sanitizePublicUrl, toDirectDownloadUrl } from "@/lib/utils";
import { IMAGE_ACCEPT } from "@/lib/admin-image";
import {
  ADMIN_NAME_MAX_LEN,
  bulkSave,
  deleteProduct,
  getProductValidationErrors,
  listCategories,
  listProducts,
  categoryNameFromSlug,
  disambiguateProductImportSlugs,
  parseSpreadsheetActive,
  parseSpreadsheetBoolean,
  saveCategory,
  saveProduct,
  slugifyProductSlug,
  syncLedgerlyProduct,
  uploadAdminImage,
  type ProductRow,
} from "@/lib/backend-data";
import { invalidateProducts } from "@/lib/storefront";
import { isBestsellerFromBadge } from "@/lib/product-flags";

export const Route = createFileRoute("/admin/products")({ component: ProductsPage });

type Product = ProductRow;
const empty: Product = {
  slug: "",
  name: "",
  sku: "",
  fabric_type: "100% Cotton",
  description: "",
  story: "",
  category_id: null,
  price: null,
  compare_at_price: null,
  currency: "INR",
  age_min_months: null,
  age_max_months: null,
  sizes: [],
  colors: [],
  item_structure: "SIMPLE",
  variants_json: null,
  images: [],
  badge: "",
  hsn: null,
  tax_pct: 5,
  stock: null,
  sort_order: null,
  featured: false,
  trending: false,
  new_arrival: false,
  best_seller: false,
  active: true,
};
const slugify = (s: string) =>
  s
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");

const normalizeImageUrl = (value: string) => {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const withProtocol =
    /^https?:\/\//i.test(trimmed) || trimmed.startsWith("/") ? trimmed : `https://${trimmed}`;
  const direct = toDirectDownloadUrl(withProtocol);
  return sanitizePublicUrl(direct);
};

function ProductsPage() {
  const [rows, setRows] = useState<Product[]>([]);
  const [cats, setCats] = useState<{ id: string; name: string; slug: string }[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState("");
  const [editing, setEditing] = useState<number | null>(null);
  const [ledgerlySyncing, setLedgerlySyncing] = useState<Set<number>>(() => new Set());

  const load = async () => {
    setLoading(true);
    try {
      const [p, c] = await Promise.all([listProducts(true), listCategories(true)]);
      setRows(p);
      setCats(c.map(({ id, name, slug }) => ({ id: id!, name, slug })));
    } catch (error) {
      showApiError(error, "Could not load products");
    }
    setLoading(false);
  };
  useEffect(() => {
    load();
  }, []);

  const upload = async (files: FileList, i: number) => {
    const urls: string[] = [];
    for (const f of Array.from(files)) {
      try {
        urls.push(await uploadAdminImage("products", f));
      } catch (error) {
        showApiError(error, "Upload failed");
      }
    }
    if (urls.length) {
      setRows((r) => r.map((x, j) => (j === i ? { ...x, images: [...x.images, ...urls] } : x)));
      toast.success(`${urls.length} image(s) uploaded`);
    }
  };

  const update = (i: number, patch: Partial<Product>) =>
    setRows((r) => r.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  const syncToLedgerly = async (i: number, p: Product) => {
    if (!p.id) {
      toast.error("Save the product first, then sync to Ledgerly.");
      return;
    }
    if (!p.sku?.trim()) {
      toast.error("SKU is required before syncing to Ledgerly.");
      return;
    }
    if (!p.category_id) {
      toast.error("Category is required before syncing to Ledgerly.");
      return;
    }
    setLedgerlySyncing((prev) => new Set(prev).add(i));
    try {
      const result = await syncLedgerlyProduct(Number(p.id));
      update(i, {
        ledgerly_sync_status: result.ledgerlySyncStatus ?? null,
        ledgerly_item_id: result.ledgerlyItemId ?? null,
        ledgerly_sync_error: result.ledgerlySyncError ?? null,
      });
      toast.success("Product synced to Ledgerly");
    } catch (error) {
      update(i, {
        ledgerly_sync_status: "FAILED",
        ledgerly_sync_error: getUserErrorMessage(error, "Ledgerly sync failed"),
      });
      showApiError(error, "Ledgerly sync failed");
    } finally {
      setLedgerlySyncing((prev) => {
        const next = new Set(prev);
        next.delete(i);
        return next;
      });
    }
  };

  const save = async (p: Product) => {
    const name = p.name.trim();
    const slug = (p.slug.trim() || slugify(name)).toLowerCase();
    const normalizedImages = (p.images ?? [])
      .map((url) => normalizeImageUrl(String(url)))
      .filter((url): url is string => !!url);
    const payload = {
      ...p,
      name,
      slug,
      sku: p.sku?.trim() ?? "",
      price: p.price,
      stock: p.stock,
      sort_order: p.sort_order,
      images: normalizedImages,
    };
    const errors = getProductValidationErrors(payload);
    if (errors.length) {
      showValidationErrors(errors);
      return;
    }
    try {
      await saveProduct(payload);
      invalidateProducts();
      toast.success("Saved");
      load();
    } catch (error) {
      showApiError(error, "Could not save product");
    }
  };
  const remove = async (id?: string, i?: number) => {
    if (!id) {
      setRows((r) => r.filter((_, j) => j !== i));
      setEditing(null);
      return;
    }
    if (!confirm("Delete this product?")) return;
    try {
      await deleteProduct(id);
      invalidateProducts();
      toast.success("Deleted");
      setEditing(null);
      load();
    } catch (error) {
      showApiError(error, "Could not delete product");
    }
  };

  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter(
      (r) =>
        r.name.toLowerCase().includes(q) ||
        r.slug.toLowerCase().includes(q) ||
        (r.sku ?? "").toLowerCase().includes(q) ||
        (r.description ?? "").toLowerCase().includes(q),
    );
  }, [rows, filter]);

  const rowIndexFor = (product: Product) =>
    rows.findIndex((row) => (product.id ? row.id === product.id : row === product));

  return (
    <AdminPage
      eyebrow="Catalog"
      title="Products"
      description="Add, edit and merchandise everything in your store. Images, sizes, age range, badges and storefront flags."
      action={
        <div className="flex items-center gap-2 flex-wrap">
          <BulkImport
            entityLabel="products"
            templateName="products"
            fields={[
              {
                key: "slug",
                required: true,
                example: "blush-petal-bodysuit",
                note: "Lowercase, hyphens. Unique.",
              },
              { key: "name", required: true, example: "Blush Petal Bodysuit" },
              { key: "sku", required: true, example: "MNM-BPB-001", note: "Unique stock code" },
              {
                key: "category_slug",
                required: true,
                example: "newborn-essentials",
                note: "Must match an existing category slug",
              },
              { key: "price", required: true, example: 1039, note: "UAT storefront price (20% off selling price)" },
              { key: "compare_at_price", example: 1299, note: "Actual selling price — synced to Ledgerly as item rate" },
              {
                key: "age_range",
                required: true,
                example: "0, 24",
                note: "Min and max months, comma-separated (or use age_min_months / age_max_months columns)",
              },
              { key: "age_min_months", example: 0, note: "Optional if age_range is set" },
              { key: "age_max_months", example: 24, note: "Optional if age_range is set" },
              { key: "fabric_type", required: true, example: "100% Cotton" },
              { key: "stock", required: true, example: 50 },
              {
                key: "images",
                example: "https://…/1.jpg | https://…/2.jpg",
                note: "Optional. Pipe (|) separated URLs",
              },
              { key: "description", required: true, example: "Soft organic cotton bodysuit…" },
              { key: "active", required: true, example: "TRUE", note: "TRUE = visible on store" },
              { key: "colors", example: "Blush, Sand", note: "Optional. Comma-separated" },
              { key: "sizes", example: "0-3m, 3-6m", note: "Comma-separated" },
              { key: "badge", example: "Bestseller" },
              { key: "featured", example: "FALSE" },
            ]}
            transform={(r) => {
              const lookup = (key: string) => {
                const wanted = key.toLowerCase().replace(/[\s-]+/g, "_");
                for (const [rawKey, value] of Object.entries(r)) {
                  const normalized = rawKey.toLowerCase().replace(/[\s-]+/g, "_");
                  if (normalized === wanted) return value;
                }
                return "";
              };

              const name = String(lookup("name") ?? "").trim();
              const slug = slugifyProductSlug(String(lookup("slug") ?? "").trim() || name);
              const sku = String(lookup("sku") ?? "").trim();
              const catSlug = String(lookup("category_slug") ?? "")
                .trim()
                .toLowerCase();
              const compareAtRaw = lookup("compare_at_price");
              const compare_at_price =
                compareAtRaw === "" || compareAtRaw == null ? null : Number(compareAtRaw);
              let price = Number(lookup("price"));
              if ((!isFinite(price) || price <= 0) && compare_at_price != null && compare_at_price > 0) {
                price = Math.round(compare_at_price * 0.8);
              }
              const ageRangeRaw = String(lookup("age_range") ?? "").trim();
              let ageMin: number;
              let ageMax: number;
              if (ageRangeRaw) {
                const parsed = parseAgeRangeField(ageRangeRaw);
                if (parsed.min == null || parsed.max == null) {
                  throw new Error('age_range must be two numbers like "0, 24"');
                }
                ageMin = parsed.min;
                ageMax = parsed.max;
              } else {
                ageMin = Number(lookup("age_min_months"));
                ageMax = Number(lookup("age_max_months"));
              }
              const fabric = String(lookup("fabric_type") ?? "").trim();
              const stock = Number(lookup("stock"));
              const imagesRaw = String(lookup("images") ?? "").trim();
              const description = String(lookup("description") ?? "").trim();
              if (!slug) throw new Error("slug is required");
              if (!name) throw new Error("name is required");
              if (name.length > ADMIN_NAME_MAX_LEN) {
                throw new Error(`name must be ${ADMIN_NAME_MAX_LEN} characters or fewer`);
              }
              if (!sku) throw new Error("sku is required");
              if (!catSlug) throw new Error("category_slug is required");
              const cat = cats.find((c) => c.slug.toLowerCase() === catSlug);
              if (!isFinite(price) || price <= 0) throw new Error("price must be > 0");
              if (!isFinite(ageMin)) throw new Error("age_min_months required");
              if (!isFinite(ageMax)) throw new Error("age_max_months required");
              if (!fabric) throw new Error("fabric_type is required");
              if (!isFinite(stock) || stock < 0) throw new Error("stock must be ≥ 0");
              if (!description) throw new Error("description is required");
              let images: string[] = [];
              if (imagesRaw) {
                images = imagesRaw
                  .split(/[|,;\n]/)
                  .map((s) => s.trim())
                  .map((url) => normalizeImageUrl(url))
                  .filter((url): url is string => !!url);
                if (images.length === 0) {
                  throw new Error("images must contain valid http(s) or site-relative URLs");
                }
              }
              return {
                slug,
                name,
                sku,
                category_slug: catSlug,
                category_id: cat?.id ?? null,
                price,
                compare_at_price,
                age_min_months: ageMin,
                age_max_months: ageMax,
                fabric_type: fabric,
                stock,
                images,
                description,
                active: parseSpreadsheetActive(lookup("active"), true),
                colors: lookup("colors")
                  ? String(lookup("colors"))
                      .split(",")
                      .map((s: string) => s.trim())
                      .filter(Boolean)
                  : [],
                sizes: lookup("sizes")
                  ? String(lookup("sizes"))
                      .split(",")
                      .map((s: string) => s.trim())
                      .filter(Boolean)
                  : [],
                badge: lookup("badge") ? String(lookup("badge")) : null,
                best_seller:
                  parseSpreadsheetBoolean(lookup("best_seller"), false) ||
                  isBestsellerFromBadge(String(lookup("badge") ?? "")),
                featured: parseSpreadsheetBoolean(lookup("featured"), false),
                currency: "INR",
              };
            }}
            persist={async (payloads) => {
              type ImportRow = Product & { category_slug?: string };
              const prepErrors: string[] = [];
              const ready: Product[] = [];
              let knownCats = [...cats];

              for (const raw of payloads as ImportRow[]) {
                const catSlug = raw.category_slug?.trim().toLowerCase();
                if (!catSlug) {
                  prepErrors.push(`"${raw.slug}": category_slug is required`);
                  continue;
                }
                let cat = knownCats.find((c) => c.slug.toLowerCase() === catSlug);
                if (!cat) {
                  try {
                    const created = await saveCategory({
                      slug: catSlug,
                      name: categoryNameFromSlug(catSlug),
                      description: null,
                      image_url: null,
                      sort_order: 0,
                      active: true,
                    });
                    cat = { id: created.id!, name: created.name, slug: created.slug };
                    knownCats = [...knownCats, cat];
                  } catch (error) {
                    prepErrors.push(
                      `"${raw.slug}": could not create category "${catSlug}" — ${getUserErrorMessage(error, "save failed")}`,
                    );
                    continue;
                  }
                }
                const { category_slug: _categorySlug, ...product } = raw;
                ready.push({ ...product, category_id: cat.id });
              }

              const { rows: deduped, warnings: slugWarnings } = disambiguateProductImportSlugs(
                ready,
                rows,
              );
              const res = await bulkSave(deduped, rows, "slug", saveProduct);
              return {
                inserted: res.inserted,
                errors: [...prepErrors, ...res.errors],
                warnings: slugWarnings,
              };
            }}
            onDone={() => {
              invalidateProducts();
              load();
            }}
          />
          <button
            onClick={() => {
              setRows((r) => [{ ...empty }, ...r]);
              setEditing(0);
            }}
            className="rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center gap-2"
          >
            <Plus className="h-4 w-4" /> New product
          </button>
        </div>
      }
    >
      <div className="glass-strong rounded-3xl p-4 mb-4">
        <input
          className={inputCls()}
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="Search by name, slug, SKU, or description…"
        />
      </div>

      {loading ? (
        <div className="text-sm text-muted-foreground">Loading…</div>
      ) : (
        <div className="space-y-3">
          {filtered.length === 0 && (
            <div className="glass rounded-2xl p-6 text-sm text-muted-foreground text-center">
              No products yet — start with “New product” above.
            </div>
          )}
          {filtered.map((p) => {
            const i = rowIndexFor(p);
            if (i < 0) return null;
            const isOpen = editing === i;
            return (
              <div key={p.id ?? `new-${i}`} className="glass-strong rounded-3xl overflow-hidden">
                <button
                  onClick={() => setEditing(isOpen ? null : i)}
                  className="w-full p-4 flex items-center gap-4 text-left hover:bg-white/30"
                >
                  <div className="h-14 w-14 rounded-xl bg-muted overflow-hidden grid place-items-center text-xs text-muted-foreground">
                    {p.images[0] ? (
                      <img
                        src={normalizeImageUrl(String(p.images[0])) ?? ""}
                        alt=""
                        className="h-full w-full object-cover"
                      />
                    ) : (
                      "—"
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="font-medium truncate">
                      {p.name || <span className="italic text-muted-foreground">Untitled</span>}
                    </div>
                    <div className="text-xs text-muted-foreground truncate">
                      /{p.slug || "(no slug)"}
                      {p.category_name ? ` · ${p.category_name}` : ""}
                      {p.price != null ? ` · ₹${Number(p.price).toLocaleString("en-IN")}` : ""}
                    </div>
                  </div>
                  <div className="flex gap-1">
                    {p.featured && <Award className="h-4 w-4 text-primary" aria-label="Featured" />}
                    {p.best_seller && (
                      <Star className="h-4 w-4 text-amber-500" aria-label="Best seller" />
                    )}
                    {p.trending && <Flame className="h-4 w-4 text-red-500" aria-label="Trending" />}
                    {p.new_arrival && (
                      <Sparkles className="h-4 w-4 text-emerald-500" aria-label="New" />
                    )}
                    {!p.active && (
                      <span className="text-[10px] uppercase tracking-wider text-muted-foreground">
                        hidden
                      </span>
                    )}
                  </div>
                </button>
                {isOpen && (
                  <div className="p-5 border-t border-white/40 space-y-4">
                    <div className="grid sm:grid-cols-3 gap-3">
                      <Field label="Name">
                        <input
                          className={inputCls()}
                          maxLength={ADMIN_NAME_MAX_LEN}
                          value={p.name}
                          onChange={(e) => update(i, { name: e.target.value })}
                        />
                      </Field>
                      <Field label="Slug">
                        <input
                          className={inputCls()}
                          value={p.slug}
                          placeholder="auto"
                          onChange={(e) => update(i, { slug: e.target.value })}
                        />
                      </Field>
                      <Field label="Category (required)">
                        <select
                          className={inputCls()}
                          value={p.category_id ?? ""}
                          onChange={(e) => update(i, { category_id: e.target.value || null })}
                        >
                          <option value="">Select category…</option>
                          {cats.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name}
                            </option>
                          ))}
                        </select>
                      </Field>
                    </div>
                    <div className="grid sm:grid-cols-2 gap-3">
                      <Field label="SKU (required)">
                        <input
                          className={inputCls()}
                          value={p.sku ?? ""}
                          onChange={(e) => update(i, { sku: e.target.value })}
                          placeholder="MNM-XXX-001"
                        />
                      </Field>
                      <Field label="Fabric type (required)">
                        <input
                          className={inputCls()}
                          value={p.fabric_type ?? ""}
                          onChange={(e) => update(i, { fabric_type: e.target.value })}
                          placeholder="100% Cotton"
                        />
                      </Field>
                    </div>
                    <div className="grid sm:grid-cols-4 gap-3">
                      <NumberField
                        label={
                          (p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS"
                            ? "UAT price (from variants)"
                            : "UAT price (₹)"
                        }
                        value={p.price}
                        onChange={(price) => {
                          if ((p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS") return;
                          update(i, { price });
                        }}
                        required
                      />
                      <NumberField
                        label={
                          (p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS"
                            ? "Selling price (min rate)"
                            : "Selling price (₹)"
                        }
                        value={p.compare_at_price}
                        onChange={(compare_at_price) => {
                          if ((p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS") return;
                          update(i, {
                            compare_at_price,
                            price:
                              compare_at_price != null && compare_at_price !== ""
                                ? Math.round(Number(compare_at_price) * 0.8)
                                : p.price,
                          });
                        }}
                      />
                      <NumberField
                        label={
                          (p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS"
                            ? "Stock (sum of variants)"
                            : "Stock"
                        }
                        value={p.stock}
                        onChange={(stock) => {
                          if ((p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS") return;
                          update(i, { stock });
                        }}
                        min={0}
                        required
                      />
                      <NumberField
                        label="Sort order"
                        value={p.sort_order}
                        onChange={(sort_order) => update(i, { sort_order })}
                        min={0}
                        placeholder="0"
                      />
                    </div>
                    <div className="grid sm:grid-cols-2 gap-3">
                      <AgeRangeField
                        min={p.age_min_months}
                        max={p.age_max_months}
                        onChange={(range) => update(i, range)}
                        required
                      />
                      <Field label="Badge">
                        <input
                          className={inputCls()}
                          value={p.badge ?? ""}
                          placeholder="e.g. Bestseller"
                          onChange={(e) => update(i, { badge: e.target.value })}
                        />
                      </Field>
                    </div>
                    <div className="grid sm:grid-cols-2 gap-3">
                      <Field label="Item structure">
                        <select
                          className={inputCls()}
                          value={p.item_structure ?? "SIMPLE"}
                          onChange={(e) =>
                            update(i, {
                              item_structure: e.target.value,
                              ...(e.target.value === "SIMPLE"
                                ? { variants_json: null }
                                : {
                                    variants_json: p.variants_json?.trim() ? p.variants_json : "[]",
                                  }),
                            })
                          }
                        >
                          <option value="SIMPLE">SIMPLE (one SKU / price)</option>
                          <option value="VARIANTS">VARIANTS (size × color × price)</option>
                        </select>
                      </Field>
                    </div>
                    {(p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS" ? (
                      <VariantsMatrixEditor
                        parentSku={p.sku ?? ""}
                        value={p.variants_json}
                        onChange={(next) => update(i, next)}
                      />
                    ) : (
                      <>
                        <CommaListField
                          label="Sizes (comma-separated)"
                          value={p.sizes}
                          onChange={(sizes) => update(i, { sizes })}
                          placeholder="0-3m, 3-6m, 6-12m"
                        />
                        <CommaListField
                          label="Colors (optional, comma-separated)"
                          value={p.colors}
                          onChange={(colors) => update(i, { colors })}
                          placeholder="Cream, Sage, Peach — optional"
                        />
                      </>
                    )}
                    <Field label="Description">
                      <textarea
                        rows={3}
                        className={inputCls()}
                        value={p.description ?? ""}
                        onChange={(e) => update(i, { description: e.target.value })}
                      />
                    </Field>
                    <Field label="Story (markdown)">
                      <textarea
                        rows={5}
                        className={inputCls() + " font-mono text-xs"}
                        value={p.story ?? ""}
                        onChange={(e) => update(i, { story: e.target.value })}
                      />
                    </Field>

                    <Field label="Images">
                      <ImageSpecHint spec={ADMIN_IMAGE_SPECS.productGallery} className="mb-2" />
                      <div className="space-y-3">
                        <div className="flex flex-wrap gap-2">
                          {p.images.map((url, k) => (
                            <div
                              key={k}
                              className="relative h-24 w-24 rounded-xl overflow-hidden ring-1 ring-border group"
                            >
                              <img
                                src={normalizeImageUrl(String(url)) ?? ""}
                                alt=""
                                className="h-full w-full object-cover"
                              />
                              <button
                                onClick={() =>
                                  update(i, { images: p.images.filter((_, j) => j !== k) })
                                }
                                className="absolute top-1 right-1 h-5 w-5 grid place-items-center rounded-full bg-foreground/70 text-background opacity-0 group-hover:opacity-100"
                              >
                                <X className="h-3 w-3" />
                              </button>
                            </div>
                          ))}
                          <label className="h-24 w-24 grid place-items-center rounded-xl border-2 border-dashed border-border text-muted-foreground hover:bg-white/30 cursor-pointer">
                            <div className="text-center">
                              <Upload className="h-4 w-4 mx-auto" />
                              <div className="text-[10px] mt-0.5">Upload</div>
                            </div>
                            <input
                              type="file"
                              accept={IMAGE_ACCEPT}
                              multiple
                              className="hidden"
                              onChange={(e) => e.target.files && upload(e.target.files, i)}
                            />
                          </label>
                        </div>
                      </div>
                    </Field>

                    <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 text-sm">
                      <label className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={p.active}
                          onChange={(e) => update(i, { active: e.target.checked })}
                        />{" "}
                        Active
                      </label>
                      <label className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={p.featured}
                          onChange={(e) => update(i, { featured: e.target.checked })}
                        />{" "}
                        Featured
                      </label>
                      <label className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={p.best_seller}
                          onChange={(e) => update(i, { best_seller: e.target.checked })}
                        />{" "}
                        Best seller
                      </label>
                      <label className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={p.trending}
                          onChange={(e) => update(i, { trending: e.target.checked })}
                        />{" "}
                        Trending
                      </label>
                      <label className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={p.new_arrival}
                          onChange={(e) => update(i, { new_arrival: e.target.checked })}
                        />{" "}
                        New arrival
                      </label>
                    </div>

                    {(p.ledgerly_sync_status || p.ledgerly_sync_error) && (
                      <div className="text-xs rounded-xl bg-white/40 px-3 py-2 space-y-1">
                        {p.ledgerly_sync_status && (
                          <p>
                            Ledgerly: <span className="font-medium">{p.ledgerly_sync_status}</span>
                            {p.ledgerly_item_id ? ` · item ${p.ledgerly_item_id}` : ""}
                          </p>
                        )}
                        {p.ledgerly_sync_error && (
                          <p className="text-destructive">{p.ledgerly_sync_error}</p>
                        )}
                      </div>
                    )}

                    <div className="flex flex-wrap gap-2 pt-2">
                      <button
                        onClick={() => save(p)}
                        className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm flex items-center gap-2"
                      >
                        <Save className="h-4 w-4" /> Save
                      </button>
                      {p.id && (
                        <button
                          type="button"
                          disabled={ledgerlySyncing.has(i)}
                          onClick={() => void syncToLedgerly(i, p)}
                          className="rounded-full glass px-4 py-2 text-sm flex items-center gap-2 disabled:opacity-50"
                          title="Push this product to Ledgerly (requires SKU and category)"
                        >
                          {ledgerlySyncing.has(i) ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <CloudUpload className="h-4 w-4" />
                          )}
                          {ledgerlySyncing.has(i) ? "Syncing…" : "Sync to Ledgerly"}
                        </button>
                      )}
                      <button
                        onClick={() => remove(p.id, i)}
                        className="rounded-full glass px-4 py-2 text-sm flex items-center gap-2 text-destructive"
                      >
                        <Trash2 className="h-4 w-4" /> Delete
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </AdminPage>
  );
}
