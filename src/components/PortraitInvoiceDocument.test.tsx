import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PortraitInvoiceDocument } from "@/components/PortraitInvoiceDocument";

describe("PortraitInvoiceDocument", () => {
  it("renders backend invoice settings, an order QR link and the calculation table", () => {
    const { container } = render(
      <PortraitInvoiceDocument
        order={{
          order_number: "MNM-1001",
          created_at: "2026-08-28T08:00:00Z",
          subtotal: 100,
          discount: 0,
          shipping: 0,
          cod_fee: 0,
          tax: 4.76,
          total: 100,
          tracking_number: "SHIP-1001",
          shipping_address: {
            full_name: "Sample Customer",
            line1: "12 Sample Road",
            city: "Bengaluru",
            state: "Karnataka",
            pincode: "560001",
          },
          order_items: [
            {
              id: 1,
              product_name: "Baby Body Suit",
              sku: "MNM-BS-01",
              hsn: "61112000",
              qty: 1,
              price: 100,
              mrp: 120,
              tax_pct: 5,
            },
          ],
        }}
        settings={{
          brand_name: "Mini's n More",
          legal_entity_name: "Stolt Global Private Limited",
          invoice_website: "https://www.minisnmore.example",
          invoice_gstin: "29ABCDE1234F1Z5",
          invoice_sold_by_address: "Seller address",
          invoice_dispatch_from_address: "Dispatch address",
          invoice_registered_office_address: "Registered office",
          invoice_cin: "U12345KA2026PTC123456",
          invoice_footer_note: "Thank you for shopping with us.",
          invoice_seller_state: "Maharashtra",
          gst_rate: 5,
          prices_include_gst: true,
        }}
      />,
    );

    const invoice = container.querySelector(".invoice-document");
    expect(invoice).toBeTruthy();
    expect(invoice).toHaveClass("min-h-[297mm]", "w-[210mm]");
    expect(invoice).toHaveTextContent("Tax Invoice");
    expect(invoice).toHaveTextContent("29-Karnataka");
    expect(invoice).toHaveTextContent("SHIP-1001");
    expect(invoice).toHaveTextContent("Seller address");
    expect(invoice).toHaveTextContent("Dispatch address");
    expect(invoice).toHaveTextContent("Registered office");
    expect(invoice).toHaveTextContent("Total IGST Amount");
    expect(invoice).toHaveTextContent("Grand Total (Inclusive of all taxes)");
    expect(container.querySelectorAll("article.invoice-document")).toHaveLength(1);

    const qrLink = container.querySelector('a[aria-label="View order MNM-1001"]');
    expect(qrLink).toHaveAttribute(
      "href",
      "https://www.minisnmore.example/account/orders/MNM-1001",
    );
    expect(qrLink?.querySelector("svg")).toBeTruthy();
  });
});
