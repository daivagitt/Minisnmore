import { describe, expect, it } from "vitest";
import {
  convertLengthInput,
  convertWeightInput,
  fromCentimetres,
  fromGrams,
  parsePackageDimensionCm,
  parsePackageWeightGrams,
  toCentimetres,
  toGrams,
} from "./package-units";

describe("package weight conversion", () => {
  it("converts kilograms and pounds to whole grams for ExpressBees", () => {
    expect(parsePackageWeightGrams("500", "g")).toEqual({ ok: true, grams: 500 });
    expect(parsePackageWeightGrams("0.5", "kg")).toEqual({ ok: true, grams: 500 });
    expect(parsePackageWeightGrams("1.25", "kg")).toEqual({ ok: true, grams: 1250 });
    expect(parsePackageWeightGrams("1", "lb")).toEqual({ ok: true, grams: 454 });
    expect(parsePackageWeightGrams("2.2", "lb")).toEqual({ ok: true, grams: 998 });
  });

  it("rejects empty or too-small weight", () => {
    expect(parsePackageWeightGrams("", "g").ok).toBe(false);
    expect(parsePackageWeightGrams("0", "kg").ok).toBe(false);
    expect(parsePackageWeightGrams("0.0004", "kg").ok).toBe(false);
  });

  it("keeps exact values when switching weight units", () => {
    expect(convertWeightInput("500", "g", "kg")).toBe("0.5");
    expect(convertWeightInput("0.5", "kg", "g")).toBe("500");
    expect(convertWeightInput("0.5", "kg", "lb")).toBe("1.102");
    expect(convertWeightInput("1", "lb", "kg")).toBe("0.454");
    expect(convertWeightInput("1", "lb", "g")).toBe("454");
    expect(convertWeightInput("1.25", "kg", "g")).toBe("1250");
  });

  it("round-trips weight through grams without changing the source value", () => {
    for (const [value, unit] of [
      [500, "g"],
      [0.5, "kg"],
      [1.25, "kg"],
      [1, "lb"],
      [2.2, "lb"],
    ] as const) {
      const grams = toGrams(value, unit);
      expect(fromGrams(grams, unit)).toBeCloseTo(value, 10);
    }
  });
});

describe("package length conversion", () => {
  it("converts inches and metres to whole centimetres for ExpressBees", () => {
    expect(parsePackageDimensionCm("10", "cm")).toEqual({ ok: true, cm: 10 });
    expect(parsePackageDimensionCm("4", "in")).toEqual({ ok: true, cm: 10 });
    expect(parsePackageDimensionCm("12", "in")).toEqual({ ok: true, cm: 30 });
    expect(parsePackageDimensionCm("0.1", "m")).toEqual({ ok: true, cm: 10 });
    expect(parsePackageDimensionCm("0.25", "m")).toEqual({ ok: true, cm: 25 });
    expect(parsePackageDimensionCm("1", "m")).toEqual({ ok: true, cm: 100 });
  });

  it("rejects empty or too-small box size", () => {
    expect(parsePackageDimensionCm("", "cm").ok).toBe(false);
    expect(parsePackageDimensionCm("0", "m").ok).toBe(false);
    expect(parsePackageDimensionCm("0.004", "m").ok).toBe(false);
  });

  it("keeps exact values when switching length units", () => {
    expect(convertLengthInput("10", "cm", "m")).toBe("0.1");
    expect(convertLengthInput("0.1", "m", "cm")).toBe("10");
    expect(convertLengthInput("1", "m", "cm")).toBe("100");
    expect(convertLengthInput("100", "cm", "m")).toBe("1");
    expect(convertLengthInput("10", "cm", "in")).toBe("3.94");
    expect(convertLengthInput("4", "in", "cm")).toBe("10.16");
    expect(convertLengthInput("4", "in", "m")).toBe("0.102");
    expect(convertLengthInput("0.1", "m", "in")).toBe("3.94");
  });

  it("round-trips length through centimetres without changing the source value", () => {
    for (const [value, unit] of [
      [10, "cm"],
      [25.4, "cm"],
      [4, "in"],
      [12, "in"],
      [0.1, "m"],
      [1, "m"],
    ] as const) {
      const cm = toCentimetres(value, unit);
      expect(fromCentimetres(cm, unit)).toBeCloseTo(value, 10);
    }
  });
});
