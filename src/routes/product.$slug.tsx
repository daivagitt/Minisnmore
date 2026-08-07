import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import type { Product } from "@/data/products";
import { useProduct, useProducts } from "@/hooks/useProducts";
import { useShop } from "@/store/shop";
import { Heart, Truck, Leaf, ChevronRight, MessageCircle, Ruler, Plus, Minus, Share2, X, BadgeCheck, Banknote, RotateCcw } from "lucide-react";
import { toast } from "sonner";
import { ProductCard } from "@/components/ProductCard";
import { ProductGallery } from "@/components/ProductGallery";
import { checkSizeFit, clampQuantity } from "@/lib/product-fit";
import { galleryForSelectedColor } from "@/lib/product-gallery";
import { lookupPincode, normalizePincode } from "@/lib/pincode";
import {
  colorNameInList,
  defaultSellableSelection,
  findVariant,
  isVariantSellable,
  isVariantsProduct,
  sellableColorsForSize,
  sizeHasSellableVariants,
} from "@/lib/product-variants";
import { StockAvailability, stockAvailabilityState } from "@/components/StockAvailability";
import { VariantColorButtons, VariantSizeButtons, variantColorsForDisplay } from "@/components/VariantPickers";
import { sortSizesAsc } from "@/lib/product-ages";

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
  const defaults = product ? defaultSellableSelection(product) : { size: null as string | null, color: null as string | null };
  const [size, setSize] = useState<string | null>(() => defaults.size);
  const [color, setColor] = useState<string | null>(() => defaults.color);
  const [heightCm, setHeightCm] = useState<string>("");
  const [chestCm, setChestCm] = useState<string>("");
  const [sizeChart, setSizeChart] = useState(false);
  const [qty, setQty] = useState(1);
  const [pin, setPin] = useState("");
  const [pinResult, setPinResult] = useState<{ city: string; state: string; eta: string } | null>(null);
  const [pinLoading, setPinLoading] = useState(false);
  const [pinError, setPinError] = useState<string | null>(null);
  const wished = product ? wishlist.includes(product.id) : false;

  useEffect(() => {
    if (!product) return;
    const next = defaultSellableSelection(product);
    setSize((prev) => {
      if (
        prev &&
        product.sizes.includes(prev) &&
        (!isVariantsProduct(product) || sizeHasSellableVariants(product, prev))
      ) {
        return prev;
      }
      return next.size;
    });
  }, [product]);

  useEffect(() => {
    if (!product || !size) return;
    const sellable = sellableColorsForSize(product, size);
    setColor((prev) => (prev && colorNameInList(prev, sellable) ? prev : sellable[0] ?? null));
  }, [product, size]);

  const selectedVariant = useMemo(
    () => (product ? findVariant(product, { size, color }) : undefined),
    [product, size, color],
  );
  const displayPrice = selectedVariant?.price ?? product?.price ?? 0;
  const displayCompareAt = selectedVariant?.compareAt ?? product?.compareAt;
  const canAdd = !product
    ? false
    : isVariantsProduct(product)
      ? isVariantSellable(selectedVariant)
      : true;
  const stockState = useMemo(
    () => (product ? stockAvailabilityState(product, selectedVariant, { size, color }) : null),
    [product, selectedVariant, size, color],
  );
  const stockIsSoldOut = stockState?.kind === "out_of_stock";
  const availableColors = useMemo(() => {
    if (!product) return [];
    return variantColorsForDisplay(product, size);
  }, [product, size]);

  const colorGallery = useMemo(
    () => (product ? galleryForSelectedColor(product, color) : { image: "", gallery: [] as string[] }),
    [product, color],
  );

  const sizeFit = useMemo(() => {
    if (!product || !heightCm.trim()) return null;
    const result = checkSizeFit({ heightCm, chestCm, sizes: sortSizesAsc(product.sizes) });
    return result.ok ? { size: result.size } : { error: result.error };
  }, [heightCm, chestCm, product]);

  const handleAdd = () => {
    if (!product || !canAdd) {
      toast.error("Selected size/color is unavailable");
      return;
    }
    addToCart(product, {
      size: size ?? product.sizes[0],
      color: color ?? product.colors[0]?.name,
      qty,
      variantSku: selectedVariant?.sku,
    });
  };

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

  const trustBadges = [
    { i: Leaf, t: "Organic" },
    { i: Banknote, t: "COD" },
    { i: Truck, t: "Free ₹1499+" },
    { i: RotateCcw, t: "30 days" },
  ] as const;

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
    <div className="max-w-7xl mx-auto w-full px-3 sm:px-4 md:px-6 space-y-6 sm:space-y-8 md:space-y-10 pb-28 sm:pb-24 lg:pb-12 overflow-x-hidden">
      <div className="text-xs text-muted-foreground flex items-center gap-1.5 flex-wrap">
        <Link to="/" className="hover:text-foreground">Home</Link><ChevronRight className="h-3 w-3" />
        <Link to="/shop" className="hover:text-foreground">Shop</Link><ChevronRight className="h-3 w-3" />
        <span className="truncate">{product.name}</span>
      </div>

      <div className="grid lg:grid-cols-2 gap-x-6 lg:gap-x-8 gap-y-4 lg:gap-y-6 lg:items-start">
        <ProductGallery
          key={color ?? "default"}
          name={product.name}
          image={colorGallery.image}
          gallery={colorGallery.gallery}
          className="lg:sticky lg:top-24"
        />

        <div className="flex flex-col gap-3 min-w-0 w-full">
        {/* Purchase card — compact, fully visible like reference PDP */}
        <div className="glass-strong rounded-2xl sm:rounded-3xl p-3.5 sm:p-4 md:p-5 space-y-3.5 sm:space-y-4 w-full min-w-0">
          <div className="space-y-1.5">
            <div className="text-[10px] uppercase tracking-[0.24em] text-primary">{product.fabric} · {product.category}</div>
            <h1 className="font-display text-lg sm:text-xl md:text-2xl lg:text-[1.65rem] leading-tight text-balance">{product.name}</h1>
            <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1 pt-0.5">
              <span className="font-display text-xl sm:text-2xl">&#8377;{displayPrice.toLocaleString("en-IN")}</span>
              {displayCompareAt && displayCompareAt > displayPrice && (
                <span className="line-through text-muted-foreground text-sm">&#8377;{displayCompareAt.toLocaleString("en-IN")}</span>
              )}
              {displayCompareAt && displayCompareAt > displayPrice && (
                <span className="rounded-md bg-primary/15 px-2 py-0.5 text-[11px] font-semibold text-primary uppercase tracking-wide">
                  Save &#8377;{(displayCompareAt - displayPrice).toLocaleString("en-IN")}
                </span>
              )}
            </div>
            {isVariantsProduct(product) && stockState && (
              <StockAvailability state={stockState} className="mt-1" />
            )}
          </div>

          <div className="space-y-3">
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <div className="text-[10px] uppercase tracking-wider text-muted-foreground">Size</div>
                <button type="button" onClick={() => setSizeChart(true)} className="text-[11px] text-primary underline-offset-2 hover:underline">Size chart</button>
              </div>
              <VariantSizeButtons product={product} size={size} onSizeChange={setSize} />
            </div>
            <div>
              <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1.5">Print · {color}</div>
              <VariantColorButtons
                product={product}
                size={size}
                color={color}
                onColorChange={setColor}
                colors={availableColors}
              />
            </div>
            {isVariantsProduct(product) && size && color && stockIsSoldOut && (
              <p className="text-xs text-destructive bg-destructive/10 rounded-lg px-3 py-2 leading-snug">
                This size and colour is sold out. Choose another combination or check back later.
              </p>
            )}
          </div>

          <div className="glass rounded-xl p-3 space-y-2">
            <div className="flex items-center gap-2 text-xs font-medium">
              <Ruler className="h-3.5 w-3.5 text-primary shrink-0" />
              Size calculator
            </div>
            {sizeFit && "size" in sizeFit ? (
              <div className="flex items-center justify-between gap-2 text-xs min-w-0">
                <span className="text-muted-foreground truncate">
                  Best fit: <span className="text-primary font-medium">{sizeFit.size}</span>
                </span>
                <button
                  type="button"
                  onClick={() => setSize(sizeFit.size)}
                  className="text-primary underline-offset-2 hover:underline shrink-0"
                >
                  Use size
                </button>
              </div>
            ) : sizeFit && "error" in sizeFit ? (
              <p className="text-destructive text-xs leading-snug line-clamp-2">{sizeFit.error}</p>
            ) : null}
            <div className="grid grid-cols-1 min-[400px]:grid-cols-2 gap-2">
              <input
                type="number"
                inputMode="decimal"
                min={30}
                max={120}
                value={heightCm}
                onChange={(e) => setHeightCm(e.target.value)}
                placeholder="Height (cm)"
                aria-label="Height in centimetres"
                className="h-10 glass rounded-full px-3 sm:px-4 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40 min-w-0 w-full"
              />
              <input
                type="number"
                inputMode="decimal"
                min={20}
                max={70}
                value={chestCm}
                onChange={(e) => setChestCm(e.target.value)}
                placeholder="Chest (cm)"
                aria-label="Chest in centimetres (optional)"
                className="h-10 glass rounded-full px-3 sm:px-4 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40 min-w-0 w-full"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-[auto_1fr] gap-3 sm:items-end">
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

          <div className="hidden sm:flex gap-2">
            <button
              type="button"
              disabled={!canAdd}
              onClick={handleAdd}
              className="flex-1 rounded-full bg-primary text-primary-foreground py-3 text-sm font-semibold uppercase tracking-wide hover:shadow-glow transition disabled:opacity-50"
            >
              {canAdd ? "Add to basket" : "Out of stock"}
            </button>
            <button
              type="button"
              onClick={() => toggleWish(product.id)}
              aria-label={wished ? "Remove from wishlist" : "Add to wishlist"}
              aria-pressed={wished}
              className={`h-11 w-11 shrink-0 grid place-items-center rounded-full glass transition ${wished ? "text-primary bg-primary/10" : ""}`}
            >
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
              className="h-11 w-11 shrink-0 grid place-items-center rounded-full glass hover:scale-105 transition"
            >
              <Share2 className="h-4 w-4" />
            </button>
          </div>
        </div>

        <div className="glass rounded-2xl px-3 sm:px-4 py-2.5 sm:py-3 w-full min-w-0 shrink-0">
          <div className="grid grid-cols-4 gap-2 sm:gap-3 md:gap-4 w-full">
            {trustBadges.map((b) => (
              <div key={b.t} className="flex flex-col items-center text-center gap-1 min-w-0 px-0.5">
                <div className="h-8 w-8 sm:h-10 sm:w-10 rounded-lg border border-primary/35 grid place-items-center bg-white/60 shrink-0">
                  <b.i className="h-3 w-3 text-primary" />
                </div>
                <div className="text-[7px] min-[360px]:text-[8px] sm:text-[9px] font-medium text-primary leading-tight">{b.t}</div>
              </div>
            ))}
          </div>
        </div>
        </div>
      </div>

      <div className="glass-strong rounded-2xl sm:rounded-3xl p-3.5 sm:p-4 md:p-5 space-y-3">
        <details open className="glass rounded-2xl p-4 group">
          <summary className="flex items-center justify-between cursor-pointer text-sm font-semibold list-none">
            <span>Product Details</span>
            <Plus className="h-4 w-4 text-muted-foreground group-open:hidden" />
            <X className="h-4 w-4 text-muted-foreground hidden group-open:block" />
          </summary>
          {product.description ? (
            <p className="mt-3 text-sm text-foreground/80 leading-relaxed whitespace-pre-wrap">
              {product.description}
            </p>
          ) : null}
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
        <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3 mb-4 sm:mb-6">
          <div className="min-w-0">
            <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground">You may also love</div>
            <h2 className="font-display text-2xl sm:text-3xl md:text-4xl mt-1.5 sm:mt-2">Pieces that pair beautifully</h2>
          </div>
          <Link to="/shop" className="story-link text-sm shrink-0">Browse all</Link>
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 md:gap-5">
          {recommendedProducts.map((p, i) => (
            <ProductCard key={p.id} product={p} index={i} />
          ))}
        </div>
      </section>

      <div className="lg:hidden fixed inset-x-0 bottom-0 z-30 p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
        <div className="glass-strong rounded-full p-2 flex items-center gap-2 sm:gap-3 max-w-7xl mx-auto">
          <div className="pl-2 sm:pl-3 flex-1 min-w-0">
            <div className="text-[11px] text-muted-foreground truncate">{product.name}</div>
            <div className="font-medium text-sm">&#8377;{displayPrice.toLocaleString("en-IN")}</div>
          </div>
          <button
            type="button"
            disabled={!canAdd}
            onClick={handleAdd}
            className="shrink-0 rounded-full bg-primary text-primary-foreground px-4 sm:px-5 py-2.5 sm:py-3 text-xs sm:text-sm font-medium disabled:opacity-50"
          >
            {canAdd ? "Add to basket" : "Out of stock"}
          </button>
        </div>
      </div>

      {sizeChart && (
        <div className="fixed inset-0 z-70 grid place-items-end sm:place-items-center p-0 sm:p-4 bg-foreground/30 backdrop-blur-sm" onClick={() => setSizeChart(false)}>
          <div
            onClick={(e) => e.stopPropagation()}
            className="glass-strong rounded-t-3xl sm:rounded-3xl max-w-md w-full p-5 sm:p-6 max-h-[85vh] overflow-y-auto"
          >
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
