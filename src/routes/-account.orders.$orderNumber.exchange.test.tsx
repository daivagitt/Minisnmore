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
        size: "2-3 yrs",
        color: "Pink",
        qty: 2,
        price: 499,
      },
    ],
  } as any,
  loading: false,
  error: null as string | null,
  refresh: vi.fn(),
}));

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
  checkReturnEligibility: vi.fn(async () => ({ eligible: true, return_deadline: null })),
  listMyOrderReturns: vi.fn(async () => ({ returns: [] })),
  requestOrderReturn: vi.fn(),
  requestOrderExchange: vi.fn(),
  uploadReturnImage: vi.fn(),
  MAX_RETURN_IMAGES: 5,
}));

vi.mock("@/lib/razorpay", () => ({ openRazorpayCheckout: vi.fn() }));
vi.mock("@/lib/invoice-availability", () => ({ isInvoiceAvailable: () => false }));
vi.mock("@/components/LazyImage", () => ({ LazyImage: () => null }));
vi.mock("@/components/CopyButton", () => ({ CopyButton: () => null }));
vi.mock("@/components/OrderTrackingCard", () => ({ OrderTrackingCard: () => null }));
vi.mock("sonner", () => ({ toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }) }));

const OrderDetail = (Route as { component: React.ComponentType }).component;

describe("order exchange dialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows a quantity, unused-item disclaimer, and Submit exchange", async () => {
    render(<OrderDetail />);

    const requestBtn = await screen.findByRole("button", { name: "Exchange" });
    fireEvent.click(requestBtn);

    await waitFor(() => {
      expect(screen.getByText(/entire quantity will be exchanged/i)).toBeTruthy();
    });
    expect(screen.getByText(/Quantity:/i).closest("p")).toHaveTextContent("Quantity: 2");
    expect(screen.getAllByText(/unused/i).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Submit exchange" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Submit exchange request" })).toBeNull();
  });

  it("places Return and Exchange beside each other on the item", async () => {
    render(<OrderDetail />);

    expect(await screen.findByRole("button", { name: "Return" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Exchange" })).toBeInTheDocument();
  });

  it("opens the return dialog with unused-item disclaimer and Submit return", async () => {
    render(<OrderDetail />);

    fireEvent.click(await screen.findByRole("button", { name: "Return" }));

    expect(await screen.findByRole("heading", { name: "Return items" })).toBeInTheDocument();
    expect(screen.getByText(/Ordered qty 2/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Submit return" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Submit return request" })).toBeNull();
  });
});
