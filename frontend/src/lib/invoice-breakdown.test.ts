import { describe, expect, it } from "vitest";
import {
  buildInvoiceBreakdown,
  type InvoiceOrder,
  type InvoiceOrderItem,
} from "./invoice-breakdown";

const bodySuit: InvoiceOrderItem = {
  id: 1,
  product_name: "Body suit (Half sleeve)",
  size: "0-3 Months",
  sku: "MnM_BHS1_0-3",
  hsn: "61112000",
  qty: 1,
  mrp: 349,
  price: 279.2,
  tax_pct: 5,
};

const threeItems: InvoiceOrderItem[] = [
  bodySuit,
  { ...bodySuit, id: 2, product_name: "Reversible jacket", mrp: 959, price: 767.2 },
  { ...bodySuit, id: 3, product_name: "Gift set", mrp: 799, price: 639.2 },
];

function order(overrides: Partial<InvoiceOrder> = {}): InvoiceOrder {
  return {
    order_number: "MNM-TEST",
    subtotal: 279.2,
    discount: 27.92,
    shipping: 99,
    cod_fee: 0,
    tax: 11.97,
    total: 350.28,
    coupon_code: "WELCOME10",
    shipping_address: { state: "Karnataka" },
    order_items: [bodySuit],
    ...overrides,
  };
}

const shippingGstLine = {
  kind: "shipping",
  itemName: "Shipping",
  taxableAmount: 94.29,
  taxType: "CGST\nSGST",
  taxRates: [2.5, 2.5],
  taxAmounts: [2.35, 2.35],
  subTotal: 99,
};

describe("invoice workbook breakdown", () => {
  it("splits GST out of prepaid shipping the same way as COD", () => {
    const result = buildInvoiceBreakdown(order());
    expect(result).toMatchObject({
      taxKind: "local",
      taxableTotal: 333.6,
      cgst: 8.34,
      sgst: 8.34,
      totalAmountIncludingTax: 350.28,
      roundOff: 0,
      grandTotal: 350.28,
      mrpTotal: 349,
      productDiscount: 69.8,
      sellingPrice: 279.2,
      couponDiscount: 27.92,
      amountAfterCoupon: 251.28,
    });
    expect(result.lines.find((line) => line.kind === "shipping")).toMatchObject(shippingGstLine);
  });

  it("matches COD and paid shipping", () => {
    const result = buildInvoiceBreakdown(order({ cod_fee: 99, tax: 16.68, total: 449.28 }));
    expect(result).toMatchObject({
      taxableTotal: 427.89,
      cgst: 10.69,
      sgst: 10.69,
      totalAmountIncludingTax: 449.27,
      roundOff: 0.01,
      grandTotal: 449.28,
    });
    expect(result.lines.find((line) => line.kind === "shipping")).toMatchObject(shippingGstLine);
    expect(result.lines.find((line) => line.kind === "cod")).toMatchObject({
      kind: "cod",
      taxableAmount: 94.29,
      subTotal: 99,
    });
  });

  it("matches prepaid with free shipping", () => {
    const result = buildInvoiceBreakdown(
      order({
        subtotal: 1685.6,
        discount: 168.56,
        shipping: 0,
        tax: 72.24,
        total: 1517.04,
        order_items: threeItems,
      }),
    );
    expect(result).toMatchObject({
      taxableTotal: 1444.8,
      cgst: 36.12,
      sgst: 36.12,
      totalAmountIncludingTax: 1517.04,
      roundOff: 0,
    });
    expect(result.lines.some((line) => line.kind === "shipping")).toBe(false);
  });

  it("matches the workbook's -₹0.01 multi-item COD round-off", () => {
    const result = buildInvoiceBreakdown(
      order({
        subtotal: 1685.6,
        discount: 168.56,
        shipping: 0,
        cod_fee: 99,
        tax: 76.95,
        total: 1616.04,
        order_items: threeItems,
      }),
    );
    expect(result).toMatchObject({
      taxableTotal: 1539.09,
      cgst: 38.48,
      sgst: 38.48,
      totalAmountIncludingTax: 1616.05,
      roundOff: -0.01,
      grandTotal: 1616.04,
    });
  });

  it("uses IGST for an out-of-state address", () => {
    const result = buildInvoiceBreakdown(
      order({
        cod_fee: 99,
        tax: 16.68,
        total: 449.28,
        shipping_address: { state: "Tamil Nadu" },
      }),
    );
    expect(result).toMatchObject({
      taxKind: "interstate",
      taxableTotal: 427.89,
      cgst: 0,
      sgst: 0,
      igst: 21.39,
      totalAmountIncludingTax: 449.28,
      roundOff: 0,
    });
    expect(result.lines.find((line) => line.kind === "shipping")).toMatchObject({
      taxType: "IGST",
      taxRates: [5],
      taxAmounts: [4.71],
      subTotal: 99,
    });
  });
});
