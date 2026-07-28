import { galleryUrlsForStorefrontColor, type StorefrontColor } from "@/lib/product-colors";

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
  const colorGallery = galleryUrlsForStorefrontColor(findColor(product.colors, selectedColor));
  if (colorGallery.length) {
    const [hero, ...rest] = colorGallery;
    return { image: hero, gallery: rest };
  }

  const baseGallery = product.gallery ?? [];
  const fallbackImage = product.image?.trim() || baseGallery[0] || "";
  const hero = fallbackImage;
  const gallery = uniqueImages(...baseGallery.filter((url) => url !== hero));
  return { image: hero, gallery: gallery.length ? gallery : [] };
}
