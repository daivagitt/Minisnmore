import { useEffect, useRef } from "react";
import { toast } from "sonner";
import { loadProducts } from "@/lib/storefront";
import { useProducts } from "@/hooks/useProducts";
import { formatCartSyncMessage } from "@/lib/cart-catalog";
import { useShop } from "@/store/shop";

/** Keep wishlist and cart aligned with the live (active-only) product catalog. */
export function CatalogSync() {
  const products = useProducts();
  const { wishlist, pruneWishlist, syncCartWithCatalog } = useShop();
  const lastCartMessage = useRef("");

  useEffect(() => {
    void loadProducts();
  }, []);

  useEffect(() => {
    if (products.length === 0) return;

    const validIds = products.map((p) => p.id);
    const valid = new Set(validIds);
    if (wishlist.some((id) => !valid.has(id))) {
      pruneWishlist(validIds);
    }

    const sync = syncCartWithCatalog(products);
    const message = formatCartSyncMessage(sync);
    if (!message) {
      lastCartMessage.current = "";
    } else if (message !== lastCartMessage.current) {
      lastCartMessage.current = message;
      toast.info(message, { id: "cart-catalog-sync" });
    }
  }, [products, wishlist, pruneWishlist, syncCartWithCatalog]);

  return null;
}
