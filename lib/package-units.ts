export type WeightUnit = "g" | "kg" | "lb";
export type LengthUnit = "cm" | "in" | "m";

export const WEIGHT_UNITS: { value: WeightUnit; label: string }[] = [
  { value: "g", label: "g" },
  { value: "kg", label: "kg" },
  { value: "lb", label: "lb" },
];

export const LENGTH_UNITS: { value: LengthUnit; label: string }[] = [
  { value: "cm", label: "cm" },
  { value: "in", label: "in" },
  { value: "m", label: "m" },
];

const GRAMS_PER_LB = 453.59237;
const CM_PER_INCH = 2.54;
const CM_PER_METRE = 100;

export function toGrams(value: number, unit: WeightUnit) {
  if (unit === "kg") return value * 1000;
  if (unit === "lb") return value * GRAMS_PER_LB;
  return value;
}

export function fromGrams(grams: number, unit: WeightUnit) {
  if (unit === "kg") return grams / 1000;
  if (unit === "lb") return grams / GRAMS_PER_LB;
  return grams;
}

export function toCentimetres(value: number, unit: LengthUnit) {
  if (unit === "in") return value * CM_PER_INCH;
  if (unit === "m") return value * CM_PER_METRE;
  return value;
}

export function fromCentimetres(cm: number, unit: LengthUnit) {
  if (unit === "in") return cm / CM_PER_INCH;
  if (unit === "m") return cm / CM_PER_METRE;
  return cm;
}

function decimalsFor(unit: WeightUnit | LengthUnit) {
  if (unit === "g") return 0;
  if (unit === "cm" || unit === "in") return 2;
  return 3;
}

export function formatPackageUnitValue(value: number, unit: WeightUnit | LengthUnit) {
  if (!Number.isFinite(value)) return "";
  return String(Number(value.toFixed(decimalsFor(unit))));
}

export function parsePackageWeightGrams(input: string, unit: WeightUnit) {
  const value = Number(input);
  if (!Number.isFinite(value) || value <= 0) {
    return { ok: false as const, message: "Enter a package weight greater than 0" };
  }
  const grams = Math.round(toGrams(value, unit));
  if (grams < 1) {
    return { ok: false as const, message: "Package weight must be at least 1 gram after conversion" };
  }
  return { ok: true as const, grams };
}

export function parsePackageDimensionCm(input: string, unit: LengthUnit) {
  const value = Number(input);
  if (!Number.isFinite(value) || value <= 0) {
    return { ok: false as const, message: "Enter length, breadth and height greater than 0" };
  }
  const cm = Math.round(toCentimetres(value, unit));
  if (cm < 1) {
    return { ok: false as const, message: "Box size must be at least 1 cm after conversion" };
  }
  return { ok: true as const, cm };
}

export function convertWeightInput(input: string, from: WeightUnit, to: WeightUnit) {
  if (from === to) return input;
  const value = Number(input);
  if (!Number.isFinite(value) || value <= 0) return input;
  return formatPackageUnitValue(fromGrams(toGrams(value, from), to), to);
}

export function convertLengthInput(input: string, from: LengthUnit, to: LengthUnit) {
  if (from === to) return input;
  const value = Number(input);
  if (!Number.isFinite(value) || value <= 0) return input;
  return formatPackageUnitValue(fromCentimetres(toCentimetres(value, from), to), to);
}
