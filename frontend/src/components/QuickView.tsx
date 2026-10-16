import { LazyImage } from "@/components/LazyImage";
import { motion, AnimatePresence } from "framer-motion";
import { X, Star } from "lucide-react";
import { Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { useShop } from "@/store/shop";
import {
  colorSelectionForSize,
  defaultSellableSelection,
  findVariant,
  isSimpleProductSellable,
  isVariantSellable,
  isVariantsProduct,
} from "@/lib/product-variants";
import { StockAvailability, stockAvailabilityState } from "@/components/StockAvailability";
import { VariantColorButtons, VariantSizeButtons, variantColorsForDisplay } from "@/components/VariantPickers";
import { galleryForSelectedColor } from "@/lib/product-gallery";
import { STOREFRONT_CARD_SIZES, prefetchStorefrontImage, storefrontImageSrcSet, storefrontImageUrl } from "@/lib/storefront-image";
import { resolveStorefrontColorLabel } from "@/lib/expected-prints";
import { sizeNoteForSelection } from "@/lib/product-size-notes";
import { SizeFeatureNote } from "@/components/SizeFeatureNote";
import { productDetailsKicker } from "@/lib/product-details-kicker";

export function QuickView() {
  const { quickView, setQuickView, addToCart } = useShop();
  const [size, setSize] = useState<string | null>(null);
  const [color, setColor] = useState<string | null>(null);

  useEffect(() => {
    if (!quickView) return;
    const next = defaultSellableSelection(quickView);
    setSize(next.size);
    setColor(next.color);
  }, [quickView]);

  useEffect(() => {
    if (!quickView || !size) return;
    setColor((prev) => colorSelectionForSize(quickView, size, prev));
  }, [quickView, size]);

  const selectedVariant = useMemo(
    () => (quickView ? findVariant(quickView, { size, color }) : undefined),
    [quickView, size, color],
  );
  const displayPrice = selectedVariant?.price ?? quickView?.price ?? 0;
  const displayCompareAt = selectedVariant?.compareAt ?? quickView?.compareAt;
  const canAdd = !quickView
    ? false
    : isVariantsProduct(quickView)
      ? isVariantSellable(selectedVariant)
      : isSimpleProductSellable(quickView);
  const stockState = useMemo(
    () => (quickView ? stockAvailabilityState(quickView, selectedVariant, { size, color }) : null),
    [quickView, selectedVariant, size, color],
  );
  const availableColors = useMemo(() => {
    if (!quickView) return [];
    return variantColorsForDisplay(quickView, size);
  }, [quickView, size]);

  const colorGallery = useMemo(
    () => (quickView ? galleryForSelectedColor(quickView, color, size) : { image: "", gallery: [] as string[] }),
    [quickView, color, size],
  );
  const sizeNote = useMemo(
    () => (quickView ? sizeNoteForSelection(quickView.sizeNotes, size) : null),
    [quickView, size],
  );

  return (
    <AnimatePresence>
      {quickView && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => setQuickView(null)} className="fixed inset-0 z-[70] bg-foreground/30 backdrop-blur-md" />
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 20 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.95, y: 20 }}
            transition={{ duration: 0.4, ease: [0.2, 0.8, 0.2, 1] }}
            className="fixed inset-3 sm:inset-4 md:inset-auto md:top-1/2 md:left-1/2 md:-translate-x-1/2 md:-translate-y-1/2 z-[71] md:w-[820px] max-h-[min(90vh,100dvh)] overflow-hidden">
            <div className="glass-strong rounded-[1.75rem] sm:rounded-[2rem] overflow-y-auto md:overflow-hidden grid md:grid-cols-2 h-full max-h-[min(90vh,100dvh)]">
              <div className="relative aspect-[4/3] sm:aspect-square md:aspect-auto min-h-0 overflow-hidden bg-secondary/40 shrink-0">
                <AnimatePresence mode="wait">
                  <motion.div
                    key={colorGallery.image || quickView.image}
                    initial={{ opacity: 0, scale: 0.98 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.98 }}
                    transition={{ type: "spring", stiffness: 400, damping: 32 }}
                    className="absolute inset-0"
                  >
                    <LazyImage
                      src={storefrontImageUrl(colorGallery.image || quickView.image, "display")}
                      fallbackSrc={colorGallery.image || quickView.image}
                      srcSet={storefrontImageSrcSet(colorGallery.image || quickView.image) || undefined}
                      sizes={storefrontImageSrcSet(colorGallery.image || quickView.image) ? STOREFRONT_CARD_SIZES : undefined}
                      alt={quickView.name}
                      className="h-full w-full object-cover object-center"
                      decoding="async"
                    />
                    <SizeFeatureNote note={sizeNote} variant="overlay" />
                  </motion.div>
                </AnimatePresence>
              </div>
              <div className="p-5 sm:p-6 md:p-8 flex flex-col overflow-y-visible md:overflow-y-auto relative min-h-0">
                <button onClick={() => setQuickView(null)} className="absolute top-4 right-4 h-9 w-9 grid place-items-center rounded-full glass hover:scale-105 transition">
                  <X className="h-4 w-4" />
                </button>
                <div className="text-xs uppercase tracking-[0.22em] text-muted-foreground">{productDetailsKicker(quickView)}</div>
                <h3 className="font-display text-3xl mt-1">{quickView.name}</h3>
                <div className="flex items-center gap-2 text-sm mt-2">
                  <Star className="h-3.5 w-3.5 fill-primary text-primary" />
                  <span>{quickView.rating}</span>
                  <span className="text-muted-foreground">({quickView.reviewCount} reviews)</span>
                </div>
                <p className="text-sm text-muted-foreground mt-4 leading-relaxed">{quickView.description}</p>
                <div className="mt-5 flex items-baseline gap-3">
                  <span className="text-2xl font-medium">&#8377;{displayPrice.toLocaleString("en-IN")}</span>
                  {displayCompareAt && displayCompareAt > displayPrice && (
                    <span className="text-sm line-through text-muted-foreground">&#8377;{displayCompareAt.toLocaleString("en-IN")}</span>
                  )}
                </div>
                {stockState && (isVariantsProduct(quickView) || stockState.kind !== "in_stock") && (
                  <StockAvailability state={stockState} className="mt-3" />
                )}
                <SizeFeatureNote note={sizeNote} className="mt-3" />
                <div className="mt-5">
                  <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2">Size</div>
                  <VariantSizeButtons
                    product={quickView}
                    size={size}
                    onSizeChange={setSize}
                    gapClass="gap-2"
                  />
                </div>
                <div className="mt-4">
                  <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2">Print · {resolveStorefrontColorLabel(quickView.colors, color, quickView.sku)}</div>
                  <VariantColorButtons
                    product={quickView}
                    size={size}
                    color={color}
                    onColorChange={setColor}
                    onColorIntent={(name) => {
                      const next = galleryForSelectedColor(quickView, name, size);
                      prefetchStorefrontImage(next.image, "display");
                    }}
                    colors={availableColors}
                    gapClass="gap-2.5"
                    swatchClass="h-11 w-11"
                  />
                </div>
                <div className="mt-auto pt-6 flex gap-2">
                  <button
                    disabled={!canAdd}
                    onClick={() => {
                      if (!canAdd) return;
                      addToCart(quickView, { size: size ?? undefined, color: color ?? undefined, variantSku: selectedVariant?.sku });
                      setQuickView(null);
                    }}
                    className="flex-1 rounded-full bg-primary text-primary-foreground py-3 font-medium hover:shadow-glow transition disabled:opacity-50"
                  >
                    {canAdd ? "Add to basket" : "Out of stock"}
                  </button>
                  <Link to="/product/$slug" params={{ slug: quickView.slug }} onClick={() => setQuickView(null)}
                    className="rounded-full glass px-5 py-3 text-sm font-medium hover:bg-white/60 transition">
                    Details
                  </Link>
                </div>
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
