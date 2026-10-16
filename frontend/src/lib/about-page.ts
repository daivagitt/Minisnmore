import { optionalPublicUrlFieldError } from "@/lib/utils";

export const ABOUT_FEATURE_ICONS = ["leaf", "heart", "sparkles", "shield"] as const;
export type AboutFeatureIcon = (typeof ABOUT_FEATURE_ICONS)[number];

export type AboutStat = { value: string; label: string };
export type AboutFeature = { icon: AboutFeatureIcon; title: string; description: string };
export type AboutFounder = {
  name: string;
  role: string;
  image_url: string;
  paragraphs: string[];
  quote: string;
};

export type AboutPageContent = {
  hero_eyebrow: string;
  hero_title: string;
  hero_script_word: string;
  hero_body: string;
  hero_image_url: string;
  story_eyebrow: string;
  story_stat: string;
  story_headline: string;
  story_sub: string;
  story_stats: AboutStat[];
  story_promise: string;
  story_paragraphs: string[];
  story_quote: string;
  why_eyebrow: string;
  why_title: string;
  why_features: AboutFeature[];
  why_footer: string;
  vision_title: string;
  vision_script_word: string;
  vision_body: string;
  mission_title: string;
  mission_script_word: string;
  mission_paragraphs: string[];
  founders_eyebrow: string;
  founders_title: string;
  founders_script_word: string;
  founders: AboutFounder[];
  cta_title: string;
  cta_body: string;
  cta_button: string;
};

