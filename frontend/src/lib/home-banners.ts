import type { BannerRow } from "@/lib/backend-data";
import { HOME_HERO_BANNER_LIMIT } from "@/lib/home-section-limit";

export type HomeBanner = {
  id: string;
  title: string;
  subtitle: string | null;
  image_url: string | null;
  link_url: string | null;
  cta_label: string | null;
  eyebrow: string | null;
  trust_fabric_label: string | null;
  trust_shipping_label: string | null;
  trust_dyes_label: string | null;
};

/** Storefront hero carousel banners — sorted, active only, max six slides. */
export function mapBannerRowsToHomeBanners(rows: BannerRow[]): HomeBanner[] {
  return [...rows]
    .sort((a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0))
    .filter((b): b is BannerRow & { id: string } => b.active && !!b.id)
    .slice(0, HOME_HERO_BANNER_LIMIT)
    .map((b) => ({
      id: b.id,
      title: b.title,
      subtitle: b.subtitle,
      image_url: b.image_url,
      link_url: b.link_url,
      cta_label: b.cta_label,
      eyebrow: b.eyebrow ?? null,
      trust_fabric_label: b.trust_fabric_label ?? null,
      trust_shipping_label: b.trust_shipping_label ?? null,
      trust_dyes_label: b.trust_dyes_label ?? null,
    }));
}
