/** Custom drag payload so admin can drop a gallery/variant photo onto the listing display image. */
export const DISPLAY_IMAGE_DRAG_TYPE = "application/x-mn-display-image";

/** Colour-gallery reorder payload (`colorIndex:imageIndex`). */
export const GALLERY_REORDER_DRAG_TYPE = "application/x-mn-gallery-reorder";

type DisplayImageDragPayload = {
  url: string;
  reorder?: string;
};

export function looksLikeImageUrl(value: string): boolean {
  const url = value.trim();
  return /^(https?:\/\/|\/)/i.test(url);
}

export function setDisplayImageDragData(
  dataTransfer: DataTransfer,
  url: string,
  reorder?: string,
): void {
  const trimmed = url.trim();
  if (!trimmed) return;
  const payload: DisplayImageDragPayload = reorder
    ? { url: trimmed, reorder }
    : { url: trimmed };
  dataTransfer.setData(DISPLAY_IMAGE_DRAG_TYPE, trimmed);
  if (reorder) dataTransfer.setData(GALLERY_REORDER_DRAG_TYPE, reorder);
  dataTransfer.setData("text/plain", JSON.stringify(payload));
}

function parsePlainPayload(raw: string): DisplayImageDragPayload | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  if (looksLikeImageUrl(trimmed)) return { url: trimmed };
  if (trimmed.startsWith("{")) {
    try {
      const parsed = JSON.parse(trimmed) as DisplayImageDragPayload;
      if (parsed?.url && looksLikeImageUrl(String(parsed.url))) {
        return {
          url: String(parsed.url).trim(),
          reorder: typeof parsed.reorder === "string" ? parsed.reorder : undefined,
        };
      }
    } catch {
      return null;
    }
  }
  return null;
}

export function getDisplayImageDragUrl(dataTransfer: DataTransfer): string | null {
  const typed = dataTransfer.getData(DISPLAY_IMAGE_DRAG_TYPE)?.trim();
  if (typed && looksLikeImageUrl(typed)) return typed;
  const fromPlain = parsePlainPayload(dataTransfer.getData("text/plain") ?? "");
  return fromPlain?.url ?? null;
}

export function getGalleryReorderKey(dataTransfer: DataTransfer): string {
  const typed = dataTransfer.getData(GALLERY_REORDER_DRAG_TYPE)?.trim();
  if (typed && /^\d+:\d+$/.test(typed)) return typed;
  const plain = (dataTransfer.getData("text/plain") ?? "").trim();
  if (/^\d+:\d+$/.test(plain)) return plain;
  const fromPlain = parsePlainPayload(plain);
  const reorder = fromPlain?.reorder?.trim();
  return reorder && /^\d+:\d+$/.test(reorder) ? reorder : "";
}

export function isDisplayImageDrag(dataTransfer: DataTransfer): boolean {
  const types = Array.from(dataTransfer.types ?? []);
  return (
    types.includes(DISPLAY_IMAGE_DRAG_TYPE) ||
    types.includes("text/plain") ||
    types.includes("text/uri-list")
  );
}

/** Put `url` first in the product listing gallery, without duplicating it. */
export function promoteDisplayImage(images: string[] | null | undefined, url: string): string[] {
  const trimmed = url.trim();
  const rest = (images ?? [])
    .map((item) => item?.trim())
    .filter((item): item is string => !!item && item !== trimmed);
  return trimmed ? [trimmed, ...rest] : rest;
}

/** Drop the current listing hero and restore the first print photo without losing other listing URLs. */
export function clearDisplayImage(
  images: string[] | null | undefined,
  fallbackUrl: string | null | undefined,
): string[] {
  const list = (images ?? []).map((item) => item?.trim()).filter((item): item is string => !!item);
  if (!list.length) return [];
  return promoteDisplayImage(list.slice(1), fallbackUrl?.trim() || "");
}
