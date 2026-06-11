import { useEffect, useState } from "react";
import { getCachedProducts, loadProducts, subscribeProducts } from "@/lib/storefront";
import { products as seedProducts, type Product } from "@/data/products";

export function useProducts(): Product[] {
  const [list, setList] = useState<Product[]>(getCachedProducts() ?? seedProducts);
  useEffect(() => {
    let alive = true;
    if (!getCachedProducts()) {
      loadProducts().then((p) => { if (alive) setList(p); });
    } else {
      setList(getCachedProducts()!);
    }
    const unsub = subscribeProducts((p) => { if (alive) setList(p); });
    return () => { alive = false; unsub(); };
  }, []);
  return list;
}

export function useProduct(slug: string | undefined): Product | undefined {
  const all = useProducts();
  if (!slug) return undefined;
  return all.find((p) => p.slug === slug) ?? seedProducts.find((p) => p.slug === slug);
}
