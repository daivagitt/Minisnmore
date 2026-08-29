import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { InvoiceOrderQr } from "@/components/InvoiceOrderQr";

describe("InvoiceOrderQr", () => {
  it("renders a scanner-friendly link using the configured store origin", () => {
    const { container } = render(
      <InvoiceOrderQr
        orderNumber="MNM-1001"
        invoiceWebsite="https://www.minisnmore.example"
        origin="https://uat.minisnmore.example"
      />,
    );

    const qrLink = container.querySelector('a[aria-label="View order MNM-1001"]');
    expect(qrLink).toHaveAttribute(
      "href",
      "https://www.minisnmore.example/account/orders/MNM-1001",
    );
    expect(qrLink?.querySelector("svg")).toBeTruthy();
  });

  it("does not render when the order number is blank", () => {
    const { container } = render(
      <InvoiceOrderQr orderNumber="   " origin="https://shop.example.com" />,
    );

    expect(container.querySelector("a")).toBeNull();
  });

  it("falls back to the current storefront origin when no website is configured", () => {
    const { container } = render(
      <InvoiceOrderQr orderNumber="MNM-2002" origin="https://uat.minisnmore.example" />,
    );

    expect(container.querySelector("a")).toHaveAttribute(
      "href",
      "https://uat.minisnmore.example/account/orders/MNM-2002",
    );
  });
});
