/** @vitest-environment jsdom */
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./account.orders.$orderNumber";

const tracking = vi.hoisted(() => ({
  order: {
    order_number: "MNM-1",
    payment_status: "paid",
    payment_method: "online",
    status: "fulfilled",
    created_at: "2026-09-01T00:00:00Z",
    total: 999,
    shipping_address: { full_name: "Asha", line1: "1 Main", city: "Bengaluru", state: "KA", pincode: "560001" },
    order_items: [
      {
        id: 11,
        product_slug: "floral-dress",
        product_name: "Floral Cotton Dress",
        product_image: "https://cdn.example/dress.jpg",
        size: "2-3 yrs",
        color: "Pink",
        qty: 1,
        price: 999,
      },
    ],
  } as any,
  loading: false,
  error: null as string | null,
  refresh: vi.fn(),
}));

const listMyOrderReturns = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: object) => ({
    ...config,
    useParams: () => ({ orderNumber: "MNM-1" }),
    useSearch: () => ({ pay: false }),
  }),
  Link: ({ children, to, ...props }: any) => (
    <a href={typeof to === "string" ? to : "#"} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/hooks/useOrderTracking", () => ({
  useOrderTracking: () => tracking,
}));

vi.mock("@/hooks/useProducts", () => ({
  useProduct: () => undefined,
}));

vi.mock("@/lib/orders.functions", () => ({
  cancelOrder: vi.fn(),
  withdrawCancelRequest: vi.fn(),
  syncPaymentStatus: vi.fn(),
  retryOrderPayment: vi.fn(),
  verifyPayment: vi.fn(),
  checkReturnEligibility: vi.fn(async () => ({ eligible: false, message: "Return already open" })),
  listMyOrderReturns: (...args: unknown[]) => listMyOrderReturns(...args),
  requestOrderReturn: vi.fn(),
  requestOrderExchange: vi.fn(),
  uploadReturnImage: vi.fn(),
  MAX_RETURN_IMAGES: 5,
}));

vi.mock("@/lib/razorpay", () => ({ openRazorpayCheckout: vi.fn() }));
vi.mock("@/lib/invoice-availability", () => ({ isInvoiceAvailable: () => false }));
vi.mock("@/components/LazyImage", () => ({
  LazyImage: ({ src, alt }: { src: string; alt?: string }) => <img src={src} alt={alt ?? ""} />,
}));
vi.mock("@/components/CopyButton", () => ({ CopyButton: () => null }));
vi.mock("@/components/OrderTrackingCard", () => ({ OrderTrackingCard: () => null }));
vi.mock("sonner", () => ({ toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }) }));

const OrderDetail = (Route as { component: React.ComponentType }).component;

describe("order return tracking", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    tracking.order.status = "fulfilled";
    tracking.order.can_cancel = false;
    tracking.order.contact_care_to_cancel = false;
    tracking.order.pending_cancellation = false;
    tracking.order.packed_at = undefined;
    tracking.order.cancellation_request = undefined;
  });

  it("shows the return state machine with AWB and tracking link", async () => {
    listMyOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "RET-1",
          order_number: "MNM-1",
          request_type: "return",
          status: "pickup_scheduled",
          reason: "Product arrived damaged in transit",
          requested_at: "2026-09-20T00:00:00Z",
          reverse_awb: "REV-123",
          tracking_url: "https://track.example/REV-123",
          product_name: "Floral Cotton Dress",
          product_image: "https://cdn.example/dress.jpg",
        },
      ],
    });

    render(<OrderDetail />);

    const trigger = await screen.findByRole("button", { name: /Return RET-1/i });
    expect(trigger).toBeInTheDocument();
    expect(screen.getByText("Pickup AWB REV-123")).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "Return timeline" })).not.toBeInTheDocument();

    fireEvent.click(trigger);

    await waitFor(() => expect(screen.getByRole("list", { name: "Return timeline" })).toBeInTheDocument());
    expect(screen.getAllByText("Pickup scheduled").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/REV-123/).length).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: "Track pickup" })).toHaveAttribute(
      "href",
      "https://track.example/REV-123",
    );
  });

  it("shows reverse pickup AWB and a tracking link when only the AWB is stored", async () => {
    listMyOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "RET-2",
          order_number: "MNM-1",
          request_type: "return",
          status: "pickup_scheduled",
          requested_at: "2026-09-20T00:00:00Z",
          reverse_awb: "XB9876543210",
          carrier_name: "XpressBees",
        },
      ],
    });

    render(<OrderDetail />);

    expect(await screen.findByText("Pickup AWB XB9876543210")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Return RET-2/i }));
    expect(await screen.findByRole("link", { name: "Track pickup" })).toHaveAttribute(
      "href",
      "https://shipmentv2.xpressbees.com/orders/tracking/XB9876543210",
    );
  });

  it("keeps delivery tracking collapsed until opened", async () => {
    listMyOrderReturns.mockResolvedValue({ returns: [] });
    render(<OrderDetail />);

    const trigger = await screen.findByRole("button", { name: /Delivery tracking/i });
    expect(trigger).toBeInTheDocument();
  });

  it("lets packed orders request cancellation until dispatched", async () => {
    tracking.order.status = "hand_wrapped";
    tracking.order.can_cancel = true;
    tracking.order.contact_care_to_cancel = false;
    tracking.order.packed_at = "2026-09-30T00:00:00Z";
    listMyOrderReturns.mockResolvedValue({ returns: [] });
    render(<OrderDetail />);

    expect(await screen.findByRole("button", { name: /Request cancellation/i })).toBeInTheDocument();
    expect(screen.queryByText("Please contact Customer Care")).not.toBeInTheDocument();
  });

  it("shows the admin return decline reason", async () => {
    listMyOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "RET-9",
          order_number: "MNM-1",
          request_type: "return",
          status: "rejected",
          rejection_reason: "Return window of 7 days has expired",
          requested_at: "2026-09-20T00:00:00Z",
        },
      ],
    });

    render(<OrderDetail />);

    expect(
      await screen.findByText("This return request was declined. Reason: Return window of 7 days has expired"),
    ).toBeInTheDocument();
  });

  it("shows the admin exchange reject reason", async () => {
    listMyOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXG-9",
          order_number: "MNM-1",
          request_type: "exchange",
          status: "rejected",
          rejection_reason: "Requested size is currently out of stock",
          requested_at: "2026-09-20T00:00:00Z",
        },
      ],
    });

    render(<OrderDetail />);

    expect(
      await screen.findByText("This exchange request was declined. Reason: Requested size is currently out of stock"),
    ).toBeInTheDocument();
  });

  it("shows the admin cancellation decline reason", async () => {
    tracking.order.pending_cancellation = false;
    tracking.order.cancellation_request = {
      status: "rejected",
      rejection_reason: "Order is already packed and cannot be cancelled",
    };
    listMyOrderReturns.mockResolvedValue({ returns: [] });

    render(<OrderDetail />);

    expect(await screen.findByText("Cancellation request declined")).toBeInTheDocument();
    expect(
      screen.getByText("Reason: Order is already packed and cannot be cancelled"),
    ).toBeInTheDocument();
  });
});
