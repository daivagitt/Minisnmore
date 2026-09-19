import { Outlet, createRootRoute, HeadContent, Scripts, Link, useRouterState } from "@tanstack/react-router";
import { useEffect, useRef } from "react";
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
import { initStorefrontCacheSync, invalidateAllStorefrontCaches } from "@/lib/storefront-cache-sync";
import { SITE_ORIGIN, SOCIAL_SHARE_IMAGE, uatNoindexMeta } from "@/lib/site-seo";
import { isStaffInvoicePath } from "@/lib/staff-roles";
const ORGANIZATION_JSON_LD = {
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "Organization",
      "@id": `${SITE_ORIGIN}/#organization`,
      name: "Mini's n More",
      legalName: "Stolt Global Private Limited",
      url: `${SITE_ORIGIN}/`,
      logo: {
        "@type": "ImageObject",
        url: `${SITE_ORIGIN}/icon-512.png`,
        width: 512,
        height: 512,
      },
    },
    {
      "@type": "WebSite",
      "@id": `${SITE_ORIGIN}/#website`,
      url: `${SITE_ORIGIN}/`,
      name: "Mini's n More",
      publisher: { "@id": `${SITE_ORIGIN}/#organization` },
    },
  ],
};

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
      ...(() => {
        const noindex = uatNoindexMeta();
        return noindex ? [noindex] : [];
      })(),
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
      { rel: "icon", href: "/favicon-48x48.png", type: "image/png", sizes: "48x48" },
      { rel: "icon", href: "/favicon-96x96.png", type: "image/png", sizes: "96x96" },
      { rel: "icon", href: "/favicon.ico", type: "image/x-icon", sizes: "48x48" },
      { rel: "apple-touch-icon", href: "/apple-touch-icon.png", sizes: "180x180" },
      { rel: "manifest", href: "/site.webmanifest" },
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
      <head>
        <HeadContent />
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(ORGANIZATION_JSON_LD) }} />
      </head>
      <body>
        {children}
        <Scripts />
      </body>
    </html>
  );
}

function RootComponent() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const isStaffApp =
    pathname.startsWith("/admin") ||
    pathname.startsWith("/operations") ||
    pathname.startsWith("/sales") ||
    pathname.startsWith("/customersupport") ||
    pathname.startsWith("/invite");
  const wasAdminRef = useRef(isStaffApp);
  const isCustomerInvoice = /^\/account\/orders\/[^/]+\/invoice$/.test(pathname);
  const isInvoice = isCustomerInvoice || isStaffInvoicePath(pathname);
  const isProductPage = !isStaffApp && !isInvoice && pathname.startsWith("/product/");
  const pdpScrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    return initStorefrontCacheSync();
  }, []);

  useEffect(() => {
    if (wasAdminRef.current && !isStaffApp) {
      invalidateAllStorefrontCaches({ broadcast: false });
    }
    wasAdminRef.current = isStaffApp;
  }, [isStaffApp]);

  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.visibilityState === "visible" && !isStaffApp) {
        invalidateAllStorefrontCaches({ broadcast: false });
      }
    };
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => document.removeEventListener("visibilitychange", onVisibilityChange);
  }, [pathname, isStaffApp]);

  useEffect(() => {
    const reloadKey = "mini:chunk-reload-attempted";
    const clearReloadFlag = window.setTimeout(() => sessionStorage.removeItem(reloadKey), 5000);
    const isChunkLoadError = (value: unknown) => {
      const message = value instanceof Error ? value.message : String(value ?? "");
      return /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i.test(message);
    };
    const reloadOnce = () => {
      if (sessionStorage.getItem(reloadKey) === "1") return;
      sessionStorage.setItem(reloadKey, "1");
      window.location.reload();
    };
    const onRejection = (event: PromiseRejectionEvent) => {
      if (isChunkLoadError(event.reason)) reloadOnce();
    };
    const onError = (event: ErrorEvent) => {
      if (isChunkLoadError(event.error ?? event.message)) reloadOnce();
    };
    window.addEventListener("unhandledrejection", onRejection);
    window.addEventListener("error", onError);
    return () => {
      window.clearTimeout(clearReloadFlag);
      window.removeEventListener("unhandledrejection", onRejection);
      window.removeEventListener("error", onError);
    };
  }, []);

  useEffect(() => {
    if (!isProductPage) return;
    const html = document.documentElement;
    const { overflow: prevHtmlOverflow, height: prevHtmlHeight } = html.style;
    const { overflow: prevBodyOverflow, height: prevBodyHeight } = document.body.style;
    html.classList.add("pdp-lock-scroll");
    document.body.classList.add("pdp-lock-scroll");
    html.style.overflow = "hidden";
    html.style.height = "100%";
    document.body.style.overflow = "hidden";
    document.body.style.height = "100%";
    return () => {
      html.classList.remove("pdp-lock-scroll");
      document.body.classList.remove("pdp-lock-scroll");
      html.style.overflow = prevHtmlOverflow;
      html.style.height = prevHtmlHeight;
      document.body.style.overflow = prevBodyOverflow;
      document.body.style.height = prevBodyHeight;
    };
  }, [isProductPage]);

  useEffect(() => {
    if (!isProductPage) return;
    pdpScrollRef.current?.scrollTo({ top: 0, left: 0, behavior: "auto" });
  }, [isProductPage, pathname]);

  return (
    <AuthProvider>
    <ShopProvider>
      <CatalogSync />
      {!isStaffApp && !isInvoice && (
        <>
          <Watermark />
          <div aria-hidden className="fixed inset-0 pointer-events-none z-0">
            <FloatingDecor count={10} variant="leaves" />
          </div>
          <Navbar />
        </>
      )}
      {isProductPage ? (
        <div className="relative z-10 flex h-[100dvh] flex-col overflow-hidden">
          <div className="h-24 shrink-0 md:h-28" aria-hidden />
          <div
            ref={pdpScrollRef}
            data-pdp-scroll
            className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto overscroll-y-contain"
          >
            <main>
              <Outlet />
            </main>
            <Footer />
          </div>
        </div>
      ) : (
        <>
          <main className={isStaffApp || isInvoice ? "relative z-10" : "relative z-10 pt-24 md:pt-28"}>
            <Outlet />
          </main>
          {!isStaffApp && !isInvoice && <Footer />}
        </>
      )}
      {!isStaffApp && !isInvoice && (
        <ScrollProgress
          key={isProductPage ? "pdp" : "page"}
          containerRef={isProductPage ? pdpScrollRef : undefined}
        />
      )}
      {!isStaffApp && !isInvoice && (
        <>
          <CartDrawer />
          <QuickView />
        </>
      )}
      <Toaster position="top-center" richColors />
    </ShopProvider>
    </AuthProvider>
  );
}
