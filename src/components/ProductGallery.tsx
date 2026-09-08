import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { LazyImage } from "@/components/LazyImage";
import { cn } from "@/lib/utils";
import { PRODUCT_GALLERY_HERO_FRAME, PRODUCT_GALLERY_HERO_MAX_H } from "@/lib/product-gallery";
import {
  contentAspect,
  detectContentBoundsFromImage,
  imageFitStyles,
  type ImageContentBounds,
} from "@/lib/product-image-bounds";

/** Shared PDP gallery card — white outline box with soft shadow (reference PDP). */
const galleryBox =
  "glass shadow-soft border border-white/70 bg-white/90 backdrop-blur-sm";
const galleryHeroRound = "rounded-[1.75rem] sm:rounded-[2rem] lg:rounded-[2.5rem]";
const galleryThumbRailRound = "rounded-[1.25rem] sm:rounded-[1.5rem]";
const galleryImageRound = "rounded-[1rem] sm:rounded-[1.25rem] lg:rounded-[1.5rem]";

const DEFAULT_ASPECT = 1;

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

function useImageContentBounds() {
  const boundsRef = useRef<Map<string, ImageContentBounds>>(new Map());
  const aspectRef = useRef<Map<string, number>>(new Map());
  const [revision, bump] = useState(0);

  const rememberBounds = useCallback((src: string, img: HTMLImageElement) => {
    const nw = img.naturalWidth;
    const nh = img.naturalHeight;
    if (!nw || !nh) return;

    const naturalAspect = nw / nh;
    if (!aspectRef.current.has(src)) {
      aspectRef.current.set(src, naturalAspect);
      bump((n) => n + 1);
    }

    // Defer pixel scan so layout can settle (avoids jank when variant pickers update).
    requestAnimationFrame(() => {
      try {
        const bounds = detectContentBoundsFromImage(img);
        const prev = boundsRef.current.get(src);
        if (
          prev &&
          prev.x === bounds.x &&
          prev.y === bounds.y &&
          prev.width === bounds.width &&
          prev.height === bounds.height
        ) {
          return;
        }
        boundsRef.current.set(src, bounds);
        const contentAr = contentAspect(bounds);
        const prevAr = aspectRef.current.get(src);
        if (!prevAr || Math.abs(contentAr - prevAr) > 0.02) {
          aspectRef.current.set(src, contentAr);
          bump((n) => n + 1);
        }
      } catch {
        boundsRef.current.set(src, {
          x: 0,
          y: 0,
          width: nw,
          height: nh,
          naturalWidth: nw,
          naturalHeight: nh,
        });
      }
    });
  }, []);

  const boundsFor = useCallback((src: string) => boundsRef.current.get(src), []);
  const aspectFor = useCallback(
    (src: string) => aspectRef.current.get(src) ?? DEFAULT_ASPECT,
    [],
  );

  return { boundsFor, aspectFor, rememberBounds, revision };
}

type FittedImageProps = {
  src: string;
  alt: string;
  bounds?: ImageContentBounds;
  frameAspect: number;
  className?: string;
  priority?: "high" | "auto" | "low";
  onBounds: (src: string, img: HTMLImageElement) => void;
};

/** Zooms to the detected product region so the inner frame has no baked-in white/cream border. */
function FittedGalleryImage({
  src,
  alt,
  bounds,
  frameAspect,
  className,
  priority,
  onBounds,
}: FittedImageProps) {
  if (!src) return null;

  const fitStyle = bounds ? imageFitStyles(bounds, frameAspect) : undefined;

  return (
    <LazyImage
      src={src}
      alt={alt}
      decoding="async"
      priority={priority ?? "auto"}
      onLoad={(e) => onBounds(src, e.currentTarget)}
      className={cn(
        "absolute select-none",
        bounds ? "max-w-none" : "inset-0 h-full w-full object-cover object-center",
        className,
      )}
      style={fitStyle}
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

/** Each thumb is its own rounded card — not grouped in a single rail container. */
function GalleryThumb({ src, index, total, active, onSelect }: ThumbProps) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={`View image ${index + 1} of ${total}`}
      aria-current={active ? "true" : undefined}
      className={cn(
        galleryBox,
        galleryThumbRailRound,
        "p-1.5 sm:p-2 overflow-hidden shrink-0 snap-start relative w-[3.25rem] sm:w-[3.75rem] lg:w-full aspect-square",
        active ? "ring-2 ring-primary ring-offset-1 opacity-100" : "opacity-70 hover:opacity-100",
      )}
    >
      <LazyImage
        src={src}
        alt=""
        decoding="async"
        priority="auto"
        className={cn(
          "absolute inset-0 h-full w-full object-cover object-center select-none",
          galleryImageRound,
        )}
        draggable={false}
      />
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
  const { boundsFor, aspectFor, rememberBounds } = useImageContentBounds();

  useEffect(() => {
    if (!rawImages.length) return;

    let cancelled = false;

    for (const src of rawImages) {
      const img = new Image();
      img.decoding = "async";
      img.onload = () => {
        if (!cancelled) {
          rememberBounds(src, img);
        }
      };
      img.onerror = () => {};
      img.src = src;
    }

    return () => {
      cancelled = true;
    };
  }, [rawImagesKey, rememberBounds]);

  useEffect(() => {
    setActive(0);
  }, [rawImagesKey]);

  const safeActive = rawImages.length ? Math.min(active, rawImages.length - 1) : 0;
  const current = rawImages[safeActive] ?? rawImages[0] ?? "";
  const heroBounds = boundsFor(current);
  /** Frame follows the active slide so each image fills the box without letterboxing. */
  const heroAspect = heroBounds ? contentAspect(heroBounds) : aspectFor(current);

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
          className="order-2 lg:order-1 shrink-0 lg:w-[5rem] xl:w-[5.5rem] lg:self-start flex flex-row lg:flex-col gap-2 sm:gap-2.5 overflow-x-auto lg:overflow-y-auto snap-x snap-mandatory lg:snap-none [scrollbar-width:thin] lg:max-h-[min(92vw,36rem)]"
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
        <motion.div
          initial={false}
          className={cn(
            galleryBox,
            galleryHeroRound,
            "relative w-fit max-w-full p-2.5 sm:p-3",
          )}
        >
        <div
          className={cn(
            "group relative overflow-hidden",
            PRODUCT_GALLERY_HERO_MAX_H,
            PRODUCT_GALLERY_HERO_FRAME,
            galleryImageRound,
          )}
          style={{ ["--hero-ar" as string]: String(heroAspect) }}
        >
          {/* In-flow spacer — absolute children alone collapse the frame to zero height. */}
          <div
            className="w-full"
            style={{ paddingBottom: `${(1 / heroAspect) * 100}%` }}
            aria-hidden
          />

          <AnimatePresence mode="sync" initial={false}>
            <motion.div
              key={current}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              className="absolute inset-0 overflow-hidden"
            >
              {current ? (
                <FittedGalleryImage
                  src={current}
                  alt={name}
                  bounds={heroBounds}
                  frameAspect={heroAspect}
                  priority="high"
                  onBounds={rememberBounds}
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
        </motion.div>
      </div>
    </div>
  );
}
