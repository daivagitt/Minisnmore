import { describe, expect, it } from "vitest";
import {
  clampPage,
  pageRange,
  pageSlice,
  parsePageParam,
  totalPages,
  visiblePageNumbers,
} from "./pagination";

describe("totalPages / clampPage", () => {
  it("treats an empty list as one page", () => {
    expect(totalPages(0, 10)).toBe(1);
  });

  it("rounds up partial last pages", () => {
    expect(totalPages(21, 10)).toBe(3);
    expect(totalPages(20, 10)).toBe(2);
  });

  it("clamps out-of-range pages", () => {
    expect(clampPage(0, 5)).toBe(1);
    expect(clampPage(99, 5)).toBe(5);
    expect(clampPage(2.9, 5)).toBe(2);
    expect(clampPage(Number.NaN, 5)).toBe(1);
  });
});

describe("pageSlice", () => {
  const items = ["a", "b", "c", "d", "e"];

  it("returns the requested window", () => {
    expect(pageSlice(items, 2, 2)).toEqual(["c", "d"]);
  });

  it("clamps an oversized page to the last window", () => {
    expect(pageSlice(items, 99, 2)).toEqual(["e"]);
  });

  it("returns an empty array for an empty list", () => {
    expect(pageSlice([], 3, 10)).toEqual([]);
  });
});

describe("pageRange", () => {
  it("reports 1-based inclusive bounds", () => {
    expect(pageRange(25, 2, 10)).toEqual({ start: 11, end: 20 });
    expect(pageRange(25, 3, 10)).toEqual({ start: 21, end: 25 });
  });

  it("is empty when there are no items", () => {
    expect(pageRange(0, 1, 10)).toEqual({ start: 0, end: 0 });
  });
});

describe("parsePageParam", () => {
  it("accepts integers and numeric strings", () => {
    expect(parsePageParam(3)).toBe(3);
    expect(parsePageParam("4")).toBe(4);
    expect(parsePageParam(["5"])).toBe(5);
  });

  it("drops invalid values so callers can default to page 1", () => {
    expect(parsePageParam(undefined)).toBeUndefined();
    expect(parsePageParam("0")).toBeUndefined();
    expect(parsePageParam(1.5)).toBeUndefined();
    expect(parsePageParam("nope")).toBeUndefined();
  });
});

describe("visiblePageNumbers", () => {
  it("lists every page when the set is small", () => {
    expect(visiblePageNumbers(1, 5)).toEqual([1, 2, 3, 4, 5]);
  });

  it("inserts ellipsis around the current page", () => {
    expect(visiblePageNumbers(8, 20)).toEqual([1, "ellipsis", 7, 8, 9, "ellipsis", 20]);
  });

  it("keeps a consecutive run at the start and end", () => {
    expect(visiblePageNumbers(1, 20)).toEqual([1, 2, 3, 4, "ellipsis", 20]);
    expect(visiblePageNumbers(20, 20)).toEqual([1, "ellipsis", 17, 18, 19, 20]);
  });
});
