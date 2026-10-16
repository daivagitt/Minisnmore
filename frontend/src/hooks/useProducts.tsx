import { useEffect, useState } from "react";
import { getCachedProducts, loadProducts, subscribeProducts } from "@/lib/storefront";
import type { Product } from "@/data/products";

export function useProducts(): Product[] {
  const [list, setList] = useState<Product[]>(() => getCachedProducts() ?? []);
  useEffect(() => {
    let alive = true;
    const unsub = subscribeProducts((p) => {
      if (alive) setList(p);
    });
    void loadProducts()
      .then((p) => {
        if (alive) setList(p);
      })
      .catch(() => {});
    return () => {
      alive = false;
      unsub();
    };
  }, []);
  return list;
}

/** True once the live catalog request has finished (empty list is a real empty catalog). */
export function useProductsLoaded(): boolean {
  const [loaded, setLoaded] = useState(() => getCachedProducts() !== null);
  useEffect(() => {
    let alive = true;
    if (getCachedProducts()) {
      setLoaded(true);
      return;
    }
    void loadProducts()
      .catch(() => {})
      .finally(() => {
        if (alive) setLoaded(true);
      });
    const unsub = subscribeProducts(() => {
      if (alive) setLoaded(true);
    });
    return () => {
      alive = false;
      unsub();
    };
  }, []);
  return loaded;
}

export function useProduct(slug: string | undefined): Product | undefined {
  const all = useProducts();
  if (!slug) return undefined;
  return all.find((p) => p.slug === slug);
}
