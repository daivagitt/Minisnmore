import { Plus, Trash2, X } from "lucide-react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import {
  aggregateFromVariantRows,
  buildVariantMatrixFromSizesColors,
  regenerateVariantSkus,
  rowsFromVariantsJson,
  serializeVariantRows,
  suggestVariantSku,
  variantAmountBlur,
  variantAmountDisplay,
  variantAmountInput,
  type VariantEditorRow,
} from "@/lib/admin-variants";
import { normalizeProductImageUrl } from "@/lib/backend-data";

export { validateVariantRows } from "@/lib/admin-variants";

function rowPhotos(row: VariantEditorRow): string[] {
  return collectRowPhotos(row);
}

function collectRowPhotos(row: VariantEditorRow): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const url of [row.imageUrl, ...(row.imageUrls ?? [])]) {
    const trimmed = url?.trim();
    if (!trimmed || seen.has(trimmed)) continue;
    seen.add(trimmed);
    out.push(trimmed);
  }
  return out;
}

export function VariantsMatrixEditor({
  parentSku,
  sizes,
  colors,
  value,
  onChange,
}: {
  parentSku: string;
  sizes: string[];
  colors: string[];
  value: string | null;
  onChange: (next: {
    variants_json: string;
    sizes: string[];
    colors: string[];
    compare_at_price: number | null;
    price: number | null;
    stock: number;
  }) => void;
}) {
  const rows = rowsFromVariantsJson(value);

  const commit = (nextRows: VariantEditorRow[]) => {
    const variants_json = serializeVariantRows(nextRows);
    const agg = aggregateFromVariantRows(nextRows, sizes);
    onChange({
      variants_json,
      sizes: agg.sizes,
      colors: agg.colors,
      compare_at_price: agg.compare_at_price,
      price: agg.price,
      stock: agg.stock,
    });
  };

  const patchRow = (index: number, patch: Partial<VariantEditorRow>) => {
    const next = rows.map((r, i) => (i === index ? { ...r, ...patch } : r));
    commit(next);
  };

  const addRow = () => {
    commit([
      ...rows,
      {
        sku: suggestVariantSku(parentSku, "", ""),
        size: "",
        color: "",
        rate: rows.find((r) => Number(r.rate) > 0)?.rate ?? "",
        mrp: rows.find((r) => Number(r.mrp) > 0)?.mrp ?? "",
        cost: "",
        stock: 0,
        active: true,
      },
    ]);
  };

  const removeRow = (index: number) => {
    const next = rows.filter((_, i) => i !== index);
    commit(
      next.length
        ? next
        : [
            {
              sku: suggestVariantSku(parentSku, "", ""),
              size: "",
              color: "",
              rate: "",
              mrp: "",
              cost: "",
              stock: 0,
              active: true,
            },
          ],
    );
  };

  const fillMissingSkus = () => {
    commit(
      rows.map((r) => ({
        ...r,
        sku: r.sku.trim() || suggestVariantSku(parentSku, r.size, r.color),
      })),
    );
  };

  const refreshMatrix = () => {
    if (!sizes.length || !colors.length) return;
    commit(buildVariantMatrixFromSizesColors(sizes, colors, parentSku, rows));
  };

  const refreshSkus = () => {
    if (!parentSku.trim()) return;
    commit(regenerateVariantSkus(rows, parentSku));
  };

  const canRefreshMatrix = sizes.length > 0 && colors.length > 0;

  return (
    <Field label="Variants (size × color matrix)">
      <div className="overflow-x-auto rounded-xl border border-border/60">
        <table className="w-full text-xs min-w-[820px]">
          <thead>
            <tr className="bg-muted/40 text-left">
              <th className="p-2 font-medium">Size</th>
              <th className="p-2 font-medium">Color</th>
              <th className="p-2 font-medium">Variant SKU</th>
              <th className="p-2 font-medium">Selling (₹)</th>
              <th className="p-2 font-medium">MRP (₹)</th>
              <th className="p-2 font-medium">Cost (₹)</th>
              <th className="p-2 font-medium">Stock</th>
              <th className="p-2 font-medium">Photo</th>
              <th className="p-2 font-medium">Active</th>
              <th className="p-2 font-medium">Zoho item</th>
              <th className="p-2 w-10" />
            </tr>
          </thead>
          <tbody>
            {rows.map((r, index) => {
              const photos = rowPhotos(r);
              return (
              <tr key={r.id ?? `row-${index}`} className="border-t border-border/40">
                <td className="p-1.5">
                  <input
                    className={inputCls() + " !text-xs"}
                    value={r.size}
                    placeholder="S / 0-3m"
                    onChange={(e) => patchRow(index, { size: e.target.value })}
                    onBlur={() => {
                      if (!r.sku.trim() && (r.size || r.color)) {
                        patchRow(index, { sku: suggestVariantSku(parentSku, r.size, r.color) });
                      }
                    }}
                  />
                </td>
                <td className="p-1.5">
                  <input
                    className={inputCls() + " !text-xs"}
                    value={r.color}
                    placeholder="Navy"
                    onChange={(e) => patchRow(index, { color: e.target.value })}
                    onBlur={() => {
                      if (!r.sku.trim() && (r.size || r.color)) {
                        patchRow(index, { sku: suggestVariantSku(parentSku, r.size, r.color) });
                      }
                    }}
                  />
                </td>
                <td className="p-1.5">
                  <input
                    className={inputCls() + " !text-xs font-mono"}
                    value={r.sku}
                    placeholder={suggestVariantSku(parentSku, r.size, r.color)}
                    onChange={(e) => patchRow(index, { sku: e.target.value })}
                  />
                </td>
                <td className="p-1.5">
                  <input
                    type="text"
                    inputMode="decimal"
                    className={inputCls() + " !text-xs"}
                    value={variantAmountDisplay(r.rate)}
                    aria-label="Selling price"
                    onChange={(e) => patchRow(index, { rate: variantAmountInput(e.target.value) })}
                    onBlur={() => patchRow(index, { rate: variantAmountBlur(r.rate) })}
                  />
                </td>
                <td className="p-1.5">
                  <input
                    type="text"
                    inputMode="decimal"
                    className={inputCls() + " !text-xs"}
                    value={variantAmountDisplay(r.mrp)}
                    aria-label="MRP"
                    onChange={(e) => patchRow(index, { mrp: variantAmountInput(e.target.value) })}
                    onBlur={() => {
                      const mrp = variantAmountBlur(r.mrp);
                      const rate = variantAmountBlur(r.rate);
                      const patch: Partial<VariantEditorRow> = { mrp };
                      if (mrp !== "" && rate === "") {
                        patch.rate = Math.round(mrp * 0.8 * 100) / 100;
                      }
                      patchRow(index, patch);
                    }}
                  />
                </td>
                <td className="p-1.5">
                  <input
                    type="text"
                    inputMode="decimal"
                    className={inputCls() + " !text-xs"}
                    value={variantAmountDisplay(r.cost)}
                    onChange={(e) => patchRow(index, { cost: variantAmountInput(e.target.value) })}
                    onBlur={() => patchRow(index, { cost: variantAmountBlur(r.cost) })}
                  />
                </td>
                <td className="p-1.5">
                  <div className="flex items-center gap-1.5">
                    <input
                      type="text"
                      inputMode="numeric"
                      className={`${inputCls()} !text-xs ${Number(r.stock) <= 0 && r.active ? "border-destructive/60" : ""}`}
                      value={variantAmountDisplay(r.stock)}
                      aria-label="Stock"
                      onChange={(e) => patchRow(index, { stock: variantAmountInput(e.target.value) })}
                      onBlur={() => {
                        const stock = variantAmountBlur(r.stock);
                        patchRow(index, { stock: stock === "" ? 0 : Math.max(0, Math.floor(stock)) });
                      }}
                    />
                    {Number(r.stock) <= 0 && r.active && (
                      <span className="shrink-0 text-[10px] font-medium text-destructive whitespace-nowrap">
                        Sold out
                      </span>
                    )}
                  </div>
                </td>
                <td className="p-1.5">
                  {photos.length ? (
                    <div className="flex items-center gap-1">
                      {photos.slice(0, 3).map((url) => (
                        <img
                          key={url}
                          src={normalizeProductImageUrl(url) ?? url}
                          alt=""
                          className="h-10 w-10 rounded-md object-cover border border-border/60"
                        />
                      ))}
                      <button
                        type="button"
                        className="h-7 w-7 grid place-items-center rounded-md text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => patchRow(index, { imageUrl: undefined, imageUrls: undefined })}
                        aria-label={`Remove photos from ${r.sku || "variant"}`}
                        title="Remove photos from this SKU"
                      >
                        <X className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  ) : (
                    <span className="text-[10px] text-muted-foreground">—</span>
                  )}
                </td>
                <td className="p-1.5 text-center">
                  <input
                    type="checkbox"
                    checked={r.active}
                    onChange={(e) => patchRow(index, { active: e.target.checked })}
                    aria-label="Active"
                  />
                </td>
                <td className="p-1.5 font-mono text-[10px] text-muted-foreground max-w-[120px] truncate" title={r.zohoItemId ?? undefined}>
                  {r.zohoItemId?.trim() ? r.zohoItemId.trim() : "—"}
                </td>
                <td className="p-1.5">
                  <button
                    type="button"
                    className="h-8 w-8 grid place-items-center rounded-lg hover:bg-destructive/10 text-destructive"
                    onClick={() => removeRow(index)}
                    aria-label="Remove variant"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </td>
              </tr>
            );
            })}
          </tbody>
        </table>
      </div>
      <p className="mt-2 text-[11px] text-muted-foreground leading-relaxed">
        Zoho Books does not use a single parent item for variant products — each variant SKU syncs as its own Zoho item.
        The <span className="font-medium">Zoho item</span> column shows the linked id after sync or pull (read-only).
        Photo is stored on this Mini product, not read from the Zoho Books item-image screen.
        Pull from Zoho always clears these thumbnails. Use the colour gallery above for real photos.
      </p>
      <div className="mt-2 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={addRow}
          className="inline-flex items-center gap-1.5 rounded-full glass px-3 py-1.5 text-xs font-medium hover:bg-white/60"
        >
          <Plus className="h-3.5 w-3.5" /> Add variant
        </button>
        <button
          type="button"
          onClick={fillMissingSkus}
          className="inline-flex items-center gap-1.5 rounded-full glass px-3 py-1.5 text-xs font-medium hover:bg-white/60"
        >
          Fill missing SKUs
        </button>
        <button
          type="button"
          onClick={refreshMatrix}
          disabled={!canRefreshMatrix}
          title={canRefreshMatrix ? undefined : "Add sizes and colours above first"}
          className="inline-flex items-center gap-1.5 rounded-full glass px-3 py-1.5 text-xs font-medium hover:bg-white/60 disabled:opacity-40"
        >
          Generate / refresh matrix
        </button>
        <button
          type="button"
          onClick={refreshSkus}
          disabled={!parentSku.trim()}
          className="inline-flex items-center gap-1.5 rounded-full glass px-3 py-1.5 text-xs font-medium hover:bg-white/60 disabled:opacity-40"
        >
          Regenerate variant SKUs
        </button>
      </div>
    </Field>
  );
}
