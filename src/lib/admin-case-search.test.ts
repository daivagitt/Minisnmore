import { describe, expect, it } from "vitest";
import {
  adminCaseListFetchStatus,
  adminCaseSearchActive,
  matchesLocalDateRange,
  matchesOrderQuery,
} from "./admin-case-search";

describe("admin case search", () => {
  it("matches order number or numeric order id", () => {
    expect(matchesOrderQuery("", { orderNumber: "MNM-100", orderId: 42 })).toBe(true);
    expect(matchesOrderQuery("mnm-1", { orderNumber: "MNM-100", orderId: 42 })).toBe(true);
    expect(matchesOrderQuery("42", { orderNumber: "MNM-100", orderId: 42 })).toBe(true);
    expect(matchesOrderQuery("99", { orderNumber: "MNM-100", orderId: 42 })).toBe(false);
  });

  it("does not treat an incidental request id as the order id", () => {
    expect(matchesOrderQuery("7", { orderNumber: "MNM-100", orderId: 42 })).toBe(false);
  });

  it("filters ISO timestamps by local date inputs", () => {
    const noon = new Date(2026, 9, 7, 12, 0, 0).toISOString();
    expect(matchesLocalDateRange(noon, "2026-10-07", "2026-10-07")).toBe(true);
    expect(matchesLocalDateRange(noon, "2026-10-08", "")).toBe(false);
    expect(matchesLocalDateRange(noon, "", "2026-10-06")).toBe(false);
    expect(matchesLocalDateRange(noon, "", "")).toBe(true);
    expect(matchesLocalDateRange(null, "2026-10-07", "")).toBe(false);
  });

  it("treats any filled search field as active", () => {
    expect(adminCaseSearchActive("", "", "")).toBe(false);
    expect(adminCaseSearchActive(" MNM ", "", "")).toBe(true);
    expect(adminCaseSearchActive("", "2026-10-01", "")).toBe(true);
  });

  it("drops the status query while staff are searching", () => {
    expect(adminCaseListFetchStatus(false, "requested")).toBe("requested");
    expect(adminCaseListFetchStatus(false, "")).toBeUndefined();
    expect(adminCaseListFetchStatus(true, "requested")).toBeUndefined();
  });
});
