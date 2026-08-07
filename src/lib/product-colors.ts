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

/** Swatch thumbnail for a colour: explicit print swatch, else its own first photo. */
export function swatchImageForColor(
  entry: { swatchUrl?: string; imageUrl?: string; imageUrls?: string[] },
): string | undefined {
  return entry.swatchUrl?.trim() || entry.imageUrl?.trim() || entry.imageUrls?.[0]?.trim() || undefined;
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

/** Keep only lifestyle URLs whose filename matches this print name (when possible). */
export function filterGalleryUrlsForPrint(
  colorName: string,
  urls: string[],
  opts?: { strict?: boolean },
): string[] {
  if (!urls.length) return [];
  const key = colorName.trim().toLowerCase();
  if (!key) return urls;

  const tokens = key
    .replace(/,/g, " ")
    .split(/[\s&]+/)
    .map((t) => t.trim())
    .filter((t) => t.length >= 3 || /^\d+$/.test(t));

  const printSpecific = urls.filter((url) => {
    const fn = url.toLowerCase();
    if (tokens.length && tokens.every((t) => fn.includes(t))) return true;
    if (key.length <= 6 && fn.includes(key.replace(/\s+/g, ""))) return true;
    const combo = key.match(/^combo\s*(\d+)$/);
    if (combo && new RegExp(`combo[\\s-]*${combo[1]}`).test(fn)) return true;
    return false;
  });

  if (printSpecific.length) return printSpecific;
  return opts?.strict ? [] : urls;
}

/** True when any URL in the set matches at least one print name on the product. */
export function sharedGalleryHasPrintSpecificFilenames(
  colors: StorefrontColor[] | undefined,
  urls: string[],
): boolean {
  if (!colors?.length || !urls.length) return false;
  return urls.some((url) =>
    colors.some((c) => filterGalleryUrlsForPrint(c.name, [url], { strict: true }).length > 0),
  );
}

/** True when the same lifestyle URL appears on more than one print. */
export function colorsHaveOverlappingGalleries(colors: StorefrontColor[] | undefined): boolean {
  if (!colors?.length) return false;
  const seen = new Set<string>();
  for (const color of colors) {
    for (const url of galleryUrlsForStorefrontColor(color)) {
      if (seen.has(url)) return true;
      seen.add(url);
    }
  }
  return false;
}

/** True when every print has its own gallery with zero URL overlap (strict per-print PDP). */
export function printsHaveDistinctGalleries(colors: StorefrontColor[] | undefined): boolean {
  if (!colors?.length) return false;
  const galleries = colors
    .map((c) => galleryUrlsForStorefrontColor(c))
    .filter((urls) => urls.length > 0);
  if (galleries.length < 2) return false;
  const seen = new Set<string>();
  for (const urls of galleries) {
    for (const url of urls) {
      if (seen.has(url)) return false;
      seen.add(url);
    }
  }
  return true;
}

/** True when two or more colours share the exact same lifestyle URL list. */
export function colorsShareIdenticalGalleries(colors: StorefrontColor[] | undefined): boolean {
  if (!colors?.length) return false;
  const sets = colors.map((c) => [...galleryUrlsForStorefrontColor(c)].sort().join("\0"));
  const nonEmpty = sets.filter((s) => s.length > 0);
  return nonEmpty.length > 1 && new Set(nonEmpty).size === 1;
}

/** True when any colour has lifestyle gallery URLs (not swatch-only). */
export function hasPerPrintLifestyleGalleries(colors: unknown): boolean {
  return parseProductColors(colors).some((entry) => colorImageUrls(entry).length > 0);
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

/** First listing hero URL — one swatch max when no lifestyle gallery exists. */
export function collectStorefrontCardImages(colors: unknown): string[] {
  const galleries = collectColorGalleryImages(colors);
  if (galleries.length) return galleries;
  for (const entry of parseProductColors(colors)) {
    const swatch = swatchUrlForEntry(entry);
    if (swatch) return [swatch];
  }
  return [];
}

/** Build API `colorImagesJson` map from per-print gallery URLs (excludes swatches). */
export function buildColorImagesJson(colors: unknown): string | null {
  const map: Record<string, string[]> = {};
  for (const entry of parseProductColors(colors)) {
    const swatch = entry.swatchUrl?.trim();
    const urls = colorImageUrls(entry).filter((url) => url !== swatch);
    if (urls.length) map[entry.name] = urls;
  }
  return Object.keys(map).length ? JSON.stringify(map) : null;
}

/** Parse API `colorImagesJson` — map of colour name → gallery URLs. */
export function parseColorImagesMap(raw: unknown): Map<string, string[]> {
  const out = new Map<string, string[]>();
  if (raw == null) return out;

  let parsed: unknown = raw;
  if (typeof raw === "string") {
    const trimmed = raw.trim();
    if (!trimmed) return out;
    try {
      parsed = JSON.parse(trimmed);
    } catch {
      return out;
    }
  }

  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return out;
  for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
    const name = key.trim();
    if (!name) continue;
    const urls = Array.isArray(value)
      ? value.map((u) => String(u ?? "").trim()).filter(Boolean)
      : [];
    if (urls.length) out.set(name, uniqueUrls(urls));
  }
  return out;
}

function galleryMapLookup(map: Map<string, string[]>, colorName: string): string[] {
  const exact = map.get(colorName);
  if (exact?.length) return exact;
  const key = colorKey(colorName);
  for (const [name, urls] of map) {
    if (colorKey(name) === key && urls.length) return urls;
  }
  return [];
}

/**
 * Merge `colorImagesJson` gallery URLs into colour entries (storefront/API parity with backend
 * {@code ProductColorImagesSupport.applyGalleryMapToColorsJson}).
 */
export function mergeColorImagesIntoColors(
  colors: ProductColorEntry[] | unknown,
  colorImagesJson: unknown,
): ProductColorEntry[] {
  const entries = normalizeColorEntries(parseProductColors(colors));
  const galleryMap = parseColorImagesMap(colorImagesJson);
  if (!galleryMap.size) return entries;

  if (!entries.length) {
    return Array.from(galleryMap.entries()).map(([name, imageUrls]) => ({
      name,
      imageUrls,
    }));
  }

  return entries.map((entry) => {
    const urls = galleryMapLookup(galleryMap, entry.name);
    if (!urls.length) return entry;
    const swatchUrl = entry.swatchUrl?.trim();
    const galleryOnly = swatchUrl ? urls.filter((url) => url !== swatchUrl) : urls;
    return {
      ...entry,
      imageUrls: galleryOnly.length ? galleryOnly : urls,
    };
  }).map((entry, i, merged) => {
    if (colorImageUrls(entry).length > 0) return entry;
    const galleryKeys = Array.from(galleryMap.keys());
    if (galleryKeys.length !== merged.length) return entry;
    const anyMatched = merged.some((e) => colorImageUrls(e).length > 0);
    if (anyMatched) return entry;
    const key = galleryKeys[i];
    const urls = galleryMap.get(key) ?? [];
    const swatchUrl = entry.swatchUrl?.trim();
    const galleryOnly = swatchUrl ? urls.filter((url) => url !== swatchUrl) : urls;
    return {
      ...entry,
      name: key,
      imageUrls: galleryOnly.length ? galleryOnly : urls,
    };
  });
}

/** True when colours carry per-print media (lifestyle gallery and/or print swatch). */
export function hasPerColorGalleries(colors: unknown): boolean {
  return parseProductColors(colors).some(
    (entry) => colorImageUrls(entry).length > 0 || Boolean(swatchUrlForEntry(entry)),
  );
}

/** Card/listing hero: first photo from the first colour that has a gallery or print swatch. */
export function storefrontCardImageFromColors(colors: unknown): string | undefined {
  for (const entry of parseProductColors(colors)) {
    const urls = colorImageUrls(entry);
    if (urls[0]) return urls[0];
    const swatch = swatchUrlForEntry(entry);
    if (swatch) return swatch;
  }
  return undefined;
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

/**
 * Storefront listing gallery. When prints have dedicated galleries, avoid mixing every print
 * into `product.gallery` — the PDP switches galleries per selected print.
 */
export function resolveStorefrontProductImages(
  images: string[] | null | undefined,
  colors: unknown,
): { image: string; gallery: string[] } {
  const perPrint = hasPerColorGalleries(colors);
  const explicit = (images ?? []).map((url) => url?.trim()).filter((url): url is string => !!url);

  if (perPrint) {
    const cardImage = storefrontCardImageFromColors(colors) ?? explicit[0] ?? "";
    return { image: cardImage, gallery: [] };
  }

  const resolved = resolveProductImages(images, colors);
  const [hero, ...rest] = resolved;
  return { image: hero ?? "", gallery: rest };
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
