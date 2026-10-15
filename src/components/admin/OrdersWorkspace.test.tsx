/** @vitest-environment jsdom */
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { OrdersWorkspace } from "./OrdersWorkspace";

const listAdminOrders = vi.hoisted(() => vi.fn());
const cancelAdminOrder = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
}));

vi.mock("@/lib/orders.functions", async () => {
  const actual = await vi.importActual<typeof import("@/lib/orders.functions")>("@/lib/orders.functions");
  return {
    ...actual,
    listAdminOrders: (...args: unknown[]) => listAdminOrders(...args),
    markCodCollected: vi.fn(),
    cancelAdminOrder: (...args: unknown[]) => cancelAdminOrder(...args),
    cancelAdminOrderShipment: vi.fn(),
  };
});

vi.mock("@/lib/feature-flags", () => ({
  SHOW_M2_POST_ORDER_UI: true,
}));

vi.mock("@/lib/backend-data", () => ({
  formatZohoSyncFailures: vi.fn(() => []),
  formatZohoSyncSummary: vi.fn(() => "ok"),
  retryZohoOrders: vi.fn(),
  syncZohoOrderByNumber: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), message: vi.fn() },
}));

vi.mock("@/lib/ui-errors", () => ({
  showApiError: vi.fn(),
}));

describe("OrdersWorkspace", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
    listAdminOrders.mockResolvedValue({
      orders: [
        {
          order_number: "MNM-100",
          full_name: "Asha",
          phone: "9999999999",
          city: "Bengaluru",
          total: 999,
          payment_method: "online",
          payment_status: "paid",
          status: "confirmed",
          placed_at: "2026-10-07T12:00:00Z",
        },
      ],
      page: 1,
      pageSize: 10,
      totalItems: 1,
      totalPages: 1,
      express_cancellation_count: 0,
    });
  });

  it("renders orders as a searchable list table", async () => {
    render(<OrdersWorkspace mode="admin" invoiceHref={(n) => `/admin/orders/${n}/invoice`} />);
    await waitFor(() => expect(screen.getByRole("table", { name: "Orders" })).toBeInTheDocument());
    expect(screen.getByRole("columnheader", { name: "Order ID" })).toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: /search orders by number/i })).toBeInTheDocument();
    expect(screen.getByLabelText("From date")).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "MNM-100" })).toBeInTheDocument();
  });

  it("cancels ExpressBees-cancelled order without shipment mode / carrier void prompt", async () => {
    const user = userEvent.setup();
    cancelAdminOrder.mockResolvedValueOnce({ order: { order_number: "MNM-1AE2EAD230C1", status: "cancelled" } });
    listAdminOrders.mockResolvedValue({
      orders: [
        {
          order_number: "MNM-1AE2EAD230C1",
          full_name: "Sai Email Test",
          phone: "7036306469",
          city: "Bangalore",
          total: 4,
          payment_method: "online",
          payment_status: "paid",
          status: "confirmed",
          tracking_status: "carrier_cancelled",
          tracking_number: "XB-VOIDED",
          shipment_logistics_mode: "xpressbees",
          can_admin_cancel: true,
          can_rebook_carrier_cancelled_shipment: true,
          placed_at: "2026-09-29T06:14:39Z",
        },
      ],
      page: 1,
      pageSize: 10,
      totalItems: 1,
      totalPages: 1,
      express_cancellation_count: 1,
    });

    render(<OrdersWorkspace mode="admin" invoiceHref={(n) => `/admin/orders/${n}/invoice`} />);
    await waitFor(() => expect(screen.getByRole("cell", { name: "MNM-1AE2EAD230C1" })).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /Cancel order/i }));
    expect(screen.getByText(/ExpressBees already voided this AWB/i)).toBeInTheDocument();
    expect(screen.queryByLabelText("Mode")).not.toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Why is this order being cancelled/i), "Customer no longer wants it");
    await user.click(screen.getByRole("button", { name: /^Cancel order$/i }));

    await waitFor(() =>
      expect(cancelAdminOrder).toHaveBeenCalledWith("MNM-1AE2EAD230C1", {
        reason: "Customer no longer wants it",
      }),
    );
  });

  it("hides Mark COD collected on cancelled first-leg COD orders", async () => {
    listAdminOrders.mockResolvedValue({
      orders: [
        {
          order_number: "MNM-314F044BF1DE",
          full_name: "Partheeban",
          phone: "9740199455",
          city: "Bangalore",
          total: 278,
          cod_fee: 99,
          payment_method: "cod",
          payment_status: "pending",
          status: "cancelled",
          can_mark_cod_collected: false,
          placed_at: "2026-10-12T11:26:10Z",
        },
      ],
      page: 1,
      pageSize: 10,
      totalItems: 1,
      totalPages: 1,
      express_cancellation_count: 0,
    });

    render(<OrdersWorkspace mode="admin" invoiceHref={(n) => `/admin/orders/${n}/invoice`} />);
    await waitFor(() => expect(screen.getByRole("cell", { name: "MNM-314F044BF1DE" })).toBeInTheDocument());

    expect(screen.queryByRole("button", { name: "Mark COD collected" })).not.toBeInTheDocument();
  });

  it("shows AWB on shipped orders in the list", async () => {
    listAdminOrders.mockResolvedValue({
      orders: [
        {
          order_number: "MNM-SHIPPED",
          full_name: "Asha",
          phone: "9999999999",
          city: "Bengaluru",
          total: 999,
          payment_method: "online",
          payment_status: "paid",
          status: "confirmed",
          tracking_status: "shipped",
          tracking_number: "XB-441122",
          placed_at: "2026-10-07T12:00:00Z",
        },
      ],
      page: 1,
      pageSize: 10,
      totalItems: 1,
      totalPages: 1,
      express_cancellation_count: 0,
    });

    render(<OrdersWorkspace mode="admin" invoiceHref={(n) => `/admin/orders/${n}/invoice`} />);
    await waitFor(() => expect(screen.getByRole("cell", { name: "MNM-SHIPPED" })).toBeInTheDocument());
    expect(screen.getByText("AWB XB-441122")).toBeInTheDocument();
  });

  it("keeps Mark COD collected enabled on open pending COD orders", async () => {
    listAdminOrders.mockResolvedValue({
      orders: [
        {
          order_number: "MNM-COD-OPEN",
          full_name: "Asha",
          phone: "9999999999",
          city: "Bengaluru",
          total: 999,
          payment_method: "cod",
          payment_status: "pending",
          status: "confirmed",
          placed_at: "2026-10-07T12:00:00Z",
        },
      ],
      page: 1,
      pageSize: 10,
      totalItems: 1,
      totalPages: 1,
      express_cancellation_count: 0,
    });

    render(<OrdersWorkspace mode="admin" invoiceHref={(n) => `/admin/orders/${n}/invoice`} />);
    await waitFor(() => expect(screen.getByRole("cell", { name: "MNM-COD-OPEN" })).toBeInTheDocument());

    expect(screen.getByRole("button", { name: "Mark COD collected" })).toBeEnabled();
  });
});
