import { createFileRoute, Outlet, Link, useRouterState, useNavigate } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { useAuth } from "@/hooks/useAuth";
import { filterAdminNavItems, visibleAdminNavItems } from "@/lib/admin-nav";
import {
  LayoutDashboard, Image as ImgIcon, Tag, BookOpen, FileText, Package, Plug, LogOut, Loader2,
  Folder, Home, EyeOff, Settings as SettingsIcon, ExternalLink, ShoppingCart, ReceiptText, Percent, RotateCcw, XCircle,
  Users, Search, Mail, ShieldCheck, Gift, MapPin, Box, type LucideIcon,
} from "lucide-react";
import { staffHomePath } from "@/lib/staff-roles";

export const Route = createFileRoute("/admin")({
  head: () => ({ meta: [{ title: "Admin · Mini's n More" }] }),
  component: AdminLayout,
});

const NAV_ICONS: Record<string, LucideIcon> = {
  "/admin": LayoutDashboard,
  "/admin/settings": SettingsIcon,
  "/admin/addresses": MapPin,
  "/admin/package-types": Box,
  "/admin/contact-messages": Mail,
  "/admin/banners": ImgIcon,
  "/admin/categories": Folder,
  "/admin/products": Package,
  "/admin/home": Home,
  "/admin/about": Users,
  "/admin/gift": Gift,
  "/admin/pages": EyeOff,
  "/admin/coupons": Tag,
  "/admin/discount": Percent,
  "/admin/blog": BookOpen,
  "/admin/policies": FileText,
  "/admin/invoices": ReceiptText,
  "/admin/orders": ShoppingCart,
  "/admin/cancellations": XCircle,
  "/admin/returns": RotateCcw,
  "/admin/zoho": Plug,
  "/admin/customers": Users,
  "/admin/rbac": ShieldCheck,
};

