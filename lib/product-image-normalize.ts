import { PRODUCT_IMAGE_SURFACE } from "@/lib/product-gallery";

export const PRODUCT_GALLERY_EXPORT_PX = 1200;
export const PRODUCT_SWATCH_EXPORT_PX = 400;

/** Scale down to fit within maxPx on the longest edge; never upscale. */
export function fitWithinMaxDimension(
  naturalWidth: number,
  naturalHeight: number,
  maxPx: number,
): { drawW: number; drawH: number } {
  if (!naturalWidth || !naturalHeight) {
    throw new Error("Image has no dimensions.");
  }
  const scale = Math.min(maxPx / naturalWidth, maxPx / naturalHeight, 1);
  return {
    drawW: Math.round(naturalWidth * scale),
    drawH: Math.round(naturalHeight * scale),
  };
}

async function loadImageFromFile(file: File): Promise<HTMLImageElement> {
  const url = URL.createObjectURL(file);
  try {
    const img = new Image();
    img.decoding = "async";
    await new Promise<void>((resolve, reject) => {
      img.onload = () => resolve();
      img.onerror = () => reject(new Error("Could not read image file."));
      img.src = url;
    });
    return img;
  } finally {
    URL.revokeObjectURL(url);
  }
}

async function exportSquareCanvas(
  img: HTMLImageElement,
  size: number,
  baseName: string,
  suffix: string,
): Promise<File> {
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Could not process image.");

  ctx.fillStyle = PRODUCT_IMAGE_SURFACE;
  ctx.fillRect(0, 0, size, size);

  const nw = img.naturalWidth;
  const nh = img.naturalHeight;
  const { drawW, drawH } = fitWithinMaxDimension(nw, nh, size);
  const x = (size - drawW) / 2;
  const y = (size - drawH) / 2;
  ctx.drawImage(img, x, y, drawW, drawH);

  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (b) => (b ? resolve(b) : reject(new Error("Could not export image."))),
      "image/jpeg",
      0.92,
    );
  });

  const stem = baseName.replace(/\.[^.]+$/, "").replace(/[^\w.-]+/g, "-").slice(0, 80) || "product";
  return new File([blob], `${stem}-${suffix}.jpg`, { type: "image/jpeg" });
}

async function exportNaturalCanvas(
  img: HTMLImageElement,
  maxPx: number,
  baseName: string,
  suffix: string,
): Promise<File> {
  const nw = img.naturalWidth;
  const nh = img.naturalHeight;
  const { drawW, drawH } = fitWithinMaxDimension(nw, nh, maxPx);

  const canvas = document.createElement("canvas");
  canvas.width = drawW;
  canvas.height = drawH;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Could not process image.");

  ctx.fillStyle = PRODUCT_IMAGE_SURFACE;
  ctx.fillRect(0, 0, drawW, drawH);
  ctx.drawImage(img, 0, 0, drawW, drawH);

  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (b) => (b ? resolve(b) : reject(new Error("Could not export image."))),
      "image/jpeg",
      0.92,
    );
  });

  const stem = baseName.replace(/\.[^.]+$/, "").replace(/[^\w.-]+/g, "-").slice(0, 80) || "product";
  return new File([blob], `${stem}-${suffix}.jpg`, { type: "image/jpeg" });
}

/** Downscale to max 1200px on the longest edge — preserves aspect ratio (no square letterboxing). */
export async function normalizeProductGalleryImage(file: File): Promise<File> {
  const img = await loadImageFromFile(file);
  return exportNaturalCanvas(img, PRODUCT_GALLERY_EXPORT_PX, file.name, "gallery");
}

/** Square swatch for the colour picker (small, crisp). */
export async function normalizeProductSwatchImage(file: File): Promise<File> {
  const img = await loadImageFromFile(file);
  return exportSquareCanvas(img, PRODUCT_SWATCH_EXPORT_PX, file.name, "swatch");
}

export type ProductImageUploadRole = "gallery" | "swatch";

export async function prepareProductImageForUpload(file: File, role: ProductImageUploadRole): Promise<File> {
  return role === "swatch" ? normalizeProductSwatchImage(file) : normalizeProductGalleryImage(file);
}
