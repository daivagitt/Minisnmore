import { describe, expect, it } from "vitest";
import { agesFromSizes, sizeToAgeBand, sortSizesAsc } from "./product-ages";

describe("product-ages", () => {
  it("maps size labels case-insensitively", () => {
    expect(sizeToAgeBand("0-3M")).toBe("0-3m");
    expect(sizeToAgeBand("0-3")).toBe("0-3m");
    expect(sizeToAgeBand("12 - 18")).toBe("12-18m");
    expect(sizeToAgeBand("6-12m")).toBe("6-12m");
  });

  it("derives age bands from product sizes", () => {
    expect(agesFromSizes(["0-3M", "3-6M", "6-12M"])).toEqual(["0-3m", "3-6m", "6-12m"]);
  });

  it("treats FreeSize as all age bands", () => {
    expect(agesFromSizes(["FreeSize"])).toEqual(["0-3m", "3-6m", "6-12m", "12-18m", "18-24m"]);
    expect(agesFromSizes(["COMMON SIZE"])).toEqual(["0-3m", "3-6m", "6-12m", "12-18m", "18-24m"]);
  });

  it("falls back to month range when sizes are missing", () => {
    expect(agesFromSizes([], { min: 6, max: 12 })).toEqual(["6-12m"]);
  });

  it("sorts sizes in ascending age-band order", () => {
    expect(sortSizesAsc(["18-24m", "0-3m", "6-12m", "3-6m"])).toEqual([
      "0-3m",
      "3-6m",
      "6-12m",
      "18-24m",
    ]);
    expect(sortSizesAsc(["One Size", "0-3m", "3-6m"])).toEqual(["0-3m", "3-6m", "One Size"]);
  });
});