function AdminLayout() {
  const { user, isAdmin, staffRole, loading, signOut } = useAuth();
  const navigate = useNavigate();
  const path = useRouterState({ select: (s) => s.location.pathname });
  const [navQuery, setNavQuery] = useState("");
  const nav = useMemo(() => filterAdminNavItems(visibleAdminNavItems(), navQuery), [navQuery]);

  useEffect(() => {
    if (loading) return;
    if (!user && path !== "/admin/login") navigate({ to: "/admin/login" });
    if (user && !isAdmin && path !== "/admin/login") {
      const home = staffHomePath(staffRole);
      void navigate({ to: home as never });
    }
  }, [user, loading, path, navigate, isAdmin, staffRole]);

  // login route renders standalone
  if (path === "/admin/login") return <Outlet />;

  // Do not render admin sidebar or outlet while checking auth or unauthenticated
  if (loading || !user) {
    return (
      <div className="min-h-screen grid place-items-center py-24">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  // Non-admins must never see admin sidebar or child routes
  if (!isAdmin) {
    if (staffRole) {
      return (
        <div className="min-h-screen grid place-items-center py-24">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
      );
    }
    return (
      <div className="px-4 max-w-md mx-auto pt-24">
        <div className="glass-strong rounded-[2rem] p-8 text-center">
          <h1 className="font-display text-3xl">Not authorised</h1>
          <p className="text-sm text-muted-foreground mt-2">Your account is signed in but doesn&rsquo;t have admin access.</p>
          <button onClick={() => signOut()} className="mt-5 rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm">
            Sign out
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="px-3 md:px-6 min-h-screen py-4 md:py-6 lg:grid lg:grid-cols-[272px_1fr] lg:gap-6 lg:items-stretch">
      {/* Sticky sidebar on desktop — darker glossy warm-brown theme */}
      <aside
        className="hidden lg:flex flex-col gap-1.5 rounded-[1.75rem] p-4 sticky top-4 self-start h-[calc(100vh-2rem)] max-h-[calc(100vh-2rem)] shadow-[0_30px_80px_-28px_rgba(90,40,15,0.7),inset_0_1px_0_rgba(255,255,255,0.35)] ring-1 ring-white/20 backdrop-blur-xl text-white relative overflow-hidden"
        style={{
          backgroundImage:
            "linear-gradient(155deg, #4a230f 0%, #7a3d1e 30%, #b06a36 55%, #8a4824 80%, #5a2c14 100%)",
        }}
      >
        <div className="pointer-events-none absolute inset-0 rounded-[1.75rem] bg-[linear-gradient(180deg,rgba(255,255,255,0.28),rgba(255,255,255,0.04)_38%,rgba(0,0,0,0.18)_100%)]" aria-hidden />
        <div className="pointer-events-none absolute -top-1/3 -left-1/4 h-[60%] w-[140%] rotate-[-8deg] rounded-full bg-[radial-gradient(ellipse_at_center,rgba(255,225,190,0.35),transparent_60%)] blur-2xl" aria-hidden />
        <div className="pointer-events-none absolute inset-x-3 top-2 h-px bg-gradient-to-r from-transparent via-white/60 to-transparent" aria-hidden />
        <div className="relative shrink-0 px-2 pb-3 mb-1 border-b border-white/15">
          <div className="text-[10px] uppercase tracking-[0.28em] text-amber-100/90 font-semibold">Admin</div>
          <div className="font-display text-lg leading-tight mt-0.5 text-white">Mini's n More</div>
          <label className="relative mt-3 block">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-amber-100/70" />
            <input
              type="search"
              value={navQuery}
              onChange={(e) => setNavQuery(e.target.value)}
              placeholder="Search pages…"
              aria-label="Search admin pages"
              className="w-full rounded-xl bg-black/20 pl-9 pr-3 py-2 text-sm text-white placeholder:text-amber-100/50 outline-none ring-1 ring-white/15 focus:ring-white/40"
            />
          </label>
        </div>
        <nav
          aria-label="Admin pages"
          data-admin-nav-scroll
          className="relative min-h-0 flex-1 overflow-y-auto overscroll-contain flex flex-col gap-1.5 pr-1 [scrollbar-width:thin]"
        >
          {nav.length === 0 ? (
            <div className="px-4 py-3 text-xs text-amber-100/70">No matching pages.</div>
          ) : nav.map((n) => {
            const active = n.exact ? path === n.to : path.startsWith(n.to);
            const Icon = NAV_ICONS[n.to] ?? LayoutDashboard;
            return (
              <Link key={n.to} to={n.to}
                onClick={() => setNavQuery("")}
                className={`group relative flex items-center gap-3 px-4 py-2.5 rounded-2xl text-sm whitespace-nowrap transition-all duration-200 ${
                  active
                    ? "bg-gradient-to-r from-white/95 to-amber-50/90 text-[#6b3a1f] shadow-[0_10px_24px_-10px_rgba(0,0,0,0.45)] font-semibold ring-1 ring-white/60"
                    : "text-amber-50/90 hover:bg-white/10 hover:text-white hover:translate-x-0.5"
                }`}>
                {active && <span className="absolute left-1 top-1.5 bottom-1.5 w-[3px] rounded-full bg-[#6b3a1f]/70" aria-hidden />}
                <Icon className={`h-4 w-4 ${active ? "" : "text-amber-100/80 group-hover:text-white"}`} /> <span>{n.label}</span>
              </Link>
            );
          })}
        </nav>
        <div className="relative shrink-0 pt-2 border-t border-white/15">
          <Link
            to="/"
            className="flex items-center gap-3 px-4 py-2.5 rounded-2xl text-sm text-amber-50/90 hover:bg-white/10 hover:text-white transition"
          >
            <ExternalLink className="h-4 w-4 text-amber-100/80" /> View storefront
          </Link>
          <button onClick={() => signOut()}
            className="w-full flex items-center gap-3 px-4 py-2.5 rounded-2xl text-sm text-amber-50/90 hover:bg-white/10 hover:text-white transition">
            <LogOut className="h-4 w-4" /> Sign out
          </button>
        </div>
      </aside>

      {/* Mobile nav — wraps so long admin menus stay usable on phones */}
      <aside
        className="lg:hidden rounded-[1.5rem] p-2 mb-4 flex flex-wrap gap-2 shadow-soft ring-1 ring-white/10 backdrop-blur-xl text-white"
        style={{
          backgroundImage:
            "linear-gradient(160deg, #6b3a1f 0%, #8a4a26 38%, #b56b3a 72%, #7a3f22 100%)",
        }}
      >
        <label className="relative w-full">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-amber-100/70" />
          <input
            type="search"
            value={navQuery}
            onChange={(e) => setNavQuery(e.target.value)}
            placeholder="Search pages…"
            aria-label="Search admin pages"
            className="w-full rounded-xl bg-black/20 pl-9 pr-3 py-2 text-sm text-white placeholder:text-amber-100/50 outline-none ring-1 ring-white/15 focus:ring-white/40"
          />
        </label>
        {nav.length === 0 ? (
          <div className="px-3 py-2 text-xs text-amber-100/70">No matching pages.</div>
        ) : nav.map((n) => {
          const active = n.exact ? path === n.to : path.startsWith(n.to);
          const Icon = NAV_ICONS[n.to] ?? LayoutDashboard;
          return (
            <Link key={n.to} to={n.to}
              onClick={() => setNavQuery("")}
              className={`flex items-center gap-2 px-3 py-2 rounded-xl text-sm transition ${
                active
                  ? "bg-white text-[#6b3a1f] shadow-md font-semibold"
                  : "text-amber-50/90 hover:bg-white/10 hover:text-white"
              }`}>
              <Icon className="h-4 w-4 shrink-0" /> <span>{n.label}</span>
            </Link>
          );
        })}
        <button onClick={() => signOut()}
          className="flex items-center gap-2 px-3 py-2 rounded-xl text-sm text-amber-50/90 hover:bg-white/10 hover:text-white transition">
          <LogOut className="h-4 w-4 shrink-0" /> Sign out
        </button>
      </aside>

      <section className="min-w-0 w-full pb-8">
        <Outlet />
      </section>
    </div>
  );
}
