import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { LazyImage } from "@/components/LazyImage";
import { cn } from "@/lib/utils";
import {
  galleryHeroFitSize,
  PRODUCT_GALLERY_HERO_MAX_PX,
  type GalleryHeroFitSize,
} from "@/lib/product-gallery";
import {
  STOREFRONT_HERO_SIZES,
  STOREFRONT_THUMB_SIZES,
  prefetchStorefrontImage,
  prefetchStorefrontImages,
  storefrontImageSrcSet,
  storefrontImageUrl,
} from "@/lib/storefront-image";
import { SizeFeatureNote } from "@/components/SizeFeatureNote";

/** Warm cream frame (site --cream), not cool catalogue white. */
const galleryCreamFrame =
  "shadow-soft border border-cream bg-cream";
const galleryHeroRound = "rounded-[1.75rem] sm:rounded-[2rem] lg:rounded-[2.5rem]";
const galleryImageRound = "rounded-[1.35rem] sm:rounded-[1.6rem] lg:rounded-[2rem]";
/** Small thumbs need a tighter radius than the hero or they render as stacked circles. */
const galleryThumbRound = "rounded-2xl";
const galleryThumbImageRound = "rounded-[0.85rem] sm:rounded-xl";
/** In-viewport side-rail thumbs; the rest stay lazy. */
const GALLERY_EAGER_THUMB_COUNT = 6;

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

function neighborGalleryUrls(images: string[], activeIndex: number, current: string): string[] {
  if (images.length <= 1) return [];
  const next = images[(activeIndex + 1) % images.length];
  const prev = images[(activeIndex - 1 + images.length) % images.length];
  return [...new Set([next, prev].filter((src) => src && src !== current))];
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
  originalSrc: string;
  alt: string;
  className?: string;
  style?: CSSProperties;
  priority?: "high" | "eager" | "auto" | "low";
  onSize: (src: string, img: HTMLImageElement) => void;
};

