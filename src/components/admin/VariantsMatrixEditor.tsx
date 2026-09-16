import { GripVertical, CloudDownload, Loader2, Plus, Trash2, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import {
  aggregateFromVariantRows,
  buildVariantMatrixFromSizesColors,
  regenerateVariantSkus,
  rowsFromVariantsJson,
  serializeVariantRows,
  suggestVariantSku,
  syncVariantRowSellingFromMrp,
  variantAmount,
  variantAmountBlur,
  variantAmountDisplay,
  variantAmountInput,
  variantRowSelling,
  type VariantEditorRow,
} from "@/lib/admin-variants";
import { AdminProductImage } from "@/components/admin/AdminProductImage";
import { normalizeProductImageUrl } from "@/lib/backend-data";
import { setDisplayImageDragData } from "@/lib/admin-display-image";

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

function coerceMoneyFields(row: VariantEditorRow): VariantEditorRow {
  return {
    ...row,
    rate: variantAmountBlur(row.rate),
    mrp: variantAmountBlur(row.mrp),
    cost: variantAmountBlur(row.cost),
    stock:
      row.stock === "" || row.stock == null
        ? 0
        : Math.max(0, Math.floor(Number(variantAmountBlur(row.stock)) || 0)),
  };
}

export function VariantsMatrixEditor({
  parentSku,
  sizes,
  colors,
  value,
  onChange,
  onPullFromZoho,
  pullingRowKeys,
  freezeColors = false,
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
  onPullFromZoho?: (row: VariantEditorRow, index: number) => void;
  pullingRowKeys?: Set<string>;
  /** Print/colour names come from Zoho pull — matrix rows show them read-only. */
  freezeColors?: boolean;
}) {
  const settings = useSiteSettings();
  const discountPercent = settings.store_discount_percent ?? 20;
  // Local rows keep decimal drafts ("2.", "2.0") — parent JSON round-trip would coerce via Number().
  const [rows, setRows] = useState<VariantEditorRow[]>(() => rowsFromVariantsJson(value, discountPercent));
  const rowsRef = useRef(rows);
  rowsRef.current = rows;
  const skipPropSync = useRef(false);

  useEffect(() => {
    if (skipPropSync.current) {
      skipPropSync.current = false;
      return;
    }
    setRows(rowsFromVariantsJson(value, discountPercent));
  }, [value, discountPercent]);

  const commit = (nextRows: VariantEditorRow[]) => {
    const normalized = nextRows.map((row) => {
      const coerced = coerceMoneyFields(row);
      return variantAmount(coerced.mrp) > 0 ? syncVariantRowSellingFromMrp(coerced, discountPercent) : coerced;
    });
    skipPropSync.current = true;
    setRows(normalized);
    const variants_json = serializeVariantRows(normalized);
    const agg = aggregateFromVariantRows(normalized, sizes, discountPercent);
    onChange({
      variants_json,
      sizes: agg.sizes,
      colors: agg.colors,
      compare_at_price: agg.compare_at_price,
      price: agg.price,
      stock: agg.stock,
    });
  };

  /** Immediate commit for non-money fields (size, sku, active, …). */
  const patchRow = (index: number, patch: Partial<VariantEditorRow>) => {
    const next = rows.map((r, i) => {
      if (i !== index) return r;
      const merged = { ...r, ...patch };
      return variantAmount(merged.mrp) > 0 ? syncVariantRowSellingFromMrp(merged, discountPercent) : merged;
    });
    commit(next);
  };

  /** Local-only updates while typing money amounts — commit on blur. */
  const patchAmountLocal = (index: number, patch: Partial<VariantEditorRow>) => {
    setRows((prev) =>
      prev.map((r, i) => {
        if (i !== index) return r;
        const merged = { ...r, ...patch };
        const mrpDraft = typeof merged.mrp === "string" && merged.mrp.includes(".");
        if (mrpDraft) return merged;
        return variantAmount(merged.mrp) > 0 ? syncVariantRowSellingFromMrp(merged, discountPercent) : merged;
      }),
    );
  };

  const commitAmountBlur = (index: number, patch: Partial<VariantEditorRow>) => {
    const next = rowsRef.current.map((r, i) => (i === index ? { ...r, ...patch } : r));
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
  const rowKey = (row: VariantEditorRow, index: number) =>
    row.zohoItemId?.trim() || row.sku?.trim() || row.id || `row-${index}`;

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
              <th className="p-2 w-20" />
            </tr>
          </thead>
          <tbody>
            {rows.map((r, index) => {
              const photos = rowPhotos(r);
              const key = rowKey(r, index);
              const pulling = pullingRowKeys?.has(key) ?? false;
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
                    className={inputCls() + " !text-xs" + (freezeColors ? " bg-muted/30 cursor-default" : "")}
                    value={r.color}
                    placeholder="Navy"
                    readOnly={freezeColors}
                    title={freezeColors ? "Updated via Zoho pull only" : undefined}
                    onChange={(e) => {
                      if (freezeColors) return;
                      patchRow(index, { color: e.target.value });
                    }}
                    onBlur={() => {
                      if (freezeColors) return;
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
                  {variantAmount(r.mrp) > 0 ? (
                    <input
                      type="text"
                      inputMode="decimal"
                      className={inputCls() + " !text-xs bg-muted/30"}
                      value={variantAmountDisplay(variantRowSelling(r, discountPercent))}
                      aria-label="Selling price"
                      readOnly
                      title={`Derived from MRP minus ${discountPercent}% store discount`}
                    />
                  ) : (
                    <input
                      type="text"
                      inputMode="decimal"
                      className={inputCls() + " !text-xs"}
                      value={variantAmountDisplay(r.rate)}
                      aria-label="Selling price"
                      onChange={(e) => patchAmountLocal(index, { rate: variantAmountInput(e.target.value) })}
                      onBlur={() =>
                        commitAmountBlur(index, { rate: variantAmountBlur(rowsRef.current[index]?.rate ?? "") })
                      }
                    />
                  )}
                </td>
                <td className="p-1.5">
                  <input
                    type="text"
                    inputMode="decimal"
                    className={inputCls() + " !text-xs"}
                    value={variantAmountDisplay(r.mrp)}
                    aria-label="MRP"
                    onChange={(e) => patchAmountLocal(index, { mrp: variantAmountInput(e.target.value) })}
                    onBlur={() =>
                      commitAmountBlur(index, { mrp: variantAmountBlur(rowsRef.current[index]?.mrp ?? "") })
                    }
                  />
                </td>
                <td className="p-1.5">
                  <input
                    type="text"
                    inputMode="decimal"
                    className={inputCls() + " !text-xs"}
                    value={variantAmountDisplay(r.cost)}
                    onChange={(e) => patchAmountLocal(index, { cost: variantAmountInput(e.target.value) })}
                    onBlur={() =>
                      commitAmountBlur(index, { cost: variantAmountBlur(rowsRef.current[index]?.cost ?? "") })
                    }
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
                      onChange={(e) => patchAmountLocal(index, { stock: variantAmountInput(e.target.value) })}
                      onBlur={() => {
                        const stock = variantAmountBlur(rowsRef.current[index]?.stock ?? "");
                        commitAmountBlur(index, { stock: stock === "" ? 0 : Math.max(0, Math.floor(stock)) });
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
                        <div
                          key={url}
                          draggable
                          onDragStart={(event) => {
                            event.dataTransfer.effectAllowed = "copy";
                            setDisplayImageDragData(event.dataTransfer, url);
                          }}
                          title="Drag to Display image"
                          aria-label={`Drag variant photo as display image`}
                          className="relative h-10 w-10 shrink-0 cursor-grab active:cursor-grabbing"
                        >
                          <AdminProductImage
                            src={normalizeProductImageUrl(url) ?? url}
                            alt=""
                            draggable={false}
                            className="h-10 w-10 rounded-md object-cover border border-border/60 pointer-events-none"
                          />
                          <span className="pointer-events-none absolute inset-0 grid place-items-start justify-start p-0.5">
                            <GripVertical className="h-3 w-3 text-background drop-shadow" aria-hidden />
                          </span>
                        </div>
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
                  <div className="flex items-center gap-1">
                    {onPullFromZoho && (
                      <button
                        type="button"
                        className="h-8 w-8 grid place-items-center rounded-lg hover:bg-muted text-muted-foreground disabled:opacity-50"
                        onClick={() => onPullFromZoho(r, index)}
                        disabled={pulling}
                        aria-label={`Pull ${r.sku || "variant"} from Zoho`}
                        title="Pull this variant row from Zoho"
                      >
                        {pulling ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CloudDownload className="h-3.5 w-3.5" />}
                      </button>
                    )}
                    <button
                      type="button"
                      className="h-8 w-8 grid place-items-center rounded-lg hover:bg-destructive/10 text-destructive"
                      onClick={() => removeRow(index)}
                      aria-label="Remove variant"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
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
        Pull from Zoho always clears these thumbnails. Drag a variant photo onto Display image
        (just above this table) to use it on shop cards. Use the colour gallery above for real
        product-page photos.
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
