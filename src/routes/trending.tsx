import { createFileRoute } from "@tanstack/react-router";
import { useMemo } from "react";
import { CollectionView } from "@/components/CollectionView";
import { useHomeSections } from "@/hooks/useHomeSections";
import { useProducts } from "@/hooks/useProducts";
import { resolveHomeSectionProducts, sectionProductSlugs } from "@/lib/home-products";
import type { Product } from "@/data/products";

function trendingFallback(products: Product[]) {
  return [...products].sort((a, b) => b.reviewCount - a.reviewCount);
}

function TrendingPage() {
  const catalog = useProducts();
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
