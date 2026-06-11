import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { Truck, Package, CheckCircle2, Home, Loader2, Search, Baby, Sparkles } from "lucide-react";
import { useAccountData } from "@/lib/account";
import heroBaby from "@/assets/hero-smiling-baby.jpg";

export const Route = createFileRoute("/account/track")({
  head: () => ({ meta: [{ title: "Track Order · Mini's n More" }] }),
  component: TrackPage,
});

const STAGES = [
  { key: "pending", icon: CheckCircle2, label: "Order placed" },
  { key: "processing", icon: Package, label: "Hand-wrapped" },
  { key: "shipped", icon: Truck, label: "On the way" },
  { key: "delivered", icon: Home, label: "Delivered" },
];
const STATUS_INDEX: Record<string, number> = { pending: 0, processing: 1, shipped: 2, delivered: 3 };

function getStatusIndex(status?: string) {
  const normalized = (status || "").toLowerCase();
  const aliases: Record<string, keyof typeof STATUS_INDEX> = {
    confirmed: "pending",
    fulfilled: "delivered",
  };
  if (normalized === "cancelled" || normalized === "canceled") return 0;
  const key = (aliases[normalized] ?? normalized) as keyof typeof STATUS_INDEX;
  return STATUS_INDEX[key] ?? 0;
}

function TrackPage() {
  const { orders, loadingOrders } = useAccountData();
  const [code, setCode] = useState("");
  const [selected, setSelected] = useState<string | null>(null);

  useEffect(() => {
    if (orders?.[0] && !selected) {
      setSelected(orders[0].order_number);
      setCode(orders[0].order_number);
    }
  }, [orders, selected]);

  const order = useMemo(() => orders?.find((o) => o.order_number === selected) ?? null, [orders, selected]);
  const idx = order ? getStatusIndex(order.status) : 0;

  return (
    <div className="space-y-4">
      <div className="relative overflow-hidden rounded-[2rem] min-h-[300px]">
        <img src={heroBaby} alt="Smiling baby wrapped in soft organic cloth" className="absolute inset-0 h-full w-full object-cover object-center" />
        <div className="absolute inset-0 bg-gradient-to-r from-background/90 via-background/65 to-background/25" />
        <div className="relative z-10 max-w-2xl p-6 md:p-10">
          <div className="inline-flex items-center gap-2 glass rounded-full px-4 py-2 text-xs uppercase tracking-[0.22em] text-primary"><Baby className="h-3.5 w-3.5" /> Tiny delivery trail</div>
          <h1 className="font-display text-4xl md:text-5xl mt-4">Track every little milestone.</h1>
          <p className="mt-3 text-sm md:text-base text-foreground/75 max-w-xl">From hand-wrapping to doorstep delivery, keep every update in one calm timeline.</p>
        </div>
      </div>

      <div className="glass-strong rounded-[2rem] p-6 md:p-8">
        <h2 className="font-display text-3xl mb-1 flex items-center gap-2"><Truck className="h-6 w-6 text-primary" /> Track your order</h2>
        <div className="mt-4 glass rounded-full p-1.5 flex items-center max-w-md">
          <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Order ID" className="flex-1 bg-transparent px-4 py-2.5 text-sm outline-none" />
          <button onClick={() => setSelected(code.trim())} className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm flex items-center gap-2"><Search className="h-4 w-4" /> Track</button>
        </div>
      </div>

      <div className="glass-strong rounded-[2rem] p-6 md:p-8">
        {loadingOrders && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
        {orders && !order && <div className="text-sm text-muted-foreground">No matching order. Pick one from <Link to="/account/orders" className="text-primary story-link">your orders</Link>.</div>}
        {order && (
          <>
            <div className="text-xs uppercase tracking-wider text-muted-foreground">Order {order.order_number}</div>
            <div className="font-display text-2xl mt-1 capitalize">{order.status}</div>

            <div className="mt-8 relative">
              <div className="absolute left-5 top-0 bottom-0 w-px bg-border" />
              <div className="space-y-5">
                {STAGES.map((s, i) => {
                  const Icon = s.icon;
                  const done = i <= idx;
                  const current = i === idx;
                  return (
                    <div key={s.key} className="flex gap-4 items-start relative">
                      <div className={`h-10 w-10 rounded-full grid place-items-center flex-shrink-0 transition ${done ? "bg-primary text-primary-foreground" : "glass text-muted-foreground"}`}>
                        <Icon className="h-4 w-4" />
                      </div>
                      <div className={`glass rounded-2xl p-4 flex-1 ${current ? "ring-2 ring-primary/40" : ""}`}>
                        <div className="font-medium text-sm">{s.label}</div>
                        <div className="text-xs text-muted-foreground mt-0.5">{done ? "Completed" : "Upcoming"}</div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </>
        )}
      </div>

      {orders && orders.length > 0 && (
        <div className="glass-strong rounded-[2rem] p-6 md:p-8">
          <div className="flex items-center justify-between gap-3 mb-3">
            <div className="text-xs uppercase tracking-wider text-muted-foreground">Recent orders</div>
            <div className="inline-flex items-center gap-2 text-xs text-primary"><Sparkles className="h-3.5 w-3.5" /> Quick lookup</div>
          </div>
          <div className="flex flex-wrap gap-2">
            {orders.slice(0, 6).map((o) => (
              <button key={o.id} onClick={() => { setSelected(o.order_number); setCode(o.order_number); }} className={`text-xs rounded-full border border-border px-3 py-1.5 hover:bg-muted ${selected === o.order_number ? "bg-primary text-primary-foreground border-primary" : ""}`}>
                {o.order_number}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}