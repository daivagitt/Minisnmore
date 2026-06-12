import { Skeleton } from "@/components/ui/skeleton";
import { HOME_SECTION_PRODUCT_LIMIT } from "@/lib/home-products";

export function ProductGridSkeleton({ count = HOME_SECTION_PRODUCT_LIMIT }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 md:gap-5">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="space-y-3">
          <Skeleton className="aspect-square w-full rounded-2xl" />
          <Skeleton className="h-4 w-4/5" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      ))}
    </div>
  );
}
