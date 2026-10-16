import { createFileRoute, Link } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { Leaf, Heart, ShieldCheck, Sparkles, Target, Eye, ArrowRight, type LucideIcon } from "lucide-react";
import lifestyle from "@/assets/lifestyle-mother.webp";
import heroFounders from "@/assets/hero-founders-atelier.jpg";
import heroInfant from "@/assets/hero-infant-organic.jpg";
import { getAboutPage } from "@/lib/backend-data";
import { resolveAboutPage, type AboutFeatureIcon, type AboutPageContent } from "@/lib/about-page";

export const Route = createFileRoute("/about")({
  head: () => ({
    meta: [
      { title: "About Us · Mini's n More — Crafted with love for your minis" },
      { name: "description", content: "Mini's n More creates premium organic cotton baby essentials — gentle on delicate skin, thoughtfully designed for modern families." },
      { property: "og:title", content: "About Mini's n More" },
      { property: "og:description", content: "Premium organic cotton baby essentials, made with care for the most delicate skin." },
      { property: "og:image", content: heroFounders },
    ],
  }),
  loader: async () => {
    try {
      return await getAboutPage();
    } catch {
      return {};
    }
  },
  component: About,
});

const WHY_ICONS: Record<AboutFeatureIcon, LucideIcon> = {
  leaf: Leaf,
  heart: Heart,
  sparkles: Sparkles,
  shield: ShieldCheck,
};

const FOUNDER_FALLBACKS = [heroFounders, heroInfant];

function TitleWithScript({ text, scriptWord }: { text: string; scriptWord?: string }) {
  const needle = scriptWord?.trim();
  if (!needle) return <>{text}</>;
  const idx = text.toLowerCase().indexOf(needle.toLowerCase());
  if (idx < 0) return <>{text}</>;
  return (
    <>
      {text.slice(0, idx)}
      <span className="font-script text-primary">{text.slice(idx, idx + needle.length)}</span>
      {text.slice(idx + needle.length)}
    </>
  );
}

