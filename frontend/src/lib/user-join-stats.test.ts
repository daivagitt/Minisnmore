import { describe, expect, it } from "vitest";
import {
  buildUserJoinStats,
  customJoinedDateRange,
  formatJoinedDateFilterLabel,
  isCustomerUser,
  joinFilterActive,
  joinFilterKey,
  joinFilterLabel,
  parseLocalDateInput,
  sameJoinFilter,
  startOfLocalWeek,
  toggleJoinFilter,
  userJoinedInFilter,
} from "./user-join-stats";

const now = new Date(2026, 8, 21, 15, 30, 0);

function isoAt(year: number, month: number, day: number, hour = 10) {
  return new Date(year, month, day, hour, 0, 0).toISOString();
}

describe("user join stats", () => {
  it("treats staff accounts as non-customers", () => {
    expect(isCustomerUser(["ROLE_USER"])).toBe(true);
    expect(isCustomerUser(["ROLE_USER", "ROLE_ADMIN"])).toBe(false);
    expect(isCustomerUser(["ROLE_SALES"])).toBe(false);
    expect(isCustomerUser([])).toBe(true);
  });

  it("counts customer joins by day, week and month and ignores staff", () => {
    const stats = buildUserJoinStats(
      [
        { createdDate: isoAt(2026, 8, 21), activated: true, authorities: ["ROLE_USER"] },
        { createdDate: isoAt(2026, 8, 20), activated: true, authorities: ["ROLE_USER"] },
        { createdDate: isoAt(2026, 8, 15), activated: false, authorities: ["ROLE_USER"] },
        { createdDate: isoAt(2026, 7, 30), activated: true, authorities: ["ROLE_USER"] },
        { createdDate: isoAt(2026, 8, 21), activated: true, authorities: ["ROLE_ADMIN"] },
        { createdDate: "not-a-date", activated: true, authorities: ["ROLE_USER"] },
      ],
      now,
    );

    expect(stats.total).toBe(5);
    expect(stats.active).toBe(4);
    expect(stats.pending).toBe(1);
    expect(stats.joinedToday).toBe(1);
    expect(stats.joinedLast7Days).toBe(3);
    expect(stats.joinedLast30Days).toBe(4);
    expect(stats.joinedThisMonth).toBe(3);
    expect(stats.last7Days).toHaveLength(7);
    expect(stats.last7Days.at(-1)).toMatchObject({ label: "Today", count: 1 });
    expect(stats.last7Days.find((bucket) => bucket.label !== "Today" && bucket.count === 1)?.count).toBe(1);
    expect(stats.last8Weeks).toHaveLength(8);
    expect(stats.last8Weeks.reduce((sum, bucket) => sum + bucket.count, 0)).toBe(4);
  });

  it("filters customers to the selected join window", () => {
    const todayJoin = isoAt(2026, 8, 21);
    const lastWeekJoin = isoAt(2026, 8, 16);
    expect(userJoinedInFilter(todayJoin, { kind: "all" }, now)).toBe(true);
    expect(userJoinedInFilter(todayJoin, { kind: "preset", preset: "today" }, now)).toBe(true);
    expect(userJoinedInFilter(lastWeekJoin, { kind: "preset", preset: "today" }, now)).toBe(false);
    expect(userJoinedInFilter(lastWeekJoin, { kind: "preset", preset: "last7" }, now)).toBe(true);
    expect(userJoinedInFilter(isoAt(2026, 7, 20), { kind: "preset", preset: "last30" }, now)).toBe(false);
    expect(userJoinedInFilter(isoAt(2026, 8, 1), { kind: "preset", preset: "thisMonth" }, now)).toBe(true);
    expect(userJoinedInFilter(null, { kind: "preset", preset: "today" }, now)).toBe(false);
  });

  it("starts local weeks on Monday", () => {
    const monday = startOfLocalWeek(now);
    expect(monday.getDay()).toBe(1);
    expect(monday.getDate()).toBe(21);
  });
});

