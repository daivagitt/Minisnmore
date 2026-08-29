import { describe, expect, it } from "vitest";
import { isInvoiceAvailable } from "./invoice-availability";

describe("invoice availability", () => {
  it("hides invoices while an order is pending", () => {
    expect(isInvoiceAvailable({ status: "pending" })).toBe(false);
    expect(isInvoiceAvailable({ status: " PENDING " })).toBe(false);
  });

  it("allows invoices after an order is confirmed", () => {
    expect(isInvoiceAvailable({ status: "confirmed" })).toBe(true);
    expect(isInvoiceAvailable({ status: "fulfilled" })).toBe(true);
  });
});
