import { Link, useNavigate } from "@tanstack/react-router";
import { motion, AnimatePresence } from "framer-motion";
import { Heart, ShoppingBag, Search, Menu, X, User, ArrowRight, Sparkles, Star, Flame } from "lucide-react";
import { useState, useEffect, useRef, useMemo, useId } from "react";
import { useShop } from "@/store/shop";
import { useProducts } from "@/hooks/useProducts";
import { usePageVisibility } from "@/hooks/usePageVisibility";
import { useCategories, type Category } from "@/hooks/useCategories";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { useAuth } from "@/hooks/useAuth";
import { buildShopSearch } from "@/lib/shop-filters";
import { filterProductsByQuery } from "@/lib/product-search";
import defaultLogo from "@/assets/logo-horizontal-optimized.webp";

const NAV_ITEMS = [
  { to: "/", label: "Home", key: null as string | null },
  { to: "/shop", label: "Shop", key: "shop" },
  { to: "/collections", label: "Collections", key: null as string | null },
  { to: "/gift", label: "Gift", key: "gift" },
  { to: "/about", label: "About", key: null as string | null },
  { to: "/track", label: "Track", key: "track" },
] as const;

export function Navbar() {
  const { cartCount, setCartOpen, wishlist } = useShop();
  const { isVisible } = usePageVisibility();
  const categories = useCategories();
  const products = useProducts();
  const settings = useSiteSettings();
  const { user } = useAuth();
  const profileTo = user ? "/account" : "/login";
  const profileLabel = user ? "Profile" : "Sign in";
  const logo = settings.logo_horizontal_url || settings.logo_url || defaultLogo;
  const nav = NAV_ITEMS.filter((n) => !n.key || isVisible(n.key));
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [hidden, setHidden] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [shopHover, setShopHover] = useState(false);
  const [collectionsHover, setCollectionsHover] = useState(false);
  const [q, setQ] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const lastY = useRef(0);
  const listboxId = useId();
  const navigate = useNavigate();

  useEffect(() => {
    const onScroll = () => {
      const y = window.scrollY;
      setScrolled(y > 20);
      // hide on scroll down past 120px, show on scroll up
      if (y > 120 && y > lastY.current) setHidden(true);
      else setHidden(false);
      lastY.current = y;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    if (!searchOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [searchOpen]);

  const results = useMemo(
    () => filterProductsByQuery(products, q, { limit: 8, ranked: true }),
    [q, products],
  );

  useEffect(() => {
    setActiveIndex(0);
  }, [q]);

  const closeSearch = () => {
    setSearchOpen(false);
    setQ("");
    setActiveIndex(0);
  };

  const goToShopResults = () => {
    const query = q.trim();
    navigate({
      to: "/shop",
      search: buildShopSearch({ q: query || undefined, sort: "new" }),
    });
    closeSearch();
  };

  const goToResult = (slug: string) => {
    navigate({ to: "/product/$slug", params: { slug } });
    closeSearch();
  };
  return (
    <motion.header
      initial={{ y: -40, opacity: 0 }}
      animate={{ y: hidden ? -120 : 0, opacity: 1 }}
      transition={{ duration: 0.45, ease: [0.2, 0.8, 0.2, 1] }}
      className="fixed top-2 left-2 right-2 sm:top-3 sm:left-3 sm:right-3 md:top-5 md:left-6 md:right-6 z-50">
      <div className={`glass-strong rounded-full px-3 sm:px-4 md:px-7 py-2.5 md:py-3 flex items-center justify-between gap-2 transition-all duration-500 ${scrolled ? "shadow-soft" : ""}`}>
        <Link
          to="/"
          onClick={() => {
            if (typeof window !== "undefined") {
              window.scrollTo({ top: 0, behavior: "smooth" });
            }
          }}
          className="flex items-center group shrink-0 mr-4 md:mr-6 gap-3 md:gap-4"
          aria-label={`${settings.brand_name} — home`}
        >
          <img
            src={logo}
            alt={settings.brand_name}
            className="h-8 sm:h-9 md:h-10 lg:h-11 w-auto object-contain transition-transform duration-500 group-hover:scale-[1.02]"
            loading="eager"
            decoding="async"
          />
        </Link>

        <nav className="hidden lg:flex items-center gap-8 text-sm font-medium tracking-wider">
          {nav.map((n) => {
            if (n.to === "/shop") {
              return (
                <div
                  key={n.to}
                  className="relative"
                  onMouseEnter={() => setShopHover(true)}
                  onMouseLeave={() => setShopHover(false)}
                >
                  <Link to={n.to} className="story-link text-foreground hover:text-primary transition-colors drop-shadow-[0_1px_0_rgba(255,255,255,0.6)]"
                    activeProps={{ className: "text-primary font-semibold" }}>
                    {n.label}
                  </Link>
                  <AnimatePresence>
                    {shopHover && (
                      <motion.div
                        initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 8 }}
                        transition={{ duration: 0.18 }}
                        className="absolute left-1/2 -translate-x-1/2 top-full pt-4 w-[min(1100px,94vw)] z-50">
                        <MegaMenu categories={categories} products={products} onClose={() => setShopHover(false)} />
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              );
            }
            if (n.to === "/collections") {
              return (
                <div
                  key={n.to}
                  className="relative"
                  onMouseEnter={() => setCollectionsHover(true)}
                  onMouseLeave={() => setCollectionsHover(false)}
                >
                  <Link
                    to="/best-sellers"
                    className="story-link text-foreground hover:text-primary transition-colors drop-shadow-[0_1px_0_rgba(255,255,255,0.6)]"
                  >
                    {n.label}
                  </Link>
                  <AnimatePresence>
                    {collectionsHover && (
                      <motion.div
                        initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 8 }}
                        transition={{ duration: 0.18 }}
                        className="absolute left-1/2 -translate-x-1/2 top-full pt-4 w-[min(360px,94vw)] z-50"
                      >
                        <CollectionsMenu onClose={() => setCollectionsHover(false)} />
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              );
            }
            return (
              <Link key={n.to} to={n.to} className="story-link text-foreground hover:text-primary transition-colors drop-shadow-[0_1px_0_rgba(255,255,255,0.6)]"
                activeProps={{ className: "text-primary font-semibold" }}>
                {n.label}
              </Link>
            );
          })}
        </nav>

        <div className="flex items-center gap-1 sm:gap-2">
          <button onClick={() => setSearchOpen(true)} title="Search" className="grid h-10 w-10 place-items-center rounded-full hover:bg-white/40 transition" aria-label="Search">
            <Search className="h-4.5 w-4.5" />
          </button>
          <Link to={profileTo} title={profileLabel} className="hidden md:grid h-10 w-10 place-items-center rounded-full hover:bg-white/40 transition" aria-label={profileLabel}>
            <User className="h-5 w-5" strokeWidth={1.6} />
          </Link>
          <Link to="/wishlist" title="Wishlist" className="relative h-10 w-10 grid place-items-center rounded-full hover:bg-white/40 transition" aria-label="Wishlist">
            <span className="relative">
              <Heart className="h-5 w-5" strokeWidth={1.6} />
              {wishlist.length > 0 && (
                <span className="absolute -top-1.5 -right-1.5 h-4 min-w-4 px-1 rounded-full bg-primary text-primary-foreground text-[9px] font-bold grid place-items-center">{wishlist.length}</span>
              )}
            </span>
          </Link>
          <button
            onClick={() => setCartOpen(true)}
            onContextMenu={(e) => e.preventDefault()}
            title="Bag"
            className="relative h-10 w-10 grid place-items-center rounded-full hover:bg-white/40 transition"
            aria-label="Bag">
            <span className="relative">
              <ShoppingBag className="h-5 w-5" strokeWidth={1.6} />
              {cartCount > 0 && (
                <span className="absolute -top-1.5 -right-1.5 h-4 min-w-4 px-1 rounded-full bg-primary text-primary-foreground text-[9px] font-bold grid place-items-center">{cartCount}</span>
              )}
            </span>
          </button>
          <button onClick={() => setOpen((v) => !v)} title="Menu" className="lg:hidden grid h-9 w-9 md:h-10 md:w-10 place-items-center rounded-full hover:bg-white/40 transition" aria-label="Menu">
            {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
      </div>

      <AnimatePresence>
        {open && (
          <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}
            className="lg:hidden mt-2 glass-strong rounded-3xl p-5 flex flex-col gap-3">
            <button onClick={() => { setOpen(false); setSearchOpen(true); }} className="flex items-center gap-2 text-base py-1.5 text-left">
              <Search className="h-4 w-4" /> Search
            </button>
            {nav.map((n) => (
              <Link key={n.to} to={n.to} onClick={() => setOpen(false)} className="text-base py-1.5">
                {n.label}
              </Link>
            ))}
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {searchOpen && (
          <motion.div
            role="dialog"
            aria-modal="true"
            aria-label="Search products"
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="fixed inset-0 z-[80] bg-foreground/30 backdrop-blur-md grid place-items-start pt-24 px-4"
            onClick={closeSearch}>
            <motion.div
              initial={{ y: -20, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: -20, opacity: 0 }}
              onClick={(e) => e.stopPropagation()}
              className="w-full max-w-2xl mx-auto glass-strong rounded-3xl p-3">
              <div className="flex items-center gap-2 px-3">
                <Search className="h-4 w-4 text-muted-foreground" aria-hidden />
                <input
                  autoFocus
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  placeholder="Search name, category, fabric, or SKU…"
                  className="flex-1 bg-transparent outline-none py-3 text-base"
                  role="combobox"
                  aria-expanded={!!q.trim()}
                  aria-controls={listboxId}
                  aria-autocomplete="list"
                  aria-activedescendant={results[activeIndex] ? `${listboxId}-option-${activeIndex}` : undefined}
                  onKeyDown={(e) => {
                    if (e.key === "Escape") {
                      e.preventDefault();
                      closeSearch();
                      return;
                    }
                    if (e.key === "ArrowDown") {
                      e.preventDefault();
                      if (!results.length) return;
                      setActiveIndex((i) => (i + 1) % results.length);
                      return;
                    }
                    if (e.key === "ArrowUp") {
                      e.preventDefault();
                      if (!results.length) return;
                      setActiveIndex((i) => (i - 1 + results.length) % results.length);
                      return;
                    }
                    if (e.key === "Enter") {
                      e.preventDefault();
                      if (results[activeIndex]) {
                        goToResult(results[activeIndex].slug);
                      } else if (q.trim()) {
                        goToShopResults();
                      }
                    }
                  }}
                />
                <button type="button" onClick={closeSearch} className="h-9 w-9 grid place-items-center rounded-full hover:bg-white/40" aria-label="Close search">
                  <X className="h-4 w-4" />
                </button>
              </div>
              {q.trim() ? (
                <div className="mt-2 max-h-[60vh] overflow-y-auto">
                  {results.length === 0 ? (
                    <div className="px-3 py-6 text-sm text-muted-foreground">
                      No little pieces match “{q.trim()}”.
                      <button type="button" onClick={goToShopResults} className="block mt-3 text-primary story-link text-sm">
                        Browse shop anyway
                      </button>
                    </div>
                  ) : (
                    <ul id={listboxId} role="listbox" className="space-y-0.5">
                      {results.map((p, index) => (
                        <li key={p.id} role="option" id={`${listboxId}-option-${index}`} aria-selected={index === activeIndex}>
                          <Link
                            to="/product/$slug"
                            params={{ slug: p.slug }}
                            onClick={closeSearch}
                            onMouseEnter={() => setActiveIndex(index)}
                            className={`flex items-center gap-3 p-2 rounded-2xl transition ${index === activeIndex ? "bg-white/50" : "hover:bg-white/40"}`}
                          >
                            <img src={p.image} alt="" className="h-12 w-12 rounded-xl object-cover" />
                            <div className="flex-1 min-w-0">
                              <div className="text-sm font-medium truncate">{p.name}</div>
                              <div className="text-xs text-muted-foreground truncate">
                                {p.sku ? `${p.sku} · ` : ""}
                                {p.tagline}
                              </div>
                            </div>
                            <div className="text-sm">₹{p.price.toLocaleString("en-IN")}</div>
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                  {results.length > 0 ? (
                    <button
                      type="button"
                      onClick={goToShopResults}
                      className="w-full mt-2 px-3 py-2.5 text-sm text-primary flex items-center justify-center gap-1.5 rounded-2xl hover:bg-white/40"
                    >
                      View all results in shop <ArrowRight className="h-3.5 w-3.5" />
                    </button>
                  ) : null}
                </div>
              ) : (
                <div className="px-3 py-3 text-xs text-muted-foreground">
                  Try: <button type="button" onClick={() => setQ("muslin")} className="underline mx-1">muslin</button>·
                  <button type="button" onClick={() => setQ("romper")} className="underline mx-1">romper</button>·
                  <button type="button" onClick={() => setQ("newborn")} className="underline mx-1">newborn</button>
                </div>
              )}
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.header>
  );
}

function MegaMenu({ categories, products, onClose }: { categories: Category[]; products: ReturnType<typeof useProducts>; onClose: () => void }) {
  const lovedNow = (() => {
    const flagged = products.filter((p) => p.isBestseller || p.isTrending || p.isNew);
    return (flagged.length > 0 ? flagged : products).slice(0, 3);
  })();
  const newArrivals = products.filter((p) => p.isNew);
  const bestsellers = products.filter((p) => p.isBestseller);
  const trending = products.filter((p) => p.isTrending);
  const left = categories.slice(0, Math.ceil(categories.length / 2));
  const right = categories.slice(Math.ceil(categories.length / 2));
  const hasCategories = categories.length > 0;

  return (
    <div className="rounded-[2rem] shadow-soft overflow-hidden border border-white/60 bg-background/98 backdrop-blur-2xl">
      <div className="grid grid-cols-12 gap-0">
        {/* Left rail — quick edits (only what we actually have) */}
        <div className="col-span-12 md:col-span-3 p-6 bg-gradient-to-br from-primary/15 via-secondary/30 to-transparent border-r border-border/60">
          <div className="inline-flex items-center gap-1.5 text-[11px] uppercase tracking-[0.28em] text-primary mb-3 font-semibold">
            <Sparkles className="h-3 w-3" /> Quick Edits
          </div>
          <ul className="space-y-1">
            <li>
              <Link to="/shop" onClick={onClose} className="group block rounded-2xl px-3 py-2.5 hover:bg-primary/10 transition">
                <div className="font-display text-base leading-tight text-foreground">All Products</div>
                <div className="text-[11px] text-muted-foreground">Browse the full collection</div>
              </Link>
            </li>
            <li>
              <Link to="/new-arrivals" onClick={onClose} className="group block rounded-2xl px-3 py-2.5 hover:bg-primary/10 transition">
                <div className="font-display text-base leading-tight text-foreground">New Arrivals</div>
                <div className="text-[11px] text-muted-foreground">
                  {newArrivals.length > 0 ? `${newArrivals.length} fresh pieces` : "Fresh off the loom"}
                </div>
              </Link>
            </li>
            <li>
              <Link to="/best-sellers" onClick={onClose} className="group block rounded-2xl px-3 py-2.5 hover:bg-primary/10 transition">
                <div className="font-display text-base leading-tight text-foreground">Bestsellers</div>
                <div className="text-[11px] text-muted-foreground">
                  {bestsellers.length > 0 ? "Loved by little families" : "Forever favourites"}
                </div>
              </Link>
            </li>
            <li>
              <Link to="/trending" onClick={onClose} className="group block rounded-2xl px-3 py-2.5 hover:bg-primary/10 transition">
                <div className="font-display text-base leading-tight text-foreground">Trending</div>
                <div className="text-[11px] text-muted-foreground">
                  {trending.length > 0 ? "Popular right now" : "Loved this week"}
                </div>
              </Link>
            </li>
          </ul>
        </div>

        {/* Middle — categories in two columns */}
        <div className="col-span-12 md:col-span-5 p-6 bg-background/60">
          <div className="text-[11px] uppercase tracking-[0.28em] text-foreground/70 mb-3 font-semibold">Shop by Category</div>
          {!hasCategories ? (
            <div className="text-sm text-muted-foreground">Loading categories…</div>
          ) : (
            <div className="grid grid-cols-2 gap-1">
              {[left, right].map((col, ci) => (
                <ul key={ci} className="space-y-1">
                  {col.map((c) => (
                    <li key={c.id}>
                      <Link
                        to="/shop"
                        search={{ category: c.slug }}
                        onClick={onClose}
                        className="group flex items-center gap-3 rounded-2xl p-2 hover:bg-primary/10 transition"
                      >
                        {c.image_url ? (
                          <img src={c.image_url} alt="" className="h-11 w-11 rounded-xl object-cover shrink-0" loading="lazy" />
                        ) : (
                          <div className="h-11 w-11 rounded-xl bg-primary/10 shrink-0" />
                        )}
                        <div className="min-w-0 flex-1">
                          <div className="text-sm font-semibold text-foreground truncate group-hover:text-primary transition">{c.name}</div>
                          {c.description && <div className="text-[11px] text-muted-foreground truncate">{c.description}</div>}
                        </div>
                        <ArrowRight className="h-3.5 w-3.5 opacity-0 -translate-x-1 group-hover:opacity-60 group-hover:translate-x-0 transition" />
                      </Link>
                    </li>
                  ))}
                </ul>
              ))}
            </div>
          )}
          <Link
            to="/shop"
            onClick={onClose}
            className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-primary story-link"
          >
            View all products <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </div>

        {/* Right — featured products showcase */}
        {lovedNow.length > 0 && (
        <div className="hidden md:block col-span-4 p-6 bg-gradient-to-bl from-secondary/50 via-background/40 to-transparent border-l border-border/60">
          <div className="text-[11px] uppercase tracking-[0.28em] text-foreground/70 mb-3 font-semibold">Loved Right Now</div>
          <div className="space-y-2.5">
            {lovedNow.map((p) => (
              <Link
                key={p.id}
                to="/product/$slug"
                params={{ slug: p.slug }}
                onClick={onClose}
                className="group flex items-center gap-3 rounded-2xl p-2 hover:bg-primary/10 transition"
              >
                <img src={p.image} alt={p.name} className="h-14 w-14 rounded-xl object-cover shrink-0" loading="lazy" />
                <div className="min-w-0 flex-1">
                  <div className="text-[10px] uppercase tracking-[0.22em] text-muted-foreground">{p.category}</div>
                  <div className="text-sm font-semibold text-foreground truncate group-hover:text-primary transition">{p.name}</div>
                  <div className="text-xs font-medium text-primary">₹{p.price.toLocaleString("en-IN")}</div>
                </div>
              </Link>
            ))}
          </div>
        </div>
        )}
      </div>
    </div>
  );
}

function CollectionsMenu({ onClose }: { onClose: () => void }) {
  const items = [
    { to: "/best-sellers", label: "Best Sellers", desc: "Forever favourites", icon: Star },
    { to: "/new-arrivals", label: "New Arrivals", desc: "Just off the loom", icon: Sparkles },
    { to: "/trending", label: "Trending", desc: "Loved this week", icon: Flame },
  ] as const;
  return (
    <div className="rounded-[1.75rem] shadow-soft overflow-hidden border border-white/60 bg-background/98 backdrop-blur-2xl p-3">
      <div className="text-[11px] uppercase tracking-[0.28em] text-foreground/70 px-3 pt-2 pb-1 font-semibold">Collections</div>
      <ul className="flex flex-col">
        {items.map((it) => {
          const Icon = it.icon;
          return (
            <li key={it.to}>
              <Link
                to={it.to}
                onClick={onClose}
                className="group flex items-center gap-3 rounded-2xl p-3 hover:bg-primary/10 transition"
              >
                <span className="h-10 w-10 grid place-items-center rounded-xl bg-primary/10 text-primary shrink-0">
                  <Icon className="h-4 w-4" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block font-display text-base leading-tight text-foreground">{it.label}</span>
                  <span className="block text-[11px] text-muted-foreground">{it.desc}</span>
                </span>
                <ArrowRight className="h-3.5 w-3.5 opacity-0 -translate-x-1 group-hover:opacity-60 group-hover:translate-x-0 transition" />
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
