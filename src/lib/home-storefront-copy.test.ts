import { describe, expect, it } from "vitest";
import { certifiedFabricLabel } from "@/data/products";
import { buildHomeMarqueeOffers, homeStorefrontTrustCopy } from "./home-storefront-copy";

describe("homeStorefrontTrustCopy", () => {
  it("returns aligned fabric and shipping labels for the homepage", () => {
    const trust = homeStorefrontTrustCopy(2500);
    expect(trust.heroShippingLabel).toBe("Free shipping ₹2,500+");
    expect(trust.fabricStoryFeatureTitle).toBe(trust.fabricLabel);
    expect(trust.trustBadgeDesc).toContain(trust.fabricLabel.toLowerCase());
  });
});

describe("buildHomeMarqueeOffers", () => {
  it("uses settings-driven free shipping threshold in the marquee", () => {
    const offers = buildHomeMarqueeOffers(2500);
    expect(offers.find((o) => o.t.includes("shipping"))?.t).toBe("Free shipping over ₹2,500");
    expect(offers.some((o) => o.t.includes("1499"))).toBe(false);
  });

  it("includes the certified fabric line from catalog config", () => {
    const offers = buildHomeMarqueeOffers(2500);
    expect(offers.some((o) => o.t === certifiedFabricLabel())).toBe(true);
  });
});
