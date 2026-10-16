import { describe, expect, it } from "vitest";
import {
  assertAllowedImageFile,
  isAllowedImageFile,
  MAX_ADMIN_IMAGE_BYTES,
  UNSUPPORTED_IMAGE_TYPE_MESSAGE,
  validateAdminImageFile,
} from "./admin-image";

describe("admin-image", () => {
  it("accepts common storefront image extensions", () => {
    expect(isAllowedImageFile(new File([], "photo.jpg"))).toBe(true);
    expect(isAllowedImageFile(new File([], "photo.PNG"))).toBe(true);
    expect(isAllowedImageFile(new File([], "photo.webp"))).toBe(true);
  });

  it("accepts image MIME types when the filename has no extension", () => {
    expect(isAllowedImageFile(new File([], "photo", { type: "image/jpeg" }))).toBe(true);
    expect(isAllowedImageFile(new File([], "blob", { type: "image/png" }))).toBe(true);
  });

  it("rejects raw and other unsupported types with a clear message", () => {
    expect(isAllowedImageFile(new File([], "photo.dng"))).toBe(false);
    expect(() => assertAllowedImageFile(new File([], "photo.dng"))).toThrow(
      UNSUPPORTED_IMAGE_TYPE_MESSAGE,
    );
    expect(validateAdminImageFile(new File([], "photo.dng"))).toEqual({
      ok: false,
      error: UNSUPPORTED_IMAGE_TYPE_MESSAGE,
    });
  });

  it("rejects images larger than the admin upload limit", () => {
    const big = new File([new Uint8Array(MAX_ADMIN_IMAGE_BYTES + 1)], "big.jpg", {
      type: "image/jpeg",
    });
    const result = validateAdminImageFile(big);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.error).toMatch(/too large/i);
      expect(result.error).toMatch(/8 MB/i);
    }
  });
});
