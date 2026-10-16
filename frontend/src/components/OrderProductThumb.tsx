import { LazyImage } from "@/components/LazyImage";
import { storefrontImageUrl } from "@/lib/storefront-image";
import { cn } from "@/lib/utils";

type Props = {
  src?: string | null;
  alt?: string;
  className?: string;
};

/** Catalog photo for an ordered item (cancel / return / exchange). */
export function OrderProductThumb({ src, alt = "", className }: Props) {
  const url = (src ?? "").trim();
  const thumb = url ? storefrontImageUrl(url, "thumb") || url : "";
  if (!thumb) {
    return <div className={cn("h-14 w-14 rounded-xl bg-muted shrink-0", className)} aria-hidden />;
  }
  return (
    <LazyImage
      src={thumb}
      fallbackSrc={url}
      alt={alt}
      className={cn("h-14 w-14 rounded-xl object-cover shrink-0", className)}
      priority="auto"
    />
  );
}
