import { describe, expect, it, vi } from "vitest";
import {
  downloadFilenameFromUrl,
  isExampleOrPlaceholderUrl,
  isPrivateObjectStorageUrl,
  isSameOriginPublicPath,
  optionalPublicUrlFieldError,
  sanitizePublicUrl,
  toDirectDownloadUrl,
  triggerPublicFileDownload,
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

describe("public file download", () => {
  it("derives filenames from URLs", () => {
    expect(downloadFilenameFromUrl("https://cdn.example.com/catalogues/soft-bloom.pdf")).toBe(
      "soft-bloom.pdf",
    );
    expect(downloadFilenameFromUrl("/e-catalogue.pdf")).toBe("e-catalogue.pdf");
    expect(
      downloadFilenameFromUrl(
        "https://cdn.example.com/catalogues/1791979056416-d89c8f50-f4d0-4b4d-8716-3816b62e4d92-Product-Catalogue.pdf",
      ),
    ).toBe("Product-Catalogue.pdf");
    expect(downloadFilenameFromUrl("")).toBe("download");
  });

  it("treats site paths as same-origin", () => {
    expect(isSameOriginPublicPath("/e-catalogue.pdf")).toBe(true);
    expect(isSameOriginPublicPath("https://cdn.example.com/cat.pdf")).toBe(false);
  });

  it("downloads cross-origin files via fetch and blob URL", async () => {
    const click = vi.fn();
    const remove = vi.fn();
    const appendChild = vi.fn();
    const createElement = vi.spyOn(document, "createElement").mockReturnValue({
      click,
      remove,
    } as unknown as HTMLAnchorElement);
    vi.spyOn(document.body, "appendChild").mockImplementation(appendChild);
    URL.createObjectURL = vi.fn(() => "blob:mock");
    URL.revokeObjectURL = vi.fn();

    const blob = new Blob(["pdf"], { type: "application/pdf" });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        blob: () => Promise.resolve(blob),
      }),
    );

    await triggerPublicFileDownload("https://cdn.example.com/catalogues/soft-bloom.pdf");

    expect(fetch).toHaveBeenCalledWith("https://cdn.example.com/catalogues/soft-bloom.pdf");
    expect(createElement).toHaveBeenCalledWith("a");
    expect(createElement.mock.results[0]?.value.download).toBe("soft-bloom.pdf");
    expect(click).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:mock");

    createElement.mockRestore();
  });

  it("converts Google Drive view links before downloading", async () => {
    const driveView = "https://drive.google.com/file/d/abc123/view?usp=sharing";
    expect(toDirectDownloadUrl(driveView)).toBe(
      "https://drive.google.com/uc?export=download&id=abc123",
    );
  });
});
