/** Recommended dimensions shown in admin upload fields (matches storefront CSS). */

export type ImageSpec = {
  /** Human-readable aspect ratio, e.g. "4 : 5" */
  ratio: string;
  /** Recommended pixel size, e.g. "1200 × 1500 px" */
  recommendedPx: string;
  /** Optional cropping / placement note */
  notes?: string;
};

export const ADMIN_IMAGE_SPECS = {
  /** Homepage hero carousel (full-width, object-cover) */
  homepageBanner: {
    ratio: "Wide (~2 : 1)",
    recommendedPx: "1920 × 900 px",
    notes: "Keep the subject center-left — headline text sits on the left.",
  },
  /** Shop by age, gift cards, product cards */
  portraitCard: {
    ratio: "4 : 5",
    recommendedPx: "1200 × 1500 px",
    notes: "Portrait photos crop cleanly in rounded cards.",
  },
  brandStory: {
    ratio: "4 : 5",
    recommendedPx: "1000 × 1250 px",
    notes: "Tall image beside brand story copy on the homepage.",
  },
  blogCover: {
    ratio: "16 : 9",
    recommendedPx: "1600 × 900 px",
    notes: "Blog grid and article header.",
  },
  subscribeHero: {
    ratio: "4 : 5 (portrait)",
    recommendedPx: "1000 × 1200 px",
    notes: "Right column hero on the subscription page.",
  },
  subscribePlan: {
    ratio: "5 : 3",
    recommendedPx: "1000 × 600 px",
    notes: "Banner on top of each plan card.",
  },
  categoryMenu: {
    ratio: "1 : 1",
    recommendedPx: "400 × 400 px",
    notes: "Shown small in the shop menu dropdown.",
  },
  productGallery: {
    ratio: "4 : 5",
    recommendedPx: "1200 × 1500 px",
    notes: "Main product gallery and shop cards — lifestyle/product photos only.",
  },
  printSwatch: {
    ratio: "1 : 1",
    recommendedPx: "200 × 200 px",
    notes: "Print preview for the colour picker on the product page. Not used in the main gallery.",
  },
  logoSquare: {
    ratio: "1 : 1",
    recommendedPx: "512 × 512 px",
    notes: "PNG with transparent background (footer).",
  },
  logoHorizontal: {
    ratio: "~3 : 1",
    recommendedPx: "600 × 200 px",
    notes: "Wide PNG with transparent background (navbar).",
  },
} as const satisfies Record<string, ImageSpec>;

export type AdminImageSpecKey = keyof typeof ADMIN_IMAGE_SPECS;

export function formatImageSpec(spec: ImageSpec): string {
  const base = `Recommended ${spec.recommendedPx} (${spec.ratio} aspect ratio)`;
  return spec.notes ? `${base}. ${spec.notes}` : base;
}

export function imageSpecLines(spec: ImageSpec): { ratio: string; size: string; notes?: string } {
  return {
    ratio: spec.ratio,
    size: spec.recommendedPx,
    notes: spec.notes,
  };
}

/** Video specs (not uploaded via image endpoint) */
export const ADMIN_VIDEO_SPECS = {
  fabricStory: {
    ratio: "16 : 9 (landscape)",
    recommendedPx: "1920 × 1080 px",
    notes: "MP4 link; fills the left panel of the fabric story block.",
  },
  watchShopReel: {
    ratio: "3 : 4 (portrait)",
    recommendedPx: "720 × 960 px",
    notes: "MP4 link; vertical reel tiles on the homepage.",
  },
} as const satisfies Record<string, ImageSpec>;

export function formatVideoSpec(spec: ImageSpec): string {
  return formatImageSpec(spec);
}
