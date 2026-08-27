import { createFileRoute, Link } from "@tanstack/react-router";
import { useProducts } from "@/hooks/useProducts";
import { useShop } from "@/store/shop";
import { ProductCard } from "@/components/ProductCard";
import { ListPagination } from "@/components/ListPagination";
import { useListPagination } from "@/hooks/useListPagination";
import { GRID_PAGE_SIZE } from "@/lib/pagination";
import { Heart } from "lucide-react";

export const Route = createFileRoute("/account/wishlist")({
  head: () => ({ meta: [{ title: "Wishlist · Mini's n More" }] }),
  component: WishlistPage,
});

function WishlistPage() {
  const { wishlist } = useShop();
  const products = useProducts();
  const items = products.filter((p) => wishlist.includes(p.id));
  const paging = useListPagination(items, {
    pageSize: GRID_PAGE_SIZE,
    resetKey: items.length,
  });

  return (
    <div className="glass-strong rounded-[2rem] p-6 md:p-8">
      <h1 className="font-display text-3xl mb-1 flex items-center gap-2"><Heart className="h-6 w-6 text-primary" /> Wishlist</h1>
      <p className="text-sm text-muted-foreground mb-6">Pieces you've saved for later.</p>

      {items.length === 0 ? (
        <div className="glass rounded-2xl p-8 text-center">
          <p className="text-sm text-muted-foreground">Nothing saved yet — tap the heart on any piece.</p>
          <Link to="/shop" className="mt-4 inline-block rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm">Browse the edit</Link>
        </div>
      ) : (
        <>
          <div id="account-wishlist-grid" className="grid grid-cols-2 lg:grid-cols-5 gap-3 md:gap-5">
            {paging.pageItems.map((p, i) => <ProductCard key={p.id} product={p} index={i} />)}
          </div>
          <ListPagination
            page={paging.page}
            totalPages={paging.totalPages}
            totalItems={paging.totalItems}
            pageSize={paging.pageSize}
            scrollToId="account-wishlist-grid"
            onPageChange={paging.setPage}
          />
        </>
      )}
    </div>
  );
}