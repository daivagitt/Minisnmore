import { describe, expect, it } from "vitest";
import {
  adminUserListFiltersActive,
  adminUserListFiltersKey,
  adminUserSearchHaystack,
  matchesAdminUserListFilters,
  userMatchesSearchQuery,
  userMatchesStatus,
  userMatchesSubscribed,
  type AdminUserFilterSource,
  type AdminUserListFilters,
} from "./user-filters";

const baseFilters = (overrides: Partial<AdminUserListFilters> = {}): AdminUserListFilters => ({
  query: "",
  status: "all",
  subscribed: "all",
  ...overrides,
});

const user = (overrides: Partial<AdminUserFilterSource> = {}): AdminUserFilterSource => ({
  login: "jane.doe",
  firstName: "Jane",
  lastName: "Doe",
  email: "jane@example.com",
  activated: true,
  subscribed: false,
  createdDate: "2026-03-15T10:00:00.000Z",
  ...overrides,
});

describe("adminUserSearchHaystack", () => {
  it("includes name, login, and email", () => {
    const haystack = adminUserSearchHaystack(user());
    expect(haystack).toContain("jane");
    expect(haystack).toContain("doe");
    expect(haystack).toContain("jane.doe");
    expect(haystack).toContain("jane@example.com");
  });
});

describe("userMatchesSearchQuery", () => {
  it("matches empty query", () => {
    expect(userMatchesSearchQuery(user(), "")).toBe(true);
    expect(userMatchesSearchQuery(user(), "   ")).toBe(true);
  });

  it("requires every token (AND)", () => {
    expect(userMatchesSearchQuery(user(), "jane example")).toBe(true);
    expect(userMatchesSearchQuery(user(), "jane missing")).toBe(false);
  });

  it("is case-insensitive and matches email or login", () => {
    expect(userMatchesSearchQuery(user(), "JANE@EXAMPLE.COM")).toBe(true);
    expect(userMatchesSearchQuery(user(), "Jane.Doe")).toBe(true);
  });
});

describe("userMatchesStatus", () => {
  it("filters active and pending", () => {
    expect(userMatchesStatus(user({ activated: true }), "all")).toBe(true);
    expect(userMatchesStatus(user({ activated: true }), "active")).toBe(true);
    expect(userMatchesStatus(user({ activated: true }), "pending")).toBe(false);
    expect(userMatchesStatus(user({ activated: false }), "pending")).toBe(true);
    expect(userMatchesStatus(user({ activated: undefined }), "pending")).toBe(true);
  });
});

describe("userMatchesSubscribed", () => {
  it("treats only explicit true as subscribed", () => {
    expect(userMatchesSubscribed(user({ subscribed: true }), "yes")).toBe(true);
    expect(userMatchesSubscribed(user({ subscribed: false }), "yes")).toBe(false);
    expect(userMatchesSubscribed(user({ subscribed: null }), "no")).toBe(true);
    expect(userMatchesSubscribed(user({ subscribed: undefined }), "no")).toBe(true);
  });
});

describe("matchesAdminUserListFilters", () => {
  it("combines query, status, and subscribed", () => {
    const row = user({
      activated: false,
      subscribed: true,
    });
    expect(
      matchesAdminUserListFilters(row, baseFilters({ query: "jane", status: "pending", subscribed: "yes" })),
    ).toBe(true);
    expect(
      matchesAdminUserListFilters(row, baseFilters({ query: "jane", status: "active", subscribed: "yes" })),
    ).toBe(false);
    expect(
      matchesAdminUserListFilters(row, baseFilters({ query: "missing", status: "pending", subscribed: "yes" })),
    ).toBe(false);
  });
});

describe("adminUserListFilters helpers", () => {
  it("detects active filters and builds a stable key", () => {
    expect(adminUserListFiltersActive(baseFilters())).toBe(false);
    expect(adminUserListFiltersActive(baseFilters({ query: "a" }))).toBe(true);
    expect(adminUserListFiltersActive(baseFilters({ status: "pending" }))).toBe(true);
    expect(adminUserListFiltersKey(baseFilters({ query: " Jane ", status: "active", subscribed: "yes" }))).toBe(
      "active|yes|jane",
    );
  });
});
