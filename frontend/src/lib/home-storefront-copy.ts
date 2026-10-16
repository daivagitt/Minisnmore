import { BadgePercent, ShieldCheck, Truck, Wind, type LucideIcon } from "lucide-react";
import { certifiedFabricLabel, PRIMARY_STOREFRONT_FABRIC } from "@/data/products";
import { freeShippingStorefrontLabels } from "@/lib/commerce";

export type HomeMarqueeOffer = { icon: LucideIcon; t: string };

export type HomeStorefrontTrustCopy = {
  fabricLabel: string;
  heroShippingLabel: string;
  trustBadgeTitle: string;
  trustBadgeDesc: string;
  fabricStoryFeatureTitle: string;
};

export function homeStorefrontTrustCopy(freeShippingMinInr: number): HomeStorefrontTrustCopy {
  const fabricLabel = certifiedFabricLabel();
  const shipping = freeShippingStorefrontLabels(freeShippingMinInr);
  return {
    fabricLabel,
    heroShippingLabel: shipping.hero,
    trustBadgeTitle: PRIMARY_STOREFRONT_FABRIC,
    trustBadgeDesc: `${fabricLabel.toLowerCase()}, free from harmful chemicals`,
    fabricStoryFeatureTitle: fabricLabel,
  };
}

export function buildHomeMarqueeOffers(freeShippingMinInr: number): HomeMarqueeOffer[] {
  const trust = homeStorefrontTrustCopy(freeShippingMinInr);
  const shipping = freeShippingStorefrontLabels(freeShippingMinInr);
  return [
    { icon: BadgePercent, t: "WELCOME10 · 10% off your first order" },
    { icon: Truck, t: shipping.marquee },
    { icon: ShieldCheck, t: trust.fabricLabel },
    { icon: Wind, t: "Comfort First Design — soft, breathable fabrics" },
  ];
}
