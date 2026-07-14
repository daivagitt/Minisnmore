import { createFileRoute, Link } from "@tanstack/react-router";
import { useState } from "react";
import { motion } from "framer-motion";
import { Plus, Minus, X, Tag, ShieldCheck, Truck } from "lucide-react";
import { useShop } from "@/store/shop";
import { toast } from "sonner";
import { validateCoupon } from "@/lib/coupons.functions";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { commerceFromSiteSettings } from "@/lib/commerce";
import { orderTotals } from "@/lib/money";
export const Route = createFileRoute("/cart")({
  head: () => ({ meta: [{ title: "Your basket · Mini's n More" }] }),
  component: CartPage,
});

function CartPage() {
  const { cart, updateQty, removeFromCart, cartTotal } = useShop();
  const settings = useSiteSettings();
  const [code, setCode] = useState("");
  const [applied, setApplied] = useState<{ code: string; off: number } | null>(null);

  const apply = async () => {
    const normalized = code.trim().toUpperCase();
    if (!normalized) return;
    try {
      const result = await validateCoupon({ data: { code: normalized, subtotal: cartTotal } });
      if (!result.ok) {
        toast.error(result.error);
        return;
      }
      setApplied({ code: result.code, off: result.discount });
      toast.success("Coupon applied");
    } catch {
      toast.error("Could not validate coupon");
    }
  };

  const discount = applied?.off ?? 0;
  const commerce = commerceFromSiteSettings(settings);
  const totals = orderTotals({ subtotal: cartTotal, discount, shipMode: "std", commerce });
  const shipping = totals.shipping;
  const gst = totals.gst;
  const grand = totals.grand;

  if (cart.length === 0) {
    return (
      <div className="px-4 md:px-6 max-w-2xl mx-auto">
        <div className="glass-strong rounded-[2rem] p-12 text-center">
          <h1 className="font-display text-4xl">Your basket is gentle and empty</h1>
          <p className="text-muted-foreground mt-3">Tiny treasures await.</p>
          <Link to="/shop" className="mt-6 inline-block rounded-full bg-primary text-primary-foreground px-6 py-3">Begin discovering</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="px-4 md:px-6 max-w-6xl mx-auto grid lg:grid-cols-[1fr_380px] gap-6">
      <div className="space-y-3">
        <div className="glass-strong rounded-[2rem] p-6 md:p-8">
          <h1 className="font-display text-3xl md:text-4xl">Your basket</h1>
          <p className="text-sm text-muted-foreground mt-1">{cart.length} {cart.length === 1 ? "piece" : "pieces"} chosen with love</p>
        </div>
        {cart.map((item, idx) => (
          <motion.div key={item.product.id + item.size + item.color}
            initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: idx * 0.05 }}
            className="glass rounded-2xl p-4 flex gap-4">
            <img src={item.product.image} alt={item.product.name} className="h-24 w-24 rounded-xl object-cover" loading="lazy" />
            <div className="flex-1 min-w-0">
              <div className="flex justify-between gap-2">
                <Link to="/product/$slug" params={{ slug: item.product.slug }} className="font-medium hover:text-primary">{item.product.name}</Link>
                <button onClick={() => removeFromCart(item.product.id, item.size, item.color)} className="text-muted-foreground hover:text-destructive" aria-label="Remove">
                  <X className="h-4 w-4" />
                </button>
              </div>
              <div className="text-xs text-muted-foreground mt-1">{item.size} · {item.color}</div>
              <div className="flex items-center justify-between mt-3">
                <div className="glass rounded-full flex items-center">
                  <button onClick={() => updateQty(item.product.id, item.qty - 1, item.size, item.color)} className="h-8 w-8 grid place-items-center" aria-label="Decrease"><Minus className="h-3.5 w-3.5" /></button>
                  <span className="text-sm w-7 text-center">{item.qty}</span>
                  <button onClick={() => updateQty(item.product.id, item.qty + 1, item.size, item.color)} className="h-8 w-8 grid place-items-center" aria-label="Increase"><Plus className="h-3.5 w-3.5" /></button>
                </div>
                <div className="text-right">
                  <div className="font-medium">₹{(item.qty * item.unitPrice).toLocaleString("en-IN")}</div>
                  <div className="text-[11px] text-muted-foreground">₹{item.unitPrice.toLocaleString("en-IN")} each</div>
                </div>
              </div>
            </div>
          </motion.div>
        ))}
      </div>

      <aside className="glass-strong rounded-[2rem] p-6 h-fit lg:sticky lg:top-28 space-y-5">
        <div className="font-display text-2xl">Order summary</div>

        <div>
          <label className="text-xs uppercase tracking-wider text-muted-foreground">Have a code?</label>
          <div className="mt-2 flex gap-2">
            <div className="flex-1 glass rounded-full flex items-center px-3">
              <Tag className="h-3.5 w-3.5 text-muted-foreground" />
              <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="LITTLEONE"
                className="flex-1 bg-transparent px-2 py-2 text-sm outline-none uppercase" />
            </div>
            <button onClick={apply} className="rounded-full bg-foreground text-background px-4 text-sm">Apply</button>
          </div>
          {applied && <div className="mt-2 text-xs text-primary flex items-center justify-between">
            <span>{applied.code} · −₹{applied.off}</span>
            <button onClick={() => setApplied(null)} className="text-muted-foreground hover:text-destructive">remove</button>
          </div>}
        </div>

        <div className="border-t border-border/60 pt-4 space-y-2 text-sm">
          <Row label="Subtotal" val={`₹${cartTotal.toLocaleString("en-IN")}`} />
          {discount > 0 && <Row label="Discount" val={`− ₹${discount.toLocaleString("en-IN")}`} accent />}
          <Row
            label="Shipping"
            val={shipping === 0 ? "Free" : `₹${shipping}`}
            hint={
              cartTotal < commerce.freeShippingMinInr
                ? `Add ₹${(commerce.freeShippingMinInr - cartTotal).toLocaleString("en-IN")} for Free Delivery Above ₹${commerce.freeShippingMinInr.toLocaleString("en-IN")}/-`
                : undefined
            }
          />
          <Row label={`GST (${commerce.gstRate}%)`} val={`₹${gst.toLocaleString("en-IN")}`} muted />
          <div className="border-t border-border/60 my-2" />
          <Row label="Total" val={`₹${grand.toLocaleString("en-IN")}`} big />
        </div>

        <Link to="/checkout" className="block text-center w-full rounded-full bg-primary text-primary-foreground py-3.5 font-medium hover:shadow-glow transition">
          Continue to checkout
        </Link>

        <div className="grid grid-cols-2 gap-2 text-[11px] text-muted-foreground">
          <div className="glass rounded-2xl p-2.5 flex items-center gap-2"><ShieldCheck className="h-3.5 w-3.5 text-primary" /> Secure pay</div>
          <div className="glass rounded-2xl p-2.5 flex items-center gap-2"><Truck className="h-3.5 w-3.5 text-primary" /> Free Delivery Above ₹{commerce.freeShippingMinInr.toLocaleString("en-IN")}/-</div>
        </div>
      </aside>
    </div>
  );
}

function Row({ label, val, hint, muted, accent, big }: { label: string; val: string; hint?: string; muted?: boolean; accent?: boolean; big?: boolean }) {
  return (
    <div>
      <div className={`flex justify-between ${big ? "font-display text-xl" : ""} ${muted ? "text-muted-foreground" : ""} ${accent ? "text-primary" : ""}`}>
        <span>{label}</span><span>{val}</span>
      </div>
      {hint && <div className="text-[11px] text-muted-foreground mt-0.5">{hint}</div>}
    </div>
  );
}
