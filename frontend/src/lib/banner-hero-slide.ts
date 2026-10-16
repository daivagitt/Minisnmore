import { resolveBannerEyebrow } from "@/lib/banner-eyebrow";
import {
  resolveBannerTrustLabels,
  type BannerTrustDefaults,
  type BannerTrustLabels,
} from "@/lib/banner-trust";

export type CmsHeroBannerInput = {
  id: string;
  title: string;
  subtitle: string | null;
  link_url: string | null;
  cta_label: string | null;
  eyebrow: string | null;
  trust_fabric_label?: string | null;
  trust_shipping_label?: string | null;
  trust_dyes_label?: string | null;
  image_url: string;
};

export type MappedHeroSlide = {
  id: string;
  eyebrow: string;
  title: string;
  subtitle: string;
  cta: string;
  link: string;
  image: string;
  align: "left";
  imageFocus: string;
} & BannerTrustLabels;

const DEFAULT_HERO_TITLE = "Minis today, memories forever.";
const DEFAULT_HERO_SUBTITLE =
  "Hand-finished organic clothing for your little one's softest beginnings.";

export function bannerToHeroSlide(
  banner: CmsHeroBannerInput,
  slideIndex: number,
  imageFocus: string,
  trustDefaults: BannerTrustDefaults,
): MappedHeroSlide {
  return {
    id: banner.id,
    eyebrow: resolveBannerEyebrow(banner.eyebrow, slideIndex),
    title: banner.title || DEFAULT_HERO_TITLE,
    subtitle: banner.subtitle || DEFAULT_HERO_SUBTITLE,
    cta: banner.cta_label || "Shop now",
    link: banner.link_url || "/shop",
    image: banner.image_url,
    align: "left",
    imageFocus,
    ...resolveBannerTrustLabels(banner, trustDefaults),
  };
}
