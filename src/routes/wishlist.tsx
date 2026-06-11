import { createFileRoute, Link } from "@tanstack/react-router";
import { useProducts } from "@/hooks/useProducts";
import { useShop } from "@/store/shop";
import { ProductCard } from "@/components/ProductCard";

export const Route = createFileRoute("/wishlist")({
  head: () => ({ meta: [{ title: "Wishlist · Mini's n More" }] }),
  component: Wishlist,
});

function Wishlist() {
  const { wishlist } = useShop();
  const products = useProducts();
  const items = products.filter((p) => wishlist.includes(p.id));
  return (
    <div className="px-4 md:px-6">
      <div className="glass-strong rounded-[2rem] p-8 md:p-12 mb-8">
        <div className="text-xs uppercase tracking-[0.28em] text-primary">Saved softly</div>
        <h1 className="font-display text-4xl md:text-6xl mt-2">Your <span className="font-script text-primary">wishlist</span></h1>
      </div>
      {items.length === 0 ? (
        <div className="glass rounded-3xl p-12 text-center">
          <p className="text-muted-foreground">No favourites yet — tap the heart on any piece.</p>
          <Link to="/shop" className="mt-4 inline-block rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm">Browse the edit</Link>
        </div>
      ) : (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6">
          {items.map((p, i) => <ProductCard key={p.id} product={p} index={i} />)}
        </div>
      )}
    </div>
  );
}