export const DEFAULT_ABOUT_PAGE: AboutPageContent = {
  hero_eyebrow: "Our story",
  hero_title: "Crafted with love for your minis.",
  hero_script_word: "love",
  hero_body:
    "A baby's skin is incredibly delicate — softer, thinner and far more sensitive than we often realise. What we choose for them in those early days plays a bigger role than we think.",
  hero_image_url: "",
  story_eyebrow: "Why we exist",
  story_stat: "2,500",
  story_headline: "outfits before the age of two.",
  story_sub:
    "Most are treated with chemicals that shouldn't touch an adult's skin, let alone a newborn's. We set out to change that — quietly, carefully, one essential at a time.",
  story_stats: [
    { value: "100%", label: "Certified organic cotton" },
    { value: "0", label: "Harsh chemicals or dyes" },
  ],
  story_promise: "The Mini's n More Promise",
  story_paragraphs: [
    "At Mini's n More, we create thoughtfully designed baby essentials that bring together comfort, quality and gentle care. At the heart of everything we make is premium organic cotton — chosen for its exceptional softness, natural stretch and breathable comfort. Its smooth, plush feel is especially gentle on delicate, sensitive skin, offering a safe and soothing experience for your little one.",
    "Like many parents, our journey began with questions and concerns. Skin rashes, confusing labels and uncertainty about chemical exposure made us realise how difficult it was to truly know what was safe for a newborn. The reality is, babies can absorb more through their skin than adults, yet most clothing undergoes chemical-heavy processing.",
    "We set out to build a line of baby essentials using certified organic cotton, minimal processing and skin-safe dyes — products we could trust for our own children. Every piece is designed to be practical, reliable and made with careful attention to detail, helping make everyday parenting just a little simpler.",
    "From the earliest days to daily moments, we focus on creating essentials that feel as good as they look — because when it comes to your baby, every touch should be pure, soft and reassuring.",
  ],
  story_quote: "That realisation led us here.",
  why_eyebrow: "Why choose Mini's n More",
  why_title: "Honest essentials, gently made.",
  why_features: [
    {
      icon: "leaf",
      title: "100% organic cotton",
      description: "Soft, breathable and grown without synthetic pesticides.",
    },
    {
      icon: "heart",
      title: "Gentle on sensitive skin",
      description: "Skin-safe dyes and seam-friendly finishing.",
    },
    {
      icon: "sparkles",
      title: "Easy, fuss-free wear",
      description: "Designed around the rhythms of everyday parenting.",
    },
    {
      icon: "shield",
      title: "Durable & consistent",
      description: "Quality you can trust, wash after wash.",
    },
  ],
  why_footer:
    "We believe a brand's real responsibility is to help parents understand why something matters — and to earn their trust through honesty. No overpromises, no shortcuts. Just thoughtfully made essentials for babies with sensitive skin, and for parents who want fewer things to worry about.",
  vision_title: "The world's most trusted baby essentials brand.",
  vision_script_word: "trusted",
  vision_body:
    "Mini's n More envisions being the world's most trusted baby essentials brand, nurturing every child's early years with products that are safe, gentle and thoughtfully designed for modern families.",
  mission_title: "Beautifully crafted essentials parents can trust.",
  mission_script_word: "trust",
  mission_paragraphs: [
    "At Mini's n More we deliver beautifully crafted baby essentials that parents can trust — designed with uncompromising attention to safety, softness and everyday practicality.",
    "We design intuitive, high-quality and beautifully functional baby products that solve real parenting challenges.",
    "We create innovative, sustainable and scientifically backed baby products that meet the highest global standards of safety and quality — empowering parents across every continent to nurture their children with confidence and peace of mind.",
  ],
  founders_eyebrow: "Meet the founders",
  founders_title: "The hands behind Mini's n More",
  founders_script_word: "Mini's n More",
  founders: [
    {
      name: "Ravikumar T",
      role: "Founder & Director",
      image_url: "",
      paragraphs: [
        "Ravikumar is the Founder of Mini's n More, a premium baby clothing brand built on the belief that every child deserves the safest and most comfortable start in life. His dream of becoming an entrepreneur began during his college days, driven by a desire to create something meaningful, build a lasting legacy, and contribute to society through innovation and job creation.",
        "He holds a Bachelor's degree in Engineering and an MBA in Business & Entrepreneurship from Germany. Before starting Mini's n More, Ravi gained over six years of experience in technology and manufacturing, working in key and strategic roles across North America and Europe.",
        "Despite a successful corporate career, his passion for entrepreneurship inspired him to return to India and build a brand of his own. With a vision to create a globally recognized Indian brand, he founded Mini's n More to provide high-quality, safe, and comfortable clothing for newborns and toddlers.",
        "Today, Ravi's mission is to make premium baby clothing accessible to families while maintaining the highest standards of quality, ethical manufacturing, and customer trust. Through Mini's n More, he aims to create meaningful employment opportunities, promote responsible business practices, and build a trusted global brand that grows alongside every child's journey.",
      ],
      quote:
        "Every great journey starts with a dream. For Ravi, that dream began in college and continues to come to life through Minis n More—one happy child at a time.",
    },
    {
      name: "Vasudev R",
      role: "Founder & Director",
      image_url: "",
      paragraphs: [
        "Vasudev R is a founder of Mini's n More, a premium baby brand dedicated to creating products that parents worldwide can trust without compromise. His journey began at Ernst & Young (EY), a prestigious Big 4 auditing company, where he learned financial rigor, compliance, ethical business practices, and attention to detail—values that remain central to Mini's n More today.",
        "After EY, Vasudev moved into marketing and the FMCG sector, becoming an expert in marketing, super stockist, and C&F (Carrying & Forwarding) operations. This unique combination gave him a competitive edge: he understood both how to move products through the supply chain and how to position them effectively to resonate with parents. His experience covers the complete picture—from product conception and branding to distribution and final sale.",
        "Vasudev brings strong expertise in distribution management, channel coordination, inventory control, warehousing, and logistics, building robust networks that reach every corner of the market. Holding an MBA in Finance from Indiana University of Pennsylvania, U.S.A., with specialized knowledge in finance, marketing, and logistics & supply chain management, he leverages his Big 4 auditing background and FMCG mastery alongside Ravikumar to build Mini's n More into a globally trusted brand.",
      ],
      quote:
        "My career taught me that trust is earned through consistency and reliability. At Minis n More, we apply those same principles to every baby product. Because when it comes to your child, excellence isn't optional—it's essential.",
    },
  ],
  cta_title: "Soft, organic, made to be loved.",
  cta_body: "Explore the collection that started it all — gentle essentials for the first 24 months.",
  cta_button: "Shop the collection",
};

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function asStringList(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.map((item) => (typeof item === "string" ? item : String(item ?? "")));
}

