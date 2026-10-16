import { describe, expect, it } from "vitest";
import {
  formatAgeRangeField,
  formatCommaList,
  parseAgeRangeField,
  parseCommaList,
} from "./comma-list-field";

describe("comma-list-field", () => {
  it("formats and parses comma lists", () => {
    expect(formatCommaList(["0-3m", "3-6m"])).toBe("0-3m, 3-6m");
    expect(parseCommaList("0-3m, 3-6m")).toEqual(["0-3m", "3-6m"]);
    expect(parseCommaList("0-3m,")).toEqual(["0-3m"]);
  });

  it("formats and parses age range", () => {
    expect(formatAgeRangeField(0, 24)).toBe("0, 24");
    expect(parseAgeRangeField("0, 24")).toEqual({ min: 0, max: 24 });
    expect(parseAgeRangeField("0,")).toEqual({ min: 0, max: null });
    expect(parseAgeRangeField("")).toEqual({ min: null, max: null });
  });
});
