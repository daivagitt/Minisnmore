import { describe, expect, it } from "vitest";
import { invoiceNumberFromOrder } from "./invoice-number";

describe("invoiceNumberFromOrder", () => {
  it("turns a store order number into a distinct invoice number", () => {
    expect(invoiceNumberFromOrder("MNM-C96E3FC21C76")).toBe("INV-C96E3FC21C76");
    expect(invoiceNumberFromOrder("MNM-PREVIEW-001")).toBe("INV-PREVIEW-001");
  });

  it("keeps an already-issued invoice number", () => {
    expect(invoiceNumberFromOrder("INV-C96E3FC21C76")).toBe("INV-C96E3FC21C76");
  });

  it("prefixes numbers that are not Mini order numbers", () => {
    expect(invoiceNumberFromOrder("42")).toBe("INV-42");
  });

  it("renders a placeholder when the order number is missing", () => {
    expect(invoiceNumberFromOrder("")).toBe("—");
    expect(invoiceNumberFromOrder(null)).toBe("—");
  });
});
