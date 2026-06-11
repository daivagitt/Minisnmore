import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Convert common cloud-storage "view" URLs into direct-download URLs so that
 * clicking a link triggers a file download instead of opening a viewer tab.
 * Currently handles Google Drive; other URLs are returned unchanged.
 */
export function toDirectDownloadUrl(url: string | null | undefined): string {
  if (!url) return "";
  try {
    // Google Drive: https://drive.google.com/file/d/<ID>/view?... → uc?export=download&id=<ID>
    const driveFile = url.match(/drive\.google\.com\/file\/d\/([^/]+)/i);
    if (driveFile?.[1]) {
      return `https://drive.google.com/uc?export=download&id=${driveFile[1]}`;
    }
    // Google Drive: https://drive.google.com/open?id=<ID>
    const u = new URL(url, "https://x.invalid");
    if (/drive\.google\.com$/i.test(u.hostname)) {
      const id = u.searchParams.get("id");
      if (id) return `https://drive.google.com/uc?export=download&id=${id}`;
    }
  } catch {
    /* fall through */
  }
  return url;
}

export function isPlaceholderValue(value: unknown): boolean {
  if (typeof value !== "string") return false;
  const text = value.trim().toLowerCase();
  return (
    !text ||
    text === "sample name" ||
    text === "sample title" ||
    text === "https://example.com/resource" ||
    text.startsWith("sample-") ||
    /\{\{[^}]*\}\}/.test(text)
  );
}

/** Template hints from admin placeholders / bulk-import examples — not real URLs. */
export function isExampleOrPlaceholderUrl(value: string): boolean {
  const text = value.trim();
  if (!text) return false;
  if (isPlaceholderValue(text)) return true;
  if (/[…]/.test(text)) return true;
  if (/https?:\/\/\.{2,}/i.test(text)) return true;
  if (text.startsWith("/")) return false;
  if (!/^https?:\/\//i.test(text)) return true;
  try {
    const url = new URL(text);
    const host = url.hostname.toLowerCase();
    if (!host || host === "x" || host === "example.com" || host === "example.invalid") return true;
    return false;
  } catch {
    return true;
  }
}

/** Validates optional admin image/link fields (empty is OK). */
export function optionalPublicUrlFieldError(
  value: string | null | undefined,
  label: string,
): string | null {
  const text = (value ?? "").trim();
  if (!text) return null;
  if (isExampleOrPlaceholderUrl(text)) {
    return `${label} must be a real uploaded image or valid site path, not a placeholder example.`;
  }
  if (text.startsWith("/")) return null;
  if (!sanitizePublicUrl(text)) {
    return `${label} must be a relative path or full http/https URL.`;
  }
  return null;
}

/** Validates required admin image/link fields. */
export function requiredPublicUrlFieldError(
  value: string | null | undefined,
  label: string,
): string | null {
  const text = (value ?? "").trim();
  if (!text) return `${label} is required.`;
  return optionalPublicUrlFieldError(text, label);
}

const DEFAULT_CONTACT_EMAIL = "care@minisnmore.com";

export function resolveContactEmail(value: string | null | undefined): string {
  const text = value?.trim() ?? "";
  if (!text || isPlaceholderValue(text) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text)) {
    return DEFAULT_CONTACT_EMAIL;
  }
  return text;
}

/** Private R2/S3 API hosts — valid for upload, not for use in image src URLs. */
export function isPrivateObjectStorageUrl(value: string): boolean {
  return /cloudflarestorage\.com/i.test(value) || /\.s3[.-][a-z0-9-]+\.amazonaws\.com/i.test(value);
}

export function sanitizePublicUrl(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const text = value.trim();
  if (isPlaceholderValue(text)) return null;
  if (text.startsWith("/")) return text;
  if (!/^https?:\/\//i.test(text)) return null;

  try {
    const url = new URL(text);
    if (["localhost", "127.0.0.1", "0.0.0.0"].includes(url.hostname)) return null;
    if (isPrivateObjectStorageUrl(text)) return null;
    return text;
  } catch {
    return null;
  }
}
