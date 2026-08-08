import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { Loader2, Package, ChevronRight } from "lucide-react";
import { useAccountData } from "@/lib/account";
import { formatOrderDate } from "@/lib/dates";

export const Route = createFileRoute("/account/orders/")({
  head: () => ({ meta: [{ title: "Your orders · Mini's n More" }] }),
  component: OrdersPage,
});

const ORDER_FILTERS = [
  { key: "all", label: "All" },
  { key: "pending", label: "Pending" },
  { key: "completed", label: "Completed" },
] as const;

function needsOnlinePayment(order: { payment_status?: string; payment_method?: string }) {
  return order.payment_status === "pending" && order.payment_method !== "cod";
}

function OrdersPage() {
  const { orders, loadingOrders, refreshOrders } = useAccountData();
  const [filter, setFilter] = useState<typeof ORDER_FILTERS[number]["key"]>("all");

  useEffect(() => {
    if (orders === null && !loadingOrders) void refreshOrders();
  }, [orders, loadingOrders, refreshOrders]);

  const filteredOrders = useMemo(() => {
    if (!orders) return orders;
    if (filter === "pending") return orders.filter((o) => o.payment_status === "pending");
    if (filter === "completed") return orders.filter((o) => o.payment_status === "paid");
    return orders;
  }, [orders, filter]);

  return (
    <div className="glass-strong rounded-[2rem] p-6 md:p-8">
      <h1 className="font-display text-3xl mb-1 flex items-center gap-2"><Package className="h-6 w-6 text-primary" /> Your orders</h1>
      <p className="text-sm text-muted-foreground mb-6">All your little adventures, in one place.</p>

      {orders && orders.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-5">
          {ORDER_FILTERS.map((f) => (
            <button key={f.key} type="button" onClick={() => setFilter(f.key)}
              className={`text-xs rounded-full px-3 py-1.5 border transition ${filter === f.key ? "bg-primary text-primary-foreground border-primary" : "border-border hover:bg-muted/60"}`}>
              {f.label}
            </button>
          ))}
        </div>
      )}

      {loadingOrders && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
      {orders && orders.length === 0 && (
        <div className="text-sm text-muted-foreground glass rounded-2xl p-6 text-center">
          No orders yet. <Link to="/shop" className="text-primary story-link">Begin discovering →</Link>
        </div>
      )}
      {orders && orders.length > 0 && filteredOrders?.length === 0 && (
        <div className="text-sm text-muted-foreground glass rounded-2xl p-6 text-center">
          No {filter} orders.
        </div>
      )}

      <div className="space-y-3">
        {filteredOrders?.map((o) => (
          <Link
            key={o.id}
            to="/account/orders/$orderNumber"
            params={{ orderNumber: o.order_number }}
            search={needsOnlinePayment(o) ? { pay: "1" } : {}}
            className="glass rounded-2xl p-4 flex items-center justify-between gap-4 hover:bg-white/60 transition group"
          >
            <div className="flex items-center gap-3 min-w-0">
              <div className="flex -space-x-2">
                {(o.order_items ?? []).slice(0, 3).map((it: any, i: number) => (
                  it.product_image ? (
                    <img key={i} src={it.product_image} alt="" className="h-10 w-10 rounded-xl object-cover ring-2 ring-background" />
                  ) : (
                    <div key={i} className="h-10 w-10 rounded-xl bg-muted ring-2 ring-background" />
                  )
                ))}
              </div>
              <div className="min-w-0">
                <div className="font-medium truncate">{o.order_number}</div>
                <div className="text-xs text-muted-foreground">
                  {formatOrderDate(o.created_at)}
                  {" · "}{o.order_items?.length || 0} item(s)
                  {needsOnlinePayment(o) && <span className="text-primary"> · Tap to complete payment</span>}
                </div>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="text-right">
                <div className="font-medium">₹{Number(o.total).toLocaleString("en-IN")}</div>
                <StatusPill status={o.payment_status} />
              </div>
              <ChevronRight className="h-4 w-4 text-muted-foreground group-hover:translate-x-0.5 transition" />
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}

function StatusPill({ status }: { status: string }) {
  const map: Record<string, string> = {
    paid: "bg-primary/15 text-primary",
    pending: "bg-amber-500/15 text-amber-700",
    failed: "bg-destructive/15 text-destructive",
  };
  return <span className={`text-[10px] uppercase tracking-wider rounded-full px-2 py-0.5 ${map[status] ?? "bg-muted text-muted-foreground"}`}>{status}</span>;
}
