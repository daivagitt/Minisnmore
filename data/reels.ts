import reelRomper from "@/assets/videos/reel-romper.mp4";
import reelDress from "@/assets/videos/reel-dress.mp4";
import reelCardigan from "@/assets/videos/reel-cardigan.mp4";
import reelDayset from "@/assets/videos/reel-dayset.mp4";
import reelInfantGirl from "@/assets/videos/reel-infant-girl.mp4";

export type WatchReel = { video: string; slug: string };

/** Theme reels — slugs must match live catalog products (Admin → Products). */
export const watchReels: WatchReel[] = [
  { video: reelRomper, slug: "baby-front-open-frock" },
  { video: reelDress, slug: "book-style-jabla-short-set" },
  { video: reelCardigan, slug: "side-button-tshirt-short-set" },
  { video: reelDayset, slug: "baby-swaddle" },
  { video: reelInfantGirl, slug: "socks" },
];

export function getReelForSlug(slug: string | undefined): WatchReel | undefined {
  if (!slug) return undefined;
  return watchReels.find((r) => r.slug === slug);
}
