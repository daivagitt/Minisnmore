import { describe, expect, it, vi } from "vitest";
import { INVOICE_PRINT_CSS } from "@/components/InvoiceViewer";

vi.mock("@/hooks/useSiteSettings", () => ({
  useSiteSettings: () => ({}),
}));

describe("InvoiceViewer print CSS", () => {
  it("pins the invoice to one A4 page and hides surrounding chrome", () => {
    expect(INVOICE_PRINT_CSS).toContain("@page { size: 210mm 297mm portrait; margin: 0; }");
    expect(INVOICE_PRINT_CSS).toContain("height: 297mm !important");
    expect(INVOICE_PRINT_CSS).toContain("max-height: 297mm !important");
    expect(INVOICE_PRINT_CSS).toContain("overflow: hidden !important");
    expect(INVOICE_PRINT_CSS).toContain(
      ".invoice-print-root > :not(.invoice-document) { display: none !important; }",
    );
    expect(INVOICE_PRINT_CSS).not.toContain("height: auto !important");
    expect(INVOICE_PRINT_CSS).not.toContain("min-height: 297mm");
  });
});
