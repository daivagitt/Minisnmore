import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { Truck, Loader2, Search, Baby, Sparkles } from "lucide-react";
import { useAccountData } from "@/lib/account";
import { useOrderTrackingPoll } from "@/hooks/useOrderTrackingPoll";
import { OrderTrackingCard } from "@/components/OrderTrackingCard";
import { TrackingRefreshHint } from "@/components/TrackingRefreshHint";
import heroBaby from "@/assets/hero-smiling-baby.jpg";

export const Route = createFileRoute("/account/track")({
  head: () => ({ meta: [{ title: "Track Order · Mini's n More" }] }),
  component: TrackPage,
});

function TrackPage() {
  const { orders, loadingOrders, refreshOrders } = useAccountData();
  const [code, setCode] = useState("");
  const [activeOrderNumber, setActiveOrderNumber] = useState<string | null>(null);

  const { order, loading, error, lastUpdated, polling } = useOrderTrackingPoll(
    activeOrderNumber,
    "authenticated",
  );

  const successfulOrders = useMemo(
    () => orders?.filter((o) => o.payment_status === "paid") ?? orders,
    [orders]
  );

  useEffect(() => {
    if (orders === null && !loadingOrders) void refreshOrders();
  }, [orders, loadingOrders, refreshOrders]);

  useEffect(() => {
    if (successfulOrders?.[0] && !activeOrderNumber) {
      setActiveOrderNumber(successfulOrders[0].order_number);
      setCode(successfulOrders[0].order_number);
    }
  }, [successfulOrders, activeOrderNumber]);

  const handleTrack = () => {
    const next = code.trim();
    if (!next) return;
    setActiveOrderNumber(next.toUpperCase());
  };

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
          <input
            value={code}
            onChange={(e) => setCode(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleTrack();
            }}
            placeholder="Order ID"
            className="flex-1 bg-transparent px-4 py-2.5 text-sm outline-none"
          />
          <button
            type="button"
            onClick={handleTrack}
            disabled={loading && !order}
            className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm flex items-center gap-2 disabled:opacity-60"
          >
            {loading && !order ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />} Track
          </button>
        </div>
      </div>

      <div className="glass-strong rounded-[2rem] p-6 md:p-8">
        {loadingOrders && !order && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
        {loading && !order && !loadingOrders && (
          <div className="grid place-items-center py-6">
            <Loader2 className="h-6 w-6 animate-spin text-primary" />
          </div>
        )}
        {error && (
          <div className="text-sm text-destructive">
            {error}. Pick one from <Link to="/account/orders" className="text-primary story-link">your orders</Link>.
          </div>
        )}
        {!loading && !order && !error && activeOrderNumber && (
          <div className="text-sm text-muted-foreground">
            No matching order. Pick one from <Link to="/account/orders" className="text-primary story-link">your orders</Link>.
          </div>
        )}
        {order && (
          <>
            <OrderTrackingCard order={order} />
            <TrackingRefreshHint lastUpdated={lastUpdated} polling={polling} />
          </>
        )}
      </div>

      {successfulOrders && successfulOrders.length > 0 && (
        <div className="glass-strong rounded-[2rem] p-6 md:p-8">
          <div className="flex items-center justify-between gap-3 mb-3">
            <div className="text-xs uppercase tracking-wider text-muted-foreground">Recent orders</div>
            <div className="inline-flex items-center gap-2 text-xs text-primary"><Sparkles className="h-3.5 w-3.5" /> Quick lookup</div>
          </div>
          <div className="flex flex-wrap gap-2">
            {successfulOrders.slice(0, 6).map((o) => (
              <button
                key={o.id}
                type="button"
                onClick={() => {
                  setActiveOrderNumber(o.order_number);
                  setCode(o.order_number);
                }}
                className={`text-xs rounded-full border border-border px-3 py-1.5 hover:bg-muted ${activeOrderNumber === o.order_number ? "bg-primary text-primary-foreground border-primary" : ""}`}
              >
                {o.order_number}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
