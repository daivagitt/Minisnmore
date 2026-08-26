import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect } from "react";
import { Loader2, Package, CreditCard, Heart, ArrowRight, Truck } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useAccountData } from "@/lib/account";
import { formatOrderDate } from "@/lib/dates";
import { formatOrderStatusSummary } from "@/lib/order-status";
import { useShop } from "@/store/shop";
import { useProducts, useProductsLoaded } from "@/hooks/useProducts";
import { wishlistCountForProducts, normalizeWishlistIds } from "@/lib/wishlist";

export const Route = createFileRoute("/account/")({
  head: () => ({ meta: [{ title: "Dashboard · Mini's n More" }] }),
  component: Dashboard,
});

function Dashboard() {
  const { user } = useAuth();
  const { wishlist } = useShop();
  const products = useProducts();
  const productsLoaded = useProductsLoaded();
  const wishlistCount = productsLoaded
    ? wishlistCountForProducts(wishlist, products.map((p) => p.id))
    : normalizeWishlistIds(wishlist).length;
  const { orders, profile, loadingOrders, refreshOrders } = useAccountData();

  useEffect(() => {
    if (orders === null && !loadingOrders) void refreshOrders();
  }, [orders, loadingOrders, refreshOrders]);

  const totalSpent = orders?.filter((o) => o.payment_status === "paid").reduce((s, o) => s + Number(o.total), 0) ?? 0;
  const recent = orders?.slice(0, 3) ?? [];
  const greeting = profile?.full_name?.split(" ")[0] || user?.email?.split("@")[0] || "there";

  return (
    <div className="space-y-4">
      <div className="glass-strong rounded-[2rem] p-6 md:p-8">
        <div className="text-xs uppercase tracking-[0.28em] text-primary">Welcome back</div>
        <h1 className="font-display text-3xl md:text-4xl mt-1">Hi, {greeting} 👋</h1>
        <p className="text-sm text-muted-foreground mt-2">Here's a quick look at your little universe.</p>
      </div>

      <div className="grid sm:grid-cols-3 gap-4">
        <StatCard icon={Package} label="Orders" value={orders?.length ?? "—"} />
        <StatCard icon={CreditCard} label="Total spent" value={`₹${totalSpent.toLocaleString("en-IN")}`} />
        <StatCard icon={Heart} label="Wishlist" value={wishlistCount} />
      </div>

      <div className="glass-strong rounded-[2rem] p-6 md:p-8">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-display text-2xl flex items-center gap-2"><Truck className="h-5 w-5 text-primary" /> Recent orders</h2>
          <Link to="/account/orders" className="text-sm text-primary story-link inline-flex items-center gap-1">View all <ArrowRight className="h-3.5 w-3.5" /></Link>
        </div>
        {loadingOrders && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
        {orders && orders.length === 0 && (
          <div className="text-sm text-muted-foreground">No orders yet. <Link to="/shop" className="text-primary story-link">Begin discovering →</Link></div>
        )}
        <div className="space-y-3">
          {recent.map((o) => (
            <Link key={o.id} to="/account/orders/$orderNumber" params={{ orderNumber: o.order_number }}
              className="glass rounded-2xl p-4 flex items-center justify-between gap-4 hover:bg-white/60 transition">
              <div className="min-w-0">
                <div className="font-medium">{o.order_number}</div>
                <div className="text-xs text-muted-foreground">
                  {formatOrderDate(o.created_at)}
                  {" · "}{o.order_items?.length || 0} item(s)
                </div>
              </div>
              <div className="text-right">
                <div className="font-medium">₹{Number(o.total).toLocaleString("en-IN")}</div>
                <div className="text-[11px] tracking-wide text-primary">{formatOrderStatusSummary(o)}</div>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}

function StatCard({ icon: Icon, label, value }: { icon: any; label: string; value: any }) {
  return (
    <div className="glass-strong rounded-[1.5rem] p-5">
      <div className="flex items-center gap-2 text-muted-foreground text-xs uppercase tracking-wider"><Icon className="h-4 w-4 text-primary" />{label}</div>
      <div className="font-display text-3xl mt-2">{value}</div>
    </div>
  );
}