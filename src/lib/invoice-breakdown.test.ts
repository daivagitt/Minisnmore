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

describe("invoice workbook breakdown", () => {
  it("matches prepaid with shipping, including its +₹0.01 reconciliation", () => {
    const result = buildInvoiceBreakdown(order());
    expect(result).toMatchObject({
      taxKind: "local",
      taxableTotal: 338.31,
      cgst: 5.98,
      sgst: 5.98,
      totalAmountIncludingTax: 350.27,
      roundOff: 0.01,
      grandTotal: 350.28,
      mrpTotal: 349,
      productDiscount: 69.8,
      sellingPrice: 279.2,
      couponDiscount: 27.92,
      amountAfterCoupon: 251.28,
    });
  });

  it("matches COD and paid shipping", () => {
    const result = buildInvoiceBreakdown(order({ cod_fee: 99, tax: 16.68, total: 449.28 }));
    expect(result).toMatchObject({
      taxableTotal: 432.6,
      cgst: 8.34,
      sgst: 8.34,
      totalAmountIncludingTax: 449.28,
      roundOff: 0,
      grandTotal: 449.28,
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
      taxableTotal: 432.6,
      cgst: 0,
      sgst: 0,
      igst: 16.68,
      totalAmountIncludingTax: 449.28,
      roundOff: 0,
    });
  });
});
