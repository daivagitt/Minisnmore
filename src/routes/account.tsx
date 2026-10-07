import { createFileRoute, Link, Outlet, useLocation, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { SHOW_M2_POST_ORDER_UI } from "@/lib/feature-flags";
import { useAuth } from "@/hooks/useAuth";
import { AccountDataProvider } from "@/lib/account";
import newbornBaby from "@/assets/cat-newborn.webp";
import {
  Loader2, LayoutDashboard, Package, User as UserIcon, CreditCard, LogOut,
  Heart, ShoppingBag, MapPin, Truck, Bell, RotateCcw,
} from "lucide-react";

export const Route = createFileRoute("/account")({
  head: () => ({ meta: [{ title: "Your account · Mini's n More" }] }),
  component: AccountLayout,
});

const navGroups: { label: string; items: { to: string; label: string; icon: any; exact?: boolean }[] }[] = [
  {
    label: "Overview",
    items: [
      { to: "/account", label: "Dashboard", icon: LayoutDashboard, exact: true },
      { to: "/account/notifications", label: "Notifications", icon: Bell },
    ],
  },
  {
    label: "Shopping",
    items: [
      { to: "/account/cart", label: "Cart", icon: ShoppingBag },
      { to: "/account/wishlist", label: "Wishlist", icon: Heart },
    ],
  },
  {
    label: "Orders",
    items: [
      { to: "/account/orders", label: "My Orders", icon: Package },
      { to: "/account/returns", label: "Returns & exchanges", icon: RotateCcw },
      { to: "/account/track", label: "Track Order", icon: Truck },
    ],
  },
  {
    label: "Account",
    items: [
      { to: "/account/profile", label: "Profile", icon: UserIcon },
      { to: "/account/addresses", label: "Addresses", icon: MapPin },
      { to: "/account/payments", label: "Payments", icon: CreditCard },
    ],
  },
];

const visibleNavGroups = navGroups.map((g) => ({
  ...g,
  items: g.items.filter((it) => SHOW_M2_POST_ORDER_UI || it.to !== "/account/returns"),
}));
const flatNavItems = visibleNavGroups.flatMap((g) => g.items);

function AccountLayout() {
  const { user, loading, signOut } = useAuth();
  const nav = useNavigate();
  const { href, pathname } = useLocation();
  const isActivationRedirect = pathname === "/account/activate";
  const isPasswordResetRedirect = pathname === "/account/reset/finish";
  const isOrderDetail =
    pathname.startsWith("/account/orders/") && pathname !== "/account/orders/";

  useEffect(() => {
    if (isActivationRedirect || isPasswordResetRedirect) return;
    if (!loading && !user) nav({ to: "/login", search: { redirect: href } });
  }, [user, loading, nav, href, isActivationRedirect, isPasswordResetRedirect]);

  return (
    <AccountDataProvider>
      <div className="px-3 sm:px-4 md:px-6 min-h-[calc(100vh-10rem)] lg:grid lg:grid-cols-[292px_1fr] xl:grid-cols-[292px_minmax(0,1fr)_260px] lg:gap-6 lg:items-stretch">
      {/* Sticky sidebar on desktop — flows with content so footer never overlaps */}
      <aside className="hidden lg:flex flex-col glass-tint text-foreground rounded-[2rem] p-5 sticky top-28 self-start min-h-[calc(100vh-9rem)] shadow-soft ring-1 ring-white/45">
        <div className="px-2 pb-4 border-b border-background/15">
          <div className="text-[10px] uppercase tracking-[0.28em] text-primary">Signed in</div>
          <div className="font-medium text-sm mt-1 truncate text-foreground">{user?.email ?? "…"}</div>
        </div>
        <nav className="mt-4 flex flex-col gap-4 flex-1">
          {visibleNavGroups.map((g) => (
            <div key={g.label} className="flex flex-col gap-1.5">
              <div className="px-3 pt-1 pb-1 text-[10px] uppercase tracking-[0.22em] text-muted-foreground">{g.label}</div>
              {g.items.map((it) => {
                const Icon = it.icon;
                return (
                  <Link
                    key={it.to}
                    to={it.to}
                    activeOptions={{ exact: it.exact }}
                    className="flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium text-foreground/82 hover:bg-white/45 hover:text-foreground transition whitespace-nowrap"
                    activeProps={{ className: "!bg-primary !text-primary-foreground shadow-md" }}
                  >
                    <Icon className="h-4 w-4" />
                    {it.label}
                  </Link>
                );
              })}
            </div>
          ))}
          <button
            onClick={() => signOut()}
            className="flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium text-foreground/82 hover:bg-white/45 hover:text-foreground transition whitespace-nowrap text-left mt-auto border-t border-white/35 pt-4"
          >
            <LogOut className="h-4 w-4" />
            Sign out
          </button>
        </nav>
      </aside>

      {!isOrderDetail && (
      <aside className="lg:hidden glass-tint text-foreground rounded-[1.25rem] p-2.5 mb-3 shadow-soft">
        <div className="px-1.5 pb-2 mb-2 border-b border-white/35">
          <div className="text-[10px] uppercase tracking-[0.28em] text-primary">Signed in</div>
          <div className="font-medium text-sm mt-0.5 truncate text-foreground">{user?.email ?? "…"}</div>
        </div>
        <nav className="flex gap-1.5 overflow-x-auto overscroll-x-contain pb-0.5 -mx-0.5 px-0.5 [scrollbar-width:thin]">
          {flatNavItems.map((it) => {
            const Icon = it.icon;
            return (
              <Link
                key={it.to}
                to={it.to}
                activeOptions={{ exact: it.exact }}
                className="flex items-center gap-1.5 rounded-xl px-2.5 py-2 text-xs font-medium text-foreground/85 hover:bg-white/45 hover:text-foreground transition shrink-0 whitespace-nowrap"
                activeProps={{ className: "!bg-primary !text-primary-foreground" }}
              >
                <Icon className="h-4 w-4 shrink-0" />
                {it.label}
              </Link>
            );
          })}
          <button
            onClick={() => signOut()}
            className="flex items-center gap-1.5 rounded-xl px-2.5 py-2 text-xs font-medium text-foreground/85 hover:bg-white/45 hover:text-foreground transition shrink-0 whitespace-nowrap"
          >
            <LogOut className="h-4 w-4 shrink-0" />
            Sign out
          </button>
        </nav>
      </aside>
      )}

      <main className="min-w-0 max-w-5xl w-full pb-8">
        {isActivationRedirect ? (
          <Outlet />
        ) : loading || !user ? (
          <div className="grid place-items-center py-24">
            <Loader2 className="h-6 w-6 animate-spin text-primary" />
          </div>
        ) : (
          <Outlet />
        )}
      </main>

      <aside className="hidden xl:flex flex-col gap-4 pt-2">
        <figure className="relative overflow-hidden rounded-[2rem] min-h-[360px] shadow-soft">
          <img
            src={newbornBaby}
            alt="Softly wrapped newborn in organic babywear"
            className="absolute inset-0 h-full w-full object-cover"
            loading="lazy"
            decoding="async"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-background/90 via-background/28 to-transparent" />
          <figcaption className="absolute inset-x-0 bottom-0 p-5">
            <div className="text-[10px] uppercase tracking-[0.26em] text-primary">Soft moments</div>
            <p className="mt-2 font-display text-2xl leading-tight">Gentle details, ready beside every account page.</p>
          </figcaption>
        </figure>
        <div className="px-1 text-sm leading-relaxed text-muted-foreground">
          Keep orders, addresses and payments close at hand while the little details stay warm and calm.
        </div>
      </aside>
      </div>
    </AccountDataProvider>
  );
}
