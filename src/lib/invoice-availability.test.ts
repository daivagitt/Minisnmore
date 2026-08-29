import { describe, expect, it } from "vitest";
import { isInvoiceAvailable } from "./invoice-availability";

describe("invoice availability", () => {
  it("hides invoices while an order is pending", () => {
    expect(isInvoiceAvailable({ status: "pending" })).toBe(false);
    expect(isInvoiceAvailable({ status: " PENDING " })).toBe(false);
  });

  it("allows invoices for confirmed online orders", () => {
    expect(
      isInvoiceAvailable({
        status: "confirmed",
        payment_method: "online",
        payment_status: "paid",
      }),
    ).toBe(true);
    expect(isInvoiceAvailable({ status: "fulfilled", payment_status: "paid" })).toBe(true);
  });

  it("hides invoices for confirmed unpaid COD orders until collection", () => {
    expect(
      isInvoiceAvailable({
        status: "confirmed",
        payment_method: "cod",
        payment_status: "pending",
      }),
    ).toBe(false);
  });

  it("allows invoices after COD cash is collected", () => {
    expect(
      isInvoiceAvailable({
        status: "fulfilled",
        payment_method: "cod",
        payment_status: "paid",
      }),
    ).toBe(true);
  });
});
