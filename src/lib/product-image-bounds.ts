import type { CSSProperties } from "react";
import { PRODUCT_IMAGE_SURFACE } from "@/lib/product-gallery";

/** Catalogue backdrop (#F6F6F6) and common near-white JPEG padding. */
const SURFACE_RGB = { r: 246, g: 246, b: 246 };
const BG_DISTANCE_SQ = 40 * 40;
/** Keep crop tight — padding reintroduces the white gaps we strip. */
const CONTENT_PAD_FRACTION = 0;
const SAMPLE_MAX_PX = 256;

export type ImageContentBounds = {
  x: number;
  y: number;
  width: number;
  height: number;
  naturalWidth: number;
  naturalHeight: number;
};

export function isProductImageBackground(r: number, g: number, b: number, a: number): boolean {
  if (a < 128) return true;
  // Pure / cool catalogue whites (#F6F6F6 and JPEG padding)
  if (r >= 228 && g >= 228 && b >= 228) return true;
  const dr = r - SURFACE_RGB.r;
  const dg = g - SURFACE_RGB.g;
  const db = b - SURFACE_RGB.b;
  if (dr * dr + dg * dg + db * db <= BG_DISTANCE_SQ) return true;
  // Warm cream / beige studio backdrops (common in product photography)
  if (r >= 200 && g >= 190 && b >= 170 && r >= g && g >= b - 8) return true;
  return false;
}

function fullBounds(naturalWidth: number, naturalHeight: number): ImageContentBounds {
  return {
    x: 0,
    y: 0,
    width: naturalWidth,
    height: naturalHeight,
    naturalWidth,
    naturalHeight,
  };
}

function applyContentPadding(bounds: ImageContentBounds): ImageContentBounds {
  const { naturalWidth, naturalHeight } = bounds;
  const padX = Math.round(naturalWidth * CONTENT_PAD_FRACTION);
  const padY = Math.round(naturalHeight * CONTENT_PAD_FRACTION);
  const x = Math.max(0, bounds.x - padX);
  const y = Math.max(0, bounds.y - padY);
  const width = Math.min(naturalWidth - x, bounds.width + 2 * padX);
  const height = Math.min(naturalHeight - y, bounds.height + 2 * padY);
  return { x, y, width, height, naturalWidth, naturalHeight };
}

/** Bounding box of non-background pixels in a downsampled mask. */
export function boundsFromBackgroundMask(
  sampleWidth: number,
  sampleHeight: number,
  isBackground: (x: number, y: number) => boolean,
  naturalWidth: number,
  naturalHeight: number,
): ImageContentBounds {
  let minX = sampleWidth;
  let minY = sampleHeight;
  let maxX = -1;
  let maxY = -1;

  for (let y = 0; y < sampleHeight; y++) {
    for (let x = 0; x < sampleWidth; x++) {
      if (!isBackground(x, y)) {
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
      }
    }
  }

  if (maxX < minX || maxY < minY) {
    return fullBounds(naturalWidth, naturalHeight);
  }

  const scaleX = naturalWidth / sampleWidth;
  const scaleY = naturalHeight / sampleHeight;
  const raw: ImageContentBounds = {
    x: Math.floor(minX * scaleX),
    y: Math.floor(minY * scaleY),
    width: Math.ceil((maxX - minX + 1) * scaleX),
    height: Math.ceil((maxY - minY + 1) * scaleY),
    naturalWidth,
    naturalHeight,
  };
  return applyContentPadding(raw);
}

export function contentAspect(bounds: ImageContentBounds): number {
  if (!bounds.width || !bounds.height) return 1;
  return bounds.width / bounds.height;
}

/** True when bounds cover the full file — use object-cover fill instead of zoom fit. */
export function isFullImageBounds(bounds: ImageContentBounds): boolean {
  if (!bounds.naturalWidth || !bounds.naturalHeight) return true;
  const coverW = bounds.width / bounds.naturalWidth;
  const coverH = bounds.height / bounds.naturalHeight;
  return bounds.x <= 1 && bounds.y <= 1 && coverW >= 0.98 && coverH >= 0.98;
}

/**
 * Position + scale a full image so its content region fits the frame without distortion.
 * Uses uniform scale (contain) when frame aspect differs from content aspect.
 */
export function imageFitStyles(bounds: ImageContentBounds, frameAspect?: number): CSSProperties {
  const { x, y, width, height, naturalWidth, naturalHeight } = bounds;
  if (!width || !height || !naturalWidth || !naturalHeight) return {};

  const contentAspect = width / height;
  const targetAspect =
    frameAspect && frameAspect > 0 ? frameAspect : contentAspect;

  /** Image width as a fraction of the container width (container width = 1). */
  const imageWidthFraction = Math.min(
    naturalWidth / width,
    naturalWidth / (height * targetAspect),
  );

  const contentDisplayW = imageWidthFraction * (width / naturalWidth);
  const contentDisplayH = imageWidthFraction * (height / naturalWidth) * targetAspect;

  const widthPct = imageWidthFraction * 100;
  const heightPct = imageWidthFraction * (naturalHeight / naturalWidth) * targetAspect * 100;
  const leftPct =
    ((1 - contentDisplayW) / 2 - (x / naturalWidth) * imageWidthFraction) * 100;
  const topPct =
    ((1 - contentDisplayH) / 2 - (y / naturalWidth) * imageWidthFraction * targetAspect) * 100;

  return {
    position: "absolute",
    maxWidth: "none",
    width: `${widthPct}%`,
    height: `${heightPct}%`,
    left: `${leftPct}%`,
    top: `${topPct}%`,
  };
}

export function detectContentBoundsFromImageData(
  data: Uint8ClampedArray,
  sampleWidth: number,
  sampleHeight: number,
  naturalWidth: number,
  naturalHeight: number,
): ImageContentBounds {
  const isBackground = (x: number, y: number) => {
    const i = (y * sampleWidth + x) * 4;
    return isProductImageBackground(data[i], data[i + 1], data[i + 2], data[i + 3]);
  };
  return boundsFromBackgroundMask(sampleWidth, sampleHeight, isBackground, naturalWidth, naturalHeight);
}

/** Scan loaded pixels for catalogue white-space and return the content crop. */
export function detectContentBoundsFromImage(img: HTMLImageElement): ImageContentBounds {
  const naturalWidth = img.naturalWidth;
  const naturalHeight = img.naturalHeight;
  if (!naturalWidth || !naturalHeight) return fullBounds(0, 0);

  const scale = Math.min(1, SAMPLE_MAX_PX / Math.max(naturalWidth, naturalHeight));
  const sampleWidth = Math.max(1, Math.round(naturalWidth * scale));
  const sampleHeight = Math.max(1, Math.round(naturalHeight * scale));

  const canvas = document.createElement("canvas");
  canvas.width = sampleWidth;
  canvas.height = sampleHeight;
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  if (!ctx) return fullBounds(naturalWidth, naturalHeight);

  ctx.drawImage(img, 0, 0, sampleWidth, sampleHeight);
  try {
    const { data } = ctx.getImageData(0, 0, sampleWidth, sampleHeight);
    return detectContentBoundsFromImageData(data, sampleWidth, sampleHeight, naturalWidth, naturalHeight);
  } catch {
    return fullBounds(naturalWidth, naturalHeight);
  }
}

export function gallerySurfaceStyle(): CSSProperties {
  return { backgroundColor: PRODUCT_IMAGE_SURFACE };
}
