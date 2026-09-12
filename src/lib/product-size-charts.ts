/**
 * Approved garment measurement charts (cm unless noted) from
 * "Mnm_Final measurement sheet_Approved". Matched to catalog by parent SKU,
 * then by product name / slug.
 */

export type SizeChartRow = {
  label: string;
  /** Values keyed by size-band column header (e.g. "0-3M", "Common size"). */
  values: Record<string, number | string>;
};

export type SizeChartSection = {
  title: string;
  bands: string[];
  rows: SizeChartRow[];
};

export type ProductSizeChart = {
  code: string;
  title: string;
  sections: SizeChartSection[];
  tolerance: string;
};

const AGE5 = ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"] as const;
const AGE3 = ["0-3M", "3-6M", "6-12M"] as const;
const COMMON = ["Common size"] as const;

function row(label: string, bands: readonly string[], vals: Array<number | string>): SizeChartRow {
  const values: Record<string, number | string> = {};
  bands.forEach((b, i) => {
    values[b] = vals[i]!;
  });
  return { label, values };
}

function section(title: string, bands: readonly string[], rows: SizeChartRow[]): SizeChartSection {
  return { title, bands: [...bands], rows };
}

const SHORTS: SizeChartSection = section("Shorts", AGE5, [
  row("Waistband width", AGE5, [16, 17, 18, 19, 20]),
  row("Waistband height", AGE5, [1.5, 1.5, 1.5, 1.5, 1.5]),
  row("Hip / body width", AGE5, [22, 23, 25, 27, 29]),
  row("Hip placement", AGE5, [7, 8, 9, 10, 11]),
  row("Total length", AGE5, [17, 18, 20, 22, 24]),
  row("Front rise", AGE5, [16, 17, 18, 19, 20]),
  row("Back rise", AGE5, [17, 18, 19, 20, 21]),
  row("Leg opening", AGE5, [11, 11.5, 12.5, 13.5, 14.5]),
  row("Inseam", AGE5, [5, 6, 7, 8, 9]),
]);

const BLOOMERS: SizeChartSection = section("Bloomers", AGE5, [
  row("Waistband width", AGE5, [16, 17, 18, 19, 20]),
  row("Waistband height", AGE5, [1.5, 1.5, 1.5, 1.5, 1.5]),
  row("Hip / body width", AGE5, [20, 21, 22, 23, 24]),
  row("Hip placement", AGE5, [7, 8, 9, 10, 11]),
  row("Total length", AGE5, [18, 19, 21, 23, 25]),
  row("Leg opening", AGE5, [9, 10, 11, 12, 13]),
  row("Crotch point width", AGE5, [10, 11, 12, 13, 14]),
]);

