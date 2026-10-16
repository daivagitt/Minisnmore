import { HOME_SECTION_PRODUCT_LIMIT } from "@/lib/home-products";
import { Skeleton } from "@/components/ui/skeleton";

export function WatchShopSkeleton({ count = HOME_SECTION_PRODUCT_LIMIT }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3 md:gap-5">
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} className="aspect-3/4 w-full rounded-[1.75rem]" />
      ))}
    </div>
  );
}
