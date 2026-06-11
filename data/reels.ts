import reelRomper from "@/assets/reel-romper.mp4.asset.json";
import reelDress from "@/assets/reel-dress.mp4.asset.json";
import reelCardigan from "@/assets/reel-cardigan.mp4.asset.json";
import reelDayset from "@/assets/reel-dayset.mp4.asset.json";
import reelInfantGirl from "@/assets/reel-infant-girl.mp4.asset.json";

export type WatchReel = { video: string; slug: string };

export const watchReels: WatchReel[] = [
  { video: reelRomper.url, slug: "terracotta-petal-romper" },
  { video: reelDress.url, slug: "rose-blossom-dress" },
  { video: reelCardigan.url, slug: "pearl-knit-cardigan" },
  { video: reelDayset.url, slug: "buttercup-day-set" },
  { video: reelInfantGirl.url, slug: "meadow-sleepsuit" },
];

export function getReelForSlug(slug: string | undefined): WatchReel | undefined {
  if (!slug) return undefined;
  return watchReels.find((r) => r.slug === slug);
}