import { createFileRoute, Link } from "@tanstack/react-router";
import { motion, useScroll, useTransform } from "framer-motion";
import { AnimatePresence, type MotionValue } from "framer-motion";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowRight, Leaf, Sparkles, Gift, Star, Truck, BadgePercent,
  Heart, ShieldCheck, Quote, ChevronRight, PackageCheck, Flame, Tag, Play, ShoppingBag,
  ChevronLeft, Download,
  Smile, Feather, Wind, Droplets, RefreshCw,
} from "lucide-react";
import { products, reviews } from "@/data/products";
import { useProducts, useProductsLoaded } from "@/hooks/useProducts";
import { ProductGridSkeleton } from "@/components/ProductGridSkeleton";
import { WatchShopSkeleton } from "@/components/WatchShopSkeleton";
import { ProductCard } from "@/components/ProductCard";
import { SectionDivider } from "@/components/SectionDivider";
import { FloatingDecor } from "@/components/FloatingDecor";
import { useBanners } from "@/hooks/useBanners";
import { useHomeSections } from "@/hooks/useHomeSections";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { resolveHomeByAgeCards } from "@/lib/home-by-age";
import {
  HOME_SECTION_PRODUCT_LIMIT,
  resolveHomeSectionProducts,
  sectionProductSlugs,
} from "@/lib/home-products";
import { parseWatchReels, resolveWatchShopReels } from "@/lib/watch-reels";
import { parseFabricStoryVideo } from "@/lib/asset-url";
import { productMatchesCategory } from "@/lib/shop-filters";
import { toDirectDownloadUrl } from "@/lib/utils";
import heroBaby from "@/assets/hero-smiling-baby.jpg";
import lifestyle from "@/assets/lifestyle-mother.webp";
import heroComfort from "@/assets/hero-comfort-carousel.webp";
import newborn from "@/assets/cat-newborn.webp";
import infant from "@/assets/cat-infant.webp";
import toddler from "@/assets/cat-toddler.webp";
import giftBox from "@/assets/gift-box.jpg";
import heroInfant from "@/assets/hero-infant-organic.jpg";
import heroFounders from "@/assets/hero-founders-atelier.jpg";
import fabricStoryVideo from "@/assets/videos/fabric-story-children.mp4";
import { watchReels } from "@/data/reels";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Mini's n More — Organic baby clothing, hand-finished with love" },
      { name: "description", content: "Soft, organic, beautifully made baby clothing for the first 24 months. 100% certified organic cotton, hand-loomed knits and feather-soft muslins." },
      { property: "og:title", content: "Mini's n More — Minis today, memories forever" },
      { property: "og:description", content: "Hand-finished organic baby clothing with thoughtful gifting and heirloom quality." },
      { property: "og:image", content: heroBaby },
    ],
    links: [
      { rel: "preload", as: "image", href: heroBaby, fetchPriority: "high" },
      { rel: "preload", as: "image", href: heroInfant },
      { rel: "preload", as: "image", href: lifestyle },
      { rel: "preload", as: "image", href: heroFounders },
    ],
  }),
  component: Home,
});

const defaultByAgeCats = [
  { label: "Newborn", note: "0–3M", img: newborn, to: "/shop" as const, search: { age: "0-3m" as const } },
  { label: "Infant", note: "3–6M", img: infant, to: "/shop" as const, search: { age: "3-6m" as const } },
  { label: "Crawlers", note: "6–12M", img: lifestyle, to: "/shop" as const, search: { age: "6-12m" as const } },
  { label: "Toddlers", note: "12–18M", img: toddler, to: "/shop" as const, search: { age: "12-18m" as const } },
  { label: "Kidoo", note: "18–24M", img: giftBox, to: "/shop" as const, search: { age: "18-24m" as const } },
];

const offers = [
  { icon: BadgePercent, t: "WELCOME10 · 10% off your first order" },
  { icon: Truck, t: "Free shipping over ₹1499" },
  { icon: ShieldCheck, t: "100% Certified Organic Cotton" },
  { icon: Wind, t: "Comfort First Design — soft, breathable fabrics" },
];

type HeroSlide = {
  id: string;
  eyebrow: string;
  title: string;
  subtitle: string;
  cta: string;
  link: string;
  image: string;
  align?: "left" | "right";
};

const defaultHeroSlides: HeroSlide[] = [
  {
    id: "slide-premium",
    eyebrow: "Premium · Thoughtfully designed",
    title: "Minis today, memories forever.",
    subtitle:
      "Organic clothing for your little one's softest beginnings — feather-light cottons coloured with azo-free dyes, made to be loved and passed on.",
    cta: "Shop the new edit",
    link: "/shop",
    image: heroBaby,
    align: "left",
  },
  {
    id: "slide-organic",
    eyebrow: "Made for tiny ones",
    title: "Softness made for first cuddles.",
    subtitle:
      "Whisper-soft organic cotton in azo-free dyed hues — designed for the gentlest skin on earth.",
    cta: "Shop infant edit",
    link: "/shop",
    image: heroInfant,
    align: "left",
  },
  {
    id: "slide-comfort",
    eyebrow: "Comfort first design",
    title: "All-day comfort for growing minis.",
    subtitle:
      "Crafted with soft, breathable fabrics and child-friendly stitching — built around the way little ones move, play and rest.",
    cta: "Shop the collection",
    link: "/shop",
    image: heroComfort,
    align: "left",
  },
  {
    id: "slide-founders",
    eyebrow: "A note from the founders",
    title: "Crafted with care. Made to last.",
    subtitle:
      "Premium children's clothing built on certified organic cotton, azo-free dyes and child-safe finishing — thoughtfully designed for comfort, quality and the way little ones really live.",
    cta: "Read our story",
    link: "/about",
    image: heroFounders,
    align: "left",
  },
];

