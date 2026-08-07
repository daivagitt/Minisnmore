import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { AgeRangeField } from "@/components/admin/AgeRangeField";
import { CommaListField } from "@/components/admin/CommaListField";
import { NumberField } from "@/components/admin/NumberField";
import { VariantsMatrixEditor, validateVariantRows } from "@/components/admin/VariantsMatrixEditor";
import { suggestParentSku } from "@/lib/admin-variants";
import { parseAgeRangeField } from "@/lib/comma-list-field";
import { ImageSpecHint } from "@/components/admin/ImageSpecHint";
import { ADMIN_IMAGE_SPECS } from "@/lib/admin-image-specs";
import { BulkImport } from "@/components/admin/BulkImport";
import { ColorImagesEditor } from "@/components/admin/ColorImagesEditor";
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
import { api } from "@/lib/api";
import { sanitizePublicUrl } from "@/lib/utils";
import { IMAGE_ACCEPT, validateAdminImageFile } from "@/lib/admin-image";
import { prepareProductImageForUpload } from "@/lib/product-image-normalize";
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
  parseSpreadsheetImageUrls,
  normalizeProductImageUrl,
  saveCategory,
  saveProduct,
  slugifyProductSlug,
  syncLedgerlyProduct,
  uploadAdminImage,
  type ProductRow,
} from "@/lib/backend-data";
import { invalidateProducts } from "@/lib/storefront";
import { isBestsellerFromBadge } from "@/lib/product-flags";
import { filterAdminProductsByQuery } from "@/lib/product-search";
import { colorNames, ensureColorEntries, mergeColorNames, normalizeColorEntries, resolveProductImages, type ProductColorEntry } from "@/lib/product-colors";

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
  units: "ea",
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

const normalizeImageUrl = (value: string) => normalizeProductImageUrl(value);