describe("parseLocalDateInput / customJoinedDateRange", () => {
  it("rejects invalid calendar dates", () => {
    expect(parseLocalDateInput("2026-02-30")).toBeNull();
    expect(parseLocalDateInput("not-a-date")).toBeNull();
    expect(parseLocalDateInput("")).toBeNull();
  });

  it("builds inclusive local-day ranges and swaps inverted bounds", () => {
    const range = customJoinedDateRange("2026-03-10", "2026-03-12");
    expect(range).not.toBeNull();
    expect(range!.fromMs).toBe(new Date(2026, 2, 10).getTime());
    expect(range!.toMs).toBe(new Date(2026, 2, 13).getTime());

    const swapped = customJoinedDateRange("2026-03-12", "2026-03-10");
    expect(swapped).toEqual(range);

    expect(customJoinedDateRange("", "")).toBeNull();
    expect(customJoinedDateRange("2026-03-10", "")?.fromMs).toBe(new Date(2026, 2, 10).getTime());
    expect(customJoinedDateRange("2026-03-10", "")?.toMs).toBe(Number.POSITIVE_INFINITY);
  });
});

describe("custom join filter", () => {
  it("includes the full From and To days", () => {
    const mid = new Date(2026, 2, 15, 18, 30).toISOString();
    expect(userJoinedInFilter(mid, { kind: "custom", from: "2026-03-15", to: "2026-03-15" })).toBe(true);
    expect(userJoinedInFilter(mid, { kind: "custom", from: "2026-03-14", to: "2026-03-14" })).toBe(false);
    expect(userJoinedInFilter(mid, { kind: "custom", from: "2026-03-16", to: "" })).toBe(false);
    expect(userJoinedInFilter(mid, { kind: "custom", from: "", to: "2026-03-15" })).toBe(true);
    expect(userJoinedInFilter(null, { kind: "custom", from: "2026-03-01", to: "" })).toBe(false);
  });

  it("treats empty custom sides as inactive", () => {
    expect(joinFilterActive({ kind: "custom", from: "", to: "" })).toBe(false);
    expect(joinFilterActive({ kind: "custom", from: "2026-01-01", to: "" })).toBe(true);
    expect(joinFilterActive({ kind: "all" })).toBe(false);
  });
});

describe("join filter helpers", () => {
  it("compares, toggles, labels, and keys filters", () => {
    expect(sameJoinFilter({ kind: "all" }, { kind: "all" })).toBe(true);
    expect(sameJoinFilter({ kind: "all" }, { kind: "custom", from: "2026-01-01", to: "" })).toBe(false);
    expect(sameJoinFilter({ kind: "preset", preset: "today" }, { kind: "preset", preset: "today" })).toBe(true);
    expect(
      sameJoinFilter(
        { kind: "custom", from: "2026-01-01", to: "2026-01-31" },
        { kind: "custom", from: "2026-01-01", to: "2026-01-31" },
      ),
    ).toBe(true);

    expect(toggleJoinFilter({ kind: "preset", preset: "today" }, { kind: "preset", preset: "today" })).toEqual({
      kind: "all",
    });
    expect(toggleJoinFilter({ kind: "custom", from: "a", to: "" }, { kind: "all" })).toEqual({ kind: "all" });

    expect(joinFilterLabel({ kind: "all" }, [])).toBe("All customers");
    expect(joinFilterLabel({ kind: "preset", preset: "last7" }, [])).toBe("Joined in the last 7 days");
    expect(joinFilterLabel({ kind: "custom", from: "2026-03-10", to: "2026-03-12" }, [])).toBe(
      "joined 2026-03-10 → 2026-03-12",
    );

    expect(joinFilterKey({ kind: "all" })).toBe("all");
    expect(joinFilterKey({ kind: "preset", preset: "today" })).toBe("preset:today");
    expect(joinFilterKey({ kind: "custom", from: " 2026-01-01 ", to: "2026-01-31" })).toBe(
      "custom:2026-01-01|2026-01-31",
    );
  });

  it("formats joined date labels", () => {
    expect(formatJoinedDateFilterLabel("", "")).toBeNull();
    expect(formatJoinedDateFilterLabel("2026-03-15", "2026-03-15")).toBe("joined 2026-03-15");
    expect(formatJoinedDateFilterLabel("2026-03-10", "2026-03-12")).toBe("joined 2026-03-10 → 2026-03-12");
    expect(formatJoinedDateFilterLabel("2026-03-12", "2026-03-10")).toBe("joined 2026-03-10 → 2026-03-12");
    expect(formatJoinedDateFilterLabel("2026-03-10", "")).toBe("joined from 2026-03-10");
    expect(formatJoinedDateFilterLabel("", "2026-03-10")).toBe("joined through 2026-03-10");
  });
});
