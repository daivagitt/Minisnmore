import { Outlet, createRootRoute, HeadContent, Scripts, Link, useRouterState } from "@tanstack/react-router";
import appCss from "../styles.css?url";
import { ShopProvider } from "@/store/shop";
import { AuthProvider } from "@/hooks/useAuth";
import { Navbar } from "@/components/Navbar";
import { Footer } from "@/components/Footer";
import { CartDrawer } from "@/components/CartDrawer";
import { QuickView } from "@/components/QuickView";
import { CatalogSync } from "@/components/CatalogSync";
// WhatsApp FAB removed per request — replaced by logo-led navigation only.
import { Watermark } from "@/components/Watermark";
import { ScrollProgress } from "@/components/ScrollProgress";
import { FloatingDecor } from "@/components/FloatingDecor";
import { Toaster } from "sonner";

const SOCIAL_SHARE_IMAGE = "https://minisnmore.com/og-share.jpg";

function NotFoundComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="glass-strong rounded-[2rem] p-10 max-w-md text-center">
        <h1 className="font-display text-7xl">404</h1>
        <p className="mt-3 text-muted-foreground">This page wandered off for a nap.</p>
        <Link to="/" className="mt-6 inline-block rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm">Take me home</Link>
      </div>
    </div>
  );
}

export const Route = createRootRoute({
  head: () => ({
    meta: [
      { charSet: "utf-8" },
      { name: "viewport", content: "width=device-width, initial-scale=1, viewport-fit=cover" },
      { title: "Mini's n More — Heirloom baby clothing 0–24m" },
      { name: "description", content: "Minis today, memories forever. Premium organic baby clothing for the first 24 months — softly made in India." },
      { property: "og:title", content: "Mini's n More — Heirloom baby clothing 0–24m" },
      { property: "og:description", content: "Minis today, memories forever. Premium organic baby clothing for the first 24 months — softly made in India." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
      { name: "twitter:title", content: "Mini's n More — Heirloom baby clothing 0–24m" },
      { name: "twitter:description", content: "Minis today, memories forever. Premium organic baby clothing for the first 24 months — softly made in India." },
      { property: "og:image", content: SOCIAL_SHARE_IMAGE },
      { name: "twitter:image", content: SOCIAL_SHARE_IMAGE },
    ],
    links: [
      { rel: "stylesheet", href: appCss },
      { rel: "icon", href: "/favicon.ico", sizes: "48x48" },
      { rel: "icon", href: "/favicon-16x16.png", type: "image/png", sizes: "16x16" },
      { rel: "icon", href: "/favicon-32x32.png", type: "image/png", sizes: "32x32" },
      { rel: "icon", href: "/icon-192.png", type: "image/png", sizes: "192x192" },
      { rel: "icon", href: "/icon-512.png", type: "image/png", sizes: "512x512" },
      { rel: "apple-touch-icon", href: "/apple-touch-icon.png", sizes: "180x180" },
      { rel: "preconnect", href: "https://fonts.googleapis.com" },
      { rel: "preconnect", href: "https://fonts.gstatic.com", crossOrigin: "anonymous" },
      { rel: "preconnect", href: "https://pub-f227d5520be64479a318380ac346334d.r2.dev" },
      { rel: "stylesheet", href: "https://fonts.googleapis.com/css2?family=Allura&family=Outfit:wght@300;400;500;600;700&family=Plus+Jakarta+Sans:wght@300;400;500;600;700&display=swap" },
    ],
  }),
  shellComponent: RootShell,
  component: RootComponent,
  notFoundComponent: NotFoundComponent,
});

function RootShell({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head><HeadContent /></head>
      <body>
        {children}
        <Scripts />
      </body>
    </html>
  );
}

function RootComponent() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const isAdmin = pathname.startsWith("/admin");
  const isCustomerInvoice = /^\/account\/orders\/[^/]+\/invoice$/.test(pathname);
  const isInvoice = isCustomerInvoice || /^\/admin\/orders\/[^/]+\/invoice$/.test(pathname);

  return (
    <AuthProvider>
    <ShopProvider>
      <CatalogSync />
      {!isAdmin && !isInvoice && (
        <>
          <Watermark />
          <ScrollProgress />
          <div aria-hidden className="fixed inset-0 pointer-events-none z-0">
            <FloatingDecor count={10} variant="leaves" />
          </div>
          <Navbar />
        </>
      )}
      <main className={isAdmin || isInvoice ? "relative z-10" : "relative z-10 pt-24 md:pt-28"}>
        <Outlet />
      </main>
      {!isAdmin && !isInvoice && (
        <>
          <Footer />
          <CartDrawer />
          <QuickView />
        </>
      )}
      <Toaster position="top-center" richColors />
    </ShopProvider>
    </AuthProvider>
  );
}