function Home() {
  const heroRef = useRef<HTMLDivElement | null>(null);
  const { scrollYProgress: heroProgress } = useScroll({ target: heroRef, offset: ["start start", "end start"] });
  const heroY = useTransform(heroProgress, [0, 1], ["0%", "12%"]);
  const heroScale = useTransform(heroProgress, [0, 1], [1, 1.02]);

  const { banners } = useBanners();
  const { isEnabled, titleFor, subtitleFor, byKey } = useHomeSections();
  const settings = useSiteSettings();
  const liveProducts = useProducts();
  const productsLoaded = useProductsLoaded();

  const byAgeCats = useMemo(() => {
    const payload = byKey.get("by_age")?.payload;
    return resolveHomeByAgeCards(
      payload && typeof payload === "object" ? (payload as Record<string, unknown>) : undefined,
      defaultByAgeCats,
    );
  }, [byKey]);


  const heroSlides: HeroSlide[] = useMemo(() => {
    const fromCms: HeroSlide[] = banners
      .filter((b) => b.image_url)
      .map((b, i) => ({
        id: b.id,
        eyebrow: i === 0 ? "Featured" : "Just in",
        title: b.title || "Minis today, memories forever.",
        subtitle: b.subtitle || "Hand-finished organic clothing for your little one's softest beginnings.",
        cta: b.cta_label || "Shop now",
        link: b.link_url || "/shop",
        image: b.image_url as string,
        align: "left",
      }));
    if (fromCms.length > 0) {
      return fromCms.slice(0, HOME_SECTION_PRODUCT_LIMIT);
    }
    return defaultHeroSlides;
  }, [banners]);

  const newArrivals = useMemo(
    () =>
      resolveHomeSectionProducts(
        liveProducts,
        sectionProductSlugs(byKey.get("new_arrivals")?.payload as Record<string, unknown>),
        (ps) => ps.filter((p) => p.isNew),
      ),
    [liveProducts, byKey],
  );
  const trending = useMemo(
    () =>
      resolveHomeSectionProducts(
        liveProducts,
        sectionProductSlugs(byKey.get("trending")?.payload as Record<string, unknown>),
        (ps) => [...ps].sort((a, b) => b.reviewCount - a.reviewCount),
      ),
    [liveProducts, byKey],
  );
  const gifts = useMemo(
    () =>
      resolveHomeSectionProducts(
        liveProducts,
        sectionProductSlugs(byKey.get("gifts")?.payload as Record<string, unknown>),
        (ps) => ps.filter((p) => productMatchesCategory(p, "gift-sets")),
      ),
    [liveProducts, byKey],
  );

  return (
    <div className="space-y-12 md:space-y-16">
      {/* HERO — responsive carousel */}
      {isEnabled("hero") && (
        <section className="px-3 md:px-6">
          <HeroCarousel
            slides={heroSlides}
            heroRef={heroRef}
            heroY={heroY}
            heroScale={heroScale}
            ecatalogueUrl={toDirectDownloadUrl(settings.ecatalogue_url) || "/e-catalogue.pdf"}
            ecatalogueLabel={settings.ecatalogue_label || "New Catalogue · Soft Bloom"}
          />
        </section>
      )}

      {/* OFFERS marquee */}
      <section className="px-3 md:px-6">
        <div className="relative h-12 overflow-hidden rounded-full bg-foreground text-background">
          <motion.div className="absolute inset-y-0 left-0 flex items-center gap-10 whitespace-nowrap pl-6"
            initial={{ x: 0 }} animate={{ x: "-50%" }} transition={{ duration: 40, repeat: Infinity, ease: "linear" }}>
            {Array.from({ length: 4 }).flatMap((_, k) => offers.map((o, i) => (
              <span key={`${k}-${i}`} className="inline-flex items-center gap-2 text-xs uppercase tracking-[0.22em]">
                <o.icon className="h-3.5 w-3.5 text-primary-foreground" /> {o.t}
              </span>
            )))}
          </motion.div>
        </div>
      </section>

      {/* SHOP BY AGE — 4 chapter cards */}
      {isEnabled("by_age") && (
      <section className="px-4 md:px-6">
        <div className="flex items-end justify-between mb-6">
          <div>
            <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground">Shop by age</div>
            <h2 className="font-display text-2xl md:text-4xl mt-2">{titleFor("by_age", "Made for every little chapter")}</h2>
            {subtitleFor("by_age") && <p className="text-sm text-muted-foreground mt-1">{subtitleFor("by_age")}</p>}
          </div>
          <Link to="/shop" className="hidden md:inline story-link text-sm">View all</Link>
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 md:gap-5">
          {byAgeCats.map((c, i) => (
            <motion.div key={c.label}
              initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
              transition={{ duration: 0.6, delay: i * 0.08 }} className="group">
              <Link to={c.to} search={c.search} className="block glass rounded-3xl p-2.5 md:p-3 hover-lift">
                <div className="relative aspect-square overflow-hidden rounded-2xl bg-secondary/40">
                  <img src={c.img} alt={c.label} loading="lazy" className="img-zoom h-full w-full object-cover" />
                  <div className="absolute inset-x-3 bottom-3 glass-strong rounded-2xl p-3 md:p-4 flex items-center justify-between">
                    <div>
                      <div className="font-display text-base md:text-lg">{c.label}</div>
                      <div className="text-[11px] md:text-xs text-muted-foreground">{c.note}</div>
                    </div>
                    <ArrowRight className="h-4 w-4" />
                  </div>
                </div>
              </Link>
            </motion.div>
          ))}
        </div>
      </section>
      )}

      {/* FEATURED — best sellers / trending / new */}
      {isEnabled("best_sellers") && (
        <section className="px-4 md:px-6">
          <FeaturedTabs />
        </section>
      )}

      {isEnabled("new_arrivals") && (
        <div className="pt-6 md:pt-10">
          <SectionDivider label="Just in · New Arrivals" compact />
        </div>
      )}

      {/* NEW ARRIVALS */}
      {isEnabled("new_arrivals") && (
      <section className="px-4 md:px-6">
        <div className="flex items-end justify-between mb-5">
          <div>
            <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground inline-flex items-center gap-2">
              <Sparkles className="h-3.5 w-3.5 text-primary" /> {subtitleFor("new_arrivals", "Fresh off the loom")}
            </div>
            <h2 className="font-display text-2xl md:text-3xl mt-2">{titleFor("new_arrivals", "New arrivals")}</h2>
          </div>
          <Link to="/shop" className="story-link text-sm hidden md:inline">See all new</Link>
        </div>
        {productsLoaded ? (
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 md:gap-5">
            {(newArrivals.length ? newArrivals : liveProducts.slice(0, HOME_SECTION_PRODUCT_LIMIT))
              .slice(0, HOME_SECTION_PRODUCT_LIMIT)
              .map((p, i) => (
              <ProductCard key={p.id} product={p} index={i} />
            ))}
          </div>
        ) : (
          <ProductGridSkeleton />
        )}
      </section>
      )}

      {/* TRENDING */}
      {isEnabled("trending") && (
      <section className="px-4 md:px-6">
        <div className="flex items-end justify-between mb-5">
          <div>
            <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground inline-flex items-center gap-2">
              <Flame className="h-3.5 w-3.5 text-primary" /> {subtitleFor("trending", "Loved most this week")}
            </div>
            <h2 className="font-display text-2xl md:text-3xl mt-2">{titleFor("trending", "Trending now")}</h2>
          </div>
          <Link to="/trending" className="story-link text-sm hidden md:inline">Shop trending</Link>
        </div>
        {productsLoaded ? (
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 md:gap-5">
            {trending
              .slice(0, HOME_SECTION_PRODUCT_LIMIT)
              .map((p, i) => (
                <ProductCard key={p.id} product={p} index={i} />
              ))}
          </div>
        ) : (
          <ProductGridSkeleton />
        )}
      </section>
      )}

      {/* GIFTS */}
      {isEnabled("gifts") && (
      <section className="px-4 md:px-6">
        <div className="flex items-end justify-between mb-5">
          <div>
            <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground inline-flex items-center gap-2">
              <Gift className="h-3.5 w-3.5 text-primary" /> {subtitleFor("gifts", "Thoughtful bundles for every occasion")}
            </div>
            <h2 className="font-display text-2xl md:text-3xl mt-2">{titleFor("gifts", "Gift with love")}</h2>
          </div>
          <Link to="/gift" className="story-link text-sm hidden md:inline">Build a gift</Link>
        </div>
        {productsLoaded ? (
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 md:gap-5">
            {gifts
              .slice(0, HOME_SECTION_PRODUCT_LIMIT)
              .map((p, i) => (
                <ProductCard key={p.id} product={p} index={i} />
              ))}
          </div>
        ) : (
          <ProductGridSkeleton />
        )}
      </section>
      )}

      {/* FABRIC STORY — video reel + copy */}
      {isEnabled("fabric_story") && (
        <FabricStorySection
          payload={byKey.get("fabric_story")?.payload as Record<string, unknown> | undefined}
          title={titleFor("fabric_story", "Fabric story")}
        />
      )}

      {/* TRUST BADGES */}
      <TrustBadges />

      {/* BRAND STORY — vision/mission */}
      {isEnabled("brand_story") && (
      <section className="px-4 md:px-6">
        <div className="grid md:grid-cols-2 gap-4 md:gap-6 items-stretch">
          <motion.div initial={{ opacity: 0, scale: 0.96 }} whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true, margin: "-80px" }} transition={{ duration: 1, ease: [0.2, 0.8, 0.2, 1] }}
            className="rounded-[2rem] overflow-hidden relative aspect-4/5 md:aspect-auto md:min-h-[560px] group">
            <img src={lifestyle} alt="Mother and baby" className="img-zoom absolute inset-0 h-full w-full object-cover" loading="lazy" />
            <FloatingDecor count={6} variant="petals" className="opacity-80" />
            <div className="absolute bottom-4 left-4 right-4 glass-strong rounded-2xl p-4">
              <div className="text-[10px] uppercase tracking-[0.28em] text-primary">A note from the founders</div>
              <p className="font-script text-2xl text-foreground mt-1">— with love, Mini's n More</p>
            </div>
          </motion.div>
          <motion.div initial={{ opacity: 0, y: 40 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-80px" }} transition={{ duration: 0.8, delay: 0.1 }}
            className="glass-strong rounded-[2rem] p-8 md:p-14 flex flex-col justify-center">
            <div className="text-xs uppercase tracking-[0.28em] text-primary">Our story</div>
            <h2 className="font-display text-3xl md:text-5xl mt-3 text-balance">Slowly stitched, <span className="font-script text-primary">softly</span> held.</h2>
            <p className="mt-4 text-foreground/80 leading-relaxed">
              At Mini&apos;s n More, we craft thoughtfully designed baby essentials that bring together comfort, quality and conscious care — built on certified organic cotton, azo-free dyes and child-safe finishing for the most delicate skin.
            </p>
            <div className="flex flex-col gap-4 mt-6">
              <div className="glass rounded-2xl p-5 w-full">
                <div className="text-[10px] uppercase tracking-[0.22em] text-primary font-semibold">Vision</div>
                <p className="text-sm mt-2 leading-relaxed text-foreground/85">
                  &ldquo;Minis n More&rdquo; envisions to be the world&apos;s most trusted baby essentials brand, nurturing every child&apos;s early years with products that are safe, gentle, and thoughtfully designed for modern families.
                </p>
              </div>
              <div className="glass rounded-2xl p-5 w-full">
                <div className="text-[10px] uppercase tracking-[0.22em] text-primary font-semibold">Mission</div>
                <p className="text-sm mt-2 leading-relaxed text-foreground/85">
                  At &ldquo;Minis n More&rdquo; we deliver beautifully crafted baby essentials that parents can trust—designed with uncompromising attention to safety, softness, and everyday practicality. We design intuitive, high-quality, and beautifully functional baby products that solve real parenting challenges. We create innovative, sustainable, and scientifically-backed baby products that meet the highest global standards of safety and quality, empowering parents across every continent to nurture their children with confidence and peace of mind.
                </p>
              </div>
            </div>
          </motion.div>
        </div>
      </section>
      )}

      {/* MOM REVIEWS — running marquee */}
      {isEnabled("reviews") && (
      <section className="px-3 md:px-6">
        <div className="flex items-end justify-between mb-5 px-1">
          <div>
            <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground inline-flex items-center gap-2">
              <Heart className="h-3.5 w-3.5 text-primary" /> Real moms, real stories
            </div>
            <h2 className="font-display text-3xl md:text-4xl mt-2">Loved by little ones <span className="font-script text-primary">& their mamas</span></h2>
          </div>
        </div>
        <div className="relative overflow-hidden">
          <div className="absolute inset-y-0 left-0 w-20 bg-linear-to-r from-background to-transparent z-10 pointer-events-none" />
          <div className="absolute inset-y-0 right-0 w-20 bg-linear-to-l from-background to-transparent z-10 pointer-events-none" />
          <motion.div className="flex gap-4 w-max"
            initial={{ x: 0 }} animate={{ x: "-50%" }}
            transition={{ duration: 50, repeat: Infinity, ease: "linear" }}>
            {[...reviews, ...reviews, ...reviews, ...reviews].map((r, i) => (
              <div key={i} className="glass-strong rounded-3xl p-5 w-[300px] md:w-[360px] shrink-0">
                <Quote className="h-5 w-5 text-primary/60" />
                <div className="flex gap-0.5 mt-2">
                  {Array.from({ length: 5 }).map((_, k) => (
                    <Star key={k} className={`h-3.5 w-3.5 ${k < r.rating ? "fill-primary text-primary" : "text-muted-foreground"}`} />
                  ))}
                </div>
                <div className="font-display text-lg mt-2">{r.title}</div>
                <p className="text-sm text-foreground/75 mt-1.5 leading-relaxed line-clamp-4">{r.body}</p>
                <div className="mt-3 flex items-center gap-2">
                  <img src={r.photo} alt="" className="h-8 w-8 rounded-full object-cover" />
                  <div className="text-xs">
                    <div className="font-medium">{r.author}</div>
                    <div className="text-muted-foreground">{r.date}</div>
                  </div>
                </div>
              </div>
            ))}
          </motion.div>
        </div>
      </section>
      )}

    </div>
  );
}

