import { forwardRef, useEffect, useState, type ComponentPropsWithoutRef, type SyntheticEvent } from "react";
import { loadImage, shouldBypassBlobCache } from "@/lib/image-cache";

export type LazyImagePriority = "high" | "eager" | "low" | "auto";

export type LazyImageProps = Omit<ComponentPropsWithoutRef<"img">, "src" | "loading"> & {
  src: string;
  fallbackSrc?: string;
  priority?: LazyImagePriority;
};

export const LazyImage = forwardRef<HTMLImageElement, LazyImageProps>(function LazyImage(
  {
    src,
    fallbackSrc,
    alt,
    priority = "auto",
    className,
    decoding = "async",
    fetchPriority,
    srcSet,
    sizes,
    onLoad,
    onError,
    ...rest
  },
  forwardedRef,
) {
  const trimmed = src.trim();
  const fallback = fallbackSrc?.trim() ?? "";
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [trimmed, fallback]);

  useEffect(() => {
    if (!trimmed || shouldBypassBlobCache(trimmed)) return;
    void loadImage(trimmed).catch(() => {});
  }, [trimmed]);

  if (!trimmed) return null;

  const useFallback = failed && fallback && fallback !== trimmed;
  const resolved = useFallback ? fallback : trimmed;

  const handleError = (event: SyntheticEvent<HTMLImageElement, Event>) => {
    if (!useFallback && fallback && fallback !== trimmed) {
      setFailed(true);
    }
    onError?.(event);
  };

  return (
    <img
      {...rest}
      ref={forwardedRef}
      src={resolved}
      srcSet={useFallback ? undefined : srcSet}
      sizes={useFallback ? undefined : sizes}
      alt={alt}
      decoding={decoding}
      fetchPriority={fetchPriority ?? (priority === "high" ? "high" : priority === "low" ? "low" : undefined)}
      loading={priority === "high" || priority === "eager" ? "eager" : "lazy"}
      onLoad={onLoad}
      onError={handleError}
      className={className}
    />
  );
});
