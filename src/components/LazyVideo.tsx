import { useEffect, useRef, useState, type VideoHTMLAttributes } from "react";

type LazyVideoProps = Omit<VideoHTMLAttributes<HTMLVideoElement>, "src" | "poster" | "ref"> & {
  src: string;
  poster: string;
  rootMargin?: string;
};

/**
 * Defers mounting the <video> (and its network fetch) until the element scrolls near the
 * viewport. Autoplaying video otherwise starts downloading the moment the page renders,
 * even for sections far below the fold.
 */
export function LazyVideo({
  src,
  poster,
  className,
  autoPlay = true,
  muted = true,
  loop = true,
  playsInline = true,
  rootMargin = "300px",
  ...rest
}: LazyVideoProps) {
  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const [inView, setInView] = useState(false);

  useEffect(() => {
    const el = wrapperRef.current;
    if (!el || inView) return;
    if (typeof IntersectionObserver === "undefined") {
      setInView(true);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setInView(true);
          observer.disconnect();
        }
      },
      { rootMargin },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [inView, rootMargin]);

  return (
    <div ref={wrapperRef} className="absolute inset-0">
      {inView ? (
        <video
          src={src}
          autoPlay={autoPlay}
          muted={muted}
          loop={loop}
          playsInline={playsInline}
          preload="auto"
          poster={poster}
          className={className}
          {...rest}
        />
      ) : (
        <img src={poster} alt="" loading="lazy" className={className} />
      )}
    </div>
  );
}
