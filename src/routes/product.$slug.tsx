import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import type { Product } from "@/data/products";
import { useProduct, useProducts } from "@/hooks/useProducts";
import { useShop } from "@/store/shop";
import { Heart, Truck, Leaf, ChevronRight, MessageCircle, Ruler, Plus, Minus, Share2, X, BadgeCheck, Banknote, RotateCcw } from "lucide-react";
import { toast } from "sonner";
import { ProductCard } from "@/components/ProductCard";
import { clampQuantity, matchProductSize, resolveRecommendedSize } from "@/lib/product-fit";
import { lookupPincode, normalizePincode } from "@/lib/pincode";

export const Route = createFileRoute("/product/$slug")({
  head: ({ params }) => ({
    meta: [
      { title: `${params.slug} · Mini's n More` },
    ],
  }),
  component: ProductPage,
  notFoundComponent: () => (
    <div className="px-6 py-24 text-center">
      <div className="font-display text-3xl">Piece not found</div>
      <Link to="/shop" className="story-link mt-4 inline-block">Back to shop</Link>
    </div>
  ),
});

function ProductPage() {
  const { slug } = Route.useParams();
  const products = useProducts();
  const product = useProduct(slug) as Product | undefined;
  const { addToCart, toggleWish, wishlist } = useShop();
  const [size, setSize] = useState<string | null>(() => product?.sizes?.[0] ?? null);
  const [color, setColor] = useState<string | null>(() => product?.colors?.[0]?.name ?? null);
  const [active, setActive] = useState(0);
  const [heightCm, setHeightCm] = useState<string>("");
  const [sizeChart, setSizeChart] = useState(false);
  const [qty, setQty] = useState(1);
  const [pin, setPin] = useState("");
  const [pinResult, setPinResult] = useState<{ city: string; state: string; eta: string } | null>(null);
  const [pinLoading, setPinLoading] = useState(false);
  const [pinError, setPinError] = useState<string | null>(null);
  const wished = product ? wishlist.includes(product.id) : false;

  useEffect(() => {
    if (!product) return;
    setSize((prev) => (prev && product.sizes.includes(prev) ? prev : product.sizes[0] ?? null));
    setColor((prev) => (prev && product.colors.some((c) => c.name === prev) ? prev : product.colors[0]?.name ?? null));
  }, [product]);

  if (!product) {
    return (
      <div className="px-6 py-24 text-center">
        <div className="font-display text-3xl">Loading…</div>
      </div>
    );
  }
  const recommendedProducts = products
    .filter((p) => p.id !== product.id && (p.category === product.category || p.fabric === product.fabric))
    .concat(products.filter((p) => p.id !== product.id))
    .filter((p, i, arr) => arr.findIndex((x) => x.id === p.id) === i)
    .slice(0, 4);

  type MediaItem = { type: "image"; src: string };
  const media: MediaItem[] = [
    { type: "image", src: product.image },
    ...product.gallery.map((g) => ({ type: "image" as const, src: g })),
  ];
  const activeMedia = media[Math.min(active, media.length - 1)] ?? media[0];

  const recommended = resolveRecommendedSize({ heightCm, sizes: product.sizes });
  const recommendedSize = recommended ? matchProductSize(recommended, product.sizes) ?? recommended : null;

  const checkPin = async () => {
    setPinLoading(true);
    setPinError(null);
    setPinResult(null);
    const result = await lookupPincode(pin);
    if (result.ok) {
      setPinResult(result.lookup);
    } else {
      setPinError(result.error);
    }
    setPinLoading(false);
  };

  return (
    <div className="max-w-7xl mx-auto w-full px-4 md:px-6 space-y-10 pb-24 lg:pb-12">
      <div className="text-xs text-muted-foreground flex items-center gap-1.5 flex-wrap">
        <Link to="/" className="hover:text-foreground">Home</Link><ChevronRight className="h-3 w-3" />
        <Link to="/shop" className="hover:text-foreground">Shop</Link><ChevronRight className="h-3 w-3" />
        <span className="truncate">{product.name}</span>
      </div>

      <div className="grid lg:grid-cols-2 gap-6 lg:gap-8 lg:items-start">
        <div className="space-y-3 w-full min-w-0">
          <motion.div
            initial={{ opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.6 }}
            className="glass rounded-[2rem] p-3 relative w-full"
          >
            <div className="relative aspect-square overflow-hidden rounded-2xl bg-secondary/40">
              <img
                src={activeMedia?.src}
                alt={product.name}
                className="h-full w-full object-cover"
              />
            </div>
          </motion.div>
          {media.length > 1 && (
            <div className="grid grid-cols-4 gap-2">
              {media.map((m, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => setActive(i)}
                  className={`glass rounded-xl p-1.5 transition ${active === i ? "ring-2 ring-primary" : ""}`}
                >
                  <img src={m.src} alt="" className="h-full w-full object-cover rounded-lg aspect-square" loading="lazy" />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Purchase card — compact, fully visible like reference PDP */}
        <div className="glass-strong rounded-3xl p-4 md:p-5 space-y-4 w-full min-w-0">
          <div className="space-y-1.5">
            <div className="text-[10px] uppercase tracking-[0.24em] text-primary">{product.fabric} · {product.category}</div>
            <h1 className="font-display text-xl md:text-2xl lg:text-[1.65rem] leading-tight text-balance">{product.name}</h1>
            {product.tagline ? <p className="text-muted-foreground text-sm leading-snug">{product.tagline}</p> : null}
            <div className="flex flex-wrap items-baseline gap-2 pt-0.5">
              <span className="font-display text-2xl">&#8377;{product.price.toLocaleString("en-IN")}</span>
              {product.compareAt && <span className="line-through text-muted-foreground text-sm">&#8377;{product.compareAt.toLocaleString("en-IN")}</span>}
              {product.compareAt && (
                <span className="rounded-md bg-primary/15 px-2 py-0.5 text-[11px] font-semibold text-primary uppercase tracking-wide">
                  Save &#8377;{(product.compareAt - product.price).toLocaleString("en-IN")}
                </span>
              )}
            </div>
          </div>

          <div className="space-y-3">
            <div>
              <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1.5">Color · {color}</div>
              <div className="flex gap-2">
                {product.colors.map((c) => (
                  <button key={c.name} onClick={() => setColor(c.name)}
                    className={`h-8 w-8 rounded-full border-2 transition ${color === c.name ? "border-primary scale-110" : "border-white/70"}`}
                    style={{ background: c.hex }} aria-label={c.name} />
                ))}
              </div>
            </div>
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <div className="text-[10px] uppercase tracking-wider text-muted-foreground">Size</div>
                <button type="button" onClick={() => setSizeChart(true)} className="text-[11px] text-primary underline-offset-2 hover:underline">Size chart</button>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {product.sizes.map((s) => (
                  <button key={s} type="button" onClick={() => setSize(s)}
                    className={`rounded-full px-3 py-1.5 text-xs transition ${size === s ? "bg-primary text-primary-foreground" : "glass hover:bg-white/60"}`}>
                    {s}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <details className="glass rounded-xl p-3 group">
            <summary className="flex items-center gap-2 cursor-pointer text-xs font-medium list-none">
              <Ruler className="h-3.5 w-3.5 text-primary shrink-0" /> Size calculator
            </summary>
            <div className="mt-3">
              <label className="text-xs text-muted-foreground space-y-1 block">
                <span>Height (cm)</span>
                <div className="relative max-w-xs">
                  <input
                    type="number" inputMode="decimal" min={30} max={120}
                    value={heightCm}
                    onChange={(e) => setHeightCm(e.target.value)}
                    placeholder="e.g. 75"
                    className="w-full h-10 glass rounded-full px-4 pr-12 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40"
                  />
                  <span className="absolute right-4 top-1/2 -translate-y-1/2 text-[11px] text-muted-foreground">cm</span>
                </div>
              </label>
              {recommendedSize ? (
                <div className="mt-2 flex items-center justify-between gap-2 text-xs">
                  <span className="text-muted-foreground">
                    Best fit: <span className="text-primary font-medium">{recommendedSize}</span>
                  </span>
                  <button type="button" onClick={() => setSize(recommendedSize)}
                    className="text-primary underline-offset-2 hover:underline shrink-0">Use size</button>
                </div>
              ) : (
                <p className="text-[11px] text-muted-foreground mt-2">Enter height (30–120 cm) for a size suggestion.</p>
              )}
            </div>
          </details>

          <div className="grid grid-cols-[auto_1fr] gap-3 items-end">
            <div>
              <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1.5">Quantity</div>
              <div className="glass rounded-full inline-flex items-center">
                <button type="button" onClick={() => setQty((q) => clampQuantity(q - 1))}
                  className="h-10 w-10 grid place-items-center" aria-label="Decrease quantity">
                  <Minus className="h-4 w-4" />
                </button>
                <span className="w-8 text-center text-sm font-medium tabular-nums">{qty}</span>
                <button type="button" onClick={() => setQty((q) => clampQuantity(q + 1))}
                  className="h-10 w-10 grid place-items-center" aria-label="Increase quantity">
                  <Plus className="h-4 w-4" />
                </button>
              </div>
            </div>
            <div className="min-w-0">
              <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1.5 flex items-center gap-1">
                <Truck className="h-3 w-3 text-primary" /> Check delivery
              </div>
              <div className="flex gap-2 min-w-0">
                <input
                  value={pin}
                  onChange={(e) => {
                    setPin(normalizePincode(e.target.value));
                    setPinError(null);
                    setPinResult(null);
                  }}
                  onKeyDown={(e) => e.key === "Enter" && void checkPin()}
                  inputMode="numeric"
                  placeholder="Pincode"
                  maxLength={6}
                  className="flex-1 min-w-0 h-10 glass rounded-full px-3 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40"
                />
                <button type="button" onClick={() => void checkPin()} disabled={pinLoading}
                  className="h-10 shrink-0 rounded-full bg-foreground text-background px-4 text-xs font-medium disabled:opacity-50">
                  {pinLoading ? "…" : "Check"}
                </button>
              </div>
              {pinResult && (
                <p className="mt-1.5 text-[11px] text-primary leading-snug">
                  {pinResult.city}, {pinResult.state} · <strong>{pinResult.eta}</strong>
                </p>
              )}
              {pinError && <p className="mt-1.5 text-[11px] text-destructive">{pinError}</p>}
            </div>
          </div>

          <div className="flex gap-2">
            <button type="button" onClick={() => addToCart(product, { size: size ?? product.sizes[0], color: color ?? product.colors[0].name, qty })}
              className="flex-1 rounded-full bg-primary text-primary-foreground py-3 text-sm font-semibold uppercase tracking-wide hover:shadow-glow transition">
              Add to basket
            </button>
            <button type="button" onClick={() => toggleWish(product.id)}
              aria-label={wished ? "Remove from wishlist" : "Add to wishlist"}
              aria-pressed={wished}
              className={`h-11 w-11 shrink-0 grid place-items-center rounded-full glass transition ${wished ? "text-primary bg-primary/10" : ""}`}>
              <Heart className={`h-4 w-4 ${wished ? "fill-primary" : ""}`} />
            </button>
            <button
              type="button"
              onClick={async () => {
                const url = typeof window !== "undefined" ? window.location.href : "";
                if (navigator.share) {
                  try { await navigator.share({ title: product.name, url }); } catch {}
                } else {
                  await navigator.clipboard.writeText(url);
                  toast.success("Link copied");
                }
              }}
              aria-label="Share"
              className="h-11 w-11 shrink-0 grid place-items-center rounded-full glass hover:scale-105 transition">
              <Share2 className="h-4 w-4" />
            </button>
          </div>

          <div className="grid grid-cols-4 gap-1 sm:gap-2 pt-1 border-t border-border/40">
            {[
              { i: Leaf, t: "Organic" },
              { i: Banknote, t: "COD" },
              { i: Truck, t: "Free ₹1499+" },
              { i: RotateCcw, t: "7-Day Return" },
            ].map((b) => (
              <div key={b.t} className="flex flex-col items-center text-center gap-1 min-w-0 py-1">
                <div className="h-9 w-9 sm:h-10 sm:w-10 rounded-full border border-primary/35 grid place-items-center bg-white/60">
                  <b.i className="h-4 w-4 text-primary" />
                </div>
                <div className="text-[9px] sm:text-[10px] font-medium text-primary leading-tight">{b.t}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="glass-strong rounded-3xl p-4 md:p-5 space-y-3">
        <details open className="glass rounded-2xl p-4 group">
          <summary className="flex items-center justify-between cursor-pointer text-sm font-semibold list-none">
            <span>Product Details</span>
            <Plus className="h-4 w-4 text-muted-foreground group-open:hidden" />
            <X className="h-4 w-4 text-muted-foreground hidden group-open:block" />
          </summary>
          <div className="mt-3 flex flex-wrap gap-2">
            {["100% Organic Cotton", "Pre-Washed", "Breathable"].map((tag) => (
              <span key={tag} className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 text-primary px-3 py-1.5 text-xs font-medium">
                <BadgeCheck className="h-3.5 w-3.5" /> {tag}
              </span>
            ))}
          </div>
        </details>

        <details className="glass rounded-2xl p-4 group">
          <summary className="flex items-center justify-between cursor-pointer text-sm font-semibold list-none">
            <span>Key Features</span>
            <Plus className="h-4 w-4 text-muted-foreground group-open:hidden" />
            <X className="h-4 w-4 text-muted-foreground hidden group-open:block" />
          </summary>
          <ul className="mt-3 space-y-1.5 text-sm text-foreground/80 list-disc pl-5">
            <li>100% Organic Cotton</li>
          </ul>
        </details>

        <details className="glass rounded-2xl p-4 group">
          <summary className="flex items-center justify-between cursor-pointer text-sm font-semibold list-none">
            <span>Wash Care</span>
            <Plus className="h-4 w-4 text-muted-foreground group-open:hidden" />
            <X className="h-4 w-4 text-muted-foreground hidden group-open:block" />
          </summary>
          <ul className="mt-3 space-y-1.5 text-sm text-foreground/80 list-disc pl-5">
            <li>Use cold or lukewarm water with a mild detergent.</li>
            <li>Don&apos;t use harsh chemicals to maintain fabric integrity.</li>
            <li>Opt for a gentle cycle or hand wash.</li>
            <li>Don&apos;t iron directly on prints.</li>
            <li>Avoid direct sunlight or high heat.</li>
          </ul>
        </details>

        <a href={`https://wa.me/919876543210?text=Hi%2C%20I%27d%20like%20to%20know%20more%20about%20${encodeURIComponent(product.name)}`}
          target="_blank" rel="noreferrer"
          className="flex items-center justify-center gap-2 text-xs text-muted-foreground hover:text-primary pt-1">
          <MessageCircle className="h-3.5 w-3.5" /> Ask on WhatsApp
        </a>
      </div>

      <section className="space-y-6">
        <div className="flex items-end justify-between mb-6">
          <div>
            <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground">You may also love</div>
            <h2 className="font-display text-3xl md:text-4xl mt-2">Pieces that pair beautifully</h2>
          </div>
          <Link to="/shop" className="story-link text-sm hidden md:inline">Browse all</Link>
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6">
          {recommendedProducts.map((p, i) => (
            <ProductCard key={p.id} product={p} index={i} />
          ))}
        </div>
      </section>

      <div className="lg:hidden fixed bottom-3 left-3 right-3 z-30">
        <div className="glass-strong rounded-full p-2 flex items-center gap-3">
          <div className="pl-3 flex-1">
            <div className="text-[11px] text-muted-foreground">{product.name}</div>
            <div className="font-medium text-sm">&#8377;{product.price.toLocaleString("en-IN")}</div>
          </div>
          <button onClick={() => addToCart(product, { size: size ?? product.sizes[0], color: color ?? product.colors[0].name, qty })}
            className="rounded-full bg-primary text-primary-foreground px-5 py-3 text-sm font-medium">
            Add to basket
          </button>
        </div>
      </div>

      {sizeChart && (
        <div className="fixed inset-0 z-70 grid place-items-center p-4 bg-foreground/30 backdrop-blur-sm" onClick={() => setSizeChart(false)}>
          <div onClick={(e) => e.stopPropagation()} className="glass-strong rounded-3xl max-w-md w-full p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="font-display text-2xl">Size guide</div>
              <button onClick={() => setSizeChart(false)} className="h-9 w-9 grid place-items-center rounded-full hover:bg-white/40"><X className="h-4 w-4" /></button>
            </div>
            <table className="w-full text-sm">
              <thead><tr className="text-xs uppercase tracking-wider text-muted-foreground"><th className="text-left py-2">Size</th><th>Weight</th><th>Height</th></tr></thead>
              <tbody className="text-foreground/80">
                {[["0-3m","2.5–5 kg","45–58 cm"],["3-6m","5–7 kg","58–67 cm"],["6-12m","7–9 kg","67–74 cm"],["12-18m","9–11 kg","74–82 cm"],["18-24m","11–13 kg","82–90 cm"]].map((r) => (
                  <tr key={r[0]} className="border-t border-border/40"><td className="py-2 font-medium">{r[0]}</td><td className="text-center">{r[1]}</td><td className="text-center">{r[2]}</td></tr>
                ))}
              </tbody>
            </table>
            <p className="text-xs text-muted-foreground mt-4">Most pieces have generous seams for grow-into-it comfort.</p>
          </div>
        </div>
      )}
    </div>
  );
}