function HeroGalleryImage({ originalSrc, alt, className, style, priority, onSize }: HeroImageProps) {
  const imgRef = useRef<HTMLImageElement>(null);
  const displaySrc = storefrontImageUrl(originalSrc, "display");
  const srcSet = storefrontImageSrcSet(originalSrc);

  const reportSize = useCallback(() => {
    const img = imgRef.current;
    if (!img) return;
    onSize(originalSrc, img);
  }, [originalSrc, onSize]);

  useLayoutEffect(() => {
    reportSize();
  }, [reportSize]);

  if (!originalSrc) return null;

  return (
    <LazyImage
      ref={imgRef}
      src={displaySrc}
      fallbackSrc={originalSrc}
      srcSet={srcSet || undefined}
      sizes={srcSet ? STOREFRONT_HERO_SIZES : undefined}
      alt={alt}
      decoding="auto"
      priority={priority ?? "auto"}
      onLoad={reportSize}
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
  const eager = index < GALLERY_EAGER_THUMB_COUNT;
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
          src={storefrontImageUrl(src, "thumb")}
          fallbackSrc={src}
          alt=""
          sizes={STOREFRONT_THUMB_SIZES}
          decoding="async"
          priority={eager ? "eager" : "low"}
          fetchPriority="low"
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
  sizeNote?: string | null;
  /** Other print heroes to warm during idle time (not the current gallery). */
  relatedImages?: string[];
  className?: string;
};

export function ProductGallery({
  name,
  image,
  gallery = [],
  sizeNote,
  relatedImages = [],
  className,
}: Props) {
  const rawImages = useMemo(() => uniqueImages(image, ...gallery), [image, gallery]);
  const rawImagesKey = useMemo(() => rawImages.join("\0"), [rawImages]);
  const relatedKey = useMemo(() => relatedImages.join("\0"), [relatedImages]);
  const [active, setActive] = useState(0);
  const { sizeFor, rememberSize } = useImageNaturalSize();
  const lastFrameRef = useRef<GalleryHeroFitSize | null>(null);

  useLayoutEffect(() => {
    setActive(0);
  }, [rawImagesKey]);

  const safeActive = rawImages.length ? Math.min(active, rawImages.length - 1) : 0;
  const current = rawImages[safeActive] ?? rawImages[0] ?? "";
  const natural = sizeFor(current);
  const fit = galleryHeroFitSize(natural?.width, natural?.height);
  if (fit) lastFrameRef.current = fit;
  const frame = fit ?? lastFrameRef.current;

  useEffect(() => {
    if (!current || !natural) return;

    const neighbors = neighborGalleryUrls(rawImages, safeActive, current);
    for (const src of neighbors) prefetchStorefrontImage(src, "display");

    const neighborSet = new Set(neighbors);
    const restOfGallery = rawImages.filter((src) => src !== current && !neighborSet.has(src));
    const related = (relatedKey ? relatedKey.split("\0") : []).filter((src) => src !== current);
    return prefetchStorefrontImages([...restOfGallery, ...related], "display", { idle: true });
  }, [rawImages, safeActive, current, natural, relatedKey]);

  const go = (delta: number) => {
    if (rawImages.length <= 1) return;
    setActive((i) => (i + delta + rawImages.length) % rawImages.length);
  };

  const thumbStrip =
    rawImages.length > 1 ? (
      <div
        className="order-2 lg:order-1 shrink-0 lg:self-start flex flex-row lg:flex-col gap-2 sm:gap-2.5 overflow-x-auto lg:overflow-y-auto snap-x snap-mandatory lg:snap-none [scrollbar-width:thin]"
        style={{
          maxHeight: frame
            ? `min(${frame.height}px, ${PRODUCT_GALLERY_HERO_MAX_PX}px)`
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
    ) : null;

  return (
    <div
      className={cn(
        "flex flex-col lg:flex-row gap-3 sm:gap-4 w-full min-w-0 lg:items-start",
        className,
      )}
    >
      <div className="order-1 lg:order-2 flex-1 min-w-0 flex justify-center lg:justify-start">
        <div
          className={cn(
            galleryCreamFrame,
            galleryHeroRound,
            "relative max-w-full p-1.5 sm:p-2",
            frame ? "w-full" : "w-fit min-w-[12rem] sm:min-w-[16rem]",
          )}
          style={frame ? { maxWidth: `${frame.width}px` } : undefined}
        >
          <div
            className={cn(
              "group relative w-full overflow-hidden bg-cream",
              galleryImageRound,
              !frame && "min-h-[12rem] sm:min-h-[16rem]",
            )}
          >
            {frame ? (
              <div
                className="w-full"
                style={{ paddingBottom: `${(frame.height / frame.width) * 100}%` }}
                aria-hidden
              />
            ) : null}

            <div className={frame ? "absolute inset-0 overflow-hidden" : "relative overflow-hidden min-h-[12rem] sm:min-h-[16rem]"}>
              {current ? (
                <>
                  <LazyImage
                    src={storefrontImageUrl(current, "thumb")}
                    fallbackSrc={current}
                    alt=""
                    aria-hidden
                    sizes={STOREFRONT_THUMB_SIZES}
                    decoding="async"
                    priority="eager"
                    fetchPriority="low"
                    className="absolute inset-0 h-full w-full object-contain object-center blur-md scale-105 pointer-events-none select-none"
                    draggable={false}
                  />
                  <HeroGalleryImage
                    key={current}
                    originalSrc={current}
                    alt={name}
                    priority="high"
                    onSize={rememberSize}
                    className={
                      frame
                        ? "absolute inset-0 h-full w-full"
                        : "relative z-[1] block h-auto w-auto max-w-full"
                    }
                    style={frame ? undefined : { maxHeight: PRODUCT_GALLERY_HERO_MAX_PX }}
                  />
                </>
              ) : null}
            </div>

            {rawImages.length > 1 ? (
              <>
                <button
                  type="button"
                  onClick={() => go(-1)}
                  className="absolute left-2 sm:left-3 top-1/2 z-10 -translate-y-1/2 h-9 w-9 grid place-items-center rounded-full bg-white/90 backdrop-blur-sm hover:bg-white shadow-sm border border-white/70 opacity-100 pointer-events-auto transition-opacity lg:opacity-0 lg:pointer-events-none lg:group-hover:opacity-100 lg:group-hover:pointer-events-auto focus-visible:opacity-100 focus-visible:pointer-events-auto"
                  aria-label="Previous image"
                >
                  <ChevronLeft className="h-5 w-5" />
                </button>
                <button
                  type="button"
                  onClick={() => go(1)}
                  className="absolute right-2 sm:right-3 top-1/2 z-10 -translate-y-1/2 h-9 w-9 grid place-items-center rounded-full bg-white/90 backdrop-blur-sm hover:bg-white shadow-sm border border-white/70 opacity-100 pointer-events-auto transition-opacity lg:opacity-0 lg:pointer-events-none lg:group-hover:opacity-100 lg:group-hover:pointer-events-auto focus-visible:opacity-100 focus-visible:pointer-events-auto"
                  aria-label="Next image"
                >
                  <ChevronRight className="h-5 w-5" />
                </button>
              </>
            ) : null}
            <SizeFeatureNote note={sizeNote} variant="overlay" />
          </div>
        </div>
      </div>
      {thumbStrip}
    </div>
  );
}