/* ----------------------------- Featured Tabs ----------------------------- */

const featuredTabs = [
  { key: "best", label: "Best sellers", icon: Star, hint: "Forever favourites" },
  { key: "trending", label: "Trending", icon: Flame, hint: "Loved this week" },
  { key: "new", label: "New", icon: Sparkles, hint: "Just arrived" },
  { key: "back", label: "Back in stock", icon: PackageCheck, hint: "You asked, we restocked" },
  { key: "first", label: "First-order 10%", icon: Tag, hint: "Use WELCOME10" },
] as const;

function FeaturedTabs() {
  const liveProducts = useProducts();
  const productsLoaded = useProductsLoaded();
  const { byKey } = useHomeSections();
  const [tab, setTab] = useState<typeof featuredTabs[number]["key"]>("best");
  const list = useMemo(() => {
    switch (tab) {
      case "best":
        return resolveHomeSectionProducts(
          liveProducts,
          sectionProductSlugs(byKey.get("best_sellers")?.payload as Record<string, unknown>),
          (ps) => ps.filter((p) => p.isBestseller),
        );
      case "trending":
        return resolveHomeSectionProducts(
          liveProducts,
          sectionProductSlugs(byKey.get("trending")?.payload as Record<string, unknown>),
          (ps) => [...ps].sort((a, b) => b.reviewCount - a.reviewCount),
        );
      case "new":
        return resolveHomeSectionProducts(
          liveProducts,
          sectionProductSlugs(byKey.get("new_arrivals")?.payload as Record<string, unknown>),
          (ps) => ps.filter((p) => p.isNew),
        );
      case "back":
        return resolveHomeSectionProducts(
          liveProducts,
          sectionProductSlugs(byKey.get("featured")?.payload as Record<string, unknown>),
          (ps) => [...ps].reverse(),
        );
      case "first":
        return resolveHomeSectionProducts(
          liveProducts,
          [],
          (ps) => ps.filter((p) => !!p.compareAt),
        );
      default:
        return liveProducts.slice(0, HOME_SECTION_PRODUCT_LIMIT);
    }
  }, [tab, liveProducts, byKey]);
  const active = featuredTabs.find((t) => t.key === tab)!;
  return (
    <div>
      <div className="flex items-end justify-between mb-4 flex-wrap gap-3">
        <div>
          <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground">Featured edit</div>
          <h2 className="font-display text-2xl md:text-4xl mt-2">{active.label}</h2>
          <div className="text-sm text-muted-foreground mt-1">{active.hint}</div>
        </div>
        <Link to="/shop" className="story-link text-sm hidden md:inline">Explore all</Link>
      </div>
      <div className="flex gap-2 flex-wrap mb-5">
        {featuredTabs.map((t) => {
          const TIcon = t.icon;
          const on = t.key === tab;
          return (
            <button key={t.key} onClick={() => setTab(t.key)}
              className={`inline-flex items-center gap-1.5 rounded-full px-4 py-2 text-sm transition border ${on ? "bg-primary text-primary-foreground border-primary shadow-md" : "bg-background/40 border-border hover:bg-muted/60"}`}>
              <TIcon className="h-3.5 w-3.5" /> {t.label}
            </button>
          );
        })}
      </div>
      {productsLoaded ? (
        <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 md:gap-5">
          {list.slice(0, HOME_SECTION_PRODUCT_LIMIT).map((p, i) => (
            <ProductCard key={p.id} product={p} index={i} />
          ))}
        </div>
      ) : (
        <ProductGridSkeleton />
      )}
    </div>
  );
}

