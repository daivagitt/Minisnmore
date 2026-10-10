import { describe, expect, it } from "vitest";
import { ADMIN_NAV_ITEMS, filterAdminNavItems, visibleAdminNavItems } from "./admin-nav";

describe("admin-nav", () => {
  it("returns all items when the query is empty", () => {
    expect(filterAdminNavItems(ADMIN_NAV_ITEMS, "  ")).toEqual(ADMIN_NAV_ITEMS);
  });

  it("keeps M2 desks in the visible list while tests run", () => {
    expect(visibleAdminNavItems().some((item) => item.to === "/admin/cancellations")).toBe(true);
    expect(visibleAdminNavItems().some((item) => item.to === "/admin/returns")).toBe(true);
  });

  it("matches page labels and keywords", () => {
    const products = filterAdminNavItems(ADMIN_NAV_ITEMS, "sku");
    expect(products.map((item) => item.to)).toEqual(["/admin/products"]);

    const bogo = filterAdminNavItems(ADMIN_NAV_ITEMS, "bogo");
    expect(bogo.map((item) => item.to)).toEqual(["/admin/coupons"]);

    const reviews = filterAdminNavItems(ADMIN_NAV_ITEMS, "mom review");
    expect(reviews.map((item) => item.to)).toEqual(["/admin/home"]);

    const lovedNow = filterAdminNavItems(ADMIN_NAV_ITEMS, "loved right now");
    expect(lovedNow.map((item) => item.to)).toEqual(["/admin/home"]);

    const messages = filterAdminNavItems(ADMIN_NAV_ITEMS, "write to us");
    expect(messages.map((item) => item.to)).toEqual(["/admin/contact-messages"]);

    const gift = filterAdminNavItems(ADMIN_NAV_ITEMS, "gifting occasions");
    expect(gift.map((item) => item.to)).toEqual(["/admin/gift"]);

    const users = filterAdminNavItems(ADMIN_NAV_ITEMS, "joined");
    expect(users.map((item) => item.to)).toEqual(["/admin/customers"]);

    const usersAlias = filterAdminNavItems(ADMIN_NAV_ITEMS, "users");
    expect(usersAlias.map((item) => item.to)).toEqual(["/admin/customers"]);

    const rbac = filterAdminNavItems(ADMIN_NAV_ITEMS, "rbac");
    expect(rbac.map((item) => item.to)).toEqual(["/admin/rbac"]);

    const addresses = filterAdminNavItems(ADMIN_NAV_ITEMS, "warehouse invoice");
    expect(addresses.map((item) => item.to)).toEqual(["/admin/addresses"]);

    const packageTypes = filterAdminNavItems(ADMIN_NAV_ITEMS, "box type");
    expect(packageTypes.map((item) => item.to)).toEqual(["/admin/package-types"]);
  });

  it("requires every token to match", () => {
    expect(filterAdminNavItems(ADMIN_NAV_ITEMS, "orders banana")).toEqual([]);
  });
});
