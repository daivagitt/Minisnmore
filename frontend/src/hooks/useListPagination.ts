import { useEffect, useMemo, useState } from "react";
import { LIST_PAGE_SIZE, clampPage, pageRange, pageSlice, totalPages } from "@/lib/pagination";

type Options = {
  pageSize?: number;
  /** When this value changes (filter, date range, etc.), jump back to page 1. */
  resetKey?: unknown;
};

export function useListPagination<T>(items: readonly T[] | null | undefined, options: Options = {}) {
  const pageSize = options.pageSize ?? LIST_PAGE_SIZE;
  const list = items ?? [];
  const [page, setPage] = useState(1);

  useEffect(() => {
    setPage(1);
  }, [options.resetKey]);

  const pages = totalPages(list.length, pageSize);
  const safePage = clampPage(page, pages);

  useEffect(() => {
    if (page !== safePage) setPage(safePage);
  }, [page, safePage]);

  const pageItems = useMemo(
    () => pageSlice(list, safePage, pageSize),
    [list, safePage, pageSize],
  );
  const range = pageRange(list.length, safePage, pageSize);

  return {
    page: safePage,
    setPage,
    pageItems,
    pageSize,
    totalItems: list.length,
    totalPages: pages,
    rangeStart: range.start,
    rangeEnd: range.end,
  };
}