function asStats(value: unknown): AboutStat[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.map((item) => {
    const row = asRecord(item);
    return { value: asString(row.value), label: asString(row.label) };
  });
}

function asFeatureIcon(value: unknown): AboutFeatureIcon {
  return ABOUT_FEATURE_ICONS.includes(value as AboutFeatureIcon) ? (value as AboutFeatureIcon) : "leaf";
}

function asFeatures(value: unknown): AboutFeature[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.map((item) => {
    const row = asRecord(item);
    return {
      icon: asFeatureIcon(row.icon),
      title: asString(row.title),
      description: asString(row.description),
    };
  });
}

function asFounders(value: unknown): AboutFounder[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.map((item) => {
    const row = asRecord(item);
    return {
      name: asString(row.name),
      role: asString(row.role),
      image_url: asString(row.image_url),
      paragraphs: asStringList(row.paragraphs) ?? [""],
      quote: asString(row.quote),
    };
  });
}

export function parseAboutPageJson(raw: unknown): Partial<AboutPageContent> {
  if (raw == null) return {};
  if (typeof raw === "object" && !Array.isArray(raw)) {
    return parseAboutPageObject(asRecord(raw));
  }
  if (typeof raw !== "string" || !raw.trim()) return {};
  try {
    const parsed = JSON.parse(raw) as unknown;
    return parseAboutPageObject(asRecord(parsed));
  } catch {
    return {};
  }
}

function parseAboutPageObject(row: Record<string, unknown>): Partial<AboutPageContent> {
  const next: Partial<AboutPageContent> = {};
  const assign = <K extends keyof AboutPageContent>(key: K, value: AboutPageContent[K] | undefined) => {
    if (value !== undefined) next[key] = value;
  };
  assign("hero_eyebrow", typeof row.hero_eyebrow === "string" ? row.hero_eyebrow : undefined);
  assign("hero_title", typeof row.hero_title === "string" ? row.hero_title : undefined);
  assign("hero_script_word", typeof row.hero_script_word === "string" ? row.hero_script_word : undefined);
  assign("hero_body", typeof row.hero_body === "string" ? row.hero_body : undefined);
  assign("hero_image_url", typeof row.hero_image_url === "string" ? row.hero_image_url : undefined);
  assign("story_eyebrow", typeof row.story_eyebrow === "string" ? row.story_eyebrow : undefined);
  assign("story_stat", typeof row.story_stat === "string" ? row.story_stat : undefined);
  assign("story_headline", typeof row.story_headline === "string" ? row.story_headline : undefined);
  assign("story_sub", typeof row.story_sub === "string" ? row.story_sub : undefined);
  assign("story_stats", asStats(row.story_stats));
  assign("story_promise", typeof row.story_promise === "string" ? row.story_promise : undefined);
  assign("story_paragraphs", asStringList(row.story_paragraphs));
  assign("story_quote", typeof row.story_quote === "string" ? row.story_quote : undefined);
  assign("why_eyebrow", typeof row.why_eyebrow === "string" ? row.why_eyebrow : undefined);
  assign("why_title", typeof row.why_title === "string" ? row.why_title : undefined);
  assign("why_features", asFeatures(row.why_features));
  assign("why_footer", typeof row.why_footer === "string" ? row.why_footer : undefined);
  assign("vision_title", typeof row.vision_title === "string" ? row.vision_title : undefined);
  assign("vision_script_word", typeof row.vision_script_word === "string" ? row.vision_script_word : undefined);
  assign("vision_body", typeof row.vision_body === "string" ? row.vision_body : undefined);
  assign("mission_title", typeof row.mission_title === "string" ? row.mission_title : undefined);
  assign("mission_script_word", typeof row.mission_script_word === "string" ? row.mission_script_word : undefined);
  assign("mission_paragraphs", asStringList(row.mission_paragraphs));
  assign("founders_eyebrow", typeof row.founders_eyebrow === "string" ? row.founders_eyebrow : undefined);
  assign("founders_title", typeof row.founders_title === "string" ? row.founders_title : undefined);
  assign("founders_script_word", typeof row.founders_script_word === "string" ? row.founders_script_word : undefined);
  assign("founders", asFounders(row.founders));
  assign("cta_title", typeof row.cta_title === "string" ? row.cta_title : undefined);
  assign("cta_body", typeof row.cta_body === "string" ? row.cta_body : undefined);
  assign("cta_button", typeof row.cta_button === "string" ? row.cta_button : undefined);
  return next;
}

