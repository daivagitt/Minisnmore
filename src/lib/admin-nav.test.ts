import { describe, expect, it } from "vitest";
import { ADMIN_NAV_ITEMS, filterAdminNavItems } from "./admin-nav";

describe("admin-nav", () => {
  it("returns all items when the query is empty", () => {
    expect(filterAdminNavItems(ADMIN_NAV_ITEMS, "  ")).toEqual(ADMIN_NAV_ITEMS);
  });

  it("matches page labels and keywords", () => {
    const products = filterAdminNavItems(ADMIN_NAV_ITEMS, "sku");
    expect(products.map((item) => item.to)).toEqual(["/admin/products"]);

    const bogo = filterAdminNavItems(ADMIN_NAV_ITEMS, "bogo");
    expect(bogo.map((item) => item.to)).toEqual(["/admin/coupons"]);

    const reviews = filterAdminNavItems(ADMIN_NAV_ITEMS, "mom review");
    expect(reviews.map((item) => item.to)).toEqual(["/admin/home"]);

    const messages = filterAdminNavItems(ADMIN_NAV_ITEMS, "write to us");
    expect(messages.map((item) => item.to)).toEqual(["/admin/contact-messages"]);

    const users = filterAdminNavItems(ADMIN_NAV_ITEMS, "joined");
    expect(users.map((item) => item.to)).toEqual(["/admin/users"]);

    const rbac = filterAdminNavItems(ADMIN_NAV_ITEMS, "rbac");
    expect(rbac.map((item) => item.to)).toEqual(["/admin/rbac"]);
  });

  it("requires every token to match", () => {
    expect(filterAdminNavItems(ADMIN_NAV_ITEMS, "orders banana")).toEqual([]);
  });
});
