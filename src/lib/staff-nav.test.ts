import { describe, expect, it } from "vitest";
import { STAFF_ROLE_NAV } from "./staff-nav";

describe("staff-nav", () => {
  it("limits customer support to the support desk", () => {
    expect(STAFF_ROLE_NAV.ROLE_CUSTOMER_SUPPORT.items.map((item) => item.to)).toEqual([
      "/customersupport/orders",
      "/customersupport/customers",
      "/customersupport/returns",
      "/customersupport/cancellations",
      "/customersupport/contact-messages",
    ]);
  });

  it("gives operations fulfillment and catalog desks", () => {
    expect(STAFF_ROLE_NAV.ROLE_OPERATIONS.items.map((item) => item.to)).toEqual([
      "/operations/dashboard",
      "/operations/orders",
      "/operations/returns",
      "/operations/cancellations",
      "/operations/invoices",
      "/operations/customers",
      "/operations/products",
      "/operations/contact-messages",
    ]);
  });
});
