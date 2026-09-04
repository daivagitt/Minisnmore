import type { ProductSizeChart, SizeChartSection, SizeChartRow } from "@/lib/product-size-charts";
import { isUsableSizeChart, parseProductSizeChart } from "@/lib/product-size-charts";

const META_SHEET = "Meta";
const CHART_SHEET = "Chart";
const SECTION_COL = "Section";
const MEASUREMENT_COL = "Measurement";

export function emptySizeChart(overrides?: Partial<ProductSizeChart>): ProductSizeChart {
  const bands = ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"];
  const blankRow = (label: string): SizeChartRow => ({
    label,
    values: Object.fromEntries(bands.map((b) => [b, "" as number | string])),
  });
  return {
    code: overrides?.code ?? "",
    title: overrides?.title ?? "",
    tolerance: overrides?.tolerance ?? "±1 cm",
    sections:
      overrides?.sections ??
      [
        {
          title: "Measurements",
          bands: [...bands],
          rows: [blankRow("Chest width"), blankRow("Body length")],
        },
      ],
  };
}

export function cloneSizeChart(chart: ProductSizeChart): ProductSizeChart {
  return JSON.parse(JSON.stringify(chart)) as ProductSizeChart;
}

export function validateSizeChartStructure(chart: ProductSizeChart | null | undefined): {
  ok: boolean;
  errors: string[];
  warnings: string[];
} {
  const errors: string[] = [];
  const warnings: string[] = [];
  if (!isUsableSizeChart(chart)) {
    errors.push("Add at least one numeric measurement (empty starter charts are not saved)");
    return { ok: false, errors, warnings };
  }
  for (const sec of chart.sections) {
    if (!sec.title.trim()) errors.push("Every section needs a title");
    if (!sec.bands.length) errors.push(`Section "${sec.title || "?"}" needs size bands`);
    if (!sec.rows.length) errors.push(`Section "${sec.title || "?"}" needs measurement rows`);
    for (const row of sec.rows) {
      if (!row.label.trim()) errors.push(`Section "${sec.title}" has a row without a label`);
    }
  }
  const labels = chart.sections.flatMap((s) => s.rows.map((r) => r.label));
  const hasChest = labels.some((l) => /^(chest(\s*width|\s*\(½\)|\s*\(1\/2\))?)$/i.test(l.trim()));
  const hasLength = labels.some((l) =>
    /^(body\s*length.*|length\s*\(hps.*|total\s*length|length)$/i.test(l.trim()),
  );
  if (!hasChest) warnings.push("No Chest / Chest width row — size calculator may lack chest fit");
  if (!hasLength) {
    warnings.push("No Body length / Total length row — size calculator may lack length fit");
  }
  return { ok: errors.length === 0, errors, warnings };
}

function cellToValue(raw: unknown): number | string {
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string") {
    const t = raw.trim();
    if (!t) return "";
    const n = Number(t.replace(/[^\d.-]/g, ""));
    if (t.replace(/[^\d.-]/g, "") === t && Number.isFinite(n) && t !== "") return n;
    return t;
  }
  if (raw == null || raw === "") return "";
  const n = Number(raw);
  return Number.isFinite(n) ? n : String(raw);
}

function collectBandsFromRows(rows: Record<string, unknown>[]): string[] {
  const reserved = new Set([SECTION_COL.toLowerCase(), MEASUREMENT_COL.toLowerCase()]);
  const bands: string[] = [];
  const seen = new Set<string>();
  for (const row of rows) {
    for (const key of Object.keys(row)) {
      const trimmed = key.trim();
      if (!trimmed || reserved.has(trimmed.toLowerCase())) continue;
      if (seen.has(trimmed)) continue;
      seen.add(trimmed);
      bands.push(trimmed);
    }
  }
  return bands;
}

/** Build ProductSizeChart from Meta + Chart sheet row objects. */
export function sizeChartFromExcelSheets(input: {
  metaRows?: Record<string, unknown>[];
  chartRows: Record<string, unknown>[];
}): ProductSizeChart {
  const meta: Record<string, string> = {};
  for (const row of input.metaRows ?? []) {
    const keys = Object.keys(row);
    const fieldKey =
      keys.find((k) => /^field$/i.test(k.trim())) ??
      keys.find((k) => /^key$/i.test(k.trim())) ??
      keys[0];
    const valueKey =
      keys.find((k) => /^value$/i.test(k.trim())) ??
      keys.find((k) => k !== fieldKey) ??
      keys[1];
    if (!fieldKey) continue;
    const field = String(row[fieldKey] ?? "").trim().toLowerCase();
    const value = String(valueKey != null ? (row[valueKey] ?? "") : "").trim();
    if (field) meta[field] = value;
  }

  const allBands = collectBandsFromRows(input.chartRows);
  const sectionMap = new Map<string, SizeChartSection>();
  const sectionOrder: string[] = [];

  for (const raw of input.chartRows) {
    const sectionName = String(
      raw[SECTION_COL] ?? raw.section ?? raw.Section ?? "Measurements",
    ).trim() || "Measurements";
    const label = String(
      raw[MEASUREMENT_COL] ?? raw.measurement ?? raw.Measurement ?? raw.label ?? "",
    ).trim();
    if (!label) continue;

    let section = sectionMap.get(sectionName);
    if (!section) {
      section = { title: sectionName, bands: [...allBands], rows: [] };
      sectionMap.set(sectionName, section);
      sectionOrder.push(sectionName);
    }

    const values: Record<string, number | string> = {};
    for (const band of allBands) {
      const cell =
        raw[band] ??
        Object.entries(raw).find(([k]) => k.trim() === band)?.[1];
      values[band] = cellToValue(cell);
    }
    section.rows.push({ label, values });
  }

  // Per-section bands: only columns that appear with any value, else keep allBands
  const sections = sectionOrder.map((name) => {
    const sec = sectionMap.get(name)!;
    const usedBands = allBands.filter((b) =>
      sec.rows.some((r) => {
        const v = r.values[b];
        return v !== "" && v != null;
      }),
    );
    const bands = usedBands.length > 0 ? usedBands : [...allBands];
    return {
      title: sec.title,
      bands,
      rows: sec.rows.map((r) => ({
        label: r.label,
        values: Object.fromEntries(bands.map((b) => [b, r.values[b] ?? ""])),
      })),
    };
  });

  const chart: ProductSizeChart = {
    code: meta.code ?? meta.sku ?? "",
    title: meta.title ?? meta.name ?? "",
    tolerance: meta.tolerance || "±1 cm",
    sections,
  };

  const parsed = parseProductSizeChart(chart);
  if (!parsed) {
    throw new Error("Excel chart sheet produced no usable sections (need Section, Measurement, and size columns)");
  }
  return parsed;
}

