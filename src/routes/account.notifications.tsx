import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { Bell, Package, CreditCard, Heart, Loader2 } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useAccountData } from "@/lib/account";
import { formatOrderDate } from "@/lib/dates";

export const Route = createFileRoute("/account/notifications")({
  head: () => ({ meta: [{ title: "Notifications · Mini's n More" }] }),
  component: NotificationsPage,
});

type Pref = { orders: boolean; promos: boolean; restocks: boolean };

function NotificationsPage() {
  const { user } = useAuth();
  const { orders, loadingOrders } = useAccountData();
  const key = `mnm_notif_prefs_${user?.id ?? "guest"}`;
  const [prefs, setPrefs] = useState<Pref>({ orders: true, promos: true, restocks: false });

  useEffect(() => {
    try {
      const raw = localStorage.getItem(key);
      if (raw) setPrefs(JSON.parse(raw));
    } catch {}
  }, [key]);

  const toggle = (k: keyof Pref) => {
    const next = { ...prefs, [k]: !prefs[k] };
    setPrefs(next);
    try { localStorage.setItem(key, JSON.stringify(next)); } catch {}
  };

  const items = (orders ?? []).flatMap((o: any) => {
    const list: { icon: any; title: string; body: string; at: string }[] = [];
    if (o.payment_status === "paid") list.push({ icon: CreditCard, title: "Payment received", body: `Order ${o.order_number} · ₹${Number(o.total).toLocaleString("en-IN")}`, at: o.created_at });
    list.push({ icon: Package, title: o.status === "delivered" ? "Order delivered" : "Order update", body: `${o.order_number} · ${o.status}`, at: o.created_at });
    return list;
  });

  return (
    <div className="space-y-4">
      <div className="glass-strong rounded-[2rem] p-6 md:p-8">
        <h1 className="font-display text-3xl mb-1 flex items-center gap-2"><Bell className="h-6 w-6 text-primary" /> Notifications</h1>
        <p className="text-sm text-muted-foreground">What you'd like to hear about.</p>
        <div className="mt-4 grid sm:grid-cols-3 gap-3">
          <Toggle label="Order updates" value={prefs.orders} onChange={() => toggle("orders")} />
          <Toggle label="Promotions" value={prefs.promos} onChange={() => toggle("promos")} />
          <Toggle label="Restock alerts" value={prefs.restocks} onChange={() => toggle("restocks")} />
        </div>
      </div>

      <div className="glass-strong rounded-[2rem] p-6 md:p-8">
        <h2 className="font-display text-xl mb-4">Recent activity</h2>
        {loadingOrders && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
        {orders && items.length === 0 && <div className="text-sm text-muted-foreground">Nothing yet — your updates will appear here.</div>}
        <div className="space-y-2">
          {items.map((n, i) => {
            const Icon = n.icon;
            return (
              <div key={i} className="glass rounded-2xl p-3 flex items-start gap-3">
                <div className="h-9 w-9 rounded-full bg-primary/10 text-primary grid place-items-center flex-shrink-0"><Icon className="h-4 w-4" /></div>
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium">{n.title}</div>
                  <div className="text-xs text-muted-foreground">{n.body}</div>
                </div>
                <div className="text-[11px] text-muted-foreground whitespace-nowrap">
                  {formatOrderDate(n.at, { day: "numeric", month: "short" })}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function Toggle({ label, value, onChange }: { label: string; value: boolean; onChange: () => void }) {
  return (
    <button type="button" onClick={onChange} className={`glass rounded-2xl p-4 text-left transition ${value ? "ring-2 ring-primary" : ""}`}>
      <div className="text-sm font-medium">{label}</div>
      <div className={`text-xs mt-1 ${value ? "text-primary" : "text-muted-foreground"}`}>{value ? "On" : "Off"}</div>
    </button>
  );
}