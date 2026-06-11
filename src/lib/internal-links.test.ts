import { describe, expect, it } from "vitest";
import { internalSitePathError, isInternalSitePath, resolveInternalSitePath } from "./internal-links";

describe("internal-links", () => {
  it("accepts empty and relative paths", () => {
    expect(isInternalSitePath("")).toBe(true);
    expect(isInternalSitePath("/shop")).toBe(true);
    expect(isInternalSitePath("/product/my-slug")).toBe(true);
  });

  it("rejects external and unsafe URLs", () => {
    expect(isInternalSitePath("https://google.com")).toBe(false);
    expect(isInternalSitePath("http://example.com")).toBe(false);
    expect(isInternalSitePath("//evil.com")).toBe(false);
    expect(isInternalSitePath("javascript:alert(1)")).toBe(false);
    expect(isInternalSitePath("shop")).toBe(false);
  });

  it("resolves invalid links to fallback", () => {
    expect(resolveInternalSitePath("https://google.com", "/shop")).toBe("/shop");
    expect(resolveInternalSitePath("/gift", "/shop")).toBe("/gift");
  });

  it("returns a validation message for external URLs", () => {
    expect(internalSitePathError("/ok")).toBeNull();
    expect(internalSitePathError("https://google.com")).toMatch(/on-site path/i);
  });
});
