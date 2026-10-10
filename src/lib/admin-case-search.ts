import { customJoinedDateRange, parseJoinDate } from "@/lib/user-join-stats";

export function matchesOrderQuery(
  query: string,
  ids: { orderNumber?: string | number | null; orderId?: string | number | null },
): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  const number = ids.orderNumber == null ? "" : String(ids.orderNumber).trim().toLowerCase();
  const id = ids.orderId == null ? "" : String(ids.orderId).trim().toLowerCase();
  return number.includes(q) || id.includes(q);
}

/** Inclusive local calendar days from optional From/To inputs. Missing date → no match. */
export function matchesLocalDateRange(
  iso: string | null | undefined,
  from: string,
  to: string,
): boolean {
  const range = customJoinedDateRange(from, to);
  if (!range) return true;
  const date = parseJoinDate(iso);
  if (!date) return false;
  const time = date.getTime();
  return time >= range.fromMs && time < range.toMs;
}

export function adminCaseSearchActive(orderQuery: string, from: string, to: string): boolean {
  return Boolean(orderQuery.trim() || from.trim() || to.trim());
}

/** Order/date search must see every status; otherwise keep the chip's API filter. */
export function adminCaseListFetchStatus(searchActive: boolean, status: string): string | undefined {
  if (searchActive) return undefined;
  return status || undefined;
}

/** Convert local From/To date inputs to exclusive-end ISO bounds for admin order list APIs. */
export function adminOrderApiDateRange(
  from: string,
  to: string,
): { from?: string; to?: string } {
  const range = customJoinedDateRange(from, to);
  if (!range) return {};
  return {
    from:
      Number.isFinite(range.fromMs) && range.fromMs !== Number.NEGATIVE_INFINITY
        ? new Date(range.fromMs).toISOString()
        : undefined,
    to:
      Number.isFinite(range.toMs) && range.toMs !== Number.POSITIVE_INFINITY
        ? new Date(range.toMs).toISOString()
        : undefined,
  };
}
