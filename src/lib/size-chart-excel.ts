import type { ProductSizeChart, SizeChartSection, SizeChartRow } from "@/lib/product-size-charts";
import { isUsableSizeChart, isValidMeasurementCell, parseProductSizeChart, sizeChartMeasurementErrors } from "@/lib/product-size-charts";

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

const EXAMPLE_BANDS = ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"] as const;

function exampleRow(
  label: string,
  values: [number | string, number, number, number, number],
): SizeChartRow {
  return {
    label,
    values: {
      "0-3M": values[0],
      "3-6M": values[1],
      "6-12M": values[2],
      "12-18M": values[3],
      "18-24M": values[4],
    },
  };
}

/**
 * Filled vest + shorts workbook used by “Example Excel”.
 * 0-3M uses from–to ranges; later sizes are the upper measurement in cm.
 */
export function exampleSizeChartForImport(): ProductSizeChart {
  return {
    code: "MnM-EXAMPLE",
    title: "Front-open vest & shorts set (example)",
    tolerance: "±1 cm",
    sections: [
      {
        title: "Vest",
        bands: [...EXAMPLE_BANDS],
        rows: [
          exampleRow("Chest width", ["22-23", 24, 25, 26, 27]),
          exampleRow("Body length", ["26-28", 30, 32, 34, 36]),
          exampleRow("Front neck drop", [5.5, 6, 6.5, 7, 7.5]),
          exampleRow("Back neck width", [8, 8.5, 9, 9.5, 10]),
          exampleRow("Shoulder width", [18, 19, 20, 21, 22]),
          exampleRow("Armhole straight", [11, 12, 13, 14, 15]),
          exampleRow("Bottom width", [23, 24, 25, 26, 27]),
        ],
      },
      {
        title: "Shorts",
        bands: [...EXAMPLE_BANDS],
        rows: [
          exampleRow("Waistband width", [16, 17, 18, 19, 20]),
          exampleRow("Hip / body width", [22, 23, 25, 27, 29]),
          exampleRow("Total length", ["16-17", 18, 20, 22, 24]),
          exampleRow("Front rise", [16, 17, 18, 19, 20]),
          exampleRow("Inseam", [5, 6, 7, 8, 9]),
        ],
      },
    ],
  };
}

export function sizeChartMetaAoA(chart: ProductSizeChart): (string | number)[][] {
  return [
    ["Field", "Value"],
    ["title", chart.title],
    ["code", chart.code],
    ["tolerance", chart.tolerance],
  ];
}

export function sizeChartDataAoA(chart: ProductSizeChart): (string | number)[][] {
  const allBands: string[] = [];
  const seen = new Set<string>();
  for (const sec of chart.sections) {
    for (const b of sec.bands) {
      if (seen.has(b)) continue;
      seen.add(b);
      allBands.push(b);
    }
  }
  const rows: (string | number)[][] = [[SECTION_COL, MEASUREMENT_COL, ...allBands]];
  for (const sec of chart.sections) {
    for (const row of sec.rows) {
      rows.push([
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
  return rows;
}

export function sizeChartGuideAoA(): (string | number)[][] {
  return [
    ["Topic", "How to fill this file"],
    ["Import", "Admin → product → Size chart → Example Excel → edit → Import Excel → Save"],
    ["Sheets", "Keep sheet names Meta and Chart. This Guide sheet is ignored on import."],
    ["Meta", "One row per field: title, code, tolerance"],
    [
      "Chart header",
      "Section | Measurement | 0-3M | 3-6M | 6-12M | 12-18M | 18-24M (add or remove size columns as needed)",
    ],
    ["0-3M", "Use a from–to range in cm, e.g. 26-28. Leave only the upper value if you want auto range."],
    ["Later sizes", "Single number = the upper measurement in cm. Next size starts at previous + 1 cm."],
    [
      "Calculator",
      "Vest/garment section must include Chest width and Body length (or Total length / Length HPS)",
    ],
    ["Sets", "Repeat Section name per garment. This example has Vest then Shorts."],
    ["Units", "Centimetres. Tolerance on Meta applies to the storefront copy."],
    ["Replace", "Import replaces the product size chart. Save the product to keep it."],
    ["", ""],
    ["Example Meta", "See the Meta sheet"],
    ["Example Chart", "See the Chart sheet — vest + shorts, all five sizes filled"],
    ["0-3M vest chest", "22-23"],
    ["0-3M vest length", "26-28"],
    ["3-6M vest chest / length", "24 / 30"],
  ];
}

export function validateSizeChartStructure(chart: ProductSizeChart | null | undefined): {
  ok: boolean;
  errors: string[];
  warnings: string[];
} {
  const errors: string[] = [];
  const warnings: string[] = [];
  if (chart) errors.push(...sizeChartMeasurementErrors(chart));
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
  if (!isValidMeasurementCell(raw)) throw new Error("Invalid measurement: use a non-negative number, range, or blank");
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string") {
    const t = raw.trim();
    if (!t) return "";
    const n = Number(t.replace(/[^\d.-]/g, ""));
    if (t.replace(/[^\d.-]/g, "") === t && Number.isFinite(n) && t !== "") return n;
    return t;
  }
  if (raw == null || raw === "") return "";
  return "";
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

async function workbookFromSizeChart(
  chart: ProductSizeChart,
  includeGuide: boolean,
) {
  const XLSX = await import("xlsx");
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, aoaToSheetKeepingRanges(XLSX, sizeChartMetaAoA(chart)), META_SHEET);
  XLSX.utils.book_append_sheet(wb, aoaToSheetKeepingRanges(XLSX, sizeChartDataAoA(chart)), CHART_SHEET);
  if (includeGuide) {
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(sizeChartGuideAoA()), "Guide");
  }
  return { XLSX, wb };
}

/** Keep "26-28" as text so Excel does not turn 0-3M ranges into dates. */
function aoaToSheetKeepingRanges(
  XLSX: typeof import("xlsx"),
  aoa: (string | number)[][],
) {
  const sheet = XLSX.utils.aoa_to_sheet(aoa);
  for (let r = 0; r < aoa.length; r++) {
    const row = aoa[r] ?? [];
    for (let c = 0; c < row.length; c++) {
      const val = row[c];
      if (typeof val !== "string" || !/^-?\d+(?:\.\d+)?\s*-\s*-?\d+(?:\.\d+)?$/.test(val.trim())) {
        continue;
      }
      const addr = XLSX.utils.encode_cell({ r, c });
      sheet[addr] = { t: "s", v: val, z: "@" };
    }
  }
  return sheet;
}

export async function sizeChartToXlsxBuffer(
  chart: ProductSizeChart,
  includeGuide = false,
): Promise<ArrayBuffer> {
  const { XLSX, wb } = await workbookFromSizeChart(chart, includeGuide);
  return XLSX.write(wb, { type: "array", bookType: "xlsx" }) as ArrayBuffer;
}

export async function downloadSizeChartTemplate(filename = "product-size-chart-example.xlsx") {
  const { XLSX, wb } = await workbookFromSizeChart(exampleSizeChartForImport(), true);
  XLSX.writeFile(wb, filename);
}

export async function downloadSizeChartExcel(
  chart: ProductSizeChart,
  filename = "product-size-chart.xlsx",
) {
  const { XLSX, wb } = await workbookFromSizeChart(chart, false);
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
