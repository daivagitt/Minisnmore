import { motion } from "framer-motion";
import { useMemo } from "react";
import { ProductCard } from "@/components/ProductCard";
import { useProducts } from "@/hooks/useProducts";
import type { Product } from "@/data/products";

type Props = {
  eyebrow: string;
  title: string;
  script: string;
  subtitle: string;
  /** Pre-resolved list (e.g. admin home-section picks). Skips catalog filter/sort. */
  products?: Product[];
  filter?: (p: Product) => boolean | undefined;
  sort?: (a: Product, b: Product) => number;
};

export function CollectionView({ eyebrow, title, script, subtitle, products: preset, filter, sort }: Props) {
  const catalog = useProducts();
  const list = useMemo(() => {
    if (preset) return preset;
    let l = filter ? catalog.filter(filter) : [...catalog];
    if (sort) l = [...l].sort(sort);
    return l;
  }, [catalog, preset, filter, sort]);

  return (
    <div className="px-4 md:px-6">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6 }}
        className="glass-strong rounded-[2rem] p-8 md:p-12 mb-8"
      >
        <div className="text-xs uppercase tracking-[0.28em] text-primary">{eyebrow}</div>
        <h1 className="font-display text-4xl md:text-6xl mt-2">
          {title} <span className="font-script text-primary">{script}</span>
        </h1>
        <p className="mt-3 text-muted-foreground max-w-xl">{subtitle}</p>
      </motion.div>

      {list.length === 0 ? (
        <div className="glass rounded-3xl p-10 text-center text-sm text-muted-foreground">
          No products found in this collection yet.
        </div>
      ) : (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6 items-stretch">
          {list.map((p, i) => (
            <ProductCard key={p.id} product={p} index={i} />
          ))}
        </div>
      )}
    </div>
  );
}