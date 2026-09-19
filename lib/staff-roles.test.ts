import { describe, expect, it } from "vitest";
import {
  assignableRoles,
  canManageStaff,
  isStaffInvoicePath,
  primaryStaffRole,
  staffHomePath,
} from "./staff-roles";

describe("staff-roles", () => {
  it("picks Super Admin over Admin", () => {
    expect(primaryStaffRole(["ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN"])).toBe("ROLE_SUPER_ADMIN");
  });

  it("routes staff to their home", () => {
    expect(staffHomePath("ROLE_ADMIN")).toBe("/admin");
    expect(staffHomePath("ROLE_SALES")).toBe("/sales/products");
    expect(staffHomePath("ROLE_OPERATIONS")).toBe("/operations/dashboard");
    expect(staffHomePath("ROLE_CUSTOMER_SUPPORT")).toBe("/customersupport/orders");
  });

  it("detects staff invoice routes", () => {
    expect(isStaffInvoicePath("/operations/orders/MNM-1/invoice")).toBe(true);
    expect(isStaffInvoicePath("/account/orders/MNM-1/invoice")).toBe(false);
  });

  it("limits Admin invite targets", () => {
    expect(assignableRoles("ROLE_ADMIN")).toEqual([
      "ROLE_SALES",
      "ROLE_OPERATIONS",
      "ROLE_CUSTOMER_SUPPORT",
    ]);
    expect(canManageStaff("ROLE_ADMIN", "ROLE_ADMIN")).toBe(false);
    expect(canManageStaff("ROLE_SUPER_ADMIN", "ROLE_ADMIN")).toBe(true);
  });
});
