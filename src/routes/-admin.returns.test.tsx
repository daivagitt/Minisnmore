/** @vitest-environment jsdom */
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.returns";

const listAdminOrderReturns = vi.hoisted(() => vi.fn());

const getAdminOrderReturnsSummary = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/lib/orders.functions", () => ({
  listAdminOrderReturns: (...args: unknown[]) => listAdminOrderReturns(...args),
  getAdminOrderReturnsSummary: (...args: unknown[]) => getAdminOrderReturnsSummary(...args),
  EMPTY_ADMIN_RETURNS_SUMMARY: {
    total: 0,
    returns: 0,
    exchanges: 0,
    open: 0,
    closed: 0,
    overdue: 0,
    stages: { request: 0, pickup: 0, inspection: 0, exchange: 0, closed: 0 },
  },
  acceptOrderReturnAfterInspection: vi.fn(),
  allocateReplacementForOrderReturn: vi.fn(),
  approveOrderReturn: vi.fn(),
  bookOrderReturnPickup: vi.fn(),
  bookOrderReturnOutbound: vi.fn(),
  completeOrderReturnExchange: vi.fn(),
  closeFailedOrderReturnPickup: vi.fn(),
  convertOrderReturnToRefund: vi.fn(),
  markOrderReturnDelivered: vi.fn(),
  markOrderReturnInspected: vi.fn(),
  markOrderReturnPickupFailed: vi.fn(),
  markOrderReturnPickedUp: vi.fn(),
  markOrderReturnPickupRequested: vi.fn(),
  markOrderReturnReadyToShip: vi.fn(),
  markOrderReturnReceived: vi.fn(),
  markOrderReturnRepacking: vi.fn(),
  markOrderReturnShipped: vi.fn(),
  recordOrderReturnCodRefund: vi.fn(),
  refundOrderReturn: vi.fn(),
  rejectOrderReturn: vi.fn(),
  rescheduleOrderReturnPickup: vi.fn(),
  settleOrderReturnPriceDifference: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("@/lib/ui-errors", () => ({
  showApiError: vi.fn(),
}));

function renderAdminReturns() {
  const Component = (Route as { component: React.ComponentType }).component;
  return render(<Component />);
}

describe("admin returns", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getAdminOrderReturnsSummary.mockResolvedValue({
      total: 1,
      returns: 1,
      exchanges: 0,
      open: 1,
      closed: 0,
      overdue: 0,
      stages: { request: 1, pickup: 0, inspection: 0, exchange: 0, closed: 0 },
    });
  });

  it("shows print label when an outbound shipping label is available", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXG-1",
          order_number: "MNM-1",
          request_type: "exchange",
          status: "shipped",
          reason: "Wrong size ordered by mistake",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
          outbound_awb: "OUT-456",
          outbound_tracking_url: "https://track.example/OUT-456",
          outbound_label_url: "https://label.example/OUT-456",
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("link", { name: /print label/i })).toBeInTheDocument());
    expect(screen.getByRole("link", { name: /print label/i })).toHaveAttribute(
      "href",
      "https://label.example/OUT-456",
    );
    expect(screen.getByRole("link", { name: "Track" })).toHaveAttribute("href", "https://track.example/OUT-456");
  });

  it("shows customer-uploaded return photos", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "RET-1",
          order_number: "MNM-1",
          request_type: "return",
          status: "requested",
          reason: "Product arrived damaged in transit",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
          image_urls: ["https://cdn.example/returns/damage.jpg"],
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByText("Customer photos")).toBeInTheDocument());
    expect(screen.getByAltText("Customer return photo")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Customer return photo" })).toHaveAttribute(
      "href",
      "https://cdn.example/returns/damage.jpg",
    );
  });

  it("shows customer-uploaded exchange photos", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXG-2",
          order_number: "MNM-2",
          request_type: "exchange",
          status: "requested",
          reason: "Need a larger size for my baby",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
          image_urls: ["https://cdn.example/returns/fit.jpg"],
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByAltText("Customer exchange photo")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Customer exchange photo" })).toHaveAttribute(
      "href",
      "https://cdn.example/returns/fit.jpg",
    );
  });

  it("highlights product name, customer reason, refund, and price difference", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "RET-MNM-EX-1",
          order_number: "MNM-EX-1",
          request_type: "exchange",
          status: "pickup_scheduled",
          reason: "hey this is not fit",
          requested_at: "2026-09-29T13:30:00Z",
          customer_login: "saikedar2003@gmail.com",
          order_total: 4,
          product_name: "Body Suit (Half Sleeve)",
          product_image: "https://cdn.example/products/bodysuit.jpg",
          original_size: "6-12 M",
          original_color: "Snail",
          sku: "MN-12-612M-P01-173",
          replacement_size: "6-12 M",
          replacement_color: "Snail",
          price_difference: 0,
          settlement_status: "not_required",
          items: [
            {
              id: 11,
              order_item_id: 21,
              product_name: "Body Suit (Half Sleeve)",
              product_image: "https://cdn.example/products/bodysuit.jpg",
              size: "6-12 M",
              color: "Snail",
              sku: "MN-12-612M-P01-173",
              requested_qty: 1,
              refund_amount: 3,
            },
          ],
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByText("Customer reason")).toBeInTheDocument());
    expect(screen.getByText("Body Suit (Half Sleeve)")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Body Suit (Half Sleeve)" })).toHaveAttribute(
      "src",
      "https://cdn.example/products/bodysuit.jpg",
    );
    expect(screen.getByText("6-12 M · Snail · MN-12-612M-P01-173")).toBeInTheDocument();
    expect(screen.getByText("Exchange for 6-12 M · Snail")).toBeInTheDocument();
    expect(screen.getByText(/refund ₹3/)).toBeInTheDocument();
    expect(screen.getByText("Price difference")).toBeInTheDocument();
    expect(screen.getByText(/not required/i)).toBeInTheDocument();
    expect(screen.getByText("hey this is not fit")).toBeInTheDocument();
    expect(screen.getByText("pickup scheduled")).toBeInTheDocument();
  });

  it("shows a returns and exchanges summary", async () => {
    getAdminOrderReturnsSummary.mockResolvedValue({
      total: 12,
      returns: 8,
      exchanges: 4,
      open: 7,
      closed: 5,
      overdue: 2,
      stages: { request: 3, pickup: 2, inspection: 1, exchange: 1, closed: 5 },
    });
    listAdminOrderReturns.mockResolvedValue({ returns: [] });

    renderAdminReturns();

    await waitFor(() =>
      expect(screen.getByText(/There are 8 returns and 4 exchanges/)).toBeInTheDocument(),
    );
    expect(screen.getByRole("button", { name: "8 returns" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "4 exchanges" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "7 open" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Request/ })).toHaveTextContent("3");
  });
});
