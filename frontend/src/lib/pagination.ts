/** Account/admin list pages (orders, notifications, payments, returns). */
export const LIST_PAGE_SIZE = 10;
/** Shop and collection product grids. */
export const GRID_PAGE_SIZE = 20;
/** Dense admin editors (products). */
export const ADMIN_PAGE_SIZE = 15;

export type PageWindowItem = number | "ellipsis";

export function totalPages(totalItems: number, pageSize: number): number {
  const size = Math.max(1, Math.floor(pageSize) || 1);
  const total = Math.max(0, totalItems);
  if (total === 0) return 1;
  return Math.ceil(total / size);
}

export function clampPage(page: number, pageCount: number): number {
  const count = Math.max(1, pageCount);
  if (!Number.isFinite(page)) return 1;
  return Math.min(count, Math.max(1, Math.trunc(page)));
}

export function pageSlice<T>(items: readonly T[], page: number, pageSize: number): T[] {
  const size = Math.max(1, Math.floor(pageSize) || 1);
  const pages = totalPages(items.length, size);
  const safe = clampPage(page, pages);
  const start = (safe - 1) * size;
  return items.slice(start, start + size);
}

export function pageRange(totalItems: number, page: number, pageSize: number): { start: number; end: number } {
  if (totalItems <= 0) return { start: 0, end: 0 };
  const size = Math.max(1, Math.floor(pageSize) || 1);
  const safe = clampPage(page, totalPages(totalItems, size));
  const start = (safe - 1) * size + 1;
  const end = Math.min(totalItems, safe * size);
  return { start, end };
}

/** Accepts router search values (`"2"`, `2`, arrays from duplicate query keys). */
export function parsePageParam(raw: unknown): number | undefined {
  const value = Array.isArray(raw) ? raw[0] : raw;
  const n = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN;
  if (!Number.isInteger(n) || n < 1) return undefined;
  return n;
}

/**
 * Compact window: always first + last, current ±1, ellipsis in gaps.
 * Example for page 8 of 20: 1 … 7 8 9 … 20
 */
export function visiblePageNumbers(current: number, pageCount: number): PageWindowItem[] {
  const total = Math.max(1, pageCount);
  const page = clampPage(current, total);
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }

  const pages = new Set<number>([1, total, page, page - 1, page + 1]);
  if (page <= 3) {
    pages.add(2);
    pages.add(3);
    pages.add(4);
  }
  if (page >= total - 2) {
    pages.add(total - 1);
    pages.add(total - 2);
    pages.add(total - 3);
  }

  const sorted = [...pages].filter((n) => n >= 1 && n <= total).sort((a, b) => a - b);
  const out: PageWindowItem[] = [];
  for (const n of sorted) {
    const prev = out[out.length - 1];
    if (typeof prev === "number" && n - prev > 1) out.push("ellipsis");
    out.push(n);
  }
  return out;
}
