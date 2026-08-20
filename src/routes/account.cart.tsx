import { createFileRoute, Link } from "@tanstack/react-router";
import { LazyImage } from "@/components/LazyImage";
import { EmptyCartState } from "@/components/EmptyCartState";
import { cartLineColorLabel, cartLineImage } from "@/lib/cart-catalog";
import { useShop } from "@/store/shop";
import { ShoppingBag, Plus, Minus, X, ArrowRight } from "lucide-react";

export const Route = createFileRoute("/account/cart")({
  head: () => ({ meta: [{ title: "Cart · Mini's n More" }] }),
  component: CartPage,
});

function CartPage() {
  const { cart, updateQty, promptRemoveFromCart, cartTotal, cartCount } = useShop();

  return (
    <div className="glass-strong rounded-[2rem] p-6 md:p-8">
      <h1 className="font-display text-3xl mb-1 flex items-center gap-2"><ShoppingBag className="h-6 w-6 text-primary" /> Your cart</h1>
      <p className="text-sm text-muted-foreground mb-6">{cartCount} item{cartCount === 1 ? "" : "s"} ready to ship.</p>

      {cart.length === 0 ? (
        <div className="glass rounded-2xl">
          <EmptyCartState compact />
        </div>
      ) : (
        <>
          <div className="space-y-3">
            {cart.map((it) => (
              <div key={it.product.id + it.size + it.color} className="glass rounded-2xl p-3 flex items-center gap-3">
                <LazyImage src={cartLineImage(it)} alt="" className="h-16 w-16 rounded-xl object-cover" priority="auto" />
                <div className="flex-1 min-w-0">
                  <div className="font-medium truncate">{it.product.name}</div>
                  <div className="text-xs text-muted-foreground">{[it.size, cartLineColorLabel(it)].filter(Boolean).join(" · ")}</div>
                  <div className="mt-2 inline-flex items-center gap-2 glass rounded-full px-1 py-0.5">
                    <button onClick={() => updateQty(it.product.id, it.qty - 1, it.size, it.color)} className="h-7 w-7 grid place-items-center rounded-full hover:bg-muted"><Minus className="h-3 w-3" /></button>
                    <span className="text-sm w-5 text-center">{it.qty}</span>
                    <button onClick={() => updateQty(it.product.id, it.qty + 1, it.size, it.color)} className="h-7 w-7 grid place-items-center rounded-full hover:bg-muted"><Plus className="h-3 w-3" /></button>
                  </div>
                </div>
                <div className="text-right">
                  <div className="font-medium">₹{(it.qty * it.unitPrice).toLocaleString("en-IN")}</div>
                  <button onClick={() => promptRemoveFromCart(it.product.id, it.size, it.color)} className="text-xs text-muted-foreground hover:text-destructive inline-flex items-center gap-1 mt-1"><X className="h-3 w-3" /> Remove</button>
                </div>
              </div>
            ))}
          </div>
          <div className="mt-6 flex items-center justify-between border-t border-border/60 pt-4">
            <div>
              <div className="text-xs uppercase tracking-wider text-muted-foreground">Subtotal</div>
              <div className="font-display text-2xl">₹{cartTotal.toLocaleString("en-IN")}</div>
            </div>
            <Link to="/checkout" className="inline-flex items-center gap-2 rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm">
              Checkout <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </>
      )}
    </div>
  );
}