import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import {
  Image as ImgIcon,
  Tag,
  BookOpen,
  FileText,
  Package,
  Plug,
  ArrowRight,
  Folder,
  Eye,
  EyeOff,
  Layers,
  Home,
  Barcode,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { toast } from "sonner";
import { getAdminDashboard } from "@/lib/backend-data";
import { showApiError } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/")({
  component: AdminDashboard,
});

type Stats = {
  categories: number;
  products: number;
  skus: number;
  coupons: number;
  blogs: number;
  pages: number;
  visiblePages: number;
  hiddenPages: number;
  banners: number;
  policies: number;
};

function AdminDashboard() {
  const [s, setS] = useState<Stats>({
    categories: 0,
    products: 0,
    skus: 0,
    coupons: 0,
    blogs: 0,
    pages: 0,
    visiblePages: 0,
    hiddenPages: 0,
    banners: 0,
    policies: 0,
  });
  const [zoho, setZoho] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const dashboard = await getAdminDashboard();
        const total = Number(dashboard.pageVisibility ?? 0);
        const v = Number(dashboard.visiblePages ?? 0);
        setS({
          categories: Number(dashboard.categories ?? 0),
          products: Number(dashboard.products ?? 0),
          skus: Number(dashboard.productsWithSku ?? 0),
          coupons: Number(dashboard.coupons ?? 0),
          blogs: Number(dashboard.blogPosts ?? 0),
          pages: total,
          visiblePages: v,
          hiddenPages: Math.max(0, total - v),
          banners: Number(dashboard.banners ?? 0),
          policies: Number(dashboard.policies ?? 0),
        });
        setZoho(!!dashboard.zohoConnected);
      } catch (error) {
        showApiError(error, "Could not load dashboard");
      }
    })();
  }, []);

  const stats: { label: string; n: number | string; icon: LucideIcon; tint: string }[] = [
    {
      label: "Total categories",
      n: s.categories,
      icon: Folder,
      tint: "from-rose-400/20 to-rose-200/10",
    },
    {
      label: "Total products",
      n: s.products,
      icon: Package,
      tint: "from-amber-400/20 to-amber-200/10",
    },
    {
      label: "Total SKUs",
      n: s.skus,
      icon: Barcode,
      tint: "from-emerald-400/20 to-emerald-200/10",
    },
    { label: "Total coupons", n: s.coupons, icon: Tag, tint: "from-sky-400/20 to-sky-200/10" },
    {
      label: "Total blogs",
      n: s.blogs,
      icon: BookOpen,
      tint: "from-violet-400/20 to-violet-200/10",
    },
    {
      label: "Storefront nav pages",
      n: s.pages,
      icon: Layers,
      tint: "from-fuchsia-400/20 to-fuchsia-200/10",
    },
    {
      label: "Visible on storefront",
      n: s.visiblePages,
      icon: Eye,
      tint: "from-teal-400/20 to-teal-200/10",
    },
    {
      label: "Hidden on storefront",
      n: s.hiddenPages,
      icon: EyeOff,
      tint: "from-stone-400/20 to-stone-200/10",
    },
  ];

  const cards = [
    { to: "/admin/banners", label: "Banners", icon: ImgIcon, n: s.banners },
    { to: "/admin/categories", label: "Categories", icon: Folder, n: s.categories },
    { to: "/admin/products", label: "Products", icon: Package, n: s.products },
    { to: "/admin/home", label: "Home sections", icon: Home, n: "Configure" },
    { to: "/admin/coupons", label: "Coupons", icon: Tag, n: s.coupons },
    { to: "/admin/blog", label: "Blogs", icon: BookOpen, n: s.blogs },
    { to: "/admin/policies", label: "Policies", icon: FileText, n: s.policies },
    { to: "/admin/pages", label: "Page visibility", icon: EyeOff, n: `${s.visiblePages}/${s.pages}` },
    { to: "/admin/zoho", label: "Zoho", icon: Plug, n: zoho ? "Connected" : "Not connected" },
  ];

  return (
    <AdminPage
      eyebrow="Studio"
      title="Mini's n More Dashboard"
      description="Manage everything across your storefront — catalog, content, marketing and site settings. Page visibility counts below only affect customer navigation, not this admin area."
    >
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 md:gap-4">
        {stats.map((c) => (
          <div
            key={c.label}
            className={`relative overflow-hidden rounded-3xl p-4 md:p-5 glass-strong bg-gradient-to-br ${c.tint}`}
          >
            <div className="flex items-center justify-between">
              <c.icon className="h-5 w-5 text-primary" />
            </div>
            <div className="text-[11px] uppercase tracking-[0.22em] text-muted-foreground mt-3">
              {c.label}
            </div>
            <div className="font-display text-2xl md:text-3xl mt-1">{c.n}</div>
          </div>
        ))}
      </div>

      <div className="mt-6">
        <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground mb-3">
          Quick links
        </div>
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {cards.map((c) => (
            <Link key={c.to} to={c.to} className="glass rounded-3xl p-5 hover-lift block">
              <div className="flex items-center justify-between">
                <c.icon className="h-5 w-5 text-primary" />
                <ArrowRight className="h-4 w-4 text-muted-foreground" />
              </div>
              <div className="text-xs uppercase tracking-wider text-muted-foreground mt-4">
                {c.label}
              </div>
              <div className="font-display text-2xl mt-1">{c.n}</div>
            </Link>
          ))}
        </div>
      </div>
    </AdminPage>
  );
}
