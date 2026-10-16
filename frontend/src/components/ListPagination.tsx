import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
} from "@/components/ui/pagination";
import { buttonVariants } from "@/components/ui/button";
import { pageRange, visiblePageNumbers } from "@/lib/pagination";
import { cn } from "@/lib/utils";

type Props = {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  totalItems?: number;
  pageSize?: number;
  className?: string;
  /** Scroll this element into view after a page change (avoids jumping past the list). */
  scrollToId?: string;
};

export function ListPagination({
  page,
  totalPages,
  onPageChange,
  totalItems,
  pageSize,
  className,
  scrollToId,
}: Props) {
  const go = (next: number) => {
    if (next < 1 || next > totalPages || next === page) return;
    onPageChange(next);
    if (!scrollToId || typeof document === "undefined") return;
    requestAnimationFrame(() => {
      document.getElementById(scrollToId)?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  };

  const numbers = visiblePageNumbers(page, totalPages);
  const range =
    totalItems != null && pageSize != null ? pageRange(totalItems, page, pageSize) : null;
  const showControls = totalPages > 1;
  const showRange = range != null && range.end > 0;

  if (!showControls && !showRange) return null;

  return (
    <div className={cn("mt-6 flex flex-col items-center gap-2", className)}>
      {showRange ? (
        <p className="text-xs text-muted-foreground" aria-live="polite">
          Showing {range.start}–{range.end} of {totalItems}
          {showControls ? ` · Page ${page} of ${totalPages}` : null}
        </p>
      ) : null}
      {showControls ? (
        <Pagination>
          <PaginationContent>
            <PaginationItem>
              <button
                type="button"
                aria-label="Go to previous page"
                disabled={page <= 1}
                onClick={() => go(page - 1)}
                className={cn(buttonVariants({ variant: "ghost", size: "default" }), "gap-1 pl-2.5")}
              >
                Previous
              </button>
            </PaginationItem>
            {numbers.map((item, i) =>
              item === "ellipsis" ? (
                <PaginationItem key={`e-${i}`}>
                  <PaginationEllipsis />
                </PaginationItem>
              ) : (
                <PaginationItem key={item}>
                  <button
                    type="button"
                    aria-label={`Go to page ${item}`}
                    aria-current={item === page ? "page" : undefined}
                    onClick={() => go(item)}
                    className={cn(
                      buttonVariants({ variant: item === page ? "outline" : "ghost", size: "icon" }),
                    )}
                  >
                    {item}
                  </button>
                </PaginationItem>
              ),
            )}
            <PaginationItem>
              <button
                type="button"
                aria-label="Go to next page"
                disabled={page >= totalPages}
                onClick={() => go(page + 1)}
                className={cn(buttonVariants({ variant: "ghost", size: "default" }), "gap-1 pr-2.5")}
              >
                Next
              </button>
            </PaginationItem>
          </PaginationContent>
        </Pagination>
      ) : null}
    </div>
  );
}
