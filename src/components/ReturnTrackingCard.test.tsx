/** @vitest-environment jsdom */
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ReturnTrackingCard } from "./ReturnTrackingCard";

vi.mock("@/components/CopyButton", () => ({
  CopyButton: () => null,
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
          original_size: "M",
          variant_sku: "DRS-M",
        }}
      />,
    );

    expect(screen.getByText("Dress (M · DRS-M)")).toBeInTheDocument();
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
});
