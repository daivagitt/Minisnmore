import reelRomper from "@/assets/videos/reel-romper.mp4";
import reelDress from "@/assets/videos/reel-dress.mp4";
import reelCardigan from "@/assets/videos/reel-cardigan.mp4";
import reelDayset from "@/assets/videos/reel-dayset.mp4";
import reelInfantGirl from "@/assets/videos/reel-infant-girl.mp4";

export type WatchReel = { video: string; slug: string };

export const watchReels: WatchReel[] = [
  { video: reelRomper, slug: "terracotta-petal-romper" },
  { video: reelDress, slug: "rose-blossom-dress" },
  { video: reelCardigan, slug: "pearl-knit-cardigan" },
  { video: reelDayset, slug: "buttercup-day-set" },
  { video: reelInfantGirl, slug: "meadow-sleepsuit" },
];

export function getReelForSlug(slug: string | undefined): WatchReel | undefined {
  if (!slug) return undefined;
  return watchReels.find((r) => r.slug === slug);
}