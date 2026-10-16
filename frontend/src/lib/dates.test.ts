import { describe, expect, it } from "vitest";
import { fromDatetimeLocalValue, toDatetimeLocalValue } from "@/lib/dates";

describe("datetime-local helpers", () => {
  it("toDatetimeLocalValue returns empty for missing or invalid values", () => {
    expect(toDatetimeLocalValue(null)).toBe("");
    expect(toDatetimeLocalValue("")).toBe("");
    expect(toDatetimeLocalValue("not-a-date")).toBe("");
  });

  it("toDatetimeLocalValue uses local date/time components", () => {
    const iso = "2026-09-02T01:30:00.000Z";
    const date = new Date(iso);
    const expected = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
    expect(toDatetimeLocalValue(iso)).toBe(expected);
  });

  it("roundtrips local datetime input values", () => {
    const local = "2026-09-02T07:00";
    expect(toDatetimeLocalValue(fromDatetimeLocalValue(local)!)).toBe(local);
  });

  it("fromDatetimeLocalValue returns null for empty input", () => {
    expect(fromDatetimeLocalValue("")).toBeNull();
  });
});
