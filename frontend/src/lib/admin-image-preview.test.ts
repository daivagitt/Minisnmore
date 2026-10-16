import { describe, expect, it } from "vitest";
import { ADMIN_IMAGE_PREVIEW_WIDTH, adminImagePreviewSrc } from "@/lib/admin-image-preview";

describe("adminImagePreviewSrc", () => {
  it("wraps https catalog URLs in the media preview endpoint", () => {
    const src = adminImagePreviewSrc("https://pub-abc.r2.dev/uat/products/romper.jpg");
    expect(src).toBe(
      `/api/v1/media/preview?u=${encodeURIComponent("https://pub-abc.r2.dev/uat/products/romper.jpg")}&w=${ADMIN_IMAGE_PREVIEW_WIDTH}`,
    );
  });

  it("leaves blob, data, and site-relative paths unchanged", () => {
    expect(adminImagePreviewSrc("blob:https://minisnmore.com/1")).toBe("blob:https://minisnmore.com/1");
    expect(adminImagePreviewSrc("data:image/png;base64,abc")).toBe("data:image/png;base64,abc");
    expect(adminImagePreviewSrc("/uploads/local.jpg")).toBe("/uploads/local.jpg");
  });

  it("returns empty for blank values", () => {
    expect(adminImagePreviewSrc("")).toBe("");
    expect(adminImagePreviewSrc("   ")).toBe("");
    expect(adminImagePreviewSrc(null)).toBe("");
  });
});
