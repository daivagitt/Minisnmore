import { useEffect, useMemo, useState } from "react";
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

type Props = {
  name: string;
  image: string;
  gallery?: string[];
  className?: string;
};

export function ProductGallery({ name, image, gallery = [], className }: Props) {
  const images = useMemo(() => uniqueImages(image, ...gallery), [image, gallery]);
  const [active, setActive] = useState(0);

  useEffect(() => {
    setActive(0);
  }, [images]);

  const safeActive = images.length ? Math.min(active, images.length - 1) : 0;
  const current = images[safeActive] ?? images[0] ?? "";

  const go = (delta: number) => {
    if (images.length <= 1) return;
    setActive((i) => (i + delta + images.length) % images.length);
  };

  return (
    <div
      className={cn(
        "flex flex-col lg:flex-row gap-2 sm:gap-3 w-full min-w-0 lg:h-full lg:min-h-0 lg:items-stretch",
        className,
      )}
    >
      {images.length > 1 ? (
        <div
          className="order-2 lg:order-1 flex flex-row lg:flex-col gap-2 overflow-x-auto lg:overflow-x-hidden lg:overflow-y-auto -mx-1 px-1 lg:mx-0 lg:px-0 pb-0.5 lg:pb-0 shrink-0 lg:w-[4.5rem] xl:w-[5.25rem] lg:h-full lg:max-h-none snap-x snap-mandatory lg:snap-none [scrollbar-width:thin]"
          aria-label="Product image thumbnails"
        >
          {images.map((src, i) => (
            <button
              key={`${src}-${i}`}
              type="button"
              onClick={() => setActive(i)}
              aria-label={`View image ${i + 1} of ${images.length}`}
              aria-current={safeActive === i ? "true" : undefined}
              className={cn(
                "glass rounded-xl p-1 shrink-0 transition snap-start",
                "w-[3.75rem] sm:w-[4.25rem] lg:w-full",
                safeActive === i ? "ring-2 ring-primary" : "opacity-75 hover:opacity-100",
              )}
            >
              <img src={src} alt="" className="aspect-square w-full object-cover rounded-lg" loading="lazy" />
            </button>
          ))}
        </div>
      ) : null}

      <motion.div
        initial={{ opacity: 0, scale: 0.98 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5 }}
        className={cn(
          "order-1 lg:order-2 glass rounded-[1.5rem] sm:rounded-[2rem] p-1.5 sm:p-2 relative min-w-0 w-full flex flex-col",
          "aspect-square w-full max-h-[min(92vw,24rem)] sm:max-h-[min(88vw,28rem)]",
          "lg:aspect-auto lg:max-h-none lg:flex-1 lg:h-full lg:min-h-0",
        )}
      >
        <div className="relative h-full min-h-0 w-full overflow-hidden rounded-[1rem] sm:rounded-[1.25rem] bg-secondary/20">
          <AnimatePresence mode="wait">
            <motion.img
              key={current}
              src={current}
              alt={name}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="absolute inset-0 h-full w-full object-cover"
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