function FabricStorySection({
  payload,
  title,
}: {
  payload: Record<string, unknown> | undefined;
  title: string;
}) {
  const fromCms = parseFabricStoryVideo(payload);
  const videoSrc = fromCms || fabricStoryVideo;
  const poster = heroInfant;

  return (
    <section id="fabric-story" className="px-3 md:px-6 scroll-mt-28">
      <motion.div
        initial={{ opacity: 0, y: 40 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, margin: "-80px" }}
        transition={{ duration: 0.9, ease: [0.2, 0.8, 0.2, 1] }}
        className="relative rounded-[2rem] overflow-hidden grid md:grid-cols-[1.1fr_1fr] min-h-[420px] md:min-h-[460px]"
      >
        <div className="relative overflow-hidden min-h-[240px] md:min-h-0">
          {videoSrc ? (
            <video
              src={videoSrc}
              autoPlay
              muted
              loop
              playsInline
              preload="metadata"
              poster={poster}
              className="absolute inset-0 h-full w-full object-cover"
            />
          ) : (
            <img
              src={poster}
              alt="Organic fabric story"
              className="absolute inset-0 h-full w-full object-cover animate-[kenburns_18s_ease-in-out_infinite_alternate]"
            />
          )}
        </div>
        <div className="relative flex items-center px-6 md:px-12 py-8 bg-background">
          <div className="max-w-md">
            <div className="text-[10px] uppercase tracking-[0.32em] text-primary font-semibold">{title}</div>
            <h2 className="font-display text-3xl md:text-4xl mt-3 text-balance leading-tight">
              Whisper-soft on the very <span className="font-script text-primary">first day</span>.
            </h2>
            <p className="mt-4 text-sm md:text-base text-foreground/90 leading-relaxed font-medium">
              Every fibre is selected for what it touches — your baby&apos;s skin. We use only what nature gives best, finished by hand.
            </p>
            <div className="grid grid-cols-2 gap-4 mt-7 items-stretch">
              {[
                { t: "100% Certified Organic Cotton", d: "Skin-safe, no harsh dyes" },
                { t: "Hand-finished trims", d: "Tested for safety" },
              ].map((f) => (
                <div
                  key={f.t}
                  className="flex h-full flex-col bg-background/90 backdrop-blur-md border border-border/70 rounded-2xl p-4 shadow-sm"
                >
                  <div className="min-h-11 text-xs uppercase tracking-wider text-primary font-bold leading-snug">{f.t}</div>
                  <div className="text-sm text-foreground/90 mt-2 font-medium leading-snug">{f.d}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </motion.div>
    </section>
  );
}

function WatchAndShop() {
  const live = useProducts();
  const productsLoaded = useProductsLoaded();
  const { byKey, loaded: homeLoaded } = useHomeSections();
  const reels = useMemo(() => {
    if (!productsLoaded || !homeLoaded) return [];
    const payload = byKey.get("watch_shop")?.payload;
    const fromCms = parseWatchReels(
      payload && typeof payload === "object" ? (payload as Record<string, unknown>) : {},
    );
    const reelSource = fromCms.length > 0 ? fromCms : watchReels;
    return resolveWatchShopReels(reelSource, live);
  }, [live, byKey, productsLoaded, homeLoaded]);

  if (!productsLoaded || !homeLoaded) {
    return (
      <section id="watch-shop" className="px-4 md:px-6 scroll-mt-28">
        <div className="flex items-end justify-between mb-5">
          <div>
            <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground inline-flex items-center gap-2">
              <Play className="h-3.5 w-3.5 text-primary" /> Tap a moment to shop the look
            </div>
            <h2 className="font-display text-2xl md:text-3xl mt-2">Watch & Shop</h2>
          </div>
        </div>
        <WatchShopSkeleton />
      </section>
    );
  }

  if (reels.length === 0) return null;

  return (
    <section id="watch-shop" className="px-4 md:px-6 scroll-mt-28">
      <div className="flex items-end justify-between mb-5">
        <div>
          <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground inline-flex items-center gap-2">
            <Play className="h-3.5 w-3.5 text-primary" /> Tap a moment to shop the look
          </div>
          <h2 className="font-display text-2xl md:text-3xl mt-2">Watch & Shop</h2>
        </div>
        <Link to="/shop" className="story-link text-sm hidden md:inline">Browse all</Link>
      </div>
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3 md:gap-5">
        {reels.map((r, i) => (
          <motion.div
            key={r.slug}
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-60px" }}
            transition={{ duration: 0.7, delay: i * 0.06, ease: [0.2, 0.8, 0.2, 1] }}
            className="group"
          >
            <Link
              to="/product/$slug"
              params={{ slug: r.product.slug }}
              aria-label={`Shop ${r.product.name}`}
              className="relative block overflow-hidden rounded-[1.75rem] glass-strong hover-lift aspect-3/4"
            >
              <video
                src={r.video}
                autoPlay
                muted
                loop
                playsInline
                preload={i <= 1 ? "auto" : "metadata"}
                poster={r.product.image}
                className="absolute inset-0 h-full w-full object-cover transition-transform duration-1200 group-hover:scale-[1.06]"
              />
              <div className="absolute inset-0 bg-linear-to-t from-foreground/75 via-foreground/15 to-transparent" />
              <div className="absolute top-3 left-3 inline-flex items-center gap-1.5 rounded-full bg-background/80 backdrop-blur px-2.5 py-1 text-[10px] uppercase tracking-[0.22em] text-primary">
                <Play className="h-3 w-3 fill-current" /> Reel
              </div>
              <div className="absolute inset-x-3 bottom-3 flex items-end justify-between gap-3">
                <div className="min-w-0">
                  <div className="text-[10px] uppercase tracking-[0.24em] text-background/80">{r.product.category}</div>
                  <div className="font-display text-base md:text-lg leading-tight text-background truncate">{r.product.name}</div>
                  <div className="text-xs text-background/85 mt-0.5">₹{r.product.price.toLocaleString("en-IN")}</div>
                </div>
                <span className="shrink-0 inline-flex items-center justify-center h-9 w-9 rounded-full bg-primary text-primary-foreground shadow-md transition-transform group-hover:scale-110">
                  <ShoppingBag className="h-4 w-4" />
                </span>
              </div>
            </Link>
          </motion.div>
        ))}
      </div>
    </section>
  );
}

function HeroCarousel({
  slides,
  heroRef,
  heroY,
  heroScale,
  ecatalogueUrl,
  ecatalogueLabel,
}: {
  slides: HeroSlide[];
  heroRef: React.RefObject<HTMLDivElement | null>;
  heroY: MotionValue<string>;
  heroScale: MotionValue<number>;
  ecatalogueUrl: string;
  ecatalogueLabel: string;
}) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const total = slides.length;

  useEffect(() => {
    if (paused || total <= 1) return;
    const id = setInterval(() => setIndex((i) => (i + 1) % total), 6000);
    return () => clearInterval(id);
  }, [paused, total]);

  useEffect(() => {
    if (index >= total) setIndex(0);
  }, [total, index]);

  const go = (n: number) => setIndex(((n % total) + total) % total);
  const slide = slides[index] ?? slides[0];
  if (!slide) return null;

  return (
    <div
      ref={heroRef}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      className="relative rounded-[2rem] md:rounded-[2.5rem] overflow-hidden flex items-end
                 h-[min(92vh,720px)] sm:h-[min(88vh,760px)] md:h-[min(86vh,820px)] bg-[hsl(var(--secondary)/0.25)]"
    >
      {/* All slides mounted up front so images load in parallel */}
      <motion.div
        style={{ y: heroY, scale: heroScale }}
        className="absolute inset-0"
      >
        {slides.map((s, i) => (
          <img
            key={s.id}
            src={s.image}
            alt={i === index ? s.title : ""}
            aria-hidden={i !== index}
            fetchPriority={i === 0 ? "high" : i === 1 ? "low" : "auto"}
            loading={i <= 1 ? "eager" : "lazy"}
            decoding="async"
            className={`absolute inset-0 h-full w-full object-cover object-[center_20%] md:object-[right_20%] transition-opacity duration-500 ease-out ${
              i === index ? "opacity-100" : "opacity-0"
            }`}
          />
        ))}
      </motion.div>

      <FloatingDecor count={8} variant="petals" />
      <div className="absolute inset-0 bg-linear-to-r from-background/95 via-background/55 to-transparent md:from-background/85 md:via-background/35 md:to-transparent pointer-events-none" />
      <div className="absolute inset-0 bg-linear-to-t from-background/70 via-transparent to-transparent md:hidden pointer-events-none" />

      {/* Top-right brand pill — click to download e-catalogue */}
      <motion.a
        href={ecatalogueUrl}
        download
        aria-label={`Download ${ecatalogueLabel}`}
        initial={{ opacity: 0, x: 30 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 1, ease: [0.2, 0.8, 0.2, 1] }}
        className="absolute top-5 right-4 md:top-8 md:right-10 glass-strong rounded-full pl-4 pr-3 py-2 hidden md:inline-flex items-center gap-2 z-20 hover:bg-white/70 hover:shadow-glow transition group"
      >
        <Sparkles className="h-4 w-4 text-primary" />
        <span className="text-[11px] uppercase tracking-[0.22em]">{ecatalogueLabel}</span>
        <span className="ml-1 inline-flex h-6 w-6 items-center justify-center rounded-full bg-primary text-primary-foreground transition group-hover:scale-110">
          <Download className="h-3 w-3" />
        </span>
      </motion.a>

      {/* Top-left WELCOME10 promo — visible on SSR + all breakpoints */}
      <div className="absolute top-4 left-4 md:top-6 md:left-6 z-30 inline-flex items-center gap-2 rounded-full bg-primary text-primary-foreground px-3 py-1.5 md:px-4 md:py-2 shadow-glow pointer-events-none">
        <BadgePercent className="h-3.5 w-3.5 md:h-4 md:w-4 shrink-0" aria-hidden />
        <span className="text-[10px] md:text-[11px] uppercase tracking-[0.22em] font-medium whitespace-nowrap">
          WELCOME10 · 10% off your first order
        </span>
      </div>

      {/* Slide content */}
      <div className="relative z-10 p-4 sm:p-6 md:p-12 lg:p-14 w-full max-w-2xl">
        <AnimatePresence mode="wait">
          <motion.div
            key={slide.id}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={{ duration: 0.35, ease: [0.2, 0.8, 0.2, 1] }}
            className="glass-strong rounded-[1.5rem] md:rounded-[2rem] p-5 sm:p-7 md:p-9 lg:p-10 max-w-xl backdrop-blur-md bg-white/75 dark:bg-background/75 border border-white/50 shadow-lg"
          >
            <div className="text-[11px] sm:text-xs uppercase tracking-[0.28em] text-primary">
              {slide.eyebrow}
            </div>
            <h1 className="font-display text-[clamp(1.75rem,4.6vw,3.75rem)] leading-[1.05] mt-3 sm:mt-4 text-balance">
              {slide.title}
            </h1>
            <p className="mt-3 md:mt-4 text-foreground/80 text-sm sm:text-base md:text-[1.05rem] leading-relaxed max-w-md line-clamp-3 md:line-clamp-none">
              {slide.subtitle}
            </p>
            <div className="mt-5 md:mt-6 flex flex-wrap gap-2.5 sm:gap-3">
              <Link
                to={slide.link}
                className="group rounded-full bg-primary text-primary-foreground px-5 sm:px-6 py-3 sm:py-3.5 text-sm sm:text-base font-medium inline-flex items-center gap-2 hover:shadow-glow transition"
              >
                {slide.cta}
                <ArrowRight className="h-4 w-4 transition group-hover:translate-x-1" />
              </Link>
            </div>
            <div className="mt-4 md:mt-5 flex flex-wrap gap-x-4 gap-y-1.5 text-[11px] text-muted-foreground">
              <span className="inline-flex items-center gap-1.5"><Leaf className="h-3.5 w-3.5 text-primary" /> 100% Certified Organic Cotton</span>
              <span className="inline-flex items-center gap-1.5"><Truck className="h-3.5 w-3.5 text-primary" /> Free shipping ₹1499+</span>
              <span className="hidden sm:inline-flex items-center gap-1.5"><Leaf className="h-3.5 w-3.5 text-primary" /> Azo-free dyes</span>
            </div>
          </motion.div>
        </AnimatePresence>
      </div>

      {/* Manual nav arrows */}
      {total > 1 && (
        <>
          <button
            type="button"
            aria-label="Previous slide"
            onClick={() => go(index - 1)}
            className="hidden md:flex absolute left-1 lg:left-2 bottom-5 md:bottom-6 z-20 h-10 w-10 items-center justify-center rounded-full glass-strong hover:bg-white/70 transition"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
          <button
            type="button"
            aria-label="Next slide"
            onClick={() => go(index + 1)}
            className="hidden md:flex absolute right-1 lg:right-2 bottom-5 md:bottom-6 z-20 h-10 w-10 items-center justify-center rounded-full glass-strong hover:bg-white/70 transition"
          >
            <ChevronRight className="h-5 w-5" />
          </button>

          {/* Dots */}
          <div className="absolute bottom-4 md:bottom-6 left-1/2 -translate-x-1/2 z-20 flex items-center gap-2 glass-strong rounded-full px-3 py-2">
            {slides.map((s, i) => (
              <button
                key={s.id}
                type="button"
                aria-label={`Go to slide ${i + 1}`}
                onClick={() => go(i)}
                className={`h-1.5 rounded-full transition-all duration-500 ${
                  i === index ? "w-8 bg-primary" : "w-2.5 bg-foreground/30 hover:bg-foreground/60"
                }`}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}

/* ----------------------------- Trust Badges ----------------------------- */

const trustBadges = [
  { icon: Leaf, title: "Organic Cotton", desc: "100% certified organic cotton, free from harmful chemicals" },
  { icon: Smile, title: "Skin Friendly", desc: "Hypoallergenic fabrics gentle on sensitive newborn skin" },
  { icon: Feather, title: "Ultra Soft & Gentle", desc: "Butter-soft hand-feel that gets cosier with every wash" },
  { icon: Wind, title: "Breathable", desc: "Airflow-friendly weaves to keep your baby cool and comfy" },
  { icon: Droplets, title: "Bio Wash", desc: "Eco-friendly enzyme wash for lasting colour and softness" },
  { icon: RefreshCw, title: "Prewash", desc: "Pre-shrunk and ready to wear straight out of the box" },
  { icon: ShieldCheck, title: "Quality Assured", desc: "Tested for safety & softness" },
  { icon: Heart, title: "Loved by Parents", desc: "Rated 4.9★ by little families" },
  { icon: Gift, title: "Plantable Seed Tags", desc: "A small step towards environmental responsibility with every purchase." },
  { icon: Sparkles, title: "Smart Wear", desc: "Thoughtful cuts for play & rest" },
] as const;

function TrustBadges() {
  return (
    <section className="px-4 md:px-6">
      <div className="mx-auto max-w-6xl">
        <div className="mb-8 md:mb-10 text-center">
          <div className="text-[10px] uppercase tracking-[0.32em] text-primary font-semibold">
            Why families trust us
          </div>
          <h2 className="font-display text-2xl md:text-3xl mt-2 text-balance">
            Made with care, <span className="font-script text-primary">tested with love</span>
          </h2>
        </div>
        <ul
          role="list"
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-2 gap-x-10 md:gap-x-16 gap-y-1
                     divide-y divide-border/60 sm:divide-y-0
                     sm:[&>li:nth-child(n+3)]:border-t sm:[&>li]:border-border/60
                     sm:[&>li:nth-child(odd)]:sm:pr-6 sm:[&>li:nth-child(even)]:sm:pl-6"
        >
          {trustBadges.map((b, i) => {
            const Icon = b.icon;
            return (
              <motion.li
                key={b.title}
                initial={{ opacity: 0, x: -16 }}
                whileInView={{ opacity: 1, x: 0 }}
                viewport={{ once: true, margin: "-40px" }}
                transition={{ duration: 0.45, delay: i * 0.04, ease: [0.2, 0.8, 0.2, 1] }}
                className="group flex items-start gap-4 md:gap-5 py-4 md:py-5"
              >
                <span className="relative shrink-0 inline-flex h-11 w-11 md:h-12 md:w-12 items-center justify-center rounded-full bg-primary/10 text-primary transition-colors duration-300 group-hover:bg-primary group-hover:text-primary-foreground">
                  <Icon className="h-[1.15rem] w-[1.15rem] md:h-5 md:w-5" strokeWidth={1.8} />
                </span>
                <div className="min-w-0 flex-1 pt-0.5">
                  <h3 className="font-display text-base md:text-[1.05rem] leading-tight text-foreground tracking-tight">
                    {b.title}
                  </h3>
                  <p className="mt-1 text-sm text-muted-foreground leading-relaxed">
                    {b.desc}
                  </p>
                </div>
              </motion.li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}
