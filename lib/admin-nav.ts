import { searchTokens } from "@/lib/product-search";

export type AdminNavItem = {
  to: string;
  label: string;
  exact?: boolean;
  keywords?: string;
};

export const ADMIN_NAV_ITEMS: AdminNavItem[] = [
  { to: "/admin", label: "Dashboard", exact: true, keywords: "overview stats studio home" },
  { to: "/admin/settings", label: "Site settings", keywords: "contact whatsapp social footer company" },
  { to: "/admin/banners", label: "Banners", keywords: "hero carousel slides" },
  { to: "/admin/categories", label: "Categories", keywords: "catalog folders" },
  { to: "/admin/products", label: "Products", keywords: "catalog sku inventory stock" },
  { to: "/admin/home", label: "Home sections", keywords: "mom reviews reviews carousel featured" },
  { to: "/admin/about", label: "About page", keywords: "story founders" },
  { to: "/admin/pages", label: "Page visibility", keywords: "hide show navigation" },
  { to: "/admin/coupons", label: "Coupons", keywords: "promo codes voucher" },
  { to: "/admin/discount", label: "Discount", keywords: "storewide percent sale" },
  { to: "/admin/blog", label: "Blogs", keywords: "posts articles" },
  { to: "/admin/policies", label: "Policies", keywords: "terms privacy return shipping" },
  { to: "/admin/invoices", label: "Invoices", keywords: "gst billing cin" },
  { to: "/admin/orders", label: "Orders", keywords: "sales customers checkout" },
  { to: "/admin/returns", label: "Order returns", keywords: "exchange refund pickup" },
  { to: "/admin/zoho", label: "Zoho settings", keywords: "ledgerly erp integration" },
];

export function adminNavHaystack(item: AdminNavItem): string {
  return [item.label, item.to.replaceAll("/", " "), item.keywords].filter(Boolean).join(" ").toLowerCase();
}

export function filterAdminNavItems(items: AdminNavItem[], rawQuery: string): AdminNavItem[] {
  const tokens = searchTokens(rawQuery);
  if (!tokens.length) return items;
  return items.filter((item) => tokens.every((token) => adminNavHaystack(item).includes(token)));
}
