import { describe, expect, it } from "vitest";
import { isInvoiceAvailable } from "./invoice-availability";

describe("invoice availability", () => {
  it("hides invoices while online payment is still pending", () => {
    expect(
      isInvoiceAvailable({
        status: "pending",
        payment_status: "pending",
        payment_method: "online",
      }),
    ).toBe(false);
    expect(
      isInvoiceAvailable({
        status: " PENDING ",
        payment_status: " PENDING ",
        payment_method: "online",
      }),
    ).toBe(false);
    expect(isInvoiceAvailable({ status: "pending" })).toBe(false);
    expect(
      isInvoiceAvailable({ status: "pending", payment_status: null, payment_method: null }),
    ).toBe(false);
  });

  it("allows invoices for confirmed COD orders awaiting delivery payment", () => {
    expect(
      isInvoiceAvailable({
        status: "confirmed",
        payment_status: "pending",
        payment_method: "cod",
      }),
    ).toBe(true);
  });

  it("allows invoices after an order is confirmed", () => {
    expect(
      isInvoiceAvailable({
        status: "confirmed",
        payment_status: "paid",
        payment_method: "online",
      }),
    ).toBe(true);
    expect(isInvoiceAvailable({ status: "fulfilled", payment_status: "paid" })).toBe(true);
  });
});
