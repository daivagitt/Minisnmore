import { createFileRoute, Link } from "@tanstack/react-router";
import { useProducts } from "@/hooks/useProducts";
import { useShop } from "@/store/shop";
import { ProductCard } from "@/components/ProductCard";
import { ListPagination } from "@/components/ListPagination";
import { useListPagination } from "@/hooks/useListPagination";
import { GRID_PAGE_SIZE } from "@/lib/pagination";
import { StorefrontPageHeader } from "@/components/StorefrontPageHeader";

export const Route = createFileRoute("/wishlist")({
  head: () => ({ meta: [{ title: "Wishlist · Mini's n More" }] }),
  component: Wishlist,
});

function Wishlist() {
  const { wishlist } = useShop();
  const products = useProducts();
  const items = products.filter((p) => wishlist.includes(p.id));
  const paging = useListPagination(items, {
    pageSize: GRID_PAGE_SIZE,
    resetKey: items.length,
  });
  return (
    <div className="px-4 md:px-6">
      <StorefrontPageHeader
        eyebrow="Saved softly"
        heading={
          <>
            Your <span className="font-script text-primary">wishlist</span>
          </>
        }
      />
      {items.length === 0 ? (
        <div className="glass rounded-3xl p-12 text-center">
          <p className="text-muted-foreground">No favourites yet — tap the heart on any piece.</p>
          <Link to="/shop" className="mt-4 inline-block rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm">Browse the edit</Link>
        </div>
      ) : (
        <>
          <div id="wishlist-grid" className="grid grid-cols-2 lg:grid-cols-5 gap-3 md:gap-5">
            {paging.pageItems.map((p, i) => <ProductCard key={p.id} product={p} index={i} />)}
          </div>
          <ListPagination
            page={paging.page}
            totalPages={paging.totalPages}
            totalItems={paging.totalItems}
            pageSize={paging.pageSize}
            scrollToId="wishlist-grid"
            onPageChange={paging.setPage}
          />
        </>
      )}
    </div>
  );
}
