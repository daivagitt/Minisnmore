import { isStaffAuthorities } from "@/lib/staff-roles";

export type UserJoinSource = {
  createdDate?: string | null;
  activated?: boolean;
  authorities?: string[] | null;
};

export type JoinPreset = "today" | "last7" | "last30" | "thisMonth";

export type JoinFilter =
  | { kind: "all" }
  | { kind: "preset"; preset: JoinPreset }
  | { kind: "range"; fromMs: number; toMs: number; key: string }
  /** Inclusive local calendar days `YYYY-MM-DD` from date inputs; empty side is open-ended. */
  | { kind: "custom"; from: string; to: string };

export type JoinBucket = {
  key: string;
  label: string;
  count: number;
  fromMs: number;
  toMs: number;
};

export type UserJoinStats = {
  total: number;
  active: number;
  pending: number;
  joinedToday: number;
  joinedLast7Days: number;
  joinedLast30Days: number;
  joinedThisMonth: number;
  last7Days: JoinBucket[];
  last8Weeks: JoinBucket[];
};

const LOCAL_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export function isCustomerUser(authorities?: string[] | null): boolean {
  return !isStaffAuthorities(authorities);
}

export function parseJoinDate(value?: string | null): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function startOfLocalDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function addLocalDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

export function startOfLocalMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

export function startOfLocalWeek(date: Date): Date {
  const start = startOfLocalDay(date);
  const weekday = start.getDay();
  const offset = weekday === 0 ? -6 : 1 - weekday;
  return addLocalDays(start, offset);
}

export function localDayKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

/** Parse `<input type="date" />` value as local midnight; invalid → null. */
export function parseLocalDateInput(value: string): Date | null {
  const trimmed = value.trim();
  if (!LOCAL_DATE_RE.test(trimmed)) return null;
  const [year, month, day] = trimmed.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  return date;
}

/**
 * Inclusive joined-date range from optional From/To inputs.
 * Swaps inverted ranges. Open-ended when only one side is set.
 */
export function customJoinedDateRange(
  joinedFrom: string,
  joinedTo: string,
): { fromMs: number; toMs: number } | null {
  let fromDate = joinedFrom.trim() ? parseLocalDateInput(joinedFrom) : null;
  let toDate = joinedTo.trim() ? parseLocalDateInput(joinedTo) : null;
  if (!fromDate && !toDate) return null;
  if (fromDate && toDate && fromDate.getTime() > toDate.getTime()) {
    const swap = fromDate;
    fromDate = toDate;
    toDate = swap;
  }
  return {
    fromMs: fromDate ? fromDate.getTime() : Number.NEGATIVE_INFINITY,
    toMs: toDate ? addLocalDays(toDate, 1).getTime() : Number.POSITIVE_INFINITY,
  };
}

export function formatJoinedDateFilterLabel(joinedFrom: string, joinedTo: string): string | null {
  const from = joinedFrom.trim();
  const to = joinedTo.trim();
  if (!from && !to) return null;
  if (from && to) {
    if (from === to) return `joined ${from}`;
    const [earlier, later] = from <= to ? [from, to] : [to, from];
    return `joined ${earlier} → ${later}`;
  }
  if (from) return `joined from ${from}`;
  return `joined through ${to}`;
}

export function sameJoinFilter(a: JoinFilter, b: JoinFilter): boolean {
  if (a.kind !== b.kind) return false;
  if (a.kind === "all" || b.kind === "all") return true;
  if (a.kind === "preset" && b.kind === "preset") return a.preset === b.preset;
  if (a.kind === "range" && b.kind === "range") return a.key === b.key;
  if (a.kind === "custom" && b.kind === "custom") {
    return a.from.trim() === b.from.trim() && a.to.trim() === b.to.trim();
  }
  return false;
}

export function toggleJoinFilter(current: JoinFilter, next: JoinFilter): JoinFilter {
  return sameJoinFilter(current, next) ? { kind: "all" } : next;
}

export function joinFilterLabel(filter: JoinFilter, buckets: JoinBucket[]): string {
  if (filter.kind === "all") return "All customers";
  if (filter.kind === "preset") {
    if (filter.preset === "today") return "Joined today";
    if (filter.preset === "last7") return "Joined in the last 7 days";
    if (filter.preset === "last30") return "Joined in the last 30 days";
    return "Joined this month";
  }
  if (filter.kind === "custom") {
    return formatJoinedDateFilterLabel(filter.from, filter.to) ?? "Selected period";
  }
  const bucket = buckets.find((item) => item.key === filter.key);
  return bucket ? `Joined ${bucket.label}` : "Selected period";
}

