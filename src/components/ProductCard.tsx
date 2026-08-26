import type { MouseEvent } from "react";
import { motion } from "framer-motion";
import { Link } from "@tanstack/react-router";
import { Heart, Eye, Star } from "lucide-react";
import type { Product } from "@/data/products";
import { LazyImage } from "@/components/LazyImage";
import { useShop } from "@/store/shop";

export function ProductCard({ product, index = 0 }: { product: Product; index?: number }) {
  const { toggleWish, wishlist, setQuickView } = useShop();
  const wished = wishlist.includes(product.id);

  const handleWishlistClick = (e: MouseEvent) => {
    e.preventDefault();
    toggleWish(product.id, product.name);
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-50px" }}
      transition={{ duration: 0.5, delay: Math.min(index * 0.04, 0.2), ease: [0.2, 0.8, 0.2, 1] }}
      className="group hover-lift h-full">
      <div className="glass rounded-3xl p-2.5 md:p-3 relative overflow-hidden flex flex-col h-full">
        <Link to="/product/$slug" params={{ slug: product.slug }} className="block">
          <div className="relative aspect-square overflow-hidden rounded-2xl bg-secondary/40">
            <LazyImage src={product.image} alt={product.name} priority={index < 6 ? "high" : "auto"}
              className="img-zoom h-full w-full object-cover" />
            <div className="absolute top-3 left-3 flex flex-col gap-1.5">
              {product.isNew && <span className="glass-strong text-[10px] uppercase tracking-wider px-2.5 py-1 rounded-full">New</span>}
              {product.isBestseller && <span className="bg-primary/90 text-primary-foreground text-[10px] uppercase tracking-wider px-2.5 py-1 rounded-full">Bestseller</span>}
            </div>
          </div>
        </Link>

        <div className="absolute top-3 right-3 md:top-5 md:right-5 flex flex-col items-end gap-1.5 md:gap-2 transition-all duration-500">
          <button onClick={handleWishlistClick}
            aria-label={wished ? "Remove from wishlist" : "Add to wishlist"}
            aria-pressed={wished}
            className={`glass-strong inline-flex items-center gap-1.5 rounded-full h-9 w-9 md:h-auto md:w-auto justify-center md:px-3 md:py-1.5 text-[10px] font-semibold uppercase tracking-[0.18em] hover:scale-105 active:scale-95 transition ${wished ? "text-primary bg-primary/15" : ""}`}>
            <Heart className={`h-3.5 w-3.5 transition-transform ${wished ? "fill-primary scale-110" : ""}`} />
            <span className="hidden md:inline">{wished ? "Saved" : "Wishlist"}</span>
          </button>
          <button onClick={(e) => { e.preventDefault(); setQuickView(product); }}
            className="glass-strong h-9 w-9 grid place-items-center rounded-full hover:scale-110 transition" aria-label="Quick view">
            <Eye className="h-4 w-4" />
          </button>
        </div>

        <div className="px-2 pt-3 pb-2 flex flex-col flex-1">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0 flex-1">
              <Link to="/product/$slug" params={{ slug: product.slug }} className="font-display text-base md:text-lg leading-tight hover:text-primary transition line-clamp-1 block">
                {product.name}
              </Link>
              <div className="text-xs text-muted-foreground mt-0.5 line-clamp-1">{product.tagline}</div>
            </div>
            <div className="flex items-center gap-1 text-xs shrink-0">
              <Star className="h-3 w-3 fill-primary text-primary" /> {product.rating}
            </div>
          </div>
          <div className="mt-auto pt-2.5 flex items-baseline gap-2">
            <span className="font-medium">&#8377;{product.price.toLocaleString("en-IN")}</span>
            {product.compareAt && <span className="text-xs line-through text-muted-foreground">&#8377;{product.compareAt.toLocaleString("en-IN")}</span>}
          </div>
        </div>
      </div>
    </motion.div>
  );
}