function About() {
  const raw = Route.useLoaderData();
  const content: AboutPageContent = resolveAboutPage(raw.about_page_json);
  const heroImage = content.hero_image_url.trim() || lifestyle;

  return (
    <div className="px-4 md:px-6 space-y-12 md:space-y-16">
      <motion.section
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.7 }}
        className="relative glass-strong rounded-[1.75rem] overflow-hidden"
      >
        <div className="grid md:grid-cols-2 gap-0 items-stretch">
          <div className="p-5 md:p-8 flex flex-col justify-center">
            <div className="text-xs uppercase tracking-[0.28em] text-primary">{content.hero_eyebrow}</div>
            <h1 className="font-display text-3xl md:text-5xl mt-1.5 text-balance leading-tight">
              <TitleWithScript text={content.hero_title} scriptWord={content.hero_script_word} />
            </h1>
            <p className="mt-3 text-sm md:text-base text-foreground/85 leading-relaxed">{content.hero_body}</p>
          </div>
          <div className="relative min-h-[320px] md:min-h-[460px]">
            <img src={heroImage} alt="" className="absolute inset-0 h-full w-full object-cover" />
          </div>
        </div>
      </motion.section>

      <section className="relative grid md:grid-cols-12 gap-6 md:gap-10 items-start py-2 md:py-4">
        <div className="md:col-span-5">
          <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground">{content.story_eyebrow}</div>
          <h2 className="font-display text-3xl md:text-[2.35rem] mt-2 text-balance leading-[1.15]">
            <span className="font-script text-primary text-4xl md:text-5xl align-middle">{content.story_stat}</span>{" "}
            {content.story_headline}
          </h2>
          <p className="mt-4 text-sm text-muted-foreground leading-relaxed">{content.story_sub}</p>
          <div className="grid grid-cols-2 gap-3 mt-6">
            {content.story_stats.map((s) => (
              <div
                key={s.label}
                className="rounded-2xl p-4 bg-white/85 border border-border/50 shadow-[0_8px_24px_-12px_rgba(74,55,40,0.18)]"
              >
                <div className="font-display text-2xl md:text-3xl text-primary leading-none">{s.value}</div>
                <div className="text-xs text-muted-foreground mt-2 leading-snug">{s.label}</div>
              </div>
            ))}
          </div>
          <div className="mt-6 pt-4 border-t border-border/60">
            <div className="text-[10px] uppercase tracking-[0.28em] text-muted-foreground">— {content.story_promise}</div>
          </div>
        </div>
        <div className="md:col-span-7 md:border-l md:border-border/50 md:pl-8 lg:pl-10 space-y-5 text-foreground/85 leading-relaxed">
          {content.story_paragraphs.map((p, idx) => (
            <div key={`story-${idx}`}>
              <p>{p}</p>
              {idx === 1 && content.story_quote ? (
                <blockquote className="border-l-2 border-primary/40 pl-4 italic text-foreground/70 mt-5">
                  {content.story_quote}
                </blockquote>
              ) : null}
            </div>
          ))}
          {content.story_paragraphs.length < 2 && content.story_quote ? (
            <blockquote className="border-l-2 border-primary/40 pl-4 italic text-foreground/70">
              {content.story_quote}
            </blockquote>
          ) : null}
        </div>
      </section>

      <section>
        <div className="text-center mb-8">
          <div className="text-xs uppercase tracking-[0.28em] text-primary">{content.why_eyebrow}</div>
          <h2 className="font-display text-3xl md:text-5xl mt-2">{content.why_title}</h2>
        </div>
        <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 md:gap-5">
          {content.why_features.map((f, i) => {
            const Icon = WHY_ICONS[f.icon] ?? Leaf;
            return (
              <motion.li
                key={`${f.title}-${i}`}
                initial={{ opacity: 0, y: 18 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: "-60px" }}
                transition={{ duration: 0.5, delay: i * 0.06 }}
                className="glass rounded-3xl p-6 h-full"
              >
                <span className="h-11 w-11 grid place-items-center rounded-2xl bg-primary/10 text-primary">
                  <Icon className="h-5 w-5" />
                </span>
                <div className="font-display text-lg mt-4">{f.title}</div>
                <p className="text-sm text-muted-foreground mt-1.5 leading-relaxed">{f.description}</p>
              </motion.li>
            );
          })}
        </ul>
        <p className="mt-8 text-center text-sm text-muted-foreground max-w-2xl mx-auto leading-relaxed">
          {content.why_footer}
        </p>
      </section>

      <section className="grid md:grid-cols-2 gap-4 md:gap-6">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-60px" }}
          transition={{ duration: 0.7 }}
          className="glass-strong rounded-[2rem] p-8 md:p-12 relative overflow-hidden"
        >
          <Eye className="absolute -top-6 -right-6 h-40 w-40 text-primary/5" aria-hidden />
          <div className="text-xs uppercase tracking-[0.28em] text-primary inline-flex items-center gap-2">
            <Eye className="h-3.5 w-3.5" /> Vision
          </div>
          <h3 className="font-display text-3xl md:text-4xl mt-3 text-balance leading-tight">
            <TitleWithScript text={content.vision_title} scriptWord={content.vision_script_word} />
          </h3>
          <p className="mt-4 text-foreground/85 leading-relaxed">{content.vision_body}</p>
        </motion.div>
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-60px" }}
          transition={{ duration: 0.7, delay: 0.1 }}
          className="glass-strong rounded-[2rem] p-8 md:p-12 relative overflow-hidden"
        >
          <Target className="absolute -top-6 -right-6 h-40 w-40 text-primary/5" aria-hidden />
          <div className="text-xs uppercase tracking-[0.28em] text-primary inline-flex items-center gap-2">
            <Target className="h-3.5 w-3.5" /> Mission
          </div>
          <h3 className="font-display text-3xl md:text-4xl mt-3 text-balance leading-tight">
            <TitleWithScript text={content.mission_title} scriptWord={content.mission_script_word} />
          </h3>
          <div className="mt-4 space-y-3 text-foreground/85 leading-relaxed">
            {content.mission_paragraphs.map((p, idx) => (
              <p key={`mission-${idx}`}>{p}</p>
            ))}
          </div>
        </motion.div>
      </section>

      <section>
        <div className="text-center mb-10">
          <div className="text-xs uppercase tracking-[0.28em] text-primary">{content.founders_eyebrow}</div>
          <h2 className="font-display text-3xl md:text-5xl mt-2">
            <TitleWithScript text={content.founders_title} scriptWord={content.founders_script_word} />
          </h2>
        </div>
        <div className="grid md:grid-cols-2 gap-5 md:gap-8">
          {content.founders.map((f, i) => {
            const image = f.image_url.trim() || FOUNDER_FALLBACKS[i] || FOUNDER_FALLBACKS[0];
            return (
              <motion.article
                key={`${f.name}-${i}`}
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: "-60px" }}
                transition={{ duration: 0.7, delay: i * 0.1 }}
                className="glass-strong rounded-[2rem] overflow-hidden flex flex-col"
              >
                <div className="relative aspect-[16/10] overflow-hidden">
                  <img src={image} alt={f.name} className="absolute inset-0 h-full w-full object-cover" loading="lazy" />
                  <div className="absolute inset-0 bg-gradient-to-t from-foreground/55 to-transparent" />
                  <div className="absolute bottom-4 left-5 right-5 text-background">
                    <div className="text-[10px] uppercase tracking-[0.28em] opacity-90">{f.role}</div>
                    <div className="font-display text-3xl md:text-4xl leading-tight">{f.name}</div>
                  </div>
                </div>
                <div className="p-6 md:p-8 space-y-3 text-foreground/85 leading-relaxed text-sm md:text-base">
                  {f.paragraphs.filter((p) => p.trim()).map((p, idx) => (
                    <p key={idx}>{p}</p>
                  ))}
                  {f.quote ? (
                    <blockquote className="mt-4 border-l-2 border-primary/40 pl-4 italic text-foreground/75">
                      &ldquo;{f.quote}&rdquo;
                      <footer className="mt-2 not-italic text-xs text-muted-foreground">
                        — {f.name}, {f.role}
                      </footer>
                    </blockquote>
                  ) : null}
                </div>
              </motion.article>
            );
          })}
        </div>
      </section>

      <section>
        <div className="glass-strong rounded-[2rem] p-8 md:p-12 text-center">
          <h2 className="font-display text-3xl md:text-4xl text-balance">{content.cta_title}</h2>
          <p className="mt-3 text-muted-foreground max-w-xl mx-auto">{content.cta_body}</p>
          <Link to="/shop" className="mt-6 inline-flex items-center gap-2 rounded-full bg-primary text-primary-foreground px-6 py-3 text-sm font-medium hover:opacity-95 transition">
            {content.cta_button} <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </section>
    </div>
  );
}
