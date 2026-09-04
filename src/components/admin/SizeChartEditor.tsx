import { useEffect, useMemo, useRef, useState } from "react";
import { FileDown, Loader2, Plus, Trash2, Upload } from "lucide-react";
import { toast } from "sonner";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { ProductSizeChartTables } from "@/components/ProductSizeChart";
import {
  cloneSizeChart,
  downloadSizeChartExcel,
  downloadSizeChartTemplate,
  emptySizeChart,
  parseSizeChartExcelFile,
  validateSizeChartStructure,
} from "@/lib/size-chart-excel";
import {
  sizeChartForProduct,
  type ProductSizeChart,
  type SizeChartSection,
} from "@/lib/product-size-charts";
import { sizeGuideRowsForProduct } from "@/lib/product-fit";

type Props = {
  value: ProductSizeChart | null | undefined;
  onChange: (next: ProductSizeChart | null) => void;
  productSku?: string | null;
  productName?: string | null;
  productSlug?: string | null;
  productSizes?: string[];
};

function ensureChart(value: ProductSizeChart | null | undefined): ProductSizeChart {
  return value ? cloneSizeChart(value) : emptySizeChart();
}

function blankValues(bands: string[]): Record<string, number | string> {
  return Object.fromEntries(bands.map((b) => [b, ""]));
}

