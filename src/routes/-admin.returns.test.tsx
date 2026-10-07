/** @vitest-environment jsdom */
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.returns";

const listAdminOrderReturns = vi.hoisted(() => vi.fn());

const getAdminOrderReturnsSummary = vi.hoisted(() => vi.fn());

const settleOrderReturnPriceDifference = vi.hoisted(() => vi.fn());

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
    xpressbeesReverseReady: 0,
  },
  acceptOrderReturnAfterInspection: vi.fn(),
  allocateReplacementForOrderReturn: vi.fn(),
  approveOrderReturn: vi.fn(),
  bookOrderReturnPickup: vi.fn(),
  bookOrderReturnOutbound: vi.fn(),
  completeOrderReturnPackage: vi.fn(),
  previewOrderReturnPackageRates: vi.fn(),
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
  settleOrderReturnPriceDifference: (...args: unknown[]) => settleOrderReturnPriceDifference(...args),
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
      xpressbeesReverseReady: 0,
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

  it("tells staff that delivery and COD fees are not refunded", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "RET-1",
          order_number: "MNM-1",
          request_type: "return",
          status: "accepted",
          reason: "Product arrived damaged in transit",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 1149,
          order_shipping: 99,
          order_cod_fee: 40,
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByText(/delivery ₹99 is not refunded/i)).toBeInTheDocument());
    expect(screen.getByText(/cod fee ₹40 is not refunded/i)).toBeInTheDocument();
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

  it("loads requested cases by default", async () => {
    listAdminOrderReturns.mockResolvedValue({ returns: [] });
    renderAdminReturns();
    await waitFor(() => expect(listAdminOrderReturns).toHaveBeenCalledWith("requested"));
    expect(screen.getByRole("button", { name: "Requested" })).toBeInTheDocument();
  });

  it("shows a returns and exchanges summary", async () => {
    getAdminOrderReturnsSummary.mockResolvedValue({
      total: 12,
      returns: 8,
      exchanges: 4,
      open: 7,
      closed: 5,
      overdue: 2,
      xpressbeesReverseReady: 4,
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
    expect(screen.getByRole("button", { name: "4 book reverse pickup" })).toBeInTheDocument();
    expect(
      within(screen.getByRole("group", { name: "Filter by stage" })).getByRole("button", {
        name: /Request/,
      }),
    ).toHaveTextContent("3");
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Filter by return status")).not.toBeInTheDocument();
  });

  it("shows stage statuses as pills after a parent stage is clicked", async () => {
    getAdminOrderReturnsSummary.mockResolvedValue({
      total: 12,
      returns: 8,
      exchanges: 4,
      open: 7,
      closed: 5,
      overdue: 2,
      xpressbeesReverseReady: 4,
      stages: { request: 3, pickup: 2, inspection: 1, exchange: 1, closed: 5 },
    });
    listAdminOrderReturns.mockResolvedValue({ returns: [] });
    const user = userEvent.setup();

    renderAdminReturns();

    const stageGroup = screen.getByRole("group", { name: "Filter by stage" });
    await waitFor(() => expect(within(stageGroup).getByRole("button", { name: /Request/ })).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Requested" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Shipped" })).not.toBeInTheDocument();

    await user.click(within(stageGroup).getByRole("button", { name: /^All/ }));
    expect(screen.queryByRole("button", { name: "Requested" })).not.toBeInTheDocument();

    await user.click(within(stageGroup).getByRole("button", { name: /Request/ }));
    expect(screen.getByRole("button", { name: "Requested" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approved" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Shipped" })).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();

    await user.click(within(stageGroup).getByRole("button", { name: /Exchange/ }));
    expect(screen.getByRole("button", { name: "Item allocated" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Shipped" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Requested" })).not.toBeInTheDocument();
  });

  it("does not offer mark received on an approved return until pickup is booked", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "RET-1",
          order_number: "MNM-1",
          request_type: "return",
          status: "approved",
          reason: "Product arrived damaged in transit",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("button", { name: /book pickup/i })).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: /mark received/i })).not.toBeInTheDocument();
  });

  it("offers mark received after reverse pickup shipment exists", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "RET-1",
          order_number: "MNM-1",
          request_type: "return",
          status: "picked_up",
          reason: "Product arrived damaged in transit",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
          reverse_awb: "AWB-REV-1",
          logistics_mode: "manual",
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("button", { name: /mark received/i })).toBeInTheDocument());
  });

  it("hides mark received on picked up returns with no shipment", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "RET-1",
          order_number: "MNM-1",
          request_type: "return",
          status: "picked_up",
          reason: "Product arrived damaged in transit",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByText(/picked up/i)).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: /mark received/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /book pickup/i })).toBeInTheDocument();
  });

  it("does not offer mark received on a scheduled exchange until the item is picked up", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXC-1",
          order_number: "MNM-1",
          request_type: "exchange",
          status: "pickup_scheduled",
          reason: "Wrong size, exchanging for Medium",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
          reverse_awb: "AWB-REV-1",
          logistics_mode: "manual",
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("button", { name: /mark picked up/i })).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: /mark received/i })).not.toBeInTheDocument();
  });

  it("offers mark received on a picked up exchange after reverse pickup shipment exists", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXC-1",
          order_number: "MNM-1",
          request_type: "exchange",
          status: "picked_up",
          reason: "Wrong size, exchanging for Medium",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
          reverse_awb: "AWB-REV-1",
          logistics_mode: "manual",
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("button", { name: /mark received/i })).toBeInTheDocument());
  });

  it("offers create package for an exchange that is ready to ship", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXC-1",
          order_number: "MNM-1",
          request_type: "exchange",
          status: "ready_to_ship",
          reason: "Wrong size ordered by mistake",
          requested_at: "2026-10-02T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
          product_name: "Body Suit",
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("button", { name: /create package/i })).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: /book shipment/i })).not.toBeInTheDocument();
  });

  it("offers book shipment after the exchange package exists", async () => {
    const user = userEvent.setup();
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXC-1",
          order_number: "MNM-1",
          request_type: "exchange",
          status: "ready_to_ship",
          reason: "Wrong size ordered by mistake",
          requested_at: "2026-10-02T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
          product_name: "Body Suit",
          packed_at: "2026-10-02T01:00:00Z",
          package_weight_grams: 500,
          package_length_cm: 12,
          package_breadth_cm: 10,
          package_height_cm: 8,
          can_book_outbound: true,
        },
      ],
    });

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("button", { name: /book shipment/i })).toBeInTheDocument());
    expect(screen.getByRole("button", { name: /update package/i })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /book shipment/i }));
    expect(screen.getByText("New replacement shipment")).toBeInTheDocument();
    expect(screen.getByText("Validate Address")).toBeInTheDocument();
  });

  it("refunds a cheaper prepaid replacement through Razorpay without a manual reference", async () => {
    settleOrderReturnPriceDifference.mockResolvedValue({
      return_number: "EXC-CHEAP-1",
      settlement_status: "refunded",
    });
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXC-CHEAP-1",
          order_number: "MNM-1",
          request_type: "exchange",
          status: "accepted",
          reason: "Wrong size, exchanging for a cheaper colour",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 1299,
          payment_method: "online",
          price_difference: -300,
          settlement_status: "pending",
          can_razorpay_refund_price_difference: true,
        },
      ],
    });
    const user = userEvent.setup();

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("button", { name: /refund via razorpay/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /refund via razorpay/i }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/original prepaid payment via Razorpay/i)).toBeInTheDocument();
    expect(within(dialog).queryByText("Transaction reference")).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: /refund via razorpay/i }));
    await waitFor(() =>
      expect(settleOrderReturnPriceDifference).toHaveBeenCalledWith("EXC-CHEAP-1", {
        status: "refunded",
        amount: 300,
        method: undefined,
        reference: undefined,
      }),
    );
  });

  it("shows manual settlement fields when Razorpay auto-refund is not available", async () => {
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXC-MAN-1",
          order_number: "MNM-2",
          request_type: "exchange",
          status: "accepted",
          reason: "Cheaper replacement",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
          payment_method: "online",
          price_difference: -200,
          settlement_status: "pending",
          can_razorpay_refund_price_difference: false,
        },
      ],
    });
    const user = userEvent.setup();

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("button", { name: /settle price difference/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /settle price difference/i }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Transaction reference")).toBeInTheDocument();
    expect(within(dialog).queryByText(/original prepaid payment via Razorpay/i)).not.toBeInTheDocument();
  });

  it("records a prepaid cheaper replacement manually when Razorpay is overridden", async () => {
    settleOrderReturnPriceDifference.mockResolvedValue({
      return_number: "EXC-CHEAP-1",
      settlement_status: "refunded",
    });
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXC-CHEAP-1",
          order_number: "MNM-1",
          request_type: "exchange",
          status: "accepted",
          reason: "Wrong size, exchanging for a cheaper colour",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 1299,
          payment_method: "online",
          price_difference: -300,
          settlement_status: "pending",
          can_razorpay_refund_price_difference: true,
        },
      ],
    });
    const user = userEvent.setup();

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("button", { name: /refund via razorpay/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /refund via razorpay/i }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByLabelText(/record manually instead/i));
    await user.type(within(dialog).getByLabelText(/^method$/i), "UPI");
    await user.type(within(dialog).getByLabelText(/transaction reference/i), "UTR-OUTAGE-1");
    await user.click(within(dialog).getByRole("button", { name: /record settlement/i }));
    await waitFor(() =>
      expect(settleOrderReturnPriceDifference).toHaveBeenCalledWith("EXC-CHEAP-1", {
        status: "refunded",
        amount: 300,
        method: "UPI",
        reference: "UTR-OUTAGE-1",
        force_manual: true,
      }),
    );
  });

  it("sends method and reference when collecting a higher-price exchange difference", async () => {
    settleOrderReturnPriceDifference.mockResolvedValue({
      return_number: "EXC-COLLECT-1",
      settlement_status: "collected",
    });
    listAdminOrderReturns.mockResolvedValue({
      returns: [
        {
          return_number: "EXC-COLLECT-1",
          order_number: "MNM-7C691DO23016",
          request_type: "exchange",
          status: "accepted",
          reason: "Size exchange, customer pays the difference",
          requested_at: "2026-09-20T00:00:00Z",
          customer_login: "asha",
          order_total: 999,
          payment_method: "online",
          price_difference: 299.2,
          settlement_status: "pending",
        },
      ],
    });
    const user = userEvent.setup();

    renderAdminReturns();

    await waitFor(() => expect(screen.getByRole("button", { name: /settle price difference/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /settle price difference/i }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/^method$/i), "UPI");
    await user.type(within(dialog).getByLabelText(/transaction reference/i), "UTR-COLLECT-1");
    await user.click(within(dialog).getByRole("button", { name: /record settlement/i }));
    await waitFor(() =>
      expect(settleOrderReturnPriceDifference).toHaveBeenCalledWith("EXC-COLLECT-1", {
        status: "collected",
        amount: 299.2,
        method: "UPI",
        reference: "UTR-COLLECT-1",
      }),
    );
  });
});
