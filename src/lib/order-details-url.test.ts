import { describe, expect, it } from "vitest";
import { invoiceQrOrigin, orderDetailsPath, orderDetailsUrl } from "./order-details-url";

describe("invoice order details URL", () => {
  it("builds the customer order route", () => {
    expect(orderDetailsPath("MNM-1001")).toBe("/account/orders/MNM-1001");
  });

  it("creates an absolute scanner-friendly URL and safely encodes the order number", () => {
    expect(orderDetailsUrl(" MNM/1001 ", "https://shop.example.com/")).toBe(
      "https://shop.example.com/account/orders/MNM%2F1001",
    );
  });

  it("prefers the configured invoice website origin for printed QR codes", () => {
    expect(
      invoiceQrOrigin("https://www.minisnmore.example", "https://uat.minisnmore.example"),
    ).toBe("https://www.minisnmore.example");
  });

  it("falls back to the runtime origin when invoice website is missing", () => {
    expect(invoiceQrOrigin(null, "https://uat.minisnmore.example")).toBe(
      "https://uat.minisnmore.example",
    );
  });

  it("rejects non-web schemes and malformed runtime origins", () => {
    expect(invoiceQrOrigin("javascript:alert(1)", "https://shop.example.com")).toBe(
      "https://shop.example.com",
    );
    expect(invoiceQrOrigin("mailto:care@example.com", "not a URL")).toBe("");
  });
});
