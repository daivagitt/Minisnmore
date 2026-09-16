import { describe, expect, it } from "vitest";
import {
  assertAllowedPdfFile,
  isAllowedPdfFile,
  MAX_ADMIN_PDF_BYTES,
  UNSUPPORTED_PDF_TYPE_MESSAGE,
  validateAdminPdfFile,
} from "./admin-pdf";

describe("admin-pdf", () => {
  it("accepts PDF extensions and MIME types", () => {
    expect(isAllowedPdfFile(new File([], "catalogue.pdf"))).toBe(true);
    expect(isAllowedPdfFile(new File([], "CATALOGUE.PDF"))).toBe(true);
    expect(isAllowedPdfFile(new File([], "blob", { type: "application/pdf" }))).toBe(true);
  });

  it("rejects non-PDF files", () => {
    expect(isAllowedPdfFile(new File([], "photo.jpg"))).toBe(false);
    expect(() => assertAllowedPdfFile(new File([], "notes.docx"))).toThrow(UNSUPPORTED_PDF_TYPE_MESSAGE);
  });

  it("accepts PDFs up to the 75 MB admin upload limit", () => {
    const ok = new File(["x"], "catalogue.pdf", { type: "application/pdf" });
    Object.defineProperty(ok, "size", { value: Math.round(59.4 * 1024 * 1024) });
    expect(validateAdminPdfFile(ok)).toEqual({ ok: true });
  });

  it("rejects PDFs larger than the admin upload limit", () => {
    const big = new File(["x"], "big.pdf", { type: "application/pdf" });
    Object.defineProperty(big, "size", { value: MAX_ADMIN_PDF_BYTES + 1 });
    const result = validateAdminPdfFile(big);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.error).toMatch(/too large/i);
      expect(result.error).toMatch(/75 MB/i);
    }
  });
});
