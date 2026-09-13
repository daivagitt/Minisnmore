import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { LazyImage } from "@/components/LazyImage";
import { cn } from "@/lib/utils";
import { galleryHeroFitSize, PRODUCT_GALLERY_HERO_MAX_PX } from "@/lib/product-gallery";

/** Warm cream frame (site --cream), not cool catalogue white. */
const galleryCreamFrame =
  "shadow-soft border border-cream bg-cream";
const galleryHeroRound = "rounded-[1.75rem] sm:rounded-[2rem] lg:rounded-[2.5rem]";
const galleryImageRound = "rounded-[1.35rem] sm:rounded-[1.6rem] lg:rounded-[2rem]";
/** Small thumbs need a tighter radius than the hero or they render as stacked circles. */
const galleryThumbRound = "rounded-2xl";
const galleryThumbImageRound = "rounded-[0.85rem] sm:rounded-xl";

function uniqueImages(...sources: (string | undefined)[]) {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const src of sources) {
    const s = src?.trim();
    if (!s || seen.has(s)) continue;
    seen.add(s);
    out.push(s);
  }
  return out;
}

type NaturalSize = { width: number; height: number };

function useImageNaturalSize() {
  const sizeRef = useRef<Map<string, NaturalSize>>(new Map());
  const [, bump] = useState(0);

  const rememberSize = useCallback((src: string, img: HTMLImageElement) => {
    const width = img.naturalWidth;
    const height = img.naturalHeight;
    if (!width || !height) return;

    const prev = sizeRef.current.get(src);
    if (prev && prev.width === width && prev.height === height) return;

    sizeRef.current.set(src, { width, height });
    bump((n) => n + 1);
  }, []);

  const sizeFor = useCallback((src: string) => sizeRef.current.get(src), []);

  return { sizeFor, rememberSize };
}

type HeroImageProps = {
  src: string;
  alt: string;
  className?: string;
  style?: CSSProperties;
  priority?: "high" | "auto" | "low";
  onSize: (src: string, img: HTMLImageElement) => void;
};

function HeroGalleryImage({ src, alt, className, style, priority, onSize }: HeroImageProps) {
  if (!src) return null;

  return (
    <LazyImage
      src={src}
      alt={alt}
      decoding="async"
      priority={priority ?? "auto"}
      onLoad={(e) => onSize(src, e.currentTarget)}
      className={cn("object-contain object-center select-none", className)}
      style={style}
      draggable={false}
    />
  );
}

type ThumbProps = {
  src: string;
  index: number;
  total: number;
  active: boolean;
  onSelect: () => void;
};

/** Each thumb is a fixed square so flex layout cannot collapse them into a stacked pile. */
function GalleryThumb({ src, index, total, active, onSelect }: ThumbProps) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={`View image ${index + 1} of ${total}`}
      aria-current={active ? "true" : undefined}
      className={cn(
        galleryCreamFrame,
        galleryThumbRound,
        "relative isolate shrink-0 snap-start overflow-hidden p-0.5 sm:p-1",
        "size-[3.5rem] sm:size-16 lg:size-[4.75rem]",
        active ? "ring-2 ring-primary ring-offset-1 opacity-100" : "opacity-70 hover:opacity-100",
      )}
    >
      <span className={cn("block h-full w-full overflow-hidden bg-cream", galleryThumbImageRound)}>
        <LazyImage
          src={src}
          alt=""
          decoding="async"
          priority="low"
          className="h-full w-full object-contain object-center select-none"
          draggable={false}
        />
      </span>
    </button>
  );
}

type Props = {
  name: string;
  image: string;
  gallery?: string[];
  className?: string;
};

