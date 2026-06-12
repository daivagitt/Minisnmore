import { createFileRoute } from "@tanstack/react-router";
import { useMemo } from "react";
import { CollectionView } from "@/components/CollectionView";
import { useHomeSections } from "@/hooks/useHomeSections";
import { useProducts, useProductsLoaded } from "@/hooks/useProducts";
import { ProductGridSkeleton } from "@/components/ProductGridSkeleton";
import { resolveHomeSectionProducts, sectionProductSlugs } from "@/lib/home-products";
import type { Product } from "@/data/products";

function trendingFallback(products: Product[]) {
  return [...products].sort((a, b) => b.reviewCount - a.reviewCount);
}

function TrendingPage() {
  const catalog = useProducts();
  const productsLoaded = useProductsLoaded();
  const { subtitleFor, byKey } = useHomeSections();
  const products = useMemo(
    () =>
      resolveHomeSectionProducts(
        catalog,
        sectionProductSlugs(byKey.get("trending")?.payload as Record<string, unknown>),
        trendingFallback,
      ),
    [catalog, byKey],
  );

  if (!productsLoaded) {
    return (
      <div className="px-4 md:px-6">
        <div className="glass-strong rounded-[2rem] p-8 md:p-12 mb-8">
          <div className="text-xs uppercase tracking-[0.28em] text-primary">
            {subtitleFor("trending", "Loved this week")}
          </div>
          <h1 className="font-display text-4xl md:text-6xl mt-2">
            Trending <span className="font-script text-primary">now</span>
          </h1>
          <p className="mt-3 text-muted-foreground max-w-xl">
            {subtitleFor("trending", "The styles families are reaching for right now.")}
          </p>
        </div>
        <ProductGridSkeleton count={8} />
      </div>
    );
  }

  return (
    <CollectionView
      eyebrow={subtitleFor("trending", "Loved this week")}
      title="Trending"
      script="now"
      subtitle={subtitleFor("trending", "The styles families are reaching for right now.")}
      products={products}
    />
  );
}

export const Route = createFileRoute("/trending")({
  head: () => ({ meta: [{ title: "Trending · Mini's n More" }] }),
  component: TrendingPage,
});
