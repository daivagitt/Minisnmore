/** @vitest-environment jsdom */
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { listMyOrderReturns } from "@/lib/orders.functions";
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
        qty: 2,
        price: 499,
      },
    ],
  } as any,
  loading: false,
  error: null as string | null,
  refresh: vi.fn(),
}));

const exchangeCatalog = vi.hoisted(() => ({
  product: {
    id: "p1",
    slug: "floral-dress",
    name: "Floral Cotton Dress",
    tagline: "",
    price: 499,
    image: "https://cdn.example/dress.jpg",
    gallery: ["https://cdn.example/dress.jpg"],
    ages: ["18-24m"],
    fabric: "Organic Cotton",
    colors: [
      { name: "Pink", hex: "#f8c" },
      { name: "Blue", hex: "#8cf" },
    ],
    sizes: ["2-3 yrs", "4-5 yrs"],
    rating: 5,
    reviewCount: 0,
    category: "Toddler",
    description: "",
    itemStructure: "VARIANTS",
    variants: [
      { sku: "DRESS-23-PINK", size: "2-3 yrs", color: "Pink", price: 499, stock: 3, active: true },
      { sku: "DRESS-45-PINK", size: "4-5 yrs", color: "Pink", price: 599, stock: 2, active: true },
      { sku: "DRESS-45-BLUE", size: "4-5 yrs", color: "Blue", price: 649, stock: 2, active: true },
    ],
  },
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
  useProduct: () => exchangeCatalog.product,
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
vi.mock("@/components/LazyImage", () => ({
  LazyImage: ({ src, alt }: { src: string; alt?: string }) => <img src={src} alt={alt ?? ""} />,
}));
vi.mock("@/components/CopyButton", () => ({ CopyButton: () => null }));
vi.mock("@/components/OrderTrackingCard", () => ({ OrderTrackingCard: () => null }));
vi.mock("sonner", () => ({ toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }) }));

const OrderDetail = (Route as unknown as { component: React.ComponentType }).component;

describe("order exchange dialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    tracking.order.can_cancel = false;
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
    expect(screen.getByText(/delivery and COD fees are not refunded/i)).toBeTruthy();
    expect(screen.getByLabelText(/Why are you exchanging this item/i)).toBeRequired();
    expect(screen.getByText(/Why are you exchanging this item/i).querySelector(".text-destructive")).toHaveTextContent("*");
    expect(screen.getByRole("button", { name: "Submit exchange" })).toBeTruthy();
    expect(screen.getAllByRole("img", { name: "Floral Cotton Dress" }).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Submit exchange request" })).toBeNull();
  });

  it("places Return and Exchange beside each other on the item", async () => {
    render(<OrderDetail />);

    expect(await screen.findByRole("button", { name: "Return" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Exchange" })).toBeInTheDocument();
  });

  it("still shows Return and Exchange when listing existing returns fails", async () => {
    vi.mocked(listMyOrderReturns).mockRejectedValue(new Error("Could not load return requests"));

    render(<OrderDetail />);

    expect(await screen.findByRole("button", { name: "Return" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Exchange" })).toBeInTheDocument();
  });

  it("shows the product image in the cancellation dialog", async () => {
    tracking.order.can_cancel = true;
    render(<OrderDetail />);

    fireEvent.click(await screen.findByRole("button", { name: /Request cancellation/i }));
    expect(await screen.findByRole("heading", { name: "Request cancellation?" })).toBeInTheDocument();
    expect(screen.getAllByRole("img", { name: "Floral Cotton Dress" }).length).toBeGreaterThan(0);
  });

  it("opens the return dialog with unused-item disclaimer and Submit return", async () => {
    render(<OrderDetail />);

    fireEvent.click(await screen.findByRole("button", { name: "Return" }));

    expect(await screen.findByRole("heading", { name: "Return items" })).toBeInTheDocument();
    expect(screen.getByText(/delivery and COD fees are not refunded/i)).toBeInTheDocument();
    expect(screen.getByText(/Ordered qty 2/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Why are you returning this order/i)).toBeRequired();
    expect(screen.getByText(/Why are you returning this order/i).querySelector(".text-destructive")).toHaveTextContent("*");
    expect(screen.getByRole("button", { name: "Submit return" })).toBeInTheDocument();
    expect(screen.getAllByRole("img", { name: "Floral Cotton Dress" }).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Submit return request" })).toBeNull();
  });

  it("shows the price difference as a more expensive size is selected", async () => {
    render(<OrderDetail />);

    fireEvent.click(await screen.findByRole("button", { name: "Exchange" }));

    expect(await screen.findByText("Same price as your original item")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /4-5 yrs/ }));
    expect(await screen.findByText("You'll pay ₹200 extra")).toBeInTheDocument();
  });
});