/** Stable key for pagination reset when the join filter changes. */
export function joinFilterKey(filter: JoinFilter): string {
  if (filter.kind === "all") return "all";
  if (filter.kind === "preset") return `preset:${filter.preset}`;
  if (filter.kind === "custom") return `custom:${filter.from.trim()}|${filter.to.trim()}`;
  return `range:${filter.key}|${filter.fromMs}|${filter.toMs}`;
}

export function joinFilterActive(filter: JoinFilter): boolean {
  if (filter.kind === "all") return false;
  if (filter.kind === "custom") return Boolean(filter.from.trim() || filter.to.trim());
  return true;
}

function inRange(created: Date, fromMs: number, toMs: number): boolean {
  const ms = created.getTime();
  return ms >= fromMs && ms < toMs;
}

function countInRange(dates: Date[], fromMs: number, toMs: number): number {
  return dates.filter((date) => inRange(date, fromMs, toMs)).length;
}

function dayBucket(day: Date, today: Date, dates: Date[]): JoinBucket {
  const fromMs = day.getTime();
  const toMs = addLocalDays(day, 1).getTime();
  const isToday = day.getTime() === today.getTime();
  return {
    key: `day:${localDayKey(day)}`,
    label: isToday ? "Today" : day.toLocaleDateString("en-IN", { weekday: "short" }),
    count: countInRange(dates, fromMs, toMs),
    fromMs,
    toMs,
  };
}

function weekBucket(weekStart: Date, dates: Date[]): JoinBucket {
  const fromMs = weekStart.getTime();
  const toMs = addLocalDays(weekStart, 7).getTime();
  return {
    key: `week:${localDayKey(weekStart)}`,
    label: weekStart.toLocaleDateString("en-IN", { day: "numeric", month: "short" }),
    count: countInRange(dates, fromMs, toMs),
    fromMs,
    toMs,
  };
}

export function joinFilterRange(filter: JoinFilter, now: Date): { fromMs: number; toMs: number } | null {
  if (filter.kind === "all") return null;
  if (filter.kind === "range") return { fromMs: filter.fromMs, toMs: filter.toMs };
  if (filter.kind === "custom") return customJoinedDateRange(filter.from, filter.to);

  const today = startOfLocalDay(now);
  const tomorrow = addLocalDays(today, 1).getTime();
  if (filter.preset === "today") return { fromMs: today.getTime(), toMs: tomorrow };
  if (filter.preset === "last7") return { fromMs: addLocalDays(today, -6).getTime(), toMs: tomorrow };
  if (filter.preset === "last30") return { fromMs: addLocalDays(today, -29).getTime(), toMs: tomorrow };
  return { fromMs: startOfLocalMonth(now).getTime(), toMs: tomorrow };
}

export function userJoinedInFilter(createdDate: string | null | undefined, filter: JoinFilter, now = new Date()): boolean {
  const range = joinFilterRange(filter, now);
  if (!range) return true;
  const created = parseJoinDate(createdDate);
  if (!created) return false;
  return inRange(created, range.fromMs, range.toMs);
}

export function buildUserJoinStats(users: UserJoinSource[], now = new Date()): UserJoinStats {
  const customers = users.filter((user) => isCustomerUser(user.authorities));
  const dates = customers.map((user) => parseJoinDate(user.createdDate)).filter((date): date is Date => date != null);
  const today = startOfLocalDay(now);
  const tomorrow = addLocalDays(today, 1).getTime();
  const last7From = addLocalDays(today, -6).getTime();
  const last30From = addLocalDays(today, -29).getTime();
  const monthFrom = startOfLocalMonth(now).getTime();
  const thisWeek = startOfLocalWeek(now);

  return {
    total: customers.length,
    active: customers.filter((user) => user.activated).length,
    pending: customers.filter((user) => !user.activated).length,
    joinedToday: countInRange(dates, today.getTime(), tomorrow),
    joinedLast7Days: countInRange(dates, last7From, tomorrow),
    joinedLast30Days: countInRange(dates, last30From, tomorrow),
    joinedThisMonth: countInRange(dates, monthFrom, tomorrow),
    last7Days: Array.from({ length: 7 }, (_, index) => dayBucket(addLocalDays(today, index - 6), today, dates)),
    last8Weeks: Array.from({ length: 8 }, (_, index) => weekBucket(addLocalDays(thisWeek, (index - 7) * 7), dates)),
  };
}
