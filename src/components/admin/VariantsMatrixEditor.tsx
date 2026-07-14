import { Plus, Trash2 } from "lucide-react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import {
  aggregateFromVariantRows,
  rowsFromVariantsJson,
  serializeVariantRows,
  suggestVariantSku,
  type VariantEditorRow,
} from "@/lib/admin-variants";

export { validateVariantRows } from "@/lib/admin-variants";

export function VariantsMatrixEditor({
  parentSku,
  value,
  onChange,
}: {
  parentSku: string;
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
    const agg = aggregateFromVariantRows(nextRows);
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

  return (
    <Field label="Variants (size × color matrix)">
      <p className="text-[11px] text-muted-foreground mb-2">
        Each row is a sellable SKU with its own price (Ledgerly rate), cost, and stock. Missing combos are not sold.
        Parent price/stock update from these rows automatically. Sync to Ledgerly after saving.
      </p>
      <div className="overflow-x-auto rounded-xl border border-border/60">
        <table className="w-full text-xs min-w-[720px]">
          <thead>
            <tr className="bg-muted/40 text-left">
              <th className="p-2 font-medium">Size</th>
              <th className="p-2 font-medium">Color</th>
              <th className="p-2 font-medium">Variant SKU</th>
              <th className="p-2 font-medium">Rate (₹)</th>
              <th className="p-2 font-medium">Cost (₹)</th>
              <th className="p-2 font-medium">Stock</th>
              <th className="p-2 font-medium">Active</th>
              <th className="p-2 w-10" />
            </tr>
          </thead>
          <tbody>
            {rows.map((r, index) => (
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
                    type="number"
                    min={0}
                    step="0.01"
                    className={inputCls() + " !text-xs"}
                    value={r.rate}
                    onChange={(e) =>
                      patchRow(index, { rate: e.target.value === "" ? "" : Number(e.target.value) })
                    }
                  />
                </td>
                <td className="p-1.5">
                  <input
                    type="number"
                    min={0}
                    step="0.01"
                    className={inputCls() + " !text-xs"}
                    value={r.cost}
                    onChange={(e) =>
                      patchRow(index, { cost: e.target.value === "" ? "" : Number(e.target.value) })
                    }
                  />
                </td>
                <td className="p-1.5">
                  <input
                    type="number"
                    min={0}
                    step={1}
                    className={inputCls() + " !text-xs"}
                    value={r.stock}
                    onChange={(e) =>
                      patchRow(index, { stock: e.target.value === "" ? "" : Number(e.target.value) })
                    }
                  />
                </td>
                <td className="p-1.5 text-center">
                  <input
                    type="checkbox"
                    checked={r.active}
                    onChange={(e) => patchRow(index, { active: e.target.checked })}
                    aria-label="Active"
                  />
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
            ))}
          </tbody>
        </table>
      </div>
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
      </div>
    </Field>
  );
}
