import { createFileRoute } from "@tanstack/react-router";
import { motion, AnimatePresence } from "framer-motion";
import { type BlogPost } from "@/data/blog";
import { useBlogPosts } from "@/hooks/useBlogPosts";
import { ArrowRight, Clock, X } from "lucide-react";
import { useState, useEffect } from "react";

export const Route = createFileRoute("/blog")({
  head: () => ({
    meta: [
      { title: "Blogs — gentle stories from Mini's n More" },
      { name: "description", content: "Notes on organic cotton, rash-free baby clothing, fabric care and conscious parenting from the Mini's n More blogs." },
      { property: "og:title", content: "Blogs — Mini's n More" },
      { property: "og:description", content: "Stories on organic cotton, rash-free baby clothing and conscious parenting." },
    ],
  }),
  component: BlogIndex,
});

function BlogIndex() {
  const posts = useBlogPosts();
  const [active, setActive] = useState<BlogPost | null>(null);
  const [hero, ...rest] = posts;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setActive(null); };
    if (active) {
      document.body.style.overflow = "hidden";
      window.addEventListener("keydown", onKey);
    }
    return () => { document.body.style.overflow = ""; window.removeEventListener("keydown", onKey); };
  }, [active]);

  if (!hero) return <div className="px-6 py-24 text-center text-muted-foreground">No blog entries yet.</div>;

  return (
    <div className="px-4 md:px-6 space-y-10 md:space-y-14">
      <header className="text-center max-w-2xl mx-auto pt-2">
        <div className="text-[10px] sm:text-xs uppercase tracking-[0.32em] text-primary">The Blogs</div>
        <h1 className="font-display text-3xl sm:text-4xl md:text-6xl mt-3">Quiet notes for new parents</h1>
        <p className="text-sm sm:text-base text-muted-foreground mt-3 sm:mt-4">Stories on organic fabric, rash-free routines, and the small choices that make the first year softer.</p>
      </header>

      <button onClick={() => setActive(hero)} className="block w-full text-left group">
        <motion.article
          initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6 }}
          className="glass-strong rounded-[1.75rem] md:rounded-[2.5rem] overflow-hidden grid lg:grid-cols-2 gap-0">
          <div className="aspect-4/3 lg:aspect-auto overflow-hidden">
            <img src={hero.cover} alt={hero.title} className="h-full w-full object-cover group-hover:scale-[1.03] transition-transform duration-700" />
          </div>
          <div className="p-6 sm:p-8 md:p-12 flex flex-col justify-center gap-3 sm:gap-4">
            <div className="text-[10px] sm:text-xs uppercase tracking-[0.28em] text-primary">{hero.tag} · Featured</div>
            <h2 className="font-display text-2xl sm:text-3xl md:text-4xl leading-tight">{hero.title}</h2>
            <p className="text-sm sm:text-base text-muted-foreground">{hero.excerpt}</p>
            <div className="flex items-center gap-3 text-xs text-muted-foreground">
              <span>{hero.date}</span><span>·</span>
              <span className="flex items-center gap-1"><Clock className="h-3 w-3" /> {hero.readMins} min read</span>
            </div>
            <span className="inline-flex items-center gap-2 text-sm text-primary mt-1 group-hover:gap-3 transition-all">
              Read the story <ArrowRight className="h-4 w-4" />
            </span>
          </div>
        </motion.article>
      </button>

      <section className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">
        {rest.map((p, i) => (
          <motion.button
            key={p.slug} onClick={() => setActive(p)}
            initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
            transition={{ delay: i * 0.06 }}
            className="group block glass rounded-[1.5rem] md:rounded-[2rem] overflow-hidden h-full text-left">
            <div className="aspect-5/4 overflow-hidden">
              <img src={p.cover} alt={p.title} className="h-full w-full object-cover group-hover:scale-[1.04] transition-transform duration-700" loading="lazy" />
            </div>
            <div className="p-5 sm:p-6 space-y-2.5 sm:space-y-3">
              <div className="text-[10px] sm:text-[11px] uppercase tracking-[0.28em] text-primary">{p.tag}</div>
              <h3 className="font-display text-lg sm:text-xl leading-snug">{p.title}</h3>
              <p className="text-sm text-muted-foreground line-clamp-3">{p.excerpt}</p>
              <div className="flex items-center justify-between text-[11px] text-muted-foreground pt-1">
                <span>{p.date}</span>
                <span className="flex items-center gap-1"><Clock className="h-3 w-3" /> {p.readMins} min</span>
              </div>
            </div>
          </motion.button>
        ))}
      </section>

      <AnimatePresence>
        {active && (
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => setActive(null)}
            className="fixed inset-0 z-90 bg-foreground/40 backdrop-blur-md grid place-items-center p-3 sm:p-6 overflow-y-auto">
            <motion.div
              initial={{ y: 30, opacity: 0, scale: 0.97 }} animate={{ y: 0, opacity: 1, scale: 1 }} exit={{ y: 20, opacity: 0, scale: 0.98 }}
              transition={{ duration: 0.35, ease: [0.2, 0.8, 0.2, 1] }}
              onClick={(e) => e.stopPropagation()}
              className="relative w-full max-w-5xl my-6 glass-strong rounded-[1.5rem] sm:rounded-[2rem] overflow-hidden">
              <button onClick={() => setActive(null)}
                className="absolute top-3 right-3 sm:top-4 sm:right-4 z-10 h-10 w-10 grid place-items-center rounded-full glass hover:bg-white/70 transition" aria-label="Close">
                <X className="h-4 w-4" />
              </button>
              <div className="grid md:grid-cols-2">
                <div className="relative aspect-4/3 md:aspect-auto md:h-full overflow-hidden">
                  <img src={active.cover} alt={active.title} className="h-full w-full object-cover" />
                  <div className="absolute inset-0 bg-linear-to-t from-background/70 via-background/10 to-transparent md:bg-linear-to-r md:from-transparent md:via-transparent md:to-background/20" />
                  <div className="absolute bottom-0 left-0 right-0 p-5 sm:p-7">
                    <div className="text-[10px] sm:text-xs uppercase tracking-[0.28em] text-primary-foreground/90">{active.tag}</div>
                    <h2 className="font-display text-2xl sm:text-3xl md:text-4xl mt-2 text-foreground">{active.title}</h2>
                  </div>
                </div>
                <div className="p-5 sm:p-8 md:p-10 space-y-4 max-h-[80vh] md:max-h-[85vh] overflow-y-auto">
                  <div className="flex items-center gap-3 text-xs text-muted-foreground">
                    <span>{active.date}</span><span>·</span>
                    <span className="flex items-center gap-1"><Clock className="h-3 w-3" /> {active.readMins} min read</span>
                  </div>
                  <p className="text-base sm:text-lg text-foreground/85 leading-relaxed font-medium">{active.excerpt}</p>
                  {active.body.map((para, i) => (
                    <p key={i} className="text-sm sm:text-base text-foreground/80 leading-relaxed">{para}</p>
                  ))}
                </div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