const FLEECE_VEST: SizeChartSection = section("Vest", AGE5, [
  row("Chest width", AGE5, [23, 24, 25, 26, 27]),
  row("Body length", AGE5, [28, 30, 32, 34, 36]),
  row("Front neck drop", AGE5, [5.5, 6, 6.5, 7, 7.5]),
  row("Back neck width", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Back neck drop", AGE5, [1.5, 1.5, 1.5, 1.5, 1.5]),
  row("Shoulder width", AGE5, [18, 19, 20, 21, 22]),
  row("Armhole straight", AGE5, [11, 12, 13, 14, 15]),
  row("Sleeve length", AGE5, [21, 23, 25, 27, 29]),
  row("Sleeve open without cuff", AGE5, [7.5, 7.5, 8, 8.5, 9]),
  row("Sleeve open with cuff", AGE5, [6.5, 7, 7.5, 8, 8.5]),
  row("Bottom width", AGE5, [23, 24, 25, 26, 27]),
]);

const SLEEVELESS_VEST: SizeChartSection = section("Vest", AGE5, [
  row("Chest width", AGE5, [23, 24, 25, 26, 27]),
  row("Body length", AGE5, [28, 30, 32, 34, 36]),
  row("Front neck drop", AGE5, [5.5, 6, 6.5, 7, 7.5]),
  row("Back neck width", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Back neck drop", AGE5, [1.5, 1.5, 1.5, 1.5, 1.5]),
  row("Shoulder width", AGE5, [18, 19, 20, 21, 22]),
  row("Armhole straight", AGE5, [11, 12, 13, 14, 15]),
  row("Bottom width", AGE5, [23, 24, 25, 26, 27]),
]);

const HALF_SLEEVE_VEST: SizeChartSection = section("Vest", AGE5, [
  row("Chest width", AGE5, [23, 24, 25, 26, 27]),
  row("Body length", AGE5, [28, 30, 32, 34, 36]),
  row("Front neck drop", AGE5, [5.5, 6, 6.5, 7, 7.5]),
  row("Back neck width", AGE5, [7, 8, 9, 10, 11]),
  row("Back neck drop", AGE5, [1.5, 1.5, 1.5, 1.5, 1.5]),
  row("Shoulder width", AGE5, [18, 19, 20, 21, 22]),
  row("Armhole straight", AGE5, [11, 12, 13, 14, 15]),
  row("Half sleeve length", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Sleeve open", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Bottom width", AGE5, [23, 24, 25, 26, 27]),
]);

/** Half-sleeve vest when sold with shorts uses MnM-7 back-neck widths. */
const HALF_SLEEVE_VEST_IN_SET: SizeChartSection = section("Vest", AGE5, [
  row("Chest width", AGE5, [23, 24, 25, 26, 27]),
  row("Body length", AGE5, [28, 30, 32, 34, 36]),
  row("Front neck drop", AGE5, [5.5, 6, 6.5, 7, 7.5]),
  row("Back neck width", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Back neck drop", AGE5, [1.5, 1.5, 1.5, 1.5, 1.5]),
  row("Shoulder width", AGE5, [18, 19, 20, 21, 22]),
  row("Armhole straight", AGE5, [11, 12, 13, 14, 15]),
  row("Half sleeve length", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Sleeve open", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Bottom width", AGE5, [23, 24, 25, 26, 27]),
]);

const JABLA: SizeChartSection = section("Jabla", AGE5, [
  row("Chest", AGE5, [22, 24, 26, 28, 30]),
  row("Shoulder", AGE5, [19.5, 21, 22.5, 24, 25.5]),
  row("Armhole", AGE5, [14, 14.5, 15, 15.5, 16]),
  row("Bottom", AGE5, [21.5, 23, 25, 28, 30]),
  row("Total length", AGE5, [27, 30, 33, 36, 39]),
  row("Neck width (seam to seam)", AGE5, [9, 10, 11.5, 12.5, 13.5]),
  row("Front neck drop", AGE5, [5, 5.5, 6, 6.5, 7]),
  row("Back neck drop", AGE5, [2.25, 2.5, 2.75, 3, 3.25]),
]);

const SIDE_BUTTON_TEE: SizeChartSection = section("T-shirt", AGE5, [
  row("Chest", AGE5, [22, 24, 26, 28, 30]),
  row("Bottom width", AGE5, [23, 25, 27, 29, 31]),
  row("Sleeve length", AGE5, [9, 9.5, 10.5, 11.5, 12.5]),
  row("Sleeve open", AGE5, [9, 9.5, 10.5, 11.5, 12.5]),
  row("Shoulder width", AGE5, [19.5, 21, 22.5, 24, 25.5]),
  row("Total length", AGE5, [28, 31, 34, 37, 40]),
  row("Armhole straight", AGE5, [10.5, 11.5, 12.5, 13.5, 14.5]),
  row("Neck width (seam to seam)", AGE5, [12, 13, 13.5, 14, 14.5]),
  row("Back neck drop", AGE5, [1.5, 2, 2.5, 3, 3.5]),
  row("Front neck drop", AGE5, [5.5, 6, 6.5, 7, 7.5]),
]);

const FROCK: SizeChartSection = section("Frock", AGE5, [
  row("Chest", AGE5, [24, 25.5, 26.5, 27, 28]),
  row("Length (HPS–hem)", AGE5, [30, 33, 36, 38, 40]),
  row("Shoulder", AGE5, [19, 20.5, 21.5, 22, 23]),
  row("Sleeve length", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Sleeve opening", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Neck width", AGE5, [10, 10.5, 11, 11.5, 12]),
  row("Front neck drop", AGE5, [5.5, 6, 6.5, 7, 7.5]),
  row("Back neck drop", AGE5, [1, 1.2, 1.4, 1.5, 1.8]),
  row("Armhole straight", AGE5, [10, 11, 11.5, 12, 13]),
  row("Bottom width", AGE5, [36, 38, 40, 41, 43]),
]);

/** Frock + bloomers sheet omits some frock neck/armhole rows; keep core set shown on MnM-12. */
const FROCK_IN_SET: SizeChartSection = section("Frock", AGE5, [
  row("Chest", AGE5, [24, 25.5, 26.5, 27, 28]),
  row("Length (HPS–hem)", AGE5, [30, 33, 36, 38, 40]),
  row("Shoulder", AGE5, [19, 20.5, 21.5, 22, 23]),
  row("Sleeve length", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Sleeve opening", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Bottom width", AGE5, [36, 38, 40, 41, 43]),
]);

const BODY_SUIT: SizeChartSection = section("Body suit", AGE5, [
  row("Chest (½)", AGE5, [21, 22, 24, 25, 26]),
  row("Body length (HPS to crotch)", AGE5, [33, 35.5, 38, 40, 42]),
  row("Sleeve length", AGE5, [6, 7, 8.5, 9.5, 10.5]),
  row("Sleeve open", AGE5, [6, 7, 8, 9, 10]),
  row("Armhole straight", AGE5, [9, 10, 12, 13, 14]),
  row("Shoulder", AGE5, [18, 19, 20.5, 22, 23.5]),
  row("Hip (½)", AGE5, [23, 24, 26, 27, 28]),
  row("Neck width (edge to edge)", AGE5, [8, 8.5, 9, 9.5, 10]),
  row("Front neck drop (HPS to seam)", AGE5, [4, 4.5, 5, 5.5, 6]),
  row("Back neck drop", AGE5, [1.5, 1.5, 2, 2, 2]),
  row("Inseam length", AGE5, [5, 6, 7, 8, 9]),
]);

const NAPPY: SizeChartSection = section("Nappy", AGE3, [
  row("Waist width", AGE3, [18, 20, 22]),
  row("Center front length", AGE3, [18, 20, 22]),
  row("Center back length", AGE3, [20, 22, 24]),
  row("Total length", AGE3, [34, 38, 42]),
  row("Leg open", AGE3, [10, 11, 12]),
]);

const ROUND_CAP: SizeChartSection = section("Round cap", AGE3, [
  row("Head open", AGE3, [15.5, 17, 18.5]),
  row("Cap height", AGE3, [16, 17.5, 19]),
  row("Bottom rib height", AGE3, [3.5, 3.5, 3.5]),
]);

const SOCKS: SizeChartSection = section("Socks", AGE3, [
  row("Foot length (curved)", AGE3, [13, 14.5, 16]),
  row("Foot width", AGE3, [6, 6.5, 7]),
  row("Leg opening (½)", AGE3, [7, 7.5, 8]),
  row("Rib height", AGE3, [4, 4.5, 5]),
  row("Total height", AGE3, [10, 11, 12]),
]);

const MITTENS: SizeChartSection = section("Mittens", AGE3, [
  row("Length", AGE3, [10, 11, 12]),
  row("Width", AGE3, [6.5, 7, 7.5]),
]);

const TYING_CAP: SizeChartSection = section("Tying cap", AGE3, [
  row("Head circumference around cap", AGE3, [26, 28, 30]),
  row("Cap height (center top to bottom)", AGE3, [13, 14, 15]),
  row("Front band height", AGE3, [2.5, 2.5, 2.5]),
  row("Tie length", AGE3, [18, 20, 22]),
  row("Elastic back (relaxed)", AGE3, [8, 9, 10]),
]);

const FACE_NAPKIN: SizeChartSection = section("Face napkin / wash cloth", COMMON, [
  row("Length", COMMON, ['10"']),
  row("Width", COMMON, ['10"']),
]);

const CARRY_NEST: SizeChartSection = section("Baby carry nest", COMMON, [
  row("Full length (top to bottom)", COMMON, [70]),
  row("Width (inside usable)", COMMON, [35]),
  row("Side height", COMMON, [10]),
  row("Inner mattress length", COMMON, [65]),
  row("Inner mattress width", COMMON, [50]),
  row("Head portion length", COMMON, [51]),
  row("Head portion width", COMMON, [30]),
]);

const PILLOW: SizeChartSection = section("Baby pillow", COMMON, [
  row("Length overall", COMMON, [23]),
  row("Width overall", COMMON, [25]),
  row("Thickness", COMMON, [6]),
  row("Inner rise length", COMMON, [11]),
  row("Inner rise width", COMMON, [10]),
  row("Frill height", COMMON, [2.5]),
  row("Outer to inner gap", COMMON, [5]),
]);

const BIB: SizeChartSection = section("Bib", COMMON, [
  row("Total length (neck to bottom)", COMMON, [23]),
  row("Total width", COMMON, [19]),
  row("Neck opening width", COMMON, [9]),
  row("Neck depth", COMMON, [6.5]),
]);

const SWADDLE: SizeChartSection = section("Swaddle", ["Single size"], [
  row("Length", ["Single size"], [100]),
  row("Width", ["Single size"], [100]),
]);

const TOWEL: SizeChartSection = section("Terry towel", COMMON, [
  row("Width", COMMON, [60]),
  row("Length", COMMON, [80]),
]);

function chart(code: string, title: string, sections: SizeChartSection[]): ProductSizeChart {
  return {
    code,
    title,
    sections,
    tolerance: "All measurements ±1 cm",
  };
}

/** Charts keyed by parent SKU (uppercase). */
export const SIZE_CHARTS_BY_SKU: Record<string, ProductSizeChart> = {
  "LOOP-BACK-FLEECE-KNIT-FRONT-OPEN-VEST-FULL-SLEEVE": chart(
    "MnM-1",
    "Fleece knit front open vest (full sleeve)",
    [FLEECE_VEST],
  ),
  "FRONT-OPEN-VEST-SLEVELESS": chart("MnM-2", "Front open vest (sleeveless)", [SLEEVELESS_VEST]),
  "FRONT-OPEN-VEST-SLEEVELESS": chart("MnM-2", "Front open vest (sleeveless)", [SLEEVELESS_VEST]),
  "FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET": chart(
    "MnM-3",
    "Front open vest (sleeveless) & short set",
    [SLEEVELESS_VEST, SHORTS],
  ),
  "BOOK-STYLE-TYING-JABLA": chart("MnM-4", "Book style jabla", [JABLA]),
  "BOOK-STYLE-TYING-JABLA-SHORT-SET": chart("MnM-5", "Book style jabla & short set", [JABLA, SHORTS]),
  "FRONT-OPEN-VEST-HALF-SLEEVE": chart("MnM-6", "Front open vest (half sleeve)", [HALF_SLEEVE_VEST]),
  "FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET": chart(
    "MnM-7",
    "Front open vest (half sleeve) & short set",
    [HALF_SLEEVE_VEST_IN_SET, SHORTS],
  ),
  "BASIC-SHORTS": chart("MnM-8", "Basic shorts", [SHORTS]),
  "SIDE-BUTTON-T-SHIRT-SHORTS-SET-HALF-SLEEVE": chart(
    "MnM-9",
    "Side button T-shirt & shorts set (half sleeve)",
    [SIDE_BUTTON_TEE, SHORTS],
  ),
  "SIDE-BUTTON-T-SHIRT-HALF-SLEEVE": chart("MnM-10", "Side button T-shirt (half sleeve)", [
    SIDE_BUTTON_TEE,
  ]),
  "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE": chart("MnM-11", "Baby front open frock (half sleeve)", [
    FROCK,
  ]),
  "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS": chart(
    "MnM-12",
    "Baby front open frock (half sleeve) & bloomers",
    [FROCK_IN_SET, BLOOMERS],
  ),
  "BODY-SUIT-HALF-SLEEVE": chart("MnM-13", "Body suit (half sleeve)", [BODY_SUIT]),
  "BABY-NAPPY-BUTTON": chart("MnM-14", "Baby nappy — button", [NAPPY]),
  "ROUND-CAP": chart("MnM-15", "Round cap", [ROUND_CAP]),
  "CAP-SOCKS-MITTEN-SET": chart("MnM-16", "Cap, socks & mitten set", [ROUND_CAP, SOCKS, MITTENS]),
  "PACK-OF-3-MITTENS": chart("MnM-17", "Pack of 3 mittens", [MITTENS]),
  SOCKS: chart("MnM-18", "Socks", [SOCKS]),
  "TYING-CAP": chart("MnM-19", "Tying cap", [TYING_CAP]),
  "FACE-NAPKIN-WASH-CLOTH-10-10": chart("MnM-20", "Face napkin / wash cloth", [FACE_NAPKIN]),
  "BABY-CARRY-NEST": chart("MnM-21", "Baby carry nest", [CARRY_NEST]),
  "BABY-PILLOW": chart("MnM-22", "Baby pillow", [PILLOW]),
  "BIBS-SPECIAL": chart("MnM-23", "Bib special", [BIB]),
  "BABY-SWADDLE": chart("MnM-24", "Baby swaddle", [SWADDLE]),
  "BABY-TERRY-TOWEL-FLUFFY": chart("MnM-25", "Baby terry towel — fluffy", [TOWEL]),
};

/** Name / slug fragments → SKU (longest match wins). */
const NAME_ALIASES: Array<{ match: RegExp; sku: string }> = [
  { match: /fleece.*front.?open.?vest|loop.?back.?fleece/i, sku: "LOOP-BACK-FLEECE-KNIT-FRONT-OPEN-VEST-FULL-SLEEVE" },
  { match: /front.?open.?vest.*sleeveless.*short/i, sku: "FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET" },
  { match: /front.?open.?vest.*half.?sleeve.*short/i, sku: "FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET" },
  { match: /front.?open.?vest.*half.?sleeve/i, sku: "FRONT-OPEN-VEST-HALF-SLEEVE" },
  { match: /front.?open.?vest.*sle[ev]*less|front.?open.?vest.?sleveless/i, sku: "FRONT-OPEN-VEST-SLEVELESS" },
  { match: /book.?style.*(tying.?)?jabla.*short/i, sku: "BOOK-STYLE-TYING-JABLA-SHORT-SET" },
  { match: /book.?style.*(tying.?)?jabla/i, sku: "BOOK-STYLE-TYING-JABLA" },
  { match: /basic.?shorts/i, sku: "BASIC-SHORTS" },
  { match: /side.?button.?t.?shirt.*short/i, sku: "SIDE-BUTTON-T-SHIRT-SHORTS-SET-HALF-SLEEVE" },
  { match: /side.?button.?t.?shirt/i, sku: "SIDE-BUTTON-T-SHIRT-HALF-SLEEVE" },
  { match: /front.?open.?frock.*bloomer/i, sku: "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS" },
  { match: /front.?open.?frock|garden.?party.?frock/i, sku: "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE" },
  { match: /body.?suit/i, sku: "BODY-SUIT-HALF-SLEEVE" },
  { match: /nappy/i, sku: "BABY-NAPPY-BUTTON" },
  { match: /cap.?socks.?mitten/i, sku: "CAP-SOCKS-MITTEN-SET" },
  { match: /pack.?of.?3.?mitten|mittens?/i, sku: "PACK-OF-3-MITTENS" },
  { match: /round.?cap/i, sku: "ROUND-CAP" },
  { match: /tying.?cap/i, sku: "TYING-CAP" },
  { match: /\bsocks\b/i, sku: "SOCKS" },
  { match: /face.?napkin|wash.?cloth/i, sku: "FACE-NAPKIN-WASH-CLOTH-10-10" },
  { match: /carry.?nest/i, sku: "BABY-CARRY-NEST" },
  { match: /baby.?pillow|\bpillow\b/i, sku: "BABY-PILLOW" },
  { match: /\bbibs?\b/i, sku: "BIBS-SPECIAL" },
  { match: /swaddle/i, sku: "BABY-SWADDLE" },
  { match: /terry.?towel|towel.?fluffy/i, sku: "BABY-TERRY-TOWEL-FLUFFY" },
];

export function normalizeChartSku(sku: string | null | undefined): string {
  return (sku ?? "").trim().toUpperCase().replace(/_/g, "-");
}

export function resolveSizeChartSku(input: {
  sku?: string | null;
  name?: string | null;
  slug?: string | null;
}): string | null {
  const direct = normalizeChartSku(input.sku);
  if (direct && SIZE_CHARTS_BY_SKU[direct]) return direct;

  const haystack = `${input.slug ?? ""} ${input.name ?? ""}`.trim();
  if (!haystack) return null;

  let best: { sku: string; len: number } | null = null;
  for (const alias of NAME_ALIASES) {
    const m = haystack.match(alias.match);
    if (!m) continue;
    const len = m[0].length;
    if (!best || len > best.len) best = { sku: alias.sku, len };
  }
  return best?.sku ?? null;
}

export type MeasurementCell = {
  min: number | null;
  max: number | null;
};

const RANGE_CELL =
  /^(-?\d+(?:\.\d+)?)\s*(?:[-–—]|to)\s*(-?\d+(?:\.\d+)?)\s*(cm|in(?:ch(?:es)?)?|["″])?$/i;

function parseLooseNumber(raw: string): number | null {
  const t = raw.trim();
  const match = t.match(/^(-?\d+(?:\.\d+)?)\s*(cm|in(?:ch(?:es)?)?|["″])?$/i);
  if (!match) return null;
  const n = Number(match[1]) * (match[2] && match[2].toLowerCase() !== "cm" ? 2.54 : 1);
  return Number.isFinite(n) && n >= 0 ? n : null;
}

/** Parse a sheet cell: `27`, `"27"`, or a range `"25-27"` / `"25–27 cm"`. */
export function parseMeasurementCell(value: number | string | undefined | null): MeasurementCell {
  if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
    return { min: null, max: value };
  }
  if (typeof value !== "string") return { min: null, max: null };
  const t = value.trim();
  if (!t) return { min: null, max: null };

  const range = t.match(RANGE_CELL);
  if (range) {
    const factor = range[3] && range[3].toLowerCase() !== "cm" ? 2.54 : 1;
    const a = Number(range[1]) * factor;
    const b = Number(range[2]) * factor;
    if (!Number.isFinite(a) || !Number.isFinite(b) || a < 0 || b < 0) return { min: null, max: null };
    return { min: Math.min(a, b), max: Math.max(a, b) };
  }

  const max = parseLooseNumber(t);
  return { min: null, max };
}

export function formatMeasurementCell(min: number | null, max: number | null): number | string {
  if (min != null && max != null && min !== max) return `${min}-${max}`;
  if (max != null) return max;
  if (min != null) return min;
  return "";
}

/** True for the newborn column that stores an explicit min–max range. */
export function isZeroToThreeBand(band: string): boolean {
  const key = band
    .toUpperCase()
    .replace(/\s+/g, "")
    .replace(/MONTHS?/, "M")
    .replace(/MOS?/, "M");
  return key === "0-3M" || key === "0-3";
}

function numericCell(value: number | string | undefined): number | null {
  return parseMeasurementCell(value).max;
}

/** Zero is allowed for garment construction measurements; blanks/N/A are not numeric zero. */
export function isValidMeasurementCell(value: unknown): boolean {
  if (value == null) return true;
  if (typeof value === "string" && /^(?:\s*|[-–—]|n\/?a)$/i.test(value.trim())) return true;
  if (typeof value !== "number" && typeof value !== "string") return false;
  return parseMeasurementCell(value).max != null;
}

export function normalizeSizeChartBand(band: string): string {
  const key = band.trim().toUpperCase().replace(/[–—]/g, "-").replace(/\s+/g, "")
    .replace(/MONTHS?/g, "M").replace(/MOS?/g, "M");
  return /^(ONE(?:SIZE)?|COMMON(?:SIZE)?|SINGLE(?:SIZE)?|OS)$/.test(key) ? "__COMMON__" : key;
}

export function sizeChartMeasurementErrors(chart: ProductSizeChart): string[] {
  const errors: string[] = [];
  for (const section of chart.sections) {
    const keys = section.bands.map(normalizeSizeChartBand);
    if (keys.some((key) => !key) || new Set(keys).size !== keys.length) {
      errors.push(`Section "${section.title}" has empty or duplicate size bands`);
    }
    const labels = section.rows.map((row) => row.label.trim().toLowerCase());
    if (new Set(labels).size !== labels.length) errors.push(`Section "${section.title}" has duplicate measurement rows`);
    for (const row of section.rows) {
      for (const band of section.bands) {
        if (!isValidMeasurementCell(row.values[band])) {
          errors.push(`${section.title} / ${row.label} / ${band}: use a non-negative number, range, or blank`);
        }
      }
    }
  }
  return errors;
}

export function serializeProductSizeChart(chart: ProductSizeChart | null | undefined): string | null {
  if (!chart) return null;
  const errors = sizeChartMeasurementErrors(chart);
  if (errors.length) throw new Error(errors.join("; "));
  return isUsableSizeChart(chart) ? JSON.stringify(chart) : null;
}

export function isUsableSizeChart(
  chart: ProductSizeChart | null | undefined,
): chart is ProductSizeChart {
  if (!chart || !Array.isArray(chart.sections) || chart.sections.length === 0) return false;
  if (!chart.sections.every((s) => s && Array.isArray(s.bands) && s.bands.every((b) => typeof b === "string") &&
    Array.isArray(s.rows) && s.rows.every((row) => row && typeof row.label === "string" && row.values && typeof row.values === "object"))) return false;
  if (sizeChartMeasurementErrors(chart).length > 0) return false;
  return chart.sections.some((s) => {
    if (!Array.isArray(s?.bands) || s.bands.length === 0) return false;
    if (!Array.isArray(s?.rows) || s.rows.length === 0) return false;
    return s.rows.some((row) => s.bands.some((band) => numericCell(row?.values?.[band]) != null));
  });
}

/** Parse/normalize size chart JSON from the API; returns null if unusable. */
export function parseProductSizeChart(raw: unknown): ProductSizeChart | null {
  if (raw == null) return null;
  let value: unknown = raw;
  if (typeof raw === "string") {
    const trimmed = raw.trim();
    if (!trimmed) return null;
    try {
      value = JSON.parse(trimmed);
    } catch {
      return null;
    }
  }
  if (!value || typeof value !== "object") return null;
  const obj = value as Record<string, unknown>;
  const sectionsRaw = Array.isArray(obj.sections) ? obj.sections : [];
  let invalid = false;
  const sections: SizeChartSection[] = sectionsRaw
    .map((sec): SizeChartSection | null => {
      if (!sec || typeof sec !== "object") return null;
      const s = sec as Record<string, unknown>;
      const bands = Array.isArray(s.bands)
        ? s.bands.map((b) => typeof b === "string" ? b.trim() : "")
        : [];
      const rowsRaw = Array.isArray(s.rows) ? s.rows : [];
      const rows: SizeChartRow[] = rowsRaw
        .map((row): SizeChartRow | null => {
          if (!row || typeof row !== "object") return null;
          const r = row as Record<string, unknown>;
          const label = String(r.label ?? "").trim();
          if (!label) return null;
          const valuesSrc =
            r.values && typeof r.values === "object" && !Array.isArray(r.values)
              ? (r.values as Record<string, unknown>)
              : {};
          const values: Record<string, number | string> = {};
          for (const band of bands) {
            const cell = valuesSrc[band];
            if (!isValidMeasurementCell(cell)) invalid = true;
            if (typeof cell === "number" && Number.isFinite(cell)) values[band] = cell;
            else if (typeof cell === "string") values[band] = cell;
            else if (cell != null && cell !== "") {
              invalid = true;
              values[band] = "";
            } else {
              values[band] = "";
            }
          }
          return { label, values };
        })
        .filter((r): r is SizeChartRow => r != null);
      if (bands.length === 0 || rows.length === 0) return null;
      return {
        title: String(s.title ?? "").trim() || "Measurements",
        bands,
        rows,
      };
    })
    .filter((s): s is SizeChartSection => s != null);

  const chart: ProductSizeChart = {
    code: String(obj.code ?? "").trim(),
    title: String(obj.title ?? "").trim(),
    sections,
    tolerance: String(obj.tolerance ?? "").trim() || "±1 cm",
  };
  return !invalid && isUsableSizeChart(chart) ? chart : null;
}

/** Approved SKU/name template for admin “Load from catalog”. Not used on the storefront. */
export function catalogSizeChartTemplate(input: {
  sku?: string | null;
  name?: string | null;
  slug?: string | null;
}): ProductSizeChart | null {
  const key = resolveSizeChartSku(input);
  if (!key) return null;
  return SIZE_CHARTS_BY_SKU[key] ?? null;
}

/** Saved product chart only — no hardcoded SKU fallback. */
export function sizeChartForProduct(input: {
  sku?: string | null;
  name?: string | null;
  slug?: string | null;
  sizeChart?: ProductSizeChart | null;
}): ProductSizeChart | null {
  return isUsableSizeChart(input.sizeChart) ? input.sizeChart : null;
}

export type ChartFitPoint = {
  /** Product size band label as on the chart (e.g. "0-3M"). */
  band: string;
  lengthCm: number | null;
  chestCm: number | null;
  /** Explicit lower bound when the cell is a range (typically 0-3M). */
  lengthMin?: number | null;
  chestMin?: number | null;
};

const CHEST_LABEL = /^(chest(\s*width|\s*\(½\)|\s*\(1\/2\))?)$/i;
const LENGTH_LABEL =
  /^(body\s*length.*|length\s*\(hps.*|total\s*length|length)$/i;

function findRow(
  section: SizeChartSection,
  matcher: RegExp,
): SizeChartRow | undefined {
  return section.rows.find((r) => matcher.test(r.label.trim()));
}

/**
 * Length + chest points from the approved MnM sheet for the product's primary
 * garment section (first age-banded section). Used by the storefront size guide
 * and calculator.
 */
export function fitPointsFromSizeChart(chart: ProductSizeChart): ChartFitPoint[] {
  const section =
    chart.sections.find(
      (s) => s.bands.length > 1 && !/common|single/i.test(s.bands[0] ?? ""),
    ) ?? chart.sections[0];
  if (!section) return [];

  const chestRow = findRow(section, CHEST_LABEL);
  // Prefer body/HPS length over "Total length" when both exist (tees have both sleeve-related totals).
  const lengthRow =
    section.rows.find((r) => /body\s*length|length\s*\(hps/i.test(r.label)) ??
    findRow(section, LENGTH_LABEL);

  return section.bands.map((band) => {
    const length = parseMeasurementCell(lengthRow?.values[band]);
    const chest = parseMeasurementCell(chestRow?.values[band]);
    return {
      band,
      lengthCm: length.max,
      chestCm: chest.max,
      ...(length.min != null ? { lengthMin: length.min } : {}),
      ...(chest.min != null ? { chestMin: chest.min } : {}),
    };
  });
}

/** Filter chart columns to sizes the product actually sells (when age-banded). */
export function filterSizeChartToProductSizes(
  chart: ProductSizeChart,
  productSizes: string[],
): ProductSizeChart {
  const wanted = new Set(productSizes.map(normalizeSizeChartBand).filter(Boolean));

  if (wanted.size === 0) return chart;

  const sections = chart.sections.map((sec) => {
    const isCommonOnly =
      sec.bands.length === 1 &&
      /common|single/i.test(sec.bands[0]!);
    if (isCommonOnly) return sec;

    const bands = sec.bands.filter((b) => {
      const key = normalizeSizeChartBand(b);
      if (wanted.has(key)) return true;
      if (wanted.has("__COMMON__") && /common|single/i.test(b)) return true;
      return false;
    });
    if (bands.length === 0 || bands.length === sec.bands.length) return sec;
    return {
      ...sec,
      bands,
      rows: sec.rows.map((r) => ({
        label: r.label,
        values: Object.fromEntries(bands.map((b) => [b, r.values[b]!])),
      })),
    };
  });

  return { ...chart, sections };
}
