import { motion, AnimatePresence } from "framer-motion";
import { X, Star } from "lucide-react";
import { Link } from "@tanstack/react-router";
import { useShop } from "@/store/shop";

export function QuickView() {
  const { quickView, setQuickView, addToCart } = useShop();
  return (
    <AnimatePresence>
      {quickView && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => setQuickView(null)} className="fixed inset-0 z-[70] bg-foreground/30 backdrop-blur-md" />
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 20 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.95, y: 20 }}
            transition={{ duration: 0.4, ease: [0.2, 0.8, 0.2, 1] }}
            className="fixed inset-4 md:inset-auto md:top-1/2 md:left-1/2 md:-translate-x-1/2 md:-translate-y-1/2 z-[71] md:w-[820px] max-h-[90vh]">
            <div className="glass-strong rounded-[2rem] overflow-hidden grid md:grid-cols-2 h-full">
              <div className="relative aspect-square md:aspect-auto bg-secondary/40">
                <img src={quickView.image} alt={quickView.name} className="h-full w-full object-cover" />
              </div>
              <div className="p-6 md:p-8 flex flex-col overflow-y-auto relative">
                <button onClick={() => setQuickView(null)} className="absolute top-4 right-4 h-9 w-9 grid place-items-center rounded-full glass hover:scale-105 transition">
                  <X className="h-4 w-4" />
                </button>
                <div className="text-xs uppercase tracking-[0.22em] text-muted-foreground">{quickView.fabric}</div>
                <h3 className="font-display text-3xl mt-1">{quickView.name}</h3>
                <div className="flex items-center gap-2 text-sm mt-2">
                  <Star className="h-3.5 w-3.5 fill-primary text-primary" />
                  <span>{quickView.rating}</span>
                  <span className="text-muted-foreground">({quickView.reviewCount} reviews)</span>
                </div>
                <p className="text-sm text-muted-foreground mt-4 leading-relaxed">{quickView.description}</p>
                <div className="mt-5 flex items-baseline gap-3">
                  <span className="text-2xl font-medium">&#8377;{quickView.price.toLocaleString("en-IN")}</span>
                  {quickView.compareAt && <span className="text-sm line-through text-muted-foreground">&#8377;{quickView.compareAt.toLocaleString("en-IN")}</span>}
                </div>
                <div className="mt-5">
                  <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2">Sizes</div>
                  <div className="flex flex-wrap gap-2">
                    {quickView.sizes.map((s) => (
                      <span key={s} className="glass rounded-full px-3 py-1.5 text-xs">{s}</span>
                    ))}
                  </div>
                </div>
                <div className="mt-auto pt-6 flex gap-2">
                  <button onClick={() => { addToCart(quickView); setQuickView(null); }}
                    className="flex-1 rounded-full bg-primary text-primary-foreground py-3 font-medium hover:shadow-glow transition">
                    Add to basket
                  </button>
                  <Link to="/product/$slug" params={{ slug: quickView.slug }} onClick={() => setQuickView(null)}
                    className="rounded-full glass px-5 py-3 text-sm font-medium hover:bg-white/60 transition">
                    Details
                  </Link>
                </div>
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
