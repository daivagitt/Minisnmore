import { describe, expect, it } from "vitest";
import { formatPlaceOfSupply } from "@/lib/india-states";

describe("formatPlaceOfSupply", () => {
  it("formats known states with GST codes", () => {
    expect(formatPlaceOfSupply("Karnataka")).toBe("29-Karnataka");
    expect(formatPlaceOfSupply("Maharashtra")).toBe("27-Maharashtra");
  });

  it("returns an em dash when state is missing", () => {
    expect(formatPlaceOfSupply(null)).toBe("—");
    expect(formatPlaceOfSupply("")).toBe("—");
    expect(formatPlaceOfSupply("   ")).toBe("—");
  });

  it("returns the raw state when no code mapping exists", () => {
    expect(formatPlaceOfSupply("Unknown State")).toBe("Unknown State");
  });
});
