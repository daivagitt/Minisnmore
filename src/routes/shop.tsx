import { createFileRoute, useNavigate, useSearch } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { motion } from "framer-motion";
import { ProductCard } from "@/components/ProductCard";
import { type Fabric } from "@/data/products";
import { useCategories } from "@/hooks/useCategories";
import { useProducts } from "@/hooks/useProducts";
import {
  buildShopSearch,
  filterAndSortProducts,
  hasActiveFilters,
  parseShopSearch,
  SHOP_FILTER_FABRICS,
  type ShopSearch,
} from "@/lib/shop-filters";
import { SlidersHorizontal, X } from "lucide-react";

export const Route = createFileRoute("/shop")({
  validateSearch: (s) => parseShopSearch(s),
  head: () => ({
    meta: [
      { title: "Shop · Mini's n More" },
      { name: "description", content: "Browse heirloom baby clothing — filter by category and fabric." },
    ],
  }),
  component: Shop,
});

function Shop() {
  const products = useProducts();
  const categories = useCategories();
  const search = useSearch({ from: "/shop" });
  const navigate = useNavigate({ from: "/shop" });
  const [showFilters, setShowFilters] = useState(false);

  const filters = useMemo(() => parseShopSearch(search), [search]);
  const filtered = useMemo(() => filterAndSortProducts(products, filters), [products, filters]);

  const patchSearch = (patch: Partial<ShopSearch>) => {
    navigate({
      search: buildShopSearch({ ...filters, ...patch }),
      replace: true,
    });
  };

  const toggleFabric = (f: Fabric) => {
    patchSearch({ fabric: filters.fabric === f ? undefined : f });
  };

  const toggleCategory = (slug: string) => {
    patchSearch({ category: filters.category === slug ? undefined : slug });
  };

  const clearFilters = () => {
    navigate({
      search: buildShopSearch({ sort: filters.sort ?? "new" }),
      replace: true,
    });
  };

  const activeFilters = hasActiveFilters(filters);

  return (
    <div className="px-4 md:px-6">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6 }}
        className="glass-strong rounded-[2rem] p-6 md:p-10 mb-6"
      >
        <div className="text-xs uppercase tracking-[0.28em] text-primary">All pieces</div>
        <h1 className="font-display text-3xl md:text-5xl mt-2">
          The <span className="font-script text-primary">Collection</span>
        </h1>
        <p className="mt-3 text-muted-foreground max-w-xl">
          Considered, organic, and made to be loved beyond a single season.
        </p>
        {activeFilters && (
          <p className="mt-3 text-sm text-primary">
            Showing {filtered.length} {filtered.length === 1 ? "piece" : "pieces"} matching your filters.
          </p>
        )}
      </motion.div>

      <div className="flex flex-wrap items-center gap-3 mb-6">
        <button
          type="button"
          onClick={() => setShowFilters((v) => !v)}
          className="glass rounded-full px-4 py-2.5 text-sm flex items-center gap-2 lg:hidden"
        >
          <SlidersHorizontal className="h-4 w-4" /> Filters
        </button>
        <div className={`${showFilters ? "flex" : "hidden"} lg:flex flex-wrap gap-2 items-center flex-1`}>
          {categories.length > 0 && (
            <>
              <span className="text-xs uppercase tracking-wider text-muted-foreground mr-1">Category</span>
              {categories.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => toggleCategory(c.slug)}
                  className={`rounded-full px-3.5 py-1.5 text-xs transition ${filters.category === c.slug ? "bg-primary text-primary-foreground" : "glass hover:bg-white/60"}`}
                >
                  {c.name}
                </button>
              ))}
            </>
          )}
          <span className="text-xs uppercase tracking-wider text-muted-foreground ml-1 mr-1">Fabric</span>
          {SHOP_FILTER_FABRICS.map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => toggleFabric(f)}
              className={`rounded-full px-3.5 py-1.5 text-xs transition ${filters.fabric === f ? "bg-primary text-primary-foreground" : "glass hover:bg-white/60"}`}
            >
              {f}
            </button>
          ))}
          {activeFilters && (
            <button
              type="button"
              onClick={clearFilters}
              className="rounded-full px-3 py-1.5 text-xs flex items-center gap-1 text-muted-foreground hover:text-foreground"
            >
              <X className="h-3 w-3" /> Clear
            </button>
          )}
        </div>
        <select
          value={filters.sort ?? "new"}
          onChange={(e) => patchSearch({ sort: e.target.value as ShopSearch["sort"] })}
          className="glass rounded-full px-4 py-2.5 text-sm bg-transparent"
        >
          <option value="new">Newest</option>
          <option value="lo">Price: Low to high</option>
          <option value="hi">Price: High to low</option>
        </select>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3 md:gap-5">
        {filtered.map((p, i) => (
          <ProductCard key={p.id} product={p} index={i} />
        ))}
      </div>
      {filtered.length === 0 && (
        <div className="glass-strong rounded-3xl p-16 text-center text-muted-foreground">
          Nothing matches just yet. Try a different category or fabric.
          {activeFilters && (
            <div className="mt-4">
              <button type="button" onClick={clearFilters} className="text-primary story-link text-sm">
                Clear all filters
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
