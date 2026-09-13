/** Aligns with spring.servlet.multipart.max-file-size and S3_MAX_UPLOAD_BYTES (8 MB). */
export const MAX_ADMIN_PDF_BYTES = 8 * 1024 * 1024;

export const PDF_ACCEPT = ".pdf,application/pdf";

export const UNSUPPORTED_PDF_TYPE_MESSAGE = "File type not accepted. Upload a PDF only.";

export function maxAdminPdfSizeLabelMb() {
  return Math.round(MAX_ADMIN_PDF_BYTES / (1024 * 1024));
}

export function formatAdminPdfSizeMb(bytes: number) {
  return (bytes / (1024 * 1024)).toFixed(1);
}

export function pdfTooLargeMessage(fileSizeBytes: number) {
  return `PDF is too large (${formatAdminPdfSizeMb(fileSizeBytes)} MB). Maximum size is ${maxAdminPdfSizeLabelMb()} MB.`;
}

export function isAllowedPdfFile(file: File): boolean {
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (ext === "pdf") return true;
  return (file.type || "").toLowerCase() === "application/pdf";
}

export type AdminPdfValidationResult = { ok: true } | { ok: false; error: string };

export function validateAdminPdfFile(file: File): AdminPdfValidationResult {
  if (!isAllowedPdfFile(file)) {
    return { ok: false, error: UNSUPPORTED_PDF_TYPE_MESSAGE };
  }
  if (file.size <= 0) {
    return { ok: false, error: "File is empty." };
  }
  if (file.size > MAX_ADMIN_PDF_BYTES) {
    return { ok: false, error: pdfTooLargeMessage(file.size) };
  }
  return { ok: true };
}

export function assertAllowedPdfFile(file: File): void {
  const result = validateAdminPdfFile(file);
  if (!result.ok) {
    throw new Error(result.error);
  }
}
