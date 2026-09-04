import {
  colorsHaveOverlappingGalleries,
  colorsShareIdenticalGalleries,
  filterGalleryUrlsForPrint,
  galleryUrlsForStorefrontColor,
  hasPerColorGalleries,
  printsHaveDistinctGalleries,
  sharedGalleryHasPrintSpecificFilenames,
  type StorefrontColor,
} from "@/lib/product-colors";
import { findVariant, type ProductVariant } from "@/lib/product-variants";

/** Neutral PDP / listing backdrop (Carter's #F6F6F6, A Toddler Thing off-white tiles). */
export const PRODUCT_IMAGE_SURFACE = "#F6F6F6";

/** Tailwind classes — hero caps height; width follows each image's aspect ratio. */
export const PRODUCT_GALLERY_HERO_MAX_H =
  "max-h-[min(92vw,28rem)] sm:max-h-[min(80vw,32rem)] lg:max-h-[min(42vw,36rem)]";

/** Width shrinks for tall portraits once height hits the cap (--hero-ar set on the frame). */
export const PRODUCT_GALLERY_HERO_FRAME =
  "max-w-full w-[calc(min(92vw,28rem)*var(--hero-ar,1))] sm:w-[calc(min(80vw,32rem)*var(--hero-ar,1))] lg:w-[calc(min(42vw,36rem)*var(--hero-ar,1))]";

export const PRODUCT_GALLERY_QUICKVIEW_MAX_H =
  "max-h-[min(72vw,20rem)] md:max-h-[min(50vh,24rem)]";

/** Landscape (wider than tall), square/unknown, then portrait. */
const PORTRAIT_MAX_ASPECT = 0.98;
const LANDSCAPE_MIN_ASPECT = 1.02;

export function galleryOrientationRank(aspect: number | undefined): number {
  if (!aspect || aspect <= 0) return 1;
  if (aspect > LANDSCAPE_MIN_ASPECT) return 0;
  if (aspect < PORTRAIT_MAX_ASPECT) return 2;
  return 1;
}

/**
 * Group landscapes first and portraits last while preserving relative order within each orientation.
 */
export function sortGalleryByLeadingOrientation(
  urls: string[],
  aspectFor: (url: string) => number | undefined,
): string[] {
  if (urls.length <= 1) return urls;
  const indexed = urls.map((url, order) => ({ url, order }));
  indexed.sort((a, b) => {
    const rankA = galleryOrientationRank(aspectFor(a.url));
    const rankB = galleryOrientationRank(aspectFor(b.url));
    if (rankA !== rankB) return rankA - rankB;
    return a.order - b.order;
  });
  return indexed.map((entry) => entry.url);
}

/** @deprecated Use sortGalleryByLeadingOrientation — kept for existing imports/tests. */
export function sortGalleryPortraitFirst(
  urls: string[],
  aspectFor: (url: string) => number | undefined,
): string[] {
  return sortGalleryByLeadingOrientation(urls, aspectFor);
}

function uniqueImages(...sources: (string | undefined | null)[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const src of sources) {
    const s = src?.trim();
    if (!s || seen.has(s)) continue;
    seen.add(s);
    out.push(s);
  }
  return out;
}

function findColor(
  colors: StorefrontColor[] | undefined,
  selectedColor?: string | null,
): StorefrontColor | undefined {
  if (!selectedColor?.trim() || !colors?.length) return undefined;
  const key = selectedColor.trim().toLowerCase();
  return colors.find((c) => c.name.trim().toLowerCase() === key);
}

export type ProductGallerySource = {
  image: string;
  gallery?: string[];
  colors?: StorefrontColor[];
  variants?: ProductVariant[];
};

function extraGalleryUrls(
  product: ProductGallerySource,
  selectedColor: string | null | undefined,
  alreadyShown: string[],
): string[] {
  if (alreadyShown.length > 0) {
    return [];
  }
  const colorKey = selectedColor?.trim().toLowerCase();
  const sameColor = (product.variants ?? []).filter(
    (variant) => !colorKey || (variant.color ?? "").trim().toLowerCase() === colorKey,
  );
  return uniqueSameColorDonorPhotos(sameColor);
}

function uniqueSameColorDonorPhotos(variants: ProductVariant[]): string[] {
  let donor: string[] | null = null;
  let donorKey: string | null = null;
  for (const variant of variants) {
    const photos = uniqueImages(variant.imageUrl, ...(variant.imageUrls ?? []));
    if (!photos.length) continue;
    const key = [...photos].sort().join("\n");
    if (donorKey == null) {
      donor = photos;
      donorKey = key;
    } else if (donorKey !== key) {
      return [];
    }
  }
  return donor ?? [];
}

