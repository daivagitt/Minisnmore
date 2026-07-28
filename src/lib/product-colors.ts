export const MAX_COLOR_IMAGES = 10;

export type ProductColorEntry = {
  name: string;
  hex?: string | null;
  /** Print preview for the storefront colour picker — not used in the main product gallery. */
  swatchUrl?: string;
  /** Optional extra lifestyle photos for this colour (admin manual upload). */
  imageUrls?: string[];
};

export type StorefrontColor = {
  name: string;
  hex: string;
  swatchUrl?: string;
  imageUrl?: string;
  imageUrls?: string[];
};

const COLOR_PALETTE: Record<string, string> = {
  cream: "#f7e3d5",
  ivory: "#f2eee8",
  white: "#ffffff",
  pearl: "#efe5d3",
  cloud: "#f2eee8",
  sand: "#d9bfa6",
  beige: "#e5d4bf",
  taupe: "#c7a991",
  tan: "#c18459",
  brick: "#b0693c",
  terracotta: "#b0693c",
  peach: "#f4c5a6",
  blush: "#f1cfc6",
  rose: "#d49a92",
  petal: "#e7c3c0",
  pink: "#f1bdc3",
  coral: "#e89a82",
  butter: "#e7c76b",
  yellow: "#e7c76b",
  mustard: "#c89e3b",
  sage: "#a8c0a0",
  "sage green": "#a8c0a0",
  mint: "#bcd9c4",
  olive: "#8a8c5a",
  sky: "#bcd5e3",
  blue: "#7aa6c4",
  navy: "#2c3e58",
  grey: "#b8b1a7",
  gray: "#b8b1a7",
  charcoal: "#3a3a3a",
  black: "#1a1a1a",
  lavender: "#c9b8d7",
  purple: "#9c7fb5",
};

export function hexForColorName(name: string): string {
  const key = name.trim().toLowerCase();
  if (key.startsWith("print ")) return "#d9bfa6";
  return COLOR_PALETTE[key] ?? "#d9bfa6";
}

function colorKey(name: string): string {
  return name.trim().toLowerCase();
}

function uniqueUrls(urls: (string | null | undefined)[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of urls) {
    const url = raw?.trim();
    if (!url || seen.has(url)) continue;
    seen.add(url);
    out.push(url);
  }
  return out;
}

function parseColorImageUrls(o: {
  imageUrl?: unknown;
  imageUrls?: unknown;
}): string[] {
  const fromArray = Array.isArray(o.imageUrls)
    ? o.imageUrls.map((u) => String(u ?? "").trim()).filter(Boolean)
    : [];
  const legacy =
    o.imageUrl == null || o.imageUrl === "" ? null : String(o.imageUrl).trim();
  return uniqueUrls(legacy ? [legacy, ...fromArray] : fromArray).slice(0, MAX_COLOR_IMAGES);
}

/** Lifestyle / main-gallery images stored on a colour (excludes swatch). */
export function colorImageUrls(
  entry: Pick<ProductColorEntry, "imageUrls"> & { imageUrl?: string | null },
): string[] {
  return parseColorImageUrls(entry);
}

export function swatchUrlForEntry(
  entry: Pick<ProductColorEntry, "swatchUrl">,
): string | null {
  const swatch = entry.swatchUrl?.trim();
  return swatch || null;
}

export function parseProductColors(raw: unknown): ProductColorEntry[] {
  if (!Array.isArray(raw) || raw.length === 0) return [];
  const out: ProductColorEntry[] = [];
  const seen = new Set<string>();
  for (const item of raw) {
    let entry: ProductColorEntry | null = null;
    if (typeof item === "string") {
      const name = item.trim();
      if (name) entry = { name };
    } else if (item && typeof item === "object" && "name" in (item as object)) {
      const o = item as {
        name?: unknown;
        hex?: unknown;
        swatchUrl?: unknown;
        imageUrl?: unknown;
        imageUrls?: unknown;
      };
      const name = String(o.name ?? "").trim();
      if (!name) continue;
      const swatchUrl = o.swatchUrl == null || o.swatchUrl === "" ? undefined : String(o.swatchUrl).trim();
      const imageUrls = parseColorImageUrls(o);
      entry = {
        name,
        hex: o.hex == null || o.hex === "" ? null : String(o.hex),
        swatchUrl: swatchUrl || undefined,
        imageUrls: imageUrls.length ? imageUrls : undefined,
      };
    } else if (item != null && item !== "") {
      entry = { name: String(item).trim() };
    }
    if (!entry?.name) continue;
    const key = colorKey(entry.name);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(entry);
  }
  return out;
}

