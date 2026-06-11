import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { getOrder } from "@/lib/orders.functions";
import { Loader2, ArrowLeft, MapPin, CreditCard, Package2, CheckCircle2, Clock, XCircle } from "lucide-react";

export const Route = createFileRoute("/account/orders/$orderNumber")({
  head: ({ params }) => ({ meta: [{ title: `Order ${params.orderNumber} · Mini's n More` }] }),
  component: OrderDetail,
});

function OrderDetail() {
  const { orderNumber } = Route.useParams();
  const [order, setOrder] = useState<any | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    getOrder({ data: { orderNumber } })
      .then((r) => setOrder(r.order))
      .catch((e) => setErr(e?.message || "Could not load order"));
  }, [orderNumber]);

  if (err) return <div className="glass-strong rounded-[2rem] p-8 text-sm text-destructive">{err}</div>;
  if (!order) return <div className="glass-strong rounded-[2rem] p-12 grid place-items-center"><Loader2 className="h-6 w-6 animate-spin text-primary" /></div>;

  const addr = order.shipping_address || {};
  const StatusIcon = order.payment_status === "paid" ? CheckCircle2 : order.payment_status === "failed" ? XCircle : Clock;

  return (
    <div className="space-y-4">
      <Link to="/account/orders" className="text-sm text-muted-foreground hover:text-foreground inline-flex items-center gap-1"><ArrowLeft className="h-4 w-4" /> Back to orders</Link>

      <div className="glass-strong rounded-[2rem] p-6 md:p-8">
        <div className="flex items-start justify-between flex-wrap gap-3">
          <div>
            <div className="text-xs uppercase tracking-[0.28em] text-primary">Order</div>
            <h1 className="font-display text-3xl mt-1">{order.order_number}</h1>
            <div className="text-xs text-muted-foreground mt-1">
              Placed {new Date(order.created_at).toLocaleDateString("en-IN", { day: "numeric", month: "long", year: "numeric" })}
            </div>
          </div>
          <div className="inline-flex items-center gap-2 glass rounded-full px-4 py-2 text-sm">
            <StatusIcon className="h-4 w-4 text-primary" />
            <span className="capitalize">{order.payment_status}</span>
            <span className="text-muted-foreground">·</span>
            <span className="capitalize">{order.status}</span>
          </div>
        </div>
      </div>

      <div className="grid lg:grid-cols-[1fr_320px] gap-4">
        <div className="glass-strong rounded-[2rem] p-6 md:p-8">
          <h2 className="font-display text-xl mb-4 flex items-center gap-2"><Package2 className="h-5 w-5 text-primary" /> Items</h2>
          <div className="space-y-3">
            {order.order_items?.map((it: any) => (
              <div key={it.id} className="glass rounded-2xl p-3 flex gap-3 items-center">
                {it.product_image && <img src={it.product_image} className="h-16 w-16 rounded-xl object-cover" alt="" />}
                <div className="flex-1 min-w-0">
                  <div className="font-medium truncate">{it.product_name}</div>
                  <div className="text-xs text-muted-foreground">
                    {[it.size, it.color].filter(Boolean).join(" · ")} · ×{it.qty}
                  </div>
                </div>
                <div className="text-sm font-medium">₹{(it.qty * Number(it.price)).toLocaleString("en-IN")}</div>
              </div>
            ))}
          </div>

          <div className="grid sm:grid-cols-2 gap-4 mt-6">
            <div className="glass rounded-2xl p-4">
              <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted-foreground mb-2"><MapPin className="h-4 w-4 text-primary" /> Shipping address</div>
              <div className="text-sm font-medium">{addr.full_name}</div>
              <div className="text-sm text-muted-foreground">{addr.line1}{addr.line2 ? `, ${addr.line2}` : ""}</div>
              <div className="text-sm text-muted-foreground">{addr.city}, {addr.state} {addr.pincode}</div>
              <div className="text-xs text-muted-foreground mt-1">{addr.phone} · {addr.email}</div>
            </div>
            <div className="glass rounded-2xl p-4">
              <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted-foreground mb-2"><CreditCard className="h-4 w-4 text-primary" /> Payment</div>
              <div className="text-sm font-medium capitalize">{order.payment_status}</div>
              {order.razorpay_payment_id && <div className="text-xs text-muted-foreground break-all">ID: {order.razorpay_payment_id}</div>}
              {order.coupon_code && <div className="text-xs text-primary mt-1">Coupon: {order.coupon_code}</div>}
            </div>
          </div>
        </div>

        <aside className="glass-strong rounded-[2rem] p-6 h-fit space-y-1.5 text-sm">
          <div className="font-display text-xl mb-3">Summary</div>
          <Row label="Subtotal" value={`₹${Number(order.subtotal).toLocaleString("en-IN")}`} />
          {Number(order.discount) > 0 && <Row label="Discount" value={`− ₹${Number(order.discount).toLocaleString("en-IN")}`} accent />}
          <Row label="Shipping" value={Number(order.shipping) === 0 ? "Free" : `₹${Number(order.shipping).toLocaleString("en-IN")}`} muted />
          <Row label="GST" value={`₹${Number(order.tax).toLocaleString("en-IN")}`} muted />
          {Number(order.donation) > 0 && <Row label="Donation ♥" value={`₹${Number(order.donation).toLocaleString("en-IN")}`} accent />}
          <div className="border-t border-border/60 mt-2 pt-2 flex justify-between font-display text-lg">
            <span>Total</span><span>₹{Number(order.total).toLocaleString("en-IN")}</span>
          </div>
        </aside>
      </div>
    </div>
  );
}

function Row({ label, value, muted, accent }: { label: string; value: string; muted?: boolean; accent?: boolean }) {
  return (
    <div className={`flex justify-between ${muted ? "text-muted-foreground" : ""} ${accent ? "text-primary" : ""}`}>
      <span>{label}</span><span>{value}</span>
    </div>
  );
}