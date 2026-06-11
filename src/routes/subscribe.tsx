import { createFileRoute } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { Check, Sparkles, Heart, Gift, type LucideIcon } from "lucide-react";
import { useMemo, useState } from "react";
import babyHero from "@/assets/hero-smiling-baby.jpg";
import babyInfant from "@/assets/cat-infant.webp";
import babyToddler from "@/assets/cat-toddler.webp";
import { getSubscribePage, type SubscribePageContent } from "@/lib/backend-data";

export const Route = createFileRoute("/subscribe")({
  head: () => ({ meta: [{ title: "Subscriptions · Mini's n More" }] }),
  loader: async () => {
    try {
      return await getSubscribePage();
    } catch {
      return {};
    }
  },
  component: Subscribe,
});

type PlanView = {
  id: string;
  name: string;
  price: number;
  color: string;
  img: string;
  tagline: string;
  perks: string[];
  featured?: boolean;
};

const DEFAULT_PLANS: PlanView[] = [
  {
    id: "essentials",
    name: "Tiny Essentials",
    price: 1490,
    color: "from-cream to-sand",
    img: babyInfant,
    tagline: "Daily-wear basics, every month",
    perks: ["3 daily-wear pieces", "Free shipping pan-India", "Cancel anytime", "Sized as baby grows"],
  },
  {
    id: "edit",
    name: "Monthly Edit",
    price: 2790,
    color: "from-tan to-brick",
    img: babyHero,
    tagline: "Our most-loved curation",
    perks: ["5 hand-curated pieces", "Free gift wrap & note", "Founder's curation note", "Priority new-launch access"],
    featured: true,
  },
  {
    id: "heirloom",
    name: "Heirloom Box",
    price: 4990,
    color: "from-brick to-taupe",
    img: babyToddler,
    tagline: "Special-occasion wardrobe",
    perks: ["7 premium pieces", "Knit + linen blend", "Priority support", "Free express shipping"],
  },
];

const PLAN_IMAGES: Record<string, string> = {
  essentials: babyInfant,
  edit: babyHero,
  heirloom: babyToddler,
};

const PLAN_COLORS: Record<string, string> = {
  essentials: "from-cream to-sand",
  edit: "from-tan to-brick",
  heirloom: "from-brick to-taupe",
};

const DEFAULT_BADGES: { i: LucideIcon; t: string }[] = [
  { i: Sparkles, t: "100% Certified Organic Cotton" },
  { i: Heart, t: "Sized to baby" },
  { i: Gift, t: "Free wrap" },
];

const DEFAULT_FEATURES = [
  { t: "Pause whenever", d: "Travelling? Pause your box for up to 3 months in one tap." },
  { t: "Swap if it doesn't fit", d: "Free swaps within 7 days of delivery — we resize from your box." },
  { t: "Growth support", d: "Get sizing help and easy plan updates as your little one grows." },
];

function parseJsonArray<T>(raw: string | undefined): T[] {
  if (!raw?.trim()) return [];
  try {
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? (parsed as T[]) : [];
  } catch {
    return [];
  }
}

function resolvePlans(content: SubscribePageContent): PlanView[] {
  const fromApi = parseJsonArray<{
    id?: string;
    name?: string;
    price?: number;
    tagline?: string;
    perks?: string[];
    featured?: boolean;
  }>(content.subscribe_plans_json);

  if (!fromApi.length) return DEFAULT_PLANS;

  return fromApi.map((p, index) => {
    const id = p.id || `plan-${index}`;
    const fallback = DEFAULT_PLANS.find((d) => d.id === id) ?? DEFAULT_PLANS[1];
    return {
      id,
      name: p.name || fallback.name,
      price: typeof p.price === "number" ? p.price : fallback.price,
      color: PLAN_COLORS[id] ?? fallback.color,
      img: PLAN_IMAGES[id] ?? fallback.img,
      tagline: p.tagline || fallback.tagline,
      perks: Array.isArray(p.perks) && p.perks.length ? p.perks.map(String) : fallback.perks,
      featured: p.featured ?? fallback.featured,
    };
  });
}

