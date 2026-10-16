import { describe, expect, it } from "vitest";
import { HOME_HERO_BANNER_LIMIT } from "@/lib/home-section-limit";
import { mapBannerRowsToHomeBanners } from "./home-banners";

describe("mapBannerRowsToHomeBanners", () => {
  it("sorts by sort_order, drops inactive or id-less rows, and passes eyebrow", () => {
    const mapped = mapBannerRowsToHomeBanners([
      {
        id: "2",
        title: "Second",
        subtitle: null,
        image_url: "/b.jpg",
        link_url: null,
        cta_label: null,
        eyebrow: "Just in",
        trust_fabric_label: "GOTS cotton",
        trust_shipping_label: null,
        trust_dyes_label: "Plant dyes",
        sort_order: 2,
        active: true,
      },
      {
        id: "1",
        title: "First",
        subtitle: null,
        image_url: "/a.jpg",
        link_url: null,
        cta_label: null,
        eyebrow: null,
        trust_fabric_label: null,
        trust_shipping_label: null,
        trust_dyes_label: null,
        sort_order: 1,
        active: true,
      },
      {
        title: "No id",
        subtitle: null,
        image_url: "/x.jpg",
        link_url: null,
        cta_label: null,
        eyebrow: null,
        trust_fabric_label: null,
        trust_shipping_label: null,
        trust_dyes_label: null,
        sort_order: 0,
        active: true,
      },
      {
        id: "3",
        title: "Off",
        subtitle: null,
        image_url: "/c.jpg",
        link_url: null,
        cta_label: null,
        eyebrow: null,
        trust_fabric_label: null,
        trust_shipping_label: null,
        trust_dyes_label: null,
        sort_order: 3,
        active: false,
      },
    ]);

    expect(mapped.map((b) => b.id)).toEqual(["1", "2"]);
    expect(mapped[0]?.eyebrow).toBeNull();
    expect(mapped[1]?.eyebrow).toBe("Just in");
    expect(mapped[1]?.trust_fabric_label).toBe("GOTS cotton");
    expect(mapped[1]?.trust_dyes_label).toBe("Plant dyes");
  });

  it(`returns at most ${HOME_HERO_BANNER_LIMIT} banners`, () => {
    const rows = Array.from({ length: 8 }, (_, i) => ({
      id: String(i + 1),
      title: `Banner ${i}`,
      subtitle: null,
      image_url: "/x.jpg",
      link_url: null,
      cta_label: null,
      eyebrow: null,
      trust_fabric_label: null,
      trust_shipping_label: null,
      trust_dyes_label: null,
      sort_order: i,
      active: true,
    }));
    expect(mapBannerRowsToHomeBanners(rows)).toHaveLength(HOME_HERO_BANNER_LIMIT);
  });
});
