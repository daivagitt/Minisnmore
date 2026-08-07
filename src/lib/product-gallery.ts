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

/** Neutral PDP / listing backdrop (Carter's #F6F6F6, A Toddler Thing off-white tiles). */
export const PRODUCT_IMAGE_SURFACE = "#F6F6F6";

/** Tailwind classes — hero caps height; width follows each image's aspect ratio. */
export const PRODUCT_GALLERY_HERO_MAX_H =
  "max-h-[min(92vw,28rem)] sm:max-h-[min(80vw,32rem)] lg:max-h-[min(42vw,36rem)]";

export const PRODUCT_GALLERY_QUICKVIEW_MAX_H =
  "max-h-[min(72vw,20rem)] md:max-h-[min(50vh,24rem)]";

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
};

/** Hero + thumbnails for the selected colour, falling back to the product gallery. */
export function galleryForSelectedColor(
  product: ProductGallerySource,
  selectedColor?: string | null,
): { image: string; gallery: string[] } {
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

  // Selected print exists but has no photos — don't show other prints' images.
  if (selectedColor?.trim() && colorEntry && hasPerColorGalleries(product.colors)) {
    return { image: "", gallery: [] };
  }

  // Per-print products never fall back to the mixed product gallery.
  if (hasPerColorGalleries(product.colors)) {
    return { image: "", gallery: [] };
  }

  const baseGallery = product.gallery ?? [];
  const fallbackImage = product.image?.trim() || baseGallery[0] || "";
  const hero = fallbackImage;
  const gallery = uniqueImages(...baseGallery.filter((url) => url !== hero));
  return { image: hero, gallery: gallery.length ? gallery : [] };
}