function Subscribe() {
  const content = Route.useLoaderData();
  const plans = useMemo(() => resolvePlans(content), [content]);
  const [picked, setPicked] = useState(plans.find((p) => p.featured)?.id ?? plans[0]?.id ?? "edit");

  const heroTitle =
    content.subscribe_hero_title?.trim() ||
    "A soft little box, every month.";
  const heroSubtitle =
    content.subscribe_hero_subtitle?.trim() ||
    "As your baby grows, we send a thoughtfully curated edit — sized to fit, never to outgrow too fast. Pause, swap or cancel any time.";
  const testimonial =
    content.subscribe_testimonial?.trim() ||
    '"My monthly little box of magic." — Anika, Bengaluru';

  const badges = parseJsonArray<{ icon?: string; text?: string }>(content.subscribe_badges_json);
  const badgeRow =
    badges.length > 0
      ? badges
          .filter((b) => b.text?.trim())
          .map((b, i) => ({ i: [Sparkles, Heart, Gift][i % 3], t: b.text!.trim() }))
      : DEFAULT_BADGES;

  const features = parseJsonArray<{ title?: string; description?: string; t?: string; d?: string }>(
    content.subscribe_features_json,
  );
  const featureRow =
    features.length > 0
      ? features.map((f) => ({
          t: f.title || f.t || "",
          d: f.description || f.d || "",
        }))
      : DEFAULT_FEATURES;

  return (
    <div className="px-4 md:px-6 space-y-10 md:space-y-14">
      <div className="grid lg:grid-cols-[1.1fr_0.9fr] gap-5 md:gap-8 items-stretch">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="glass-strong rounded-[1.75rem] md:rounded-[2.5rem] p-6 sm:p-8 md:p-12 lg:p-14 flex flex-col justify-center"
        >
          <div className="text-[10px] sm:text-xs uppercase tracking-[0.28em] text-primary">Grow with us</div>
          <h1 className="font-display text-3xl sm:text-4xl md:text-5xl lg:text-6xl mt-3 text-balance">
            {heroTitle.includes("every month") ? (
              <>
                A soft little box, <span className="font-script text-primary">every month</span>.
              </>
            ) : (
              heroTitle
            )}
          </h1>
          <p className="mt-3 sm:mt-4 text-sm sm:text-base text-muted-foreground max-w-lg">{heroSubtitle}</p>
          <div className="mt-5 sm:mt-6 flex flex-wrap gap-2 text-[11px] sm:text-xs">
            {badgeRow.map((b) => (
              <span key={b.t} className="glass rounded-full px-3 py-1.5 inline-flex items-center gap-1.5">
                <b.i className="h-3 w-3 text-primary" /> {b.t}
              </span>
            ))}
          </div>
        </motion.div>
        <motion.div
          initial={{ opacity: 0, scale: 0.96 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.1 }}
          className="relative rounded-[1.75rem] md:rounded-[2.5rem] overflow-hidden min-h-[280px] sm:min-h-[340px] glass-strong"
        >
          <img src={babyHero} alt="Smiling baby in soft cotton" className="absolute inset-0 h-full w-full object-cover" />
          <div className="absolute inset-0 bg-gradient-to-tr from-background/55 via-background/10 to-transparent" />
          <div className="absolute bottom-5 left-5 right-5 sm:bottom-8 sm:left-8 sm:right-8 glass rounded-2xl p-4 sm:p-5">
            <div className="text-xs uppercase tracking-wider text-primary">Trusted by 12,000+ families</div>
            <div className="font-display text-lg sm:text-xl mt-1.5">{testimonial}</div>
          </div>
        </motion.div>
      </div>

      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-5">
        {plans.map((p, i) => (
          <motion.button
            key={p.id}
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ delay: i * 0.1 }}
            onClick={() => setPicked(p.id)}
            className={`text-left glass-strong rounded-[1.75rem] md:rounded-[2rem] overflow-hidden hover-lift relative flex flex-col ${
              picked === p.id ? "ring-2 ring-primary" : ""
            } ${p.featured ? "lg:scale-[1.04] lg:-translate-y-2" : ""}`}
          >
            {p.featured && (
              <div className="absolute top-3 right-3 z-10 bg-primary text-primary-foreground text-[10px] uppercase tracking-wider px-3 py-1 rounded-full">
                Most loved
              </div>
            )}
            <div className="aspect-[5/3] overflow-hidden">
              <img src={p.img} alt={p.name} className="h-full w-full object-cover" loading="lazy" />
            </div>
            <div className="p-5 sm:p-6 md:p-7 flex flex-col flex-1">
              <div className={`h-1.5 w-12 rounded-full bg-gradient-to-br ${p.color} mb-3`} />
              <div className="font-display text-xl sm:text-2xl">{p.name}</div>
              <div className="text-xs text-muted-foreground mt-1">{p.tagline}</div>
              <div className="mt-3 flex items-baseline gap-1">
                <span className="font-display text-2xl sm:text-3xl">₹{p.price.toLocaleString("en-IN")}</span>
                <span className="text-xs text-muted-foreground">/ month</span>
              </div>
              <ul className="mt-4 space-y-2 text-sm flex-1">
                {p.perks.map((k) => (
                  <li key={k} className="flex items-start gap-2">
                    <Check className="h-3.5 w-3.5 text-primary mt-0.5 shrink-0" /> <span>{k}</span>
                  </li>
                ))}
              </ul>
              <div
                className={`mt-5 sm:mt-6 rounded-full text-center py-3 font-medium text-sm ${
                  picked === p.id ? "bg-primary text-primary-foreground" : "glass"
                }`}
              >
                {picked === p.id ? "Selected" : "Choose plan"}
              </div>
            </div>
          </motion.button>
        ))}
      </div>

      <section className="glass-strong rounded-[1.75rem] md:rounded-[2rem] p-6 sm:p-8 md:p-10 grid md:grid-cols-3 gap-5 sm:gap-6 text-sm">
        {featureRow.map((c) => (
          <div key={c.t}>
            <div className="font-display text-lg">{c.t}</div>
            <p className="text-muted-foreground mt-1.5">{c.d}</p>
          </div>
        ))}
      </section>
    </div>
  );
}
