/** Matches S3StorageService.ALLOWED_EXTENSIONS */
export const ALLOWED_IMAGE_EXTENSIONS = new Set(["jpg", "jpeg", "png", "webp", "gif"]);

/** Aligns with spring.servlet.multipart.max-file-size and S3_MAX_UPLOAD_BYTES (8 MB). */
export const MAX_ADMIN_IMAGE_BYTES = 8 * 1024 * 1024;

export const IMAGE_ACCEPT =
  ".jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif";

export type AdminImageFolder =
  | "products"
  | "categories"
  | "branding"
  | "subscriptions"
  | "banners"
  | "home"
  | "blog"
  | "about";

export const UNSUPPORTED_IMAGE_TYPE_MESSAGE =
  "Image type not accepted. Use JPG, PNG, WebP, or GIF only.";

export function maxAdminImageSizeLabelMb() {
  return Math.round(MAX_ADMIN_IMAGE_BYTES / (1024 * 1024));
}

export function formatAdminImageSizeMb(bytes: number) {
  return (bytes / (1024 * 1024)).toFixed(1);
}

export function imageTooLargeMessage(fileSizeBytes: number) {
  return `Image is too large (${formatAdminImageSizeMb(fileSizeBytes)} MB). Maximum size is ${maxAdminImageSizeLabelMb()} MB.`;
}

export function isAllowedImageFile(file: File): boolean {
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (ALLOWED_IMAGE_EXTENSIONS.has(ext)) return true;
  const mime = (file.type || "").toLowerCase();
  return ["image/jpeg", "image/png", "image/webp", "image/gif"].includes(mime);
}

export type AdminImageValidationResult = { ok: true } | { ok: false; error: string };

export function validateAdminImageFile(file: File): AdminImageValidationResult {
  if (!isAllowedImageFile(file)) {
    return { ok: false, error: UNSUPPORTED_IMAGE_TYPE_MESSAGE };
  }
  if (file.size <= 0) {
    return { ok: false, error: "File is empty." };
  }
  if (file.size > MAX_ADMIN_IMAGE_BYTES) {
    return { ok: false, error: imageTooLargeMessage(file.size) };
  }
  return { ok: true };
}

export function assertAllowedImageFile(file: File): void {
  const result = validateAdminImageFile(file);
  if (!result.ok) {
    throw new Error(result.error);
  }
}