export function SizeChartEditor({
  value,
  onChange,
  productSku,
  productName,
  productSlug,
  productSizes = [],
}: Props) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const chart = value ?? null;
  const validation = useMemo(() => validateSizeChartStructure(chart), [chart]);

  const previewRows = useMemo(() => {
    if (!chart) return [];
    const sizes =
      productSizes.length > 0
        ? productSizes
        : chart.sections[0]?.bands.map((b) => b.replace(/M$/i, " M").replace(/(\d)-(\d)/, "$1-$2")) ??
          [];
    return sizeGuideRowsForProduct({
      sku: productSku,
      name: productName,
      slug: productSlug,
      sizes: sizes.length ? sizes : chart.sections.flatMap((s) => s.bands),
      sizeChart: chart,
    });
  }, [chart, productSku, productName, productSlug, productSizes]);

  const setChart = (next: ProductSizeChart | null) => onChange(next);

  const patchMeta = (patch: Partial<ProductSizeChart>) => {
    const base = ensureChart(chart);
    setChart({ ...base, ...patch, sections: base.sections });
  };

  const updateSection = (index: number, next: SizeChartSection) => {
    const base = ensureChart(chart);
    const sections = base.sections.map((s, i) => (i === index ? next : s));
    setChart({ ...base, sections });
  };

  const addSection = () => {
    const base = ensureChart(chart);
    const bands = base.sections[0]?.bands?.length
      ? [...base.sections[0].bands]
      : ["0-3M", "3-6M", "6-12M"];
    setChart({
      ...base,
      sections: [
        ...base.sections,
        {
          title: `Section ${base.sections.length + 1}`,
          bands,
          rows: [{ label: "Chest width", values: blankValues(bands) }],
        },
      ],
    });
  };

  const removeSection = (index: number) => {
    const base = ensureChart(chart);
    if (base.sections.length <= 1) {
      toast.error("Keep at least one section, or clear the chart");
      return;
    }
    setChart({ ...base, sections: base.sections.filter((_, i) => i !== index) });
  };

  const loadFromTemplate = () => {
    const template = sizeChartForProduct({
      sku: productSku,
      name: productName,
      slug: productSlug,
    });
    if (!template) {
      toast.error("No catalog size-chart template for this SKU/name");
      return;
    }
    setChart(cloneSizeChart(template));
    toast.success(`Loaded template ${template.code || template.title}`);
  };

  const importExcel = async (file: File) => {
    setBusy(true);
    try {
      const parsed = await parseSizeChartExcelFile(file);
      const check = validateSizeChartStructure(parsed);
      if (!check.ok) {
        toast.error(check.errors[0] ?? "Invalid size chart");
        return;
      }
      setChart(parsed);
      if (check.warnings.length) {
        toast.warning("Imported with warnings", { description: check.warnings.join("\n") });
      } else {
        toast.success("Size chart imported from Excel");
      }
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Could not parse Excel file");
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  return (
    <div className="space-y-4 rounded-xl border border-border/50 p-3 sm:p-4">
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
        <div>
          <div className="text-sm font-medium">Size chart</div>
          <p className="text-xs text-muted-foreground mt-0.5 leading-snug max-w-xl">
            Drives the storefront size guide and calculator. Leave empty to use the catalog SKU
            fallback. Excel uses Meta + Chart sheets.
          </p>
        </div>
        <div className="flex flex-wrap gap-2 shrink-0">
          <button
            type="button"
            className="text-xs rounded-full glass px-3 py-1.5 hover:bg-white/50"
            onClick={() => void downloadSizeChartTemplate()}
          >
            <FileDown className="h-3.5 w-3.5 inline mr-1" />
            Template
          </button>
          {chart ? (
            <button
              type="button"
              className="text-xs rounded-full glass px-3 py-1.5 hover:bg-white/50"
              onClick={() => void downloadSizeChartExcel(chart, `${productSlug || "product"}-size-chart.xlsx`)}
            >
              <FileDown className="h-3.5 w-3.5 inline mr-1" />
              Export
            </button>
          ) : null}
          <button
            type="button"
            className="text-xs rounded-full glass px-3 py-1.5 hover:bg-white/50"
            onClick={() => fileRef.current?.click()}
            disabled={busy}
          >
            {busy ? (
              <Loader2 className="h-3.5 w-3.5 inline mr-1 animate-spin" />
            ) : (
              <Upload className="h-3.5 w-3.5 inline mr-1" />
            )}
            Import Excel
          </button>
          <input
            ref={fileRef}
            type="file"
            accept=".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) void importExcel(f);
            }}
          />
          <button
            type="button"
            className="text-xs rounded-full glass px-3 py-1.5 hover:bg-white/50"
            onClick={loadFromTemplate}
          >
            Load from catalog
          </button>
          {chart ? (
            <button
              type="button"
              className="text-xs rounded-full glass px-3 py-1.5 text-destructive hover:bg-destructive/10"
              onClick={() => setChart(null)}
            >
              Clear
            </button>
          ) : (
            <button
              type="button"
              className="text-xs rounded-full bg-primary text-primary-foreground px-3 py-1.5"
              onClick={() => setChart(emptySizeChart({ title: productName ?? "", code: "" }))}
            >
              Start editing
            </button>
          )}
        </div>
      </div>

      {!chart ? (
        <p className="text-xs text-muted-foreground">
          No product chart saved — storefront will use the hardcoded SKU map when available.
        </p>
      ) : (
        <>
          <div className="grid sm:grid-cols-3 gap-3">
            <Field label="Title">
              <input
                className={inputCls()}
                value={chart.title}
                onChange={(e) => patchMeta({ title: e.target.value })}
                placeholder="Front-open vest"
              />
            </Field>
            <Field label="Code">
              <input
                className={inputCls()}
                value={chart.code}
                onChange={(e) => patchMeta({ code: e.target.value })}
                placeholder="MnM-4"
              />
            </Field>
            <Field label="Tolerance">
              <input
                className={inputCls()}
                value={chart.tolerance}
                onChange={(e) => patchMeta({ tolerance: e.target.value })}
                placeholder="±1 cm"
              />
            </Field>
          </div>

          {validation.warnings.map((w) => (
            <p key={w} className="text-xs text-amber-700 dark:text-amber-400">
              {w}
            </p>
          ))}
          {validation.errors.map((w) => (
            <p key={w} className="text-xs text-destructive">
              {w}
            </p>
          ))}

          {chart.sections.map((section, si) => (
            <SectionEditor
              key={si}
              section={section}
              onChange={(next) => updateSection(si, next)}
              onRemove={() => removeSection(si)}
              canRemove={chart.sections.length > 1}
            />
          ))}

          <button
            type="button"
            onClick={addSection}
            className="text-xs rounded-full glass px-3 py-1.5 inline-flex items-center gap-1 hover:bg-white/50"
          >
            <Plus className="h-3.5 w-3.5" />
            Add section
          </button>

          {previewRows.length > 0 ? (
            <div className="rounded-lg glass p-3">
              <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-2">
                Storefront preview (length / chest)
              </div>
              <ProductSizeChartTables rows={previewRows} title={chart.title || null} />
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}

function SectionEditor({
  section,
  onChange,
  onRemove,
  canRemove,
}: {
  section: SizeChartSection;
  onChange: (next: SizeChartSection) => void;
  onRemove: () => void;
  canRemove: boolean;
}) {
  const bandsJoined = section.bands.join(", ");
  const [bandsDraft, setBandsDraft] = useState(bandsJoined);
  const [bandsFocused, setBandsFocused] = useState(false);

  useEffect(() => {
    if (!bandsFocused) setBandsDraft(bandsJoined);
  }, [bandsJoined, bandsFocused]);

  const commitBands = (text: string) => {
    const bands = text
      .split(",")
      .map((b) => b.trim())
      .filter(Boolean);
    if (!bands.length) return;
    const unchanged =
      bands.length === section.bands.length && bands.every((b, i) => b === section.bands[i]);
    if (unchanged) return;
    onChange({
      ...section,
      bands,
      rows: section.rows.map((r) => ({
        label: r.label,
        values: Object.fromEntries(bands.map((b) => [b, r.values[b] ?? ""])),
      })),
    });
  };

  const setCell = (rowIndex: number, band: string, raw: string) => {
    const trimmed = raw.trim();
    let value: number | string = trimmed;
    if (trimmed !== "" && /^-?\d+(\.\d+)?$/.test(trimmed)) value = Number(trimmed);
    const rows = section.rows.map((r, i) =>
      i === rowIndex ? { ...r, values: { ...r.values, [band]: value } } : r,
    );
    onChange({ ...section, rows });
  };

  const setLabel = (rowIndex: number, label: string) => {
    const rows = section.rows.map((r, i) => (i === rowIndex ? { ...r, label } : r));
    onChange({ ...section, rows });
  };

  const addRow = () => {
    onChange({
      ...section,
      rows: [...section.rows, { label: "", values: blankValues(section.bands) }],
    });
  };

  const removeRow = (rowIndex: number) => {
    if (section.rows.length <= 1) {
      toast.error("Keep at least one measurement row");
      return;
    }
    onChange({ ...section, rows: section.rows.filter((_, i) => i !== rowIndex) });
  };

  return (
    <div className="space-y-2 rounded-lg border border-border/40 p-3">
      <div className="flex flex-wrap items-end gap-2">
        <div className="flex-1 min-w-[10rem]">
          <Field label="Section title">
            <input
              className={inputCls()}
              value={section.title}
              onChange={(e) => onChange({ ...section, title: e.target.value })}
            />
          </Field>
        </div>
        <div className="flex-[2] min-w-[14rem]">
          <Field label="Size bands (comma-separated)">
            <input
              className={inputCls()}
              value={bandsDraft}
              onFocus={() => setBandsFocused(true)}
              onChange={(e) => {
                const text = e.target.value;
                setBandsDraft(text);
                commitBands(text);
              }}
              onBlur={() => {
                commitBands(bandsDraft);
                setBandsFocused(false);
              }}
              placeholder="0-3M, 3-6M, 6-12M"
            />
          </Field>
        </div>
        {canRemove ? (
          <button
            type="button"
            className="h-10 w-10 grid place-items-center rounded-full glass text-destructive"
            onClick={onRemove}
            aria-label="Remove section"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        ) : null}
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-xs min-w-[28rem]">
          <thead>
            <tr className="text-[10px] uppercase tracking-wider text-muted-foreground">
              <th className="text-left py-1.5 pr-2">Measurement</th>
              {section.bands.map((b) => (
                <th key={b} className="px-1 py-1.5 text-center whitespace-nowrap">
                  {b}
                </th>
              ))}
              <th className="w-8" />
            </tr>
          </thead>
          <tbody>
            {section.rows.map((row, ri) => (
              <tr key={ri} className="border-t border-border/30">
                <td className="py-1 pr-2">
                  <input
                    className={inputCls() + " text-xs h-8"}
                    value={row.label}
                    onChange={(e) => setLabel(ri, e.target.value)}
                    placeholder="Chest width"
                  />
                </td>
                {section.bands.map((b) => (
                  <td key={b} className="px-0.5 py-1">
                    <input
                      className={inputCls() + " text-xs h-8 text-center tabular-nums px-1"}
                      value={row.values[b] ?? ""}
                      onChange={(e) => setCell(ri, b, e.target.value)}
                      inputMode="decimal"
                    />
                  </td>
                ))}
                <td className="pl-1">
                  <button
                    type="button"
                    className="h-8 w-8 grid place-items-center rounded-full hover:bg-destructive/10 text-destructive"
                    onClick={() => removeRow(ri)}
                    aria-label="Remove row"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button
        type="button"
        onClick={addRow}
        className="text-xs rounded-full glass px-2.5 py-1 inline-flex items-center gap-1"
      >
        <Plus className="h-3 w-3" />
        Add row
      </button>
    </div>
  );
}
