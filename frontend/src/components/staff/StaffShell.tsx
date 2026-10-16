import { Link, Outlet, useNavigate, useRouterState } from "@tanstack/react-router";
import { useEffect } from "react";
import {
  ArrowLeft,
  LayoutDashboard,
  Loader2,
  LogOut,
  Mail,
  Package,
  ReceiptText,
  RotateCcw,
  ShoppingCart,
  Users,
  XCircle,
  type LucideIcon,
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { STAFF_ROLE_NAV, type DeskStaffRole } from "@/lib/staff-nav";
import { isCmsAdminRole, staffHomePath, type StaffRole } from "@/lib/staff-roles";

const NAV_ICONS: Record<string, LucideIcon> = {
  Dashboard: LayoutDashboard,
  Orders: ShoppingCart,
  Products: Package,
  Customers: Users,
  Returns: RotateCcw,
  Cancellations: XCircle,
  Invoices: ReceiptText,
  "Contact messages": Mail,
};

export function StaffShell({ allowedRole }: { allowedRole: StaffRole }) {
  const { user, staffRole, loading, signOut } = useAuth();
  const navigate = useNavigate();
  const path = useRouterState({ select: (s) => s.location.pathname });
  const nav = allowedRole in STAFF_ROLE_NAV ? STAFF_ROLE_NAV[allowedRole as DeskStaffRole] : { title: "Staff", items: [] };
  const isCmsAdmin = isCmsAdminRole(staffRole);
  const canAccess = Boolean(staffRole === allowedRole || isCmsAdmin);

  useEffect(() => {
    if (loading) return;
    if (!user) {
      void navigate({ to: "/admin/login" });
      return;
    }
    if (!canAccess) {
      const home = staffHomePath(staffRole);
      void navigate({ to: (home === "/" ? "/admin/login" : home) as never });
    }
  }, [user, staffRole, loading, canAccess, navigate]);

  if (loading || !user || !canAccess) {
    return (
      <div className="grid place-items-center py-24">
        <Loader2 className="h-6 w-6 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="px-3 md:px-6 min-h-screen py-4 md:py-6 lg:grid lg:grid-cols-[240px_1fr] lg:gap-6">
      <aside
        className="hidden lg:flex flex-col gap-1.5 rounded-[1.75rem] p-4 sticky top-4 self-start min-h-[calc(100vh-2rem)] text-white ring-1 ring-white/20"
        style={{
          backgroundImage: "linear-gradient(155deg, #4a230f 0%, #7a3d1e 30%, #b06a36 55%, #8a4824 80%, #5a2c14 100%)",
        }}
      >
        <div className="px-2 pb-3 mb-1 border-b border-white/15">
          <div className="text-[10px] uppercase tracking-[0.28em] text-amber-100/90 font-semibold">{nav.title}</div>
          <div className="font-display text-lg leading-tight mt-0.5">Mini&apos;s n More</div>
        </div>
        {nav.items.map((item) => {
          const active = item.exact ? path === item.to : path.startsWith(item.to);
          const Icon = NAV_ICONS[item.label] ?? ShoppingCart;
          return (
            <Link
              key={item.to}
              to={item.to}
              className={`flex items-center gap-3 px-4 py-2.5 rounded-2xl text-sm ${
                active ? "bg-white text-[#6b3a1f] font-semibold" : "text-amber-50/90 hover:bg-white/10"
              }`}
            >
              <Icon className="h-4 w-4" /> {item.label}
            </Link>
          );
        })}
        {isCmsAdmin && (
          <Link
            to="/admin"
            className="flex items-center gap-3 px-4 py-2.5 rounded-2xl text-sm text-amber-100/90 hover:bg-white/10 hover:text-white transition"
          >
            <ArrowLeft className="h-4 w-4" /> Admin Console
          </Link>
        )}
        <button
          type="button"
          onClick={() => signOut()}
          className="mt-auto flex items-center gap-3 px-4 py-2.5 rounded-2xl text-sm text-amber-50/90 hover:bg-white/10"
        >
          <LogOut className="h-4 w-4" /> Sign out
        </button>
      </aside>
      <aside
        className="lg:hidden rounded-[1.5rem] p-2 mb-4 flex flex-wrap gap-2 text-white"
        style={{ backgroundImage: "linear-gradient(160deg, #6b3a1f 0%, #8a4a26 38%, #b56b3a 72%, #7a3f22 100%)" }}
      >
        {isCmsAdmin && (
          <Link
            to="/admin"
            className="px-3 py-2 rounded-xl text-sm text-amber-100 font-medium"
          >
            ← Admin
          </Link>
        )}
        {nav.items.map((item) => (
          <Link
            key={item.to}
            to={item.to}
            className={`px-3 py-2 rounded-xl text-sm ${path.startsWith(item.to) ? "bg-white text-[#6b3a1f] font-semibold" : "text-amber-50"}`}
          >
            {item.label}
          </Link>
        ))}
        <button type="button" onClick={() => signOut()} className="px-3 py-2 rounded-xl text-sm text-amber-50">
          Sign out
        </button>
      </aside>
      <section className="min-w-0 w-full pb-8">
        <Outlet />
      </section>
    </div>
  );
}
