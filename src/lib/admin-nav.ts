import { isM2AdminNavPath, SHOW_M2_POST_ORDER_UI } from "@/lib/feature-flags";
import { searchTokens } from "@/lib/product-search";

export type AdminNavItem = {
  to: string;
  label: string;
  exact?: boolean;
  keywords?: string;
};

export const ADMIN_NAV_ITEMS: AdminNavItem[] = [
  { to: "/admin", label: "Dashboard", exact: true, keywords: "overview stats studio home" },
  { to: "/admin/settings", label: "Site settings", keywords: "contact office address whatsapp social footer company" },
  { to: "/admin/addresses", label: "Addresses", keywords: "warehouse dispatch shipment invoice billing registered office company address" },
  { to: "/admin/contact-messages", label: "Contact messages", keywords: "write to us comments email phone address communication" },
  { to: "/admin/banners", label: "Banners", keywords: "hero carousel slides" },
  { to: "/admin/categories", label: "Categories", keywords: "catalog folders" },
  { to: "/admin/products", label: "Products", keywords: "catalog sku inventory stock" },
  { to: "/admin/home", label: "Home sections", keywords: "mom reviews reviews carousel featured watch shop reels loved right now pair beautifully merchandising shop menu" },
  { to: "/admin/about", label: "About page", keywords: "story founders" },
  { to: "/admin/gift", label: "Gift page", keywords: "gifting occasions budgets wrapping" },
  { to: "/admin/pages", label: "Page visibility", keywords: "hide show navigation" },
  { to: "/admin/coupons", label: "Coupons", keywords: "promo codes voucher bogo buy one get one offers" },
  { to: "/admin/discount", label: "Discount", keywords: "storewide percent sale" },
  { to: "/admin/blog", label: "Blogs", keywords: "posts articles" },
  { to: "/admin/policies", label: "Policies", keywords: "terms privacy return shipping" },
  { to: "/admin/invoices", label: "Invoices", keywords: "gst billing cin" },
  { to: "/admin/orders", label: "Orders", keywords: "sales customers checkout" },
  { to: "/admin/cancellations", label: "Cancellations", keywords: "cancel request refund" },
  { to: "/admin/returns", label: "Returns & exchanges", keywords: "order returns exchange refund pickup" },
  { to: "/admin/zoho", label: "Zoho settings", keywords: "ledgerly erp integration" },
  { to: "/admin/customers", label: "Customers", keywords: "accounts registered buyers patrons shoppers users joined timeline week" },
  { to: "/admin/rbac", label: "RBAC", keywords: "staff invite roles access permissions hierarchy" },
];

export function adminNavHaystack(item: AdminNavItem): string {
  return [item.label, item.to.replaceAll("/", " "), item.keywords].filter(Boolean).join(" ").toLowerCase();
}

export function visibleAdminNavItems(items: AdminNavItem[] = ADMIN_NAV_ITEMS): AdminNavItem[] {
  if (SHOW_M2_POST_ORDER_UI) return items;
  return items.filter((item) => !isM2AdminNavPath(item.to));
}

export function filterAdminNavItems(items: AdminNavItem[], rawQuery: string): AdminNavItem[] {
  const tokens = searchTokens(rawQuery);
  if (!tokens.length) return items;
  return items.filter((item) => tokens.every((token) => adminNavHaystack(item).includes(token)));
}