export function ProductGallery({ name, image, gallery = [], className }: Props) {
  const rawImages = useMemo(() => uniqueImages(image, ...gallery), [image, gallery]);
  const rawImagesKey = useMemo(() => rawImages.join("\0"), [rawImages]);
  const [active, setActive] = useState(0);
  const { sizeFor, rememberSize } = useImageNaturalSize();

  useEffect(() => {
    setActive(0);
  }, [rawImagesKey]);

  const safeActive = rawImages.length ? Math.min(active, rawImages.length - 1) : 0;
  const current = rawImages[safeActive] ?? rawImages[0] ?? "";
  const natural = sizeFor(current);
  const fit = galleryHeroFitSize(natural?.width, natural?.height);

  useEffect(() => {
    if (rawImages.length <= 1 || !current || !natural) return;

    const next = rawImages[(safeActive + 1) % rawImages.length];
    const prev = rawImages[(safeActive - 1 + rawImages.length) % rawImages.length];
    const neighbors = [...new Set([next, prev].filter((src) => src && src !== current))];
    if (!neighbors.length) return;

    const loaders: HTMLImageElement[] = [];
    for (const src of neighbors) {
      const img = new Image();
      img.decoding = "async";
      img.src = src;
      loaders.push(img);
    }

    return () => {
      for (const img of loaders) {
        img.src = "";
      }
    };
  }, [rawImages, safeActive, current, natural]);

  const go = (delta: number) => {
    if (rawImages.length <= 1) return;
    setActive((i) => (i + delta + rawImages.length) % rawImages.length);
  };

  return (
    <div
      className={cn(
        "flex flex-col lg:flex-row gap-3 sm:gap-4 w-full min-w-0 lg:items-start",
        className,
      )}
    >
      {rawImages.length > 1 ? (
        <div
          className="order-2 lg:order-1 shrink-0 lg:self-start flex flex-row lg:flex-col gap-2 sm:gap-2.5 overflow-x-auto lg:overflow-y-auto snap-x snap-mandatory lg:snap-none [scrollbar-width:thin]"
          style={{
            maxHeight: fit
              ? `min(${fit.height}px, ${PRODUCT_GALLERY_HERO_MAX_PX}px)`
              : `min(70vh, ${PRODUCT_GALLERY_HERO_MAX_PX}px)`,
          }}
          aria-label="Product image thumbnails"
        >
          {rawImages.map((src, i) => (
            <GalleryThumb
              key={src}
              src={src}
              index={i}
              total={rawImages.length}
              active={safeActive === i}
              onSelect={() => setActive(i)}
            />
          ))}
        </div>
      ) : null}

      <div className="order-1 lg:order-2 flex-1 min-w-0 flex justify-center lg:justify-start">
        <div
          className={cn(
            galleryCreamFrame,
            galleryHeroRound,
            "relative max-w-full p-1.5 sm:p-2",
            fit ? "w-full" : "w-fit",
          )}
          style={fit ? { maxWidth: `${fit.width}px` } : undefined}
        >
          <div className={cn("group relative w-full overflow-hidden bg-cream", galleryImageRound)}>
            {fit ? (
              <div
                className="w-full"
                style={{ paddingBottom: `${(fit.height / fit.width) * 100}%` }}
                aria-hidden
              />
            ) : null}

            <AnimatePresence mode="sync" initial={false}>
              <motion.div
                key={current}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
                className={fit ? "absolute inset-0 overflow-hidden" : "relative overflow-hidden"}
              >
                {current ? (
                  <HeroGalleryImage
                    src={current}
                    alt={name}
                    priority="high"
                    onSize={rememberSize}
                    className={
                      fit
                        ? "absolute inset-0 h-full w-full"
                        : "relative block h-auto w-auto max-w-full"
                    }
                    style={fit ? undefined : { maxHeight: PRODUCT_GALLERY_HERO_MAX_PX }}
                  />
                ) : null}
              </motion.div>
            </AnimatePresence>

            {rawImages.length > 1 ? (
              <>
                <button
                  type="button"
                  onClick={() => go(-1)}
                  className="absolute left-2 sm:left-3 top-1/2 z-10 -translate-y-1/2 h-8 w-8 sm:h-9 sm:w-9 grid place-items-center rounded-full bg-white/80 backdrop-blur-sm hover:bg-white shadow-sm border border-white/70 opacity-0 pointer-events-none transition-opacity group-hover:opacity-100 group-hover:pointer-events-auto focus-visible:opacity-100 focus-visible:pointer-events-auto"
                  aria-label="Previous image"
                >
                  <ChevronLeft className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={() => go(1)}
                  className="absolute right-2 sm:right-3 top-1/2 z-10 -translate-y-1/2 h-8 w-8 sm:h-9 sm:w-9 grid place-items-center rounded-full bg-white/80 backdrop-blur-sm hover:bg-white shadow-sm border border-white/70 opacity-0 pointer-events-none transition-opacity group-hover:opacity-100 group-hover:pointer-events-auto focus-visible:opacity-100 focus-visible:pointer-events-auto"
                  aria-label="Next image"
                >
                  <ChevronRight className="h-4 w-4" />
                </button>
              </>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
