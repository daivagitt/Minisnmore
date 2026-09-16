import { describe, expect, it } from "vitest";
import { ADMIN_NAV_ITEMS, filterAdminNavItems } from "./admin-nav";

describe("admin-nav", () => {
  it("returns all items when the query is empty", () => {
    expect(filterAdminNavItems(ADMIN_NAV_ITEMS, "  ")).toEqual(ADMIN_NAV_ITEMS);
  });

  it("matches page labels and keywords", () => {
    const products = filterAdminNavItems(ADMIN_NAV_ITEMS, "sku");
    expect(products.map((item) => item.to)).toEqual(["/admin/products"]);

    const reviews = filterAdminNavItems(ADMIN_NAV_ITEMS, "mom review");
    expect(reviews.map((item) => item.to)).toEqual(["/admin/home"]);
  });

  it("requires every token to match", () => {
    expect(filterAdminNavItems(ADMIN_NAV_ITEMS, "orders banana")).toEqual([]);
  });
});
