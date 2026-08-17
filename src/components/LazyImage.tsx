import { forwardRef, useEffect, type ComponentPropsWithoutRef } from "react";
import { loadImage, shouldBypassBlobCache } from "@/lib/image-cache";

export type LazyImagePriority = "high" | "low" | "auto";

export type LazyImageProps = Omit<ComponentPropsWithoutRef<"img">, "src" | "loading"> & {
  src: string;
  priority?: LazyImagePriority;
};

export const LazyImage = forwardRef<HTMLImageElement, LazyImageProps>(function LazyImage(
  {
    src,
    alt,
    priority = "auto",
    className,
    decoding = "async",
    fetchPriority,
    onLoad,
    onError,
    ...rest
  },
  forwardedRef,
) {
  const trimmed = src.trim();

  useEffect(() => {
    if (!trimmed || shouldBypassBlobCache(trimmed)) return;
    void loadImage(trimmed).catch(() => {});
  }, [trimmed]);

  if (!trimmed) return null;

  return (
    <img
      {...rest}
      ref={forwardedRef}
      src={trimmed}
      alt={alt}
      decoding={decoding}
      fetchPriority={fetchPriority ?? (priority === "high" ? "high" : priority === "low" ? "low" : undefined)}
      loading={priority === "high" ? "eager" : "lazy"}
      onLoad={onLoad}
      onError={onError}
      className={className}
    />
  );
});
