import { createFileRoute, Link } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { Leaf, Heart, ShieldCheck, Sparkles, Target, Eye, ArrowRight } from "lucide-react";
import lifestyle from "@/assets/lifestyle-mother.webp";
import heroFounders from "@/assets/hero-founders-atelier.jpg";
import heroInfant from "@/assets/hero-infant-organic.jpg";

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
  component: About,
});

const promiseStats = [
  { value: "100%", label: "Certified organic cotton" },
  { value: "0", label: "Harsh chemicals or dyes" },
];

const whyChoose = [
  { icon: Leaf, t: "100% organic cotton", d: "Soft, breathable and grown without synthetic pesticides." },
  { icon: Heart, t: "Gentle on sensitive skin", d: "Skin-safe dyes and seam-friendly finishing." },
  { icon: Sparkles, t: "Easy, fuss-free wear", d: "Designed around the rhythms of everyday parenting." },
  { icon: ShieldCheck, t: "Durable & consistent", d: "Quality you can trust, wash after wash." },
];

const founders = [
  {
    name: "Ravikumar T",
    role: "Founder & Director",
    image: heroFounders,
    paras: [
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
    image: heroInfant,
    paras: [
      "Vasudev R is a founder of Mini's n More, a premium baby brand dedicated to creating products that parents worldwide can trust without compromise. His journey began at Ernst & Young (EY), a prestigious Big 4 auditing company, where he learned financial rigor, compliance, ethical business practices, and attention to detail—values that remain central to Mini's n More today.",
      "After EY, Vasudev moved into marketing and the FMCG sector, becoming an expert in marketing, super stockist, and C&F (Carrying & Forwarding) operations. This unique combination gave him a competitive edge: he understood both how to move products through the supply chain and how to position them effectively to resonate with parents. His experience covers the complete picture—from product conception and branding to distribution and final sale.",
      "Vasudev brings strong expertise in distribution management, channel coordination, inventory control, warehousing, and logistics, building robust networks that reach every corner of the market. Holding an MBA in Finance from Indiana University of Pennsylvania, U.S.A., with specialized knowledge in finance, marketing, and logistics & supply chain management, he leverages his Big 4 auditing background and FMCG mastery alongside Ravikumar to build Mini's n More into a globally trusted brand.",
    ],
    quote:
      "My career taught me that trust is earned through consistency and reliability. At Minis n More, we apply those same principles to every baby product. Because when it comes to your child, excellence isn't optional—it's essential.",
  },
];

function About() {
  return (
    <div className="px-4 md:px-6 space-y-12 md:space-y-16">
      {/* Hero */}
      <motion.section
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.7 }}
        className="relative glass-strong rounded-[1.75rem] overflow-hidden"
      >
        <div className="grid md:grid-cols-2 gap-0 items-stretch">
          <div className="p-5 md:p-8 flex flex-col justify-center">
            <div className="text-xs uppercase tracking-[0.28em] text-primary">Our story</div>
            <h1 className="font-display text-3xl md:text-5xl mt-1.5 text-balance leading-tight">
              Crafted with <span className="font-script text-primary">love</span> for your minis.
            </h1>
            <p className="mt-3 text-sm md:text-base text-foreground/85 leading-relaxed">
              A baby's skin is incredibly delicate — softer, thinner and far more sensitive than we often realise. What we choose for them in those early days plays a bigger role than we think.
            </p>
          </div>
          <div className="relative min-h-[320px] md:min-h-[460px]">
            <img src={lifestyle} alt="Mother and baby" className="absolute inset-0 h-full w-full object-cover" />
          </div>
        </div>
      </motion.section>

      {/* Story long-form — stats left, narrative right */}
      <section className="relative grid md:grid-cols-12 gap-6 md:gap-10 items-start py-2 md:py-4">
        <div className="md:col-span-5">
          <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground">Why we exist</div>
          <h2 className="font-display text-3xl md:text-[2.35rem] mt-2 text-balance leading-[1.15]">
            <span className="font-script text-primary text-4xl md:text-5xl align-middle">2,500</span>{" "}
            outfits before the age of two.
          </h2>
          <p className="mt-4 text-sm text-muted-foreground leading-relaxed">
            Most are treated with chemicals that shouldn&apos;t touch an adult&apos;s skin, let alone a newborn&apos;s. We set out to change that — quietly, carefully, one essential at a time.
          </p>
          <div className="grid grid-cols-2 gap-3 mt-6">
            {promiseStats.map((s) => (
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
            <div className="text-[10px] uppercase tracking-[0.28em] text-muted-foreground">
              — The Mini&apos;s n More Promise
            </div>
          </div>
        </div>
        <div className="md:col-span-7 md:border-l md:border-border/50 md:pl-8 lg:pl-10 space-y-5 text-foreground/85 leading-relaxed">
          <p>
            At Mini&apos;s n More, we create thoughtfully designed baby essentials that bring together comfort, quality and gentle care. At the heart of everything we make is premium organic cotton — chosen for its exceptional softness, natural stretch and breathable comfort. Its smooth, plush feel is especially gentle on delicate, sensitive skin, offering a safe and soothing experience for your little one.
          </p>
          <p>
            Like many parents, our journey began with questions and concerns. Skin rashes, confusing labels and uncertainty about chemical exposure made us realise how difficult it was to truly know what was safe for a newborn. The reality is, babies can absorb more through their skin than adults, yet most clothing undergoes chemical-heavy processing.
          </p>
          <blockquote className="border-l-2 border-primary/40 pl-4 italic text-foreground/70">
            That realisation led us here.
          </blockquote>
          <p>
            We set out to build a line of baby essentials using certified organic cotton, minimal processing and skin-safe dyes — products we could trust for our own children. Every piece is designed to be practical, reliable and made with careful attention to detail, helping make everyday parenting just a little simpler.
          </p>
          <p>
            From the earliest days to daily moments, we focus on creating essentials that feel as good as they look — because when it comes to your baby, every touch should be pure, soft and reassuring.
          </p>
        </div>
      </section>

      {/* Why choose */}
      <section>
        <div className="text-center mb-8">
          <div className="text-xs uppercase tracking-[0.28em] text-primary">Why choose Mini's n More</div>
          <h2 className="font-display text-3xl md:text-5xl mt-2">Honest essentials, gently made.</h2>
        </div>
        <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 md:gap-5">
          {whyChoose.map((f, i) => {
            const Icon = f.icon;
            return (
              <motion.li
                key={f.t}
                initial={{ opacity: 0, y: 18 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: "-60px" }}
                transition={{ duration: 0.5, delay: i * 0.06 }}
                className="glass rounded-3xl p-6 h-full"
              >
                <span className="h-11 w-11 grid place-items-center rounded-2xl bg-primary/10 text-primary">
                  <Icon className="h-5 w-5" />
                </span>
                <div className="font-display text-lg mt-4">{f.t}</div>
                <p className="text-sm text-muted-foreground mt-1.5 leading-relaxed">{f.d}</p>
              </motion.li>
            );
          })}
        </ul>
        <p className="mt-8 text-center text-sm text-muted-foreground max-w-2xl mx-auto leading-relaxed">
          We believe a brand's real responsibility is to help parents understand why something matters — and to earn their trust through honesty. No overpromises, no shortcuts. Just thoughtfully made essentials for babies with sensitive skin, and for parents who want fewer things to worry about.
        </p>
      </section>

      {/* Vision & Mission */}
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
            The world's most <span className="font-script text-primary">trusted</span> baby essentials brand.
          </h3>
          <p className="mt-4 text-foreground/85 leading-relaxed">
            Mini's n More envisions being the world's most trusted baby essentials brand, nurturing every child's early years with products that are safe, gentle and thoughtfully designed for modern families.
          </p>
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
            Beautifully crafted essentials parents can <span className="font-script text-primary">trust</span>.
          </h3>
          <div className="mt-4 space-y-3 text-foreground/85 leading-relaxed">
            <p>
              At Mini's n More we deliver beautifully crafted baby essentials that parents can trust — designed with uncompromising attention to safety, softness and everyday practicality.
            </p>
            <p>
              We design intuitive, high-quality and beautifully functional baby products that solve real parenting challenges.
            </p>
            <p>
              We create innovative, sustainable and scientifically backed baby products that meet the highest global standards of safety and quality — empowering parents across every continent to nurture their children with confidence and peace of mind.
            </p>
          </div>
        </motion.div>
      </section>

      {/* Founders */}
      <section>
        <div className="text-center mb-10">
          <div className="text-xs uppercase tracking-[0.28em] text-primary">Meet the founders</div>
          <h2 className="font-display text-3xl md:text-5xl mt-2">The hands behind <span className="font-script text-primary">Mini's n More</span></h2>
        </div>
        <div className="grid md:grid-cols-2 gap-5 md:gap-8">
          {founders.map((f, i) => (
            <motion.article
              key={f.name}
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-60px" }}
              transition={{ duration: 0.7, delay: i * 0.1 }}
              className="glass-strong rounded-[2rem] overflow-hidden flex flex-col"
            >
              <div className="relative aspect-[16/10] overflow-hidden">
                <img src={f.image} alt={f.name} className="absolute inset-0 h-full w-full object-cover" loading="lazy" />
                <div className="absolute inset-0 bg-gradient-to-t from-foreground/55 to-transparent" />
                <div className="absolute bottom-4 left-5 right-5 text-background">
                  <div className="text-[10px] uppercase tracking-[0.28em] opacity-90">{f.role}</div>
                  <div className="font-display text-3xl md:text-4xl leading-tight">{f.name}</div>
                </div>
              </div>
              <div className="p-6 md:p-8 space-y-3 text-foreground/85 leading-relaxed text-sm md:text-base">
                {f.paras.map((p, idx) => <p key={idx}>{p}</p>)}
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
          ))}
        </div>
      </section>

      {/* CTA */}
      <section>
        <div className="glass-strong rounded-[2rem] p-8 md:p-12 text-center">
          <h2 className="font-display text-3xl md:text-4xl text-balance">Soft, organic, made to be loved.</h2>
          <p className="mt-3 text-muted-foreground max-w-xl mx-auto">Explore the collection that started it all — gentle essentials for the first 24 months.</p>
          <Link to="/shop" className="mt-6 inline-flex items-center gap-2 rounded-full bg-primary text-primary-foreground px-6 py-3 text-sm font-medium hover:opacity-95 transition">
            Shop the collection <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </section>
    </div>
  );
}