export function serializeProductColors(entries: ProductColorEntry[]): unknown[] {
  const clean = entries
    .map((e) => {
      const swatchUrl = e.swatchUrl?.trim() || undefined;
      const galleryUrls = colorImageUrls(e).filter((url) => url !== swatchUrl);
      return {
        name: e.name.trim(),
        hex: e.hex?.trim() || undefined,
        swatchUrl,
        galleryUrls,
      };
    })
    .filter((e) => e.name);
  const hasRich = clean.some((e) => e.swatchUrl || e.galleryUrls.length || e.hex);
  if (!hasRich) return clean.map((e) => e.name);
  return clean.map((e) => {
    const row: Record<string, string | string[]> = { name: e.name };
    if (e.hex) row.hex = e.hex;
    if (e.swatchUrl) row.swatchUrl = e.swatchUrl;
    if (e.galleryUrls.length === 1) row.imageUrl = e.galleryUrls[0];
    else if (e.galleryUrls.length > 1) row.imageUrls = e.galleryUrls;
    return row;
  });
}

export function colorNames(entries: ProductColorEntry[] | null | undefined): string[] {
  return parseProductColors(entries).map((e) => e.name).filter(Boolean);
}

export function ensureColorEntries(raw: unknown): ProductColorEntry[] {
  return normalizeColorEntries(parseProductColors(raw));
}

export function mergeColorNames(
  existing: ProductColorEntry[] | null | undefined,
  names: string[],
): ProductColorEntry[] {
  const byKey = new Map((existing ?? []).map((e) => [colorKey(e.name), e]));
  const out: ProductColorEntry[] = [];
  const seen = new Set<string>();
  for (const raw of names) {
    const name = raw.trim();
    if (!name) continue;
    const key = colorKey(name);
    if (seen.has(key)) continue;
    seen.add(key);
    const prev = byKey.get(key);
    out.push(prev ? { ...prev, name } : { name });
  }
  return out;
}

export function normalizeColorEntries(entries: ProductColorEntry[]): ProductColorEntry[] {
  return entries.map((entry) => {
    const explicitSwatch = entry.swatchUrl?.trim();
    const gallery = parseColorImageUrls(entry);
    if (explicitSwatch) {
      const galleryOnly = gallery.filter((url) => url !== explicitSwatch);
      return {
        ...entry,
        swatchUrl: explicitSwatch,
        imageUrls: galleryOnly.length ? galleryOnly : undefined,
      };
    }
    return {
      ...entry,
      swatchUrl: undefined,
      imageUrls: gallery.length ? gallery : undefined,
    };
  });
}

export function galleryUrlsForStorefrontColor(color: StorefrontColor | undefined): string[] {
  if (!color) return [];
  if (color.imageUrls?.length) return [...color.imageUrls];
  if (color.imageUrl?.trim()) return [color.imageUrl.trim()];
  return [];
}

/** All unique per-colour main-gallery URLs in colour order. */
export function collectColorGalleryImages(colors: unknown): string[] {
  const out: string[] = [];
  const seen = new Set<string>();
  for (const entry of parseProductColors(colors)) {
    for (const url of colorImageUrls(entry)) {
      if (!seen.has(url)) {
        seen.add(url);
        out.push(url);
      }
    }
  }
  return out;
}

/** Main product images: explicit gallery first, otherwise per-colour galleries. */
export function resolveProductImages(
  images: string[] | null | undefined,
  colors: unknown,
): string[] {
  const explicit = (images ?? []).map((url) => url?.trim()).filter((url): url is string => !!url);
  if (explicit.length) return explicit;
  return collectColorGalleryImages(colors);
}

export function normalizeStorefrontColors(raw: unknown): StorefrontColor[] {
  const parsed = parseProductColors(raw);
  if (!parsed.length) return [{ name: "Natural", hex: hexForColorName("Natural") }];
  return parsed.map((e) => {
    const storedUrls = parseColorImageUrls(e);
    const explicitSwatch = e.swatchUrl?.trim();
    const galleryUrls = explicitSwatch
      ? storedUrls.filter((url) => url !== explicitSwatch)
      : storedUrls;
    return {
      name: e.name,
      hex: e.hex?.trim() || hexForColorName(e.name),
      swatchUrl: explicitSwatch,
      imageUrl: galleryUrls[0],
      imageUrls: galleryUrls.length ? galleryUrls : undefined,
    };
  });
}
