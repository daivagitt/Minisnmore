import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { Bell, CreditCard, Home, Loader2, Package, Truck, type LucideIcon } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useAccountData } from "@/lib/account";
import { formatOrderDate } from "@/lib/dates";
import { listMyNotifications, type NotificationEvent } from "@/lib/orders.functions";
import {
  trackingActivityDate,
  trackingActivityIcon,
  trackingActivityTitle,
  type OrderTrackingFields,
} from "@/lib/order-tracking";
import { formatPaymentStatusLabel } from "@/lib/order-status";

export const Route = createFileRoute("/account/notifications")({
  head: () => ({ meta: [{ title: "Notifications · Mini's n More" }] }),
  component: NotificationsPage,
});

type Pref = { orders: boolean; promos: boolean; restocks: boolean };

type ActivityItem = {
  key: string;
  icon: LucideIcon;
  title: string;
  body: string;
  datePrefix: string;
  dateValue: string;
};

function iconForEventType(eventType: string): LucideIcon {
  switch (eventType) {
    case "payment_received":
    case "refunded":
      return CreditCard;
    case "shipped":
      return Truck;
    case "delivered":
      return Home;
    default:
      return Package;
  }
}

function datePrefixForEventType(eventType: string): string {
  switch (eventType) {
    case "order_placed":
      return "Ordered";
    case "payment_received":
      return "Paid";
    case "shipped":
      return "Shipped";
    case "delivered":
      return "Delivered";
    case "refunded":
      return "Refunded";
    case "cancelled":
      return "Cancelled";
    default:
      return "Updated";
  }
}

function eventToActivity(event: NotificationEvent): ActivityItem {
  return {
    key: `${event.id}-${event.event_type}`,
    icon: iconForEventType(event.event_type),
    title: event.title,
    body: event.body,
    datePrefix: datePrefixForEventType(event.event_type),
    dateValue: event.created_at,
  };
}

function buildOrderActivityFallback(orders: OrderTrackingFields[]): ActivityItem[] {
  return orders.map((order) => {
    const paymentLabel = formatPaymentStatusLabel(
      (order as { payment_status?: string }).payment_status,
      (order as { payment_method?: string }).payment_method,
    );
    const total = Number((order as { total?: number }).total ?? 0);
    const orderNumber = (order as { order_number?: string }).order_number ?? "";
    const parts = [orderNumber];
    if (total > 0) parts.push(`₹${total.toLocaleString("en-IN")}`);
    if (paymentLabel) parts.push(paymentLabel);
    if (order.tracking_number) parts.push(`Tracking ${order.tracking_number}`);

    const { prefix, value } = trackingActivityDate(
      order as OrderTrackingFields & { created_at?: string },
    );

    return {
      key: orderNumber || String((order as { id?: number }).id ?? ""),
      icon: trackingActivityIcon(order),
      title: trackingActivityTitle(order),
      body: parts.join(" · "),
      datePrefix: prefix,
      dateValue: value,
    };
  });
}

function NotificationsPage() {
  const { user } = useAuth();
  const { orders, loadingOrders, refreshOrders } = useAccountData();
  const key = `mnm_notif_prefs_${user?.id ?? "guest"}`;
  const [prefs, setPrefs] = useState<Pref>({ orders: true, promos: true, restocks: false });
  const [events, setEvents] = useState<NotificationEvent[] | null>(null);
  const [loadingEvents, setLoadingEvents] = useState(false);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(key);
      if (raw) setPrefs(JSON.parse(raw));
    } catch {}
  }, [key]);

  useEffect(() => {
    if (orders === null && !loadingOrders) void refreshOrders();
  }, [orders, loadingOrders, refreshOrders]);

  useEffect(() => {
    if (!prefs.orders) {
      setEvents([]);
      return;
    }
    let alive = true;
    setLoadingEvents(true);
    void listMyNotifications()
      .then((res) => {
        if (alive) setEvents(res.events ?? []);
      })
      .catch(() => {
        if (alive) setEvents(null);
      })
      .finally(() => {
        if (alive) setLoadingEvents(false);
      });
    return () => {
      alive = false;
    };
  }, [prefs.orders, orders?.length]);

  const toggle = (k: keyof Pref) => {
    const next = { ...prefs, [k]: !prefs[k] };
    setPrefs(next);
    try { localStorage.setItem(key, JSON.stringify(next)); } catch {}
  };

  const items = useMemo(() => {
    if (!prefs.orders) return [];
    if (events && events.length > 0) return events.map(eventToActivity);
    if (events === null && orders) return buildOrderActivityFallback(orders);
    return [];
  }, [events, orders, prefs.orders]);

  const showLoading = loadingOrders || loadingEvents;

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
        {showLoading && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
        {!showLoading && !prefs.orders && (
          <div className="text-sm text-muted-foreground">Order updates are turned off.</div>
        )}
        {!showLoading && prefs.orders && items.length === 0 && (
          <div className="text-sm text-muted-foreground">Nothing yet — your updates will appear here.</div>
        )}
        <div className="space-y-2">
          {items.map((n) => {
            const Icon = n.icon;
            return (
              <div key={n.key} className="glass rounded-2xl p-3 flex items-start gap-3">
                <div className="h-9 w-9 rounded-full bg-primary/10 text-primary grid place-items-center flex-shrink-0"><Icon className="h-4 w-4" /></div>
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium">{n.title}</div>
                  <div className="text-xs text-muted-foreground">{n.body}</div>
                </div>
                <div className="text-[11px] text-muted-foreground whitespace-nowrap text-right">
                  <span className="text-muted-foreground/70">{n.datePrefix}</span>
                  <br />
                  {formatOrderDate(n.dateValue, { day: "numeric", month: "short" })}
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