function fallbackProductHero(product: ProductGallerySource): string {
  const swatches = new Set(
    (product.colors ?? [])
      .map((color) => color.swatchUrl?.trim())
      .filter((url): url is string => Boolean(url)),
  );
  return uniqueImages(product.image, ...(product.gallery ?? [])).find((url) => !swatches.has(url)) ?? "";
}

/** Hero + thumbnails for the selected colour (and size, when SKUs have their own photos). */
export function galleryForSelectedColor(
  product: ProductGallerySource,
  selectedColor?: string | null,
  selectedSize?: string | null,
  selectedSku?: string | null,
): { image: string; gallery: string[] } {
  const variant = findVariant(
    { variants: product.variants, sizes: [], colors: [], price: 0 },
    { size: selectedSize, color: selectedColor, sku: selectedSku },
  );
  const sameColor = (product.variants ?? []).filter(
    (row) =>
      !selectedColor?.trim() || (row.color ?? "").trim().toLowerCase() === selectedColor.trim().toLowerCase(),
  );
  const otherSizeFronts = new Set(
    sameColor
      .filter((row) => !variant || row.sku.trim().toLowerCase() !== variant.sku.trim().toLowerCase())
      .map((row) => row.imageUrl?.trim())
      .filter((url): url is string => Boolean(url)),
  );
  const variantUrls = uniqueImages(variant?.imageUrl, ...(variant?.imageUrls ?? [])).filter(
    (url) => url === variant?.imageUrl?.trim() || !otherSizeFronts.has(url),
  );
  if (variantUrls.length) {
    const [hero, ...rest] = variantUrls;
    return { image: hero, gallery: rest };
  }

  const fallback = extraGalleryUrls(product, selectedColor, []);
  if (fallback.length) {
    const [hero, ...rest] = fallback;
    return { image: hero, gallery: rest };
  }

  const colorHasVariantPhotos = sameColor.some(
    (row) => uniqueImages(row.imageUrl, ...(row.imageUrls ?? [])).length > 0,
  );
  if (variant && colorHasVariantPhotos) {
    return { image: fallbackProductHero(product), gallery: [] };
  }

  const colorEntry = findColor(product.colors, selectedColor);

  // Per-print catalogs with zero overlap: show ONLY the selected print's photos.
  if (colorEntry && printsHaveDistinctGalleries(product.colors)) {
    const urls = galleryUrlsForStorefrontColor(colorEntry);
    if (urls.length) {
      const [hero, ...rest] = urls;
      return { image: hero, gallery: rest };
    }
    return { image: "", gallery: [] };
  }

  let colorGallery = galleryUrlsForStorefrontColor(colorEntry);
  const sharedGalleries = colorsShareIdenticalGalleries(product.colors);
  const overlappingGalleries = colorsHaveOverlappingGalleries(product.colors);

  if (colorEntry && colorGallery.length && (sharedGalleries || overlappingGalleries)) {
    const raw = colorGallery;
    const filtered = filterGalleryUrlsForPrint(colorEntry.name, raw, {
      strict: sharedGalleries || overlappingGalleries,
    });
    if (filtered.length) {
      colorGallery = filtered;
    } else if (!sharedGalleryHasPrintSpecificFilenames(product.colors, raw)) {
      colorGallery = raw;
    } else {
      colorGallery = [];
    }
  }

  if (colorGallery.length) {
    const [hero, ...rest] = colorGallery;
    return { image: hero, gallery: rest };
  }

  // Selected print exists but has no photos. Prefer the product hero over a blank PDP.
  if (selectedColor?.trim() && colorEntry && hasPerColorGalleries(product.colors)) {
    return { image: fallbackProductHero(product), gallery: [] };
  }

  // Per-print products without selected-color photos still need a stable product hero.
  if (hasPerColorGalleries(product.colors)) {
    return { image: fallbackProductHero(product), gallery: [] };
  }

  const baseGallery = product.gallery ?? [];
  const fallbackImage = product.image?.trim() || baseGallery[0] || "";
  const hero = fallbackImage;
  const gallery = uniqueImages(...baseGallery.filter((url) => url !== hero));
  return { image: hero, gallery: gallery.length ? gallery : [] };
}