export function resolveAboutPage(raw: unknown): AboutPageContent {
  const parsed = parseAboutPageJson(raw);
  return {
    ...DEFAULT_ABOUT_PAGE,
    ...parsed,
    story_stats: parsed.story_stats ?? DEFAULT_ABOUT_PAGE.story_stats,
    story_paragraphs: parsed.story_paragraphs ?? DEFAULT_ABOUT_PAGE.story_paragraphs,
    why_features: parsed.why_features ?? DEFAULT_ABOUT_PAGE.why_features,
    mission_paragraphs: parsed.mission_paragraphs ?? DEFAULT_ABOUT_PAGE.mission_paragraphs,
    founders: parsed.founders ?? DEFAULT_ABOUT_PAGE.founders,
  };
}

export function serializeAboutPage(content: AboutPageContent): string {
  return JSON.stringify(content);
}

function isStatStarted(stat: AboutStat): boolean {
  return !!(stat.value.trim() || stat.label.trim());
}

function isFeatureStarted(feature: AboutFeature): boolean {
  return !!(feature.title.trim() || feature.description.trim() || feature.icon !== "leaf");
}

function isFounderStarted(founder: AboutFounder): boolean {
  return !!(
    founder.name.trim() ||
    founder.role.trim() ||
    founder.image_url.trim() ||
    founder.quote.trim() ||
    founder.paragraphs.some((p) => p.trim())
  );
}

/** Validates about-page JSON before save. Empty sections fall back to storefront defaults. */
export function getAboutPageValidationErrors(raw: unknown): string[] {
  const errors: string[] = [];
  if (typeof raw === "string" && raw.trim()) {
    try {
      JSON.parse(raw);
    } catch {
      return ["About page content is not valid JSON."];
    }
  }
  const content = resolveAboutPage(raw);

  content.story_stats.forEach((stat, i) => {
    if (!isStatStarted(stat)) return;
    if (!stat.value.trim()) errors.push(`Promise stat ${i + 1}: Value is required.`);
    if (!stat.label.trim()) errors.push(`Promise stat ${i + 1}: Label is required.`);
  });

  content.why_features.forEach((feature, i) => {
    if (!isFeatureStarted(feature)) return;
    if (!feature.title.trim()) errors.push(`Why-choose ${i + 1}: Title is required.`);
    if (!feature.description.trim()) errors.push(`Why-choose ${i + 1}: Description is required.`);
  });

  content.founders.forEach((founder, i) => {
    if (!isFounderStarted(founder)) return;
    const prefix = `Founder ${i + 1}: `;
    if (!founder.name.trim()) errors.push(`${prefix}Name is required.`);
    if (!founder.role.trim()) errors.push(`${prefix}Role is required.`);
    const imageError = optionalPublicUrlFieldError(founder.image_url, `${prefix}Image`);
    if (imageError) errors.push(imageError);
  });

  const heroImageError = optionalPublicUrlFieldError(content.hero_image_url, "Hero image");
  if (heroImageError) errors.push(heroImageError);

  return errors;
}