export async function downloadSizeChartTemplate(filename = "product-size-chart-template.xlsx") {
  const XLSX = await import("xlsx");
  const sample = emptySizeChart({
    code: "MnM-4",
    title: "Example vest",
    tolerance: "±1 cm",
  });
  const metaSheet = XLSX.utils.aoa_to_sheet([
    ["Field", "Value"],
    ["title", sample.title],
    ["code", sample.code],
    ["tolerance", sample.tolerance],
  ]);
  const bands = sample.sections[0]!.bands;
  const chartAoA: (string | number)[][] = [[SECTION_COL, MEASUREMENT_COL, ...bands]];
  for (const sec of sample.sections) {
    for (const row of sec.rows) {
      chartAoA.push([
        sec.title,
        row.label,
        ...sec.bands.map((b) => {
          const v = row.values[b];
          return typeof v === "number" ? v : String(v ?? "");
        }),
      ]);
    }
  }
  const chartSheet = XLSX.utils.aoa_to_sheet(chartAoA);
  const guide = XLSX.utils.aoa_to_sheet([
    ["Notes"],
    ["Meta sheet: title, code, tolerance"],
    ["Chart sheet: Section | Measurement | one column per size band (e.g. 0-3M)"],
    ["Use Chest width and Body length (or Total length) so the storefront calculator works"],
    ["Import replaces the product size chart on save"],
  ]);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, metaSheet, META_SHEET);
  XLSX.utils.book_append_sheet(wb, chartSheet, CHART_SHEET);
  XLSX.utils.book_append_sheet(wb, guide, "Guide");
  XLSX.writeFile(wb, filename);
}

export async function downloadSizeChartExcel(
  chart: ProductSizeChart,
  filename = "product-size-chart.xlsx",
) {
  const XLSX = await import("xlsx");
  const metaSheet = XLSX.utils.aoa_to_sheet([
    ["Field", "Value"],
    ["title", chart.title],
    ["code", chart.code],
    ["tolerance", chart.tolerance],
  ]);
  const allBands: string[] = [];
  const seen = new Set<string>();
  for (const sec of chart.sections) {
    for (const b of sec.bands) {
      if (seen.has(b)) continue;
      seen.add(b);
      allBands.push(b);
    }
  }
  const chartAoA: (string | number)[][] = [[SECTION_COL, MEASUREMENT_COL, ...allBands]];
  for (const sec of chart.sections) {
    for (const row of sec.rows) {
      chartAoA.push([
        sec.title,
        row.label,
        ...allBands.map((b) => {
          const v = row.values[b];
          if (typeof v === "number") return v;
          return v == null ? "" : String(v);
        }),
      ]);
    }
  }
  const chartSheet = XLSX.utils.aoa_to_sheet(chartAoA);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, metaSheet, META_SHEET);
  XLSX.utils.book_append_sheet(wb, chartSheet, CHART_SHEET);
  XLSX.writeFile(wb, filename);
}

export async function parseSizeChartExcelFile(file: File): Promise<ProductSizeChart> {
  const XLSX = await import("xlsx");
  const buf = await file.arrayBuffer();
  const wb = XLSX.read(buf, { type: "array" });
  const metaName =
    wb.SheetNames.find((n) => n.trim().toLowerCase() === "meta") ?? wb.SheetNames[0];
  const chartName =
    wb.SheetNames.find((n) => n.trim().toLowerCase() === "chart") ??
    wb.SheetNames.find((n) => n !== metaName) ??
    wb.SheetNames[0];
  if (!chartName) throw new Error("Workbook has no sheets");

  const metaRows: Record<string, unknown>[] =
    metaName && wb.Sheets[metaName]
      ? (XLSX.utils.sheet_to_json(wb.Sheets[metaName], { defval: "" }) as Record<
          string,
          unknown
        >[])
      : [];
  const chartRows = XLSX.utils.sheet_to_json(wb.Sheets[chartName]!, {
    defval: "",
  }) as Record<string, unknown>[];
  if (!chartRows.length) throw new Error("Chart sheet has no data rows");

  return sizeChartFromExcelSheets({ metaRows, chartRows });
}

export function chartHasCalculatorMetrics(chart: ProductSizeChart): boolean {
  const { warnings } = validateSizeChartStructure(chart);
  return warnings.length === 0;
}