function ProductsPage() {
  const [rows, setRows] = useState<Product[]>([]);
  const [cats, setCats] = useState<{ id: string; name: string; slug: string }[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState("");
  const [editing, setEditing] = useState<number | null>(null);
  const [ledgerlySyncing, setLedgerlySyncing] = useState<Set<number>>(() => new Set());
  const [imageUploading, setImageUploading] = useState<number | null>(null);
  const [skuWiseImporting, setSkuWiseImporting] = useState(false);

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

  const importSkuWisePrint = async (file: File) => {
    setSkuWiseImporting(true);
    try {
      const formData = new FormData();
      formData.append("file", file);
      await api.post("/api/v1/admin/import/sku-wise-print", formData, { auth: true });
      toast.success("SKU-wise print import completed! Reloading products...");
      await new Promise(r => setTimeout(r, 1000));
      load();
    } catch (error) {
      showApiError(error, "SKU-wise print import failed");
    } finally {
      setSkuWiseImporting(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const upload = async (files: FileList, i: number) => {
    const accepted: File[] = [];
    for (const file of Array.from(files)) {
      const result = validateAdminImageFile(file);
      if (result.ok) accepted.push(file);
      else toast.error(result.error);
    }
    if (!accepted.length) return;

    setImageUploading(i);
    const urls: string[] = [];
    try {
      for (const f of accepted) {
        try {
          const prepared = await prepareProductImageForUpload(f, "gallery");
          urls.push(await uploadAdminImage("products", prepared));
        } catch (error) {
          showApiError(error, "Upload failed");
        }
      }
      if (urls.length) {
        setRows((r) => r.map((x, j) => (j === i ? { ...x, images: [...x.images, ...urls] } : x)));
        toast.success(`${urls.length} image(s) normalized to square and uploaded. Click Save to keep them.`);
      }
    } finally {
      setImageUploading(null);
    }
  };

  const update = (i: number, patch: Partial<Product>) =>
    setRows((r) => r.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  const patchProductColor = (rowIndex: number, colorIndex: number, patch: Partial<ProductColorEntry>) => {
    setRows((rows) =>
      rows.map((row, j) => {
        if (j !== rowIndex) return row;
        const colors = ensureColorEntries(row.colors).map((entry, ci) => {
          if (ci !== colorIndex) return entry;
          const merged = { ...entry, ...patch };
          return normalizeColorEntries([merged])[0] ?? merged;
        });
        return { ...row, colors };
      }),
    );
  };

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

  const saveWithColorGallery = async (p: Product, colorIndex: number, imageUrls: string[]) => {
    const colors = ensureColorEntries(p.colors).map((entry, ci) =>
      ci === colorIndex ? { ...entry, imageUrls } : entry,
    );
    await save({ ...p, colors });
  };

  const saveWithColorSwatch = async (p: Product, colorIndex: number, swatchUrl: string) => {
    const colors = ensureColorEntries(p.colors).map((entry, ci) =>
      ci === colorIndex ? { ...entry, swatchUrl } : entry,
    );
    await save({ ...p, colors });
  };

  const save = async (p: Product) => {
    const name = p.name.trim();
    const slug = (p.slug.trim() || slugify(name)).toLowerCase();
    const sku = p.sku?.trim() || suggestParentSku(name);
    const normalizedImages = resolveProductImages(
      (p.images ?? [])
        .map((url) => normalizeImageUrl(String(url)))
        .filter((url): url is string => !!url),
      p.colors,
    );
    const payload = {
      ...p,
      name,
      slug,
      sku,
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

  const filtered = useMemo(() => filterAdminProductsByQuery(rows, filter), [rows, filter]);
  const filterActive = filter.trim().length > 0;

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
              { key: "price", required: true, example: 1039, note: "Selling price (storefront; 20% off MRP)" },
              { key: "compare_at_price", example: 1299, note: "MRP — synced to Ledgerly as mrp (not rate)" },
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
                example: "https://cdn.example.com/1.jpg | https://cdn.example.com/2.jpg",
                note: "Optional. Pipe (|), comma, or semicolon separated. Also accepts columns: image, image_url, image_urls",
              },
              { key: "description", example: "Soft organic cotton bodysuit…", note: "Optional" },
              { key: "active", required: true, example: "TRUE", note: "TRUE = visible on store" },
              { key: "colors", example: "Blush, Sand", note: "Optional. Comma-separated" },
              { key: "sizes", example: "0-3m, 3-6m", note: "Comma-separated" },
              { key: "badge", example: "Bestseller" },
              { key: "featured", example: "FALSE" },
            ]}
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
          <label
            className={`rounded-full border border-amber-600 bg-amber-50 text-amber-900 px-4 py-2.5 text-sm flex items-center gap-2 cursor-pointer hover:bg-amber-100 transition ${
              skuWiseImporting ? "opacity-60 pointer-events-none" : ""
            }`}
            title="Import products from SKU WISE PRINT spreadsheet (upserts by parent SKU)"
          >
            {skuWiseImporting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
            {skuWiseImporting ? "Importing..." : "SKU-wise Print Import"}
            <input
              type="file"
              accept=".xlsx,.xls"
              disabled={skuWiseImporting}
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) {
                  if (confirm("Import from spreadsheet? Existing products with matching parent SKU will be updated; new styles will be added. Products not in the file stay unchanged.")) {
                    importSkuWisePrint(file);
                  }
                }
                e.target.value = "";
              }}
            />
          </label>
        </div>
      }
    >
      <div className="glass-strong rounded-3xl p-4 mb-4">
        <input
          className={inputCls()}
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="Search by name, slug, SKU, variant SKU, or description…"
        />
      </div>

      {loading ? (
        <div className="text-sm text-muted-foreground">Loading…</div>
      ) : (
        <div className="space-y-3">
          {filtered.length === 0 && (
            <div className="glass rounded-2xl p-6 text-sm text-muted-foreground text-center">
              {filterActive
                ? `No products match “${filter.trim()}”.`
                : rows.length === 0
                  ? "No products yet — start with “New product” above."
                  : "No products to show."}
              {filterActive ? (
                <div className="mt-3">
                  <button
                    type="button"
                    onClick={() => setFilter("")}
                    className="text-primary story-link text-sm"
                  >
                    Clear search
                  </button>
                </div>
              ) : null}
            </div>
          )}
          {filtered.map((p) => {
            const i = rowIndexFor(p);
            if (i < 0) return null;
            const isOpen = editing === i;
            const previewImage = resolveProductImages(p.images, p.colors)[0];
            return (
              <div key={p.id ?? `new-${i}`} className="glass-strong rounded-3xl overflow-hidden">
                <button
                  onClick={() => setEditing(isOpen ? null : i)}
                  className="w-full p-4 flex items-center gap-4 text-left hover:bg-white/30"
                >
                  <div className="h-14 w-14 rounded-xl bg-muted overflow-hidden grid place-items-center text-xs text-muted-foreground">
                    {previewImage ? (
                      <img
                        src={normalizeImageUrl(String(previewImage)) ?? ""}
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
                        <div className="flex gap-2">
                          <input
                            className={inputCls()}
                            value={p.sku ?? ""}
                            onChange={(e) => update(i, { sku: e.target.value })}
                            placeholder={suggestParentSku(p.name || "Product")}
                          />
                          <button
                            type="button"
                            title="Generate from product name (Ledgerly parity)"
                            onClick={() => update(i, { sku: suggestParentSku(p.name || "") })}
                            className="shrink-0 rounded-full glass px-3 py-2 text-xs font-medium hover:bg-white/60"
                          >
                            Generate
                          </button>
                        </div>
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
                            ? "Selling price (from variants)"
                            : "Selling price (₹)"
                        }
                        value={p.price}
                        readOnly={(p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS"}
                        onChange={(price) => update(i, { price })}
                        required
                      />
                      <NumberField
                        label={
                          (p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS"
                            ? "MRP (min from variants)"
                            : "MRP (₹)"
                        }
                        value={p.compare_at_price}
                        readOnly={(p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS"}
                        onChange={(compare_at_price) => {
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
                        readOnly={(p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS"}
                        onChange={(stock) => update(i, { stock })}
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
                    <div className="grid sm:grid-cols-3 gap-3">
                      <Field label="Units (UOM)">
                        <select
                          className={inputCls()}
                          value={p.units ?? "ea"}
                          onChange={(e) => update(i, { units: e.target.value })}
                        >
                          <option value="ea">ea — each / piece</option>
                          <option value="pc">pc — piece</option>
                          <option value="set">set — set</option>
                          <option value="pair">pair — pair</option>
                          <option value="pack">pack — pack</option>
                        </select>
                      </Field>
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
                          <option value="VARIANTS">VARIANTS (size × color matrix)</option>
                        </select>
                      </Field>
                    </div>
                    {(p.item_structure ?? "SIMPLE").toUpperCase() === "VARIANTS" ? (
                      <>
                        <div className="grid sm:grid-cols-2 gap-3">
                          <CommaListField
                            label="Sizes (comma-separated)"
                            value={p.sizes}
                            onChange={(sizes) => update(i, { sizes })}
                            placeholder="S, M, L or 0-3m, 3-6m"
                          />
                          <CommaListField
                            label="Colors (comma-separated)"
                            value={colorNames(p.colors)}
                            onChange={(names) => update(i, { colors: mergeColorNames(p.colors, names) })}
                            placeholder="Navy, Ivory, Blush"
                          />
                        </div>
                        <ColorImagesEditor
                          colors={p.colors ?? []}
                          onPatchColor={(colorIndex, patch) => patchProductColor(i, colorIndex, patch)}
                          onGalleryUploaded={({ index, imageUrls }) => saveWithColorGallery(p, index, imageUrls)}
                          onSwatchUploaded={({ index, swatchUrl }) => saveWithColorSwatch(p, index, swatchUrl)}
                        />
                        <VariantsMatrixEditor
                          parentSku={p.sku?.trim() || suggestParentSku(p.name || "")}
                          sizes={p.sizes ?? []}
                          colors={colorNames(p.colors)}
                          value={p.variants_json}
                          onChange={(next) =>
                            update(i, {
                              ...next,
                              colors: mergeColorNames(p.colors, next.colors),
                            })
                          }
                        />
                      </>
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
                          value={colorNames(p.colors)}
                          onChange={(names) => update(i, { colors: mergeColorNames(p.colors, names) })}
                          placeholder="Cream, Sage, Peach — optional"
                        />
                        {colorNames(p.colors).length > 0 ? (
                          <ColorImagesEditor
                            colors={p.colors ?? []}
                            onPatchColor={(colorIndex, patch) => patchProductColor(i, colorIndex, patch)}
                            onGalleryUploaded={({ index, imageUrls }) => saveWithColorGallery(p, index, imageUrls)}
                          onSwatchUploaded={({ index, swatchUrl }) => saveWithColorSwatch(p, index, swatchUrl)}
                          />
                        ) : null}
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
                      {(() => {
                        const displayImages = resolveProductImages(p.images, p.colors);
                        const fromColorGalleries = !(p.images ?? []).length && displayImages.length > 0;
                        return (
                      <div className="space-y-3">
                        {fromColorGalleries ? (
                          <p className="text-xs text-muted-foreground rounded-lg glass px-3 py-2 leading-snug">
                            Showing photos from colour galleries below. They sync into main images when you Save (for shop cards and Ledgerly).
                          </p>
                        ) : null}
                        <div className="flex flex-wrap gap-2">
                          {displayImages.map((url, k) => (
                            <div
                              key={`${url}-${k}`}
                              className={`relative h-24 w-24 rounded-xl overflow-hidden ring-1 ring-border group ${fromColorGalleries ? "ring-primary/30" : ""}`}
                            >
                              <img
                                src={normalizeImageUrl(String(url)) ?? ""}
                                alt=""
                                className="h-full w-full object-cover"
                              />
                              {!fromColorGalleries ? (
                              <button
                                type="button"
                                onClick={() =>
                                  update(i, { images: p.images.filter((_, j) => j !== k) })
                                }
                                className="absolute top-1 right-1 h-5 w-5 grid place-items-center rounded-full bg-foreground/70 text-background opacity-0 group-hover:opacity-100"
                              >
                                <X className="h-3 w-3" />
                              </button>
                              ) : null}
                            </div>
                          ))}
                          <div className={`relative h-24 w-24 shrink-0 rounded-xl border-2 border-dashed border-border text-muted-foreground overflow-hidden hover:bg-white/30 ${imageUploading === i ? "opacity-50" : ""}`}>
                            <input
                              type="file"
                              accept={IMAGE_ACCEPT}
                              multiple
                              disabled={imageUploading === i}
                              aria-label="Upload product images"
                              className="absolute inset-0 z-10 h-full w-full cursor-pointer opacity-0 disabled:cursor-not-allowed"
                              onChange={(e) => {
                                const files = e.target.files;
                                e.target.value = "";
                                if (files?.length) void upload(files, i);
                              }}
                            />
                            <div className="pointer-events-none absolute inset-0 grid place-items-center">
                              {imageUploading === i ? (
                                <Loader2 className="h-4 w-4 animate-spin" />
                              ) : (
                              <div className="text-center">
                                <Upload className="h-4 w-4 mx-auto" />
                                <div className="text-[10px] mt-0.5">Upload</div>
                              </div>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                        );
                      })()}
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
