import { motion, AnimatePresence } from "framer-motion";
import { X, Plus, Minus, ShoppingBag } from "lucide-react";
import { Link } from "@tanstack/react-router";
import { FIT_LIMITS } from "@/lib/product-fit";
import { useShop } from "@/store/shop";

export function CartDrawer() {
  const { cartOpen, setCartOpen, cart, promptRemoveFromCart, updateQty, cartTotal } = useShop();

  return (
    <AnimatePresence>
      {cartOpen && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => setCartOpen(false)} className="fixed inset-0 z-[60] bg-foreground/20 backdrop-blur-sm" />
          <motion.aside
            initial={{ x: "100%" }} animate={{ x: 0 }} exit={{ x: "100%" }}
            transition={{ type: "spring", damping: 28, stiffness: 240 }}
            className="fixed top-0 right-0 bottom-0 z-[61] w-full sm:w-[440px] p-3">
            <div className="glass-strong h-full rounded-[2rem] flex flex-col overflow-hidden">
              <div className="flex items-center justify-between p-6 pb-4">
                <div className="font-display text-2xl">Your basket</div>
                <button onClick={() => setCartOpen(false)} className="h-9 w-9 grid place-items-center rounded-full hover:bg-white/40">
                  <X className="h-4 w-4" />
                </button>
              </div>
              <div className="flex-1 overflow-y-auto px-6 space-y-4">
                {cart.length === 0 && (
                  <div className="text-center py-16 space-y-3">
                    <div className="mx-auto h-16 w-16 rounded-full glass grid place-items-center">
                      <ShoppingBag className="h-6 w-6 text-muted-foreground" />
                    </div>
                    <p className="text-muted-foreground text-sm">Your basket is feeling light.</p>
                    <Link to="/shop" onClick={() => setCartOpen(false)} className="inline-block rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm">Discover pieces</Link>
                  </div>
                )}
                {cart.map((item) => (
                  <div key={item.product.id + item.size + item.color} className="glass rounded-2xl p-3 flex gap-3">
                    <img src={item.product.image} alt={item.product.name} className="h-20 w-20 rounded-xl object-cover" loading="lazy" />
                    <div className="flex-1 min-w-0">
                      <div className="flex justify-between gap-2">
                        <div className="font-medium text-sm leading-tight">{item.product.name}</div>
                        <button onClick={() => promptRemoveFromCart(item.product.id, item.size, item.color)} aria-label="Remove" className="text-muted-foreground hover:text-destructive">
                          <X className="h-3.5 w-3.5" />
                        </button>
                      </div>
                      <div className="text-xs text-muted-foreground mt-1">{item.size} &middot; {item.color}</div>
                      <div className="flex items-center justify-between mt-2">
                        <div className="glass rounded-full flex items-center">
                          <button onClick={() => updateQty(item.product.id, item.qty - 1, item.size, item.color)} className="h-7 w-7 grid place-items-center"><Minus className="h-3 w-3" /></button>
                          <span className="text-xs w-6 text-center">{item.qty}</span>
                          <button onClick={() => updateQty(item.product.id, item.qty + 1, item.size, item.color)} disabled={item.qty >= FIT_LIMITS.qtyMax} className="h-7 w-7 grid place-items-center disabled:opacity-30"><Plus className="h-3 w-3" /></button>
                        </div>
                        <div className="text-sm font-medium">&#8377;{(item.qty * item.unitPrice).toLocaleString("en-IN")}</div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              {cart.length > 0 && (
                <div className="p-6 pt-4 border-t border-border/60 space-y-3">
                  <div className="flex justify-between text-sm"><span className="text-muted-foreground">Subtotal</span><span className="font-medium">&#8377;{cartTotal.toLocaleString("en-IN")}</span></div>
                  <div className="flex justify-between text-xs text-muted-foreground"><span>Shipping</span><span>Calculated at checkout</span></div>
                  <Link to="/cart" onClick={() => setCartOpen(false)} className="block text-center w-full rounded-full bg-primary text-primary-foreground py-3.5 font-medium hover:shadow-glow transition">
                    View basket &middot; &#8377;{cartTotal.toLocaleString("en-IN")}
                  </Link>
                  <Link to="/checkout" onClick={() => setCartOpen(false)} className="block text-center w-full rounded-full glass py-2.5 text-sm hover:bg-white/60 transition">
                    Quick checkout
                  </Link>
                </div>
              )}
            </div>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}