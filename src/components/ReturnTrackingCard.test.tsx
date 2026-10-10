/** @vitest-environment jsdom */
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ReturnTrackingCard } from "./ReturnTrackingCard";

vi.mock("@/components/CopyButton", () => ({
  CopyButton: () => null,
}));

vi.mock("@/hooks/useSiteSettings", () => ({
  useSiteSettings: () => ({
    refund_min_business_days: 2,
    refund_max_business_days: 5,
  }),
}));

describe("ReturnTrackingCard", () => {
  it("shows the return state machine and pickup tracking details", () => {
    render(
      <ReturnTrackingCard
        item={{
          return_number: "RET-1",
          order_number: "MNM-1",
          request_type: "return",
          status: "pickup_scheduled",
          reverse_awb: "REV-123",
          tracking_url: "https://track.example/REV-123",
          carrier_name: "XpressBees",
        }}
      />,
    );

    expect(screen.getAllByText("Pickup scheduled").length).toBeGreaterThan(0);
    expect(screen.getByRole("list", { name: "Return timeline" })).toBeInTheDocument();
    expect(screen.getByText("AWB:")).toBeInTheDocument();
    expect(screen.getByText("REV-123")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Track pickup" })).toHaveAttribute(
      "href",
      "https://track.example/REV-123",
    );
  });

  it("shows variant_sku in the product subtitle", () => {
    render(
      <ReturnTrackingCard
        item={{
          return_number: "RET-2",
          request_type: "return",
          status: "approved",
          product_name: "Dress",
          product_image: "https://cdn.example/dress.jpg",
          original_size: "M",
          variant_sku: "DRS-M",
        }}
      />,
    );

    expect(screen.getByText("Dress (M · DRS-M)")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Dress" })).toHaveAttribute(
      "src",
      "https://cdn.example/dress.jpg.thumb.jpg",
    );
  });

  it("shows uploaded return photos", () => {
    render(
      <ReturnTrackingCard
        item={{
          return_number: "RET-3",
          request_type: "return",
          status: "requested",
          image_urls: ["https://cdn.example/returns/damage.jpg"],
        }}
      />,
    );

    expect(screen.getByText("Photos")).toBeInTheDocument();
    expect(screen.getByAltText("Customer return photo")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Customer return photo" })).toHaveAttribute(
      "href",
      "https://cdn.example/returns/damage.jpg",
    );
  });

  it("shows the exchange timeline with replacement AWB and tracking link", () => {
    render(
      <ReturnTrackingCard
        item={{
          return_number: "EXG-1",
          request_type: "exchange",
          status: "shipped",
          outbound_awb: "OUT-456",
          outbound_tracking_url: "https://track.example/OUT-456",
          outbound_carrier_name: "XpressBees",
        }}
      />,
    );

    expect(screen.getAllByText("Replacement on the way").length).toBeGreaterThan(0);
    expect(screen.getByRole("list", { name: "Exchange timeline" })).toBeInTheDocument();
    expect(screen.getByText("OUT-456")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Track replacement" })).toHaveAttribute(
      "href",
      "https://track.example/OUT-456",
    );
  });

  it("shows a refund initiated alert when the refund has been triggered", () => {
    render(
      <ReturnTrackingCard
        item={{
          return_number: "RET-9",
          request_type: "return",
          status: "refunded",
        }}
      />,
    );

    expect(screen.getByRole("status")).toHaveTextContent("Refund initiated");
    expect(screen.getByRole("status")).not.toHaveTextContent("2–5 business days");
    expect(screen.getByRole("status")).toHaveTextContent("original payment method");
  });

  it("does not mark refund as current after the item is accepted", () => {
    render(
      <ReturnTrackingCard
        item={{
          return_number: "RET-8",
          request_type: "return",
          status: "accepted",
        }}
      />,
    );

    expect(screen.getAllByText("Quality check complete").length).toBeGreaterThan(0);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(screen.getByRole("listitem", { name: /Quality check complete: Current/i })).toBeInTheDocument();
    expect(screen.getByRole("listitem", { name: /Refund: Upcoming/i })).toBeInTheDocument();
  });

  it("shows the bank timeline only after the gateway marks the payment refunded", () => {
    render(
      <ReturnTrackingCard
        item={{
          return_number: "RET-10",
          request_type: "return",
          status: "refunded",
          order_payment_status: "refunded",
        }}
      />,
    );

    expect(screen.getAllByText("Refund processed").length).toBeGreaterThan(0);
    expect(screen.getByRole("status")).toHaveTextContent("Refund processed");
    expect(screen.getByRole("status")).toHaveTextContent("2–5 business days");
    expect(screen.getByRole("status")).toHaveTextContent("original payment source");
  });

  it("shows a declined return reason without a header", () => {
    render(
      <ReturnTrackingCard
        showHeader={false}
        item={{
          return_number: "RET-11",
          request_type: "return",
          status: "rejected",
          rejection_reason: "Tags missing and item appears used",
        }}
      />,
    );

    expect(
      screen.getByText("This return request was declined. Reason: Tags missing and item appears used"),
    ).toBeInTheDocument();
  });

  it("shows a declined exchange reason", () => {
    render(
      <ReturnTrackingCard
        item={{
          return_number: "EXG-11",
          request_type: "exchange",
          status: "rejected",
          rejection_reason: "Requested size is out of stock",
        }}
      />,
    );

    expect(
      screen.getByText("This exchange request was declined. Reason: Requested size is out of stock"),
    ).toBeInTheDocument();
  });
});
