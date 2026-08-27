import { createFileRoute, useNavigate, useSearch } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { ProductCard } from "@/components/ProductCard";
import { ListPagination } from "@/components/ListPagination";
import { type AgeRange, type Fabric } from "@/data/products";
import { useCategories } from "@/hooks/useCategories";
import { useProducts } from "@/hooks/useProducts";
import {
  buildShopSearch,
  filterAndSortProducts,
  hasActiveFilters,
  parseShopSearch,
  SHOP_AGE_FILTERS,
  SHOP_FILTER_FABRICS,
  type ShopSearch,
} from "@/lib/shop-filters";
import { GRID_PAGE_SIZE, clampPage, pageSlice, totalPages } from "@/lib/pagination";
import { Search, SlidersHorizontal, X } from "lucide-react";
import { StorefrontPageHeader } from "@/components/StorefrontPageHeader";

export const Route = createFileRoute("/shop")({
  validateSearch: (s) => parseShopSearch(s),
  head: () => ({
    meta: [
      { title: "Shop · Mini's n More" },
      {
        name: "description",
        content: "Browse heirloom baby clothing — search and filter by category, age, and fabric.",
      },
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
  const [queryDraft, setQueryDraft] = useState(() => parseShopSearch(search).q ?? "");

  const filters = useMemo(() => parseShopSearch(search), [search]);
  const filtered = useMemo(() => filterAndSortProducts(products, filters), [products, filters]);

  // Keep draft in sync when URL changes (back/forward, header “view all”).
  useEffect(() => {
    setQueryDraft(filters.q ?? "");
  }, [filters.q]);

  // Debounce writing `q` into the URL so typing stays smooth.
  useEffect(() => {
    const handle = window.setTimeout(() => {
      const nextQ = queryDraft.trim();
      const currentQ = filters.q ?? "";
      if (nextQ.toLowerCase() === currentQ.toLowerCase()) return;
      navigate({
        search: buildShopSearch({ ...filters, q: nextQ || undefined, page: undefined }),
        replace: true,
      });
    }, 250);
    return () => window.clearTimeout(handle);
  }, [queryDraft, filters, navigate]);

  const patchSearch = (patch: Partial<ShopSearch>) => {
    const keepPage = Object.prototype.hasOwnProperty.call(patch, "page");
    navigate({
      search: buildShopSearch({
        ...filters,
        ...patch,
        page: keepPage ? patch.page : undefined,
      }),
      replace: true,
    });
  };

  const toggleFabric = (f: Fabric) => {
    patchSearch({ fabric: filters.fabric === f ? undefined : f });
  };

  const toggleCategory = (slug: string) => {
    patchSearch({ category: filters.category === slug ? undefined : slug });
  };

  const toggleAge = (age: AgeRange) => {
    patchSearch({ age: filters.age === age ? undefined : age });
  };

  const clearFilters = () => {
    setQueryDraft("");
    navigate({
      search: buildShopSearch({ sort: filters.sort ?? "new" }),
      replace: true,
    });
  };

  const activeFilters = hasActiveFilters(filters);
  const pages = totalPages(filtered.length, GRID_PAGE_SIZE);
  const page = clampPage(filters.page ?? 1, pages);
  const pageItems = pageSlice(filtered, page, GRID_PAGE_SIZE);

  useEffect(() => {
    if (filtered.length === 0) return;
    if ((filters.page ?? 1) === page) return;
    navigate({
      search: buildShopSearch({ ...filters, page: page > 1 ? page : undefined }),
      replace: true,
    });
  }, [filtered.length, filters, page, navigate]);

  return (
    <div className="px-4 md:px-6">
      <StorefrontPageHeader
        eyebrow="All pieces"
        heading={
          <>
            The <span className="font-script text-primary">Collection</span>
          </>
        }
        subtitle="Considered, organic, and made to be loved beyond a single season."
      >
        {activeFilters ? (
          <p className="mt-2 text-sm text-primary">
            Showing {filtered.length} {filtered.length === 1 ? "piece" : "pieces"} matching your filters.
          </p>
        ) : null}
      </StorefrontPageHeader>

      <div className="mb-4">
        <label className="relative block max-w-xl">
          <span className="sr-only">Search products</span>
          <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            value={queryDraft}
            onChange={(e) => setQueryDraft(e.target.value)}
            placeholder="Search by name, category, fabric, or SKU…"
            className="w-full glass rounded-full pl-11 pr-10 py-2.5 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/30"
            autoComplete="off"
            enterKeyHint="search"
          />
          {queryDraft ? (
            <button
              type="button"
              aria-label="Clear search"
              onClick={() => {
                setQueryDraft("");
                patchSearch({ q: undefined });
              }}
              className="absolute right-2 top-1/2 -translate-y-1/2 h-8 w-8 grid place-items-center rounded-full hover:bg-white/50"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          ) : null}
        </label>
      </div>

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
          <span className="text-xs uppercase tracking-wider text-muted-foreground ml-1 mr-1">Age</span>
          {SHOP_AGE_FILTERS.map((a) => (
            <button
              key={a.value}
              type="button"
              onClick={() => toggleAge(a.value)}
              className={`rounded-full px-3.5 py-1.5 text-xs transition ${filters.age === a.value ? "bg-primary text-primary-foreground" : "glass hover:bg-white/60"}`}
            >
              {a.label}
            </button>
          ))}
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

      <div id="shop-product-grid" className="grid grid-cols-2 lg:grid-cols-5 gap-3 md:gap-5">
        {pageItems.map((p, i) => (
          <ProductCard key={p.id} product={p} index={i} />
        ))}
      </div>
      <ListPagination
        page={page}
        totalPages={pages}
        totalItems={filtered.length}
        pageSize={GRID_PAGE_SIZE}
        scrollToId="shop-product-grid"
        onPageChange={(next) => patchSearch({ page: next > 1 ? next : undefined })}
      />
      {filtered.length === 0 && (
        <div className="glass-strong rounded-3xl p-16 text-center text-muted-foreground">
          {activeFilters
            ? "Nothing matches your search or filters yet."
            : "No products are available right now."}
          {activeFilters && (
            <div className="mt-4">
              <button type="button" onClick={clearFilters} className="text-primary story-link text-sm">
                Clear search and filters
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
