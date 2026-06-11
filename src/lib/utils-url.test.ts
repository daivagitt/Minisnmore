import { describe, expect, it } from "vitest";
import {
  isExampleOrPlaceholderUrl,
  isPrivateObjectStorageUrl,
  optionalPublicUrlFieldError,
  sanitizePublicUrl,
} from "@/lib/utils";

describe("public URL validation", () => {
  it("detects bulk-import placeholder image URLs", () => {
    expect(isExampleOrPlaceholderUrl("https://…/cover.jpg")).toBe(true);
    expect(isExampleOrPlaceholderUrl("https://.../1.jpg")).toBe(true);
    expect(isExampleOrPlaceholderUrl("https://example.com/resource")).toBe(true);
  });

  it("accepts real uploaded and site paths", () => {
    expect(isExampleOrPlaceholderUrl("/uploads/categories/cover.jpg")).toBe(false);
    expect(isExampleOrPlaceholderUrl("https://cdn.minisnmore.com/categories/cover.jpg")).toBe(false);
    expect(sanitizePublicUrl("https://cdn.minisnmore.com/categories/cover.jpg")).toBeTruthy();
  });

  it("rejects private R2 API URLs for public image src", () => {
    const privateUrl =
      "https://241217d8624d6e0a8a425dbbc8bb0ca7.r2.cloudflarestorage.com/miniandmore/uat/products/test.png";
    expect(isPrivateObjectStorageUrl(privateUrl)).toBe(true);
    expect(sanitizePublicUrl(privateUrl)).toBeNull();
  });

  it("returns a field error for placeholder category images", () => {
    expect(optionalPublicUrlFieldError("https://…/cover.jpg", "Category image")).toBe(
      "Category image must be a real uploaded image or valid site path, not a placeholder example.",
    );
  });
});
