import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";

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

function useImageAspects(sources: string[]) {
  const aspectsRef = useRef<Map<string, number>>(new Map());
  const [, bump] = useState(0);

  const rememberAspect = useCallback((src: string, naturalWidth: number, naturalHeight: number) => {
    if (!naturalWidth || !naturalHeight) return;
    const ratio = naturalWidth / naturalHeight;
    if (aspectsRef.current.get(src) === ratio) return;
    aspectsRef.current.set(src, ratio);
    bump((n) => n + 1);
  }, []);

  const aspectFor = useCallback((src: string) => aspectsRef.current.get(src) ?? 1, []);

  return { aspectFor, rememberAspect };
}

type ThumbProps = {
  src: string;
  index: number;
  total: number;
  active: boolean;
  aspect: number;
  onAspect: (src: string, w: number, h: number) => void;
  onSelect: () => void;
};

function GalleryThumb({ src, index, total, active, aspect, onAspect, onSelect }: ThumbProps) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={`View image ${index + 1} of ${total}`}
      aria-current={active ? "true" : undefined}
      style={{ aspectRatio: aspect }}
      className={cn(
        "glass rounded-xl overflow-hidden shrink-0 transition snap-start w-[3.75rem] sm:w-[4.25rem] lg:w-full",
        active ? "ring-2 ring-primary ring-inset" : "opacity-75 hover:opacity-100",
      )}
    >
      <img
        src={src}
        alt=""
        className="block h-full w-full object-contain object-center"
        loading="lazy"
        onLoad={(e) => onAspect(src, e.currentTarget.naturalWidth, e.currentTarget.naturalHeight)}
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
  const images = useMemo(() => uniqueImages(image, ...gallery), [image, gallery]);
  const [active, setActive] = useState(0);
  const { aspectFor, rememberAspect } = useImageAspects(images);

  useEffect(() => {
    setActive(0);
  }, [images]);

  const safeActive = images.length ? Math.min(active, images.length - 1) : 0;
  const current = images[safeActive] ?? images[0] ?? "";
  const heroAspect = aspectFor(current);

  const go = (delta: number) => {
    if (images.length <= 1) return;
    setActive((i) => (i + delta + images.length) % images.length);
  };

  return (
    <div
      className={cn(
        "flex flex-col lg:flex-row gap-2 sm:gap-3 w-full min-w-0 lg:items-start",
        className,
      )}
    >
      {images.length > 1 ? (
        <div
          className="order-2 lg:order-1 flex flex-row lg:flex-col gap-2 overflow-x-auto lg:overflow-y-auto -mx-1 px-1 lg:mx-0 lg:px-0 pb-0.5 lg:pb-0 shrink-0 lg:w-[4.5rem] xl:w-[5.25rem] lg:max-h-[min(92vw,36rem)] snap-x snap-mandatory lg:snap-none [scrollbar-width:thin]"
          aria-label="Product image thumbnails"
        >
          {images.map((src, i) => (
            <GalleryThumb
              key={`${src}-${i}`}
              src={src}
              index={i}
              total={images.length}
              active={safeActive === i}
              aspect={aspectFor(src)}
              onAspect={rememberAspect}
              onSelect={() => setActive(i)}
            />
          ))}
        </div>
      ) : null}

      <motion.div
        initial={{ opacity: 0, scale: 0.98 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5 }}
        className="order-1 lg:order-2 glass rounded-[1.5rem] sm:rounded-[2rem] overflow-hidden relative min-w-0 w-full lg:flex-1"
      >
        <div
          className="relative w-full overflow-hidden max-h-[85vh]"
          style={{ aspectRatio: heroAspect }}
        >
          <AnimatePresence mode="wait">
            <motion.img
              key={current}
              src={current}
              alt={name}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              onLoad={(e) => rememberAspect(current, e.currentTarget.naturalWidth, e.currentTarget.naturalHeight)}
              className="absolute inset-0 h-full w-full object-cover object-center"
            />
          </AnimatePresence>

          {images.length > 1 ? (
            <>
              <button
                type="button"
                onClick={() => go(-1)}
                className="absolute left-1.5 sm:left-2 top-1/2 z-10 -translate-y-1/2 h-8 w-8 sm:h-9 sm:w-9 grid place-items-center rounded-full glass hover:bg-white/80 transition"
                aria-label="Previous image"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={() => go(1)}
                className="absolute right-1.5 sm:right-2 top-1/2 z-10 -translate-y-1/2 h-8 w-8 sm:h-9 sm:w-9 grid place-items-center rounded-full glass hover:bg-white/80 transition"
                aria-label="Next image"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </>
          ) : null}
        </div>
      </motion.div>
    </div>
  );
}
