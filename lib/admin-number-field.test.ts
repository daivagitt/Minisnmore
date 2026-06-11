import { describe, expect, it } from "vitest";
import {
  numberFieldDisplay,
  numberFieldOr,
  coerceNonNegativeIntFromUnknown,
  optionalNumberFromUnknown,
  parseNumberField,
  requireNumberField,
} from "./admin-number-field";

describe("admin-number-field", () => {
  it("shows empty string for unset values", () => {
    expect(numberFieldDisplay(null)).toBe("");
    expect(numberFieldDisplay(undefined)).toBe("");
  });

  it("shows zero when explicitly set", () => {
    expect(numberFieldDisplay(0)).toBe("0");
  });

  it("parses blank as null", () => {
    expect(parseNumberField("")).toBeNull();
    expect(parseNumberField("  ")).toBeNull();
    expect(parseNumberField("12")).toBe(12);
  });

  it("optionalNumberFromUnknown preserves zero", () => {
    expect(optionalNumberFromUnknown(null)).toBeNull();
    expect(optionalNumberFromUnknown("")).toBeNull();
    expect(optionalNumberFromUnknown(0)).toBe(0);
    expect(optionalNumberFromUnknown("0")).toBe(0);
    expect(optionalNumberFromUnknown("12.5")).toBe(12.5);
  });

  it("coerceNonNegativeIntFromUnknown matches bulk-import sort order semantics", () => {
    expect(coerceNonNegativeIntFromUnknown(null)).toBe(0);
    expect(coerceNonNegativeIntFromUnknown("")).toBe(0);
    expect(coerceNonNegativeIntFromUnknown("10.9")).toBe(10);
    expect(coerceNonNegativeIntFromUnknown(-3)).toBe(0);
  });

  it("falls back on save when empty", () => {
    expect(numberFieldOr(null, 0)).toBe(0);
    expect(numberFieldOr(5, 0)).toBe(5);
  });

  it("requires numeric fields when blank", () => {
    expect(requireNumberField(null, "Price")).toMatch(/required/i);
    expect(requireNumberField(0, "Price")).toMatch(/greater than 0/i);
    expect(requireNumberField(0, "Stock", { allowZero: true })).toBeNull();
    expect(requireNumberField(null, "Stock", { allowZero: true })).toMatch(/required/i);
    expect(requireNumberField(101, "GST rate", { allowZero: true, max: 100 })).toMatch(/100 or less/i);
  });
});
