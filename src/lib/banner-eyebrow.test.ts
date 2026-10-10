import { describe, expect, it } from "vitest";
import {
  DEFAULT_BANNER_EYEBROW_FIRST,
  DEFAULT_BANNER_EYEBROW_REST,
  resolveBannerEyebrow,
} from "./banner-eyebrow";

describe("resolveBannerEyebrow", () => {
  it("uses saved eyebrow when present", () => {
    expect(resolveBannerEyebrow("New drop", 0)).toBe("New drop");
    expect(resolveBannerEyebrow("  Sale  ", 2)).toBe("Sale");
  });

  it("falls back by slide index when eyebrow is empty", () => {
    expect(resolveBannerEyebrow(null, 0)).toBe(DEFAULT_BANNER_EYEBROW_FIRST);
    expect(resolveBannerEyebrow("", 1)).toBe(DEFAULT_BANNER_EYEBROW_REST);
    expect(resolveBannerEyebrow("   ", 3)).toBe(DEFAULT_BANNER_EYEBROW_REST);
  });
});
