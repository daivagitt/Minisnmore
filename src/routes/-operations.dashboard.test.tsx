import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./operations.dashboard";

const ordersMock = vi.hoisted(() => ({
  listAdminOrders: vi.fn(),
  listCodPendingOrders: vi.fn(),
}));

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
  Link: ({ children, to, ...props }: any) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/lib/orders.functions", () => ({
  listAdminOrders: (...args: unknown[]) => ordersMock.listAdminOrders(...args),
  listCodPendingOrders: (...args: unknown[]) => ordersMock.listCodPendingOrders(...args),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

function renderDashboard() {
  const Component = (Route as any).component;
  return render(<Component />);
}

describe("operations dashboard route", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    ordersMock.listAdminOrders.mockResolvedValue({
      orders: [
        {
          id: 101,
          order_number: "MNM-101",
          status: "confirmed",
          payment_status: "paid",
          payment_method: "prepaid",
          total: 1299,
          created_at: "2026-09-25T10:00:00.000Z",
          shipping_address: {
            first_name: "Anita",
            last_name: "Sharma",
            city: "Bengaluru",
            pincode: "560001",
          },
          order_items: [{ title: "Baby Romper - Blue / 6M" }],
        },
      ],
      totalItems: 1,
      totalPages: 1,
      page: 1,
      pageSize: 10,
    });
    ordersMock.listCodPendingOrders.mockResolvedValue({
      orders: [
        {
          id: 102,
          order_number: "MNM-102",
          payment_status: "pending",
          total: 899,
        },
      ],
    });
  });

  it("renders operations dashboard title, filters, and metric cards", async () => {
    renderDashboard();

    expect(screen.getByRole("heading", { name: "Fulfillment Dashboard" })).toBeInTheDocument();
    expect(screen.getByText("Fulfillment & Time Filters")).toBeInTheDocument();

    // Check window filter buttons
    expect(screen.getByRole("button", { name: "All time" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Today" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Yesterday" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Last 7 days" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Last 30 days" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Custom" })).toBeInTheDocument();

    // Check filter selects
    expect(screen.getByRole("combobox", { name: /fulfillment status/i })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: /payment method/i })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: /payment status/i })).toBeInTheDocument();

    // Check COD alert banner
    await waitFor(() =>
      expect(screen.getByText(/1 Cash on Delivery order pending payment collection/i)).toBeInTheDocument(),
    );

    // Check dispatch queue item
    expect(screen.getByText("MNM-101")).toBeInTheDocument();
    expect(screen.getByText("Anita Sharma")).toBeInTheDocument();
  });

  it("changes time period when clicking a window pill", async () => {
    renderDashboard();

    const todayButton = screen.getByRole("button", { name: "Today" });
    await userEvent.click(todayButton);

    await waitFor(() => expect(ordersMock.listAdminOrders).toHaveBeenCalled());
    expect(screen.getByRole("button", { name: "Reset all filters" })).toBeInTheDocument();
  });

  it("filters by payment method and resets filters", async () => {
    renderDashboard();

    const methodSelect = screen.getByRole("combobox", { name: /payment method/i });
    await userEvent.selectOptions(methodSelect, "cod");

    await waitFor(() => expect(screen.getByRole("button", { name: "Reset all filters" })).toBeInTheDocument());

    const resetButton = screen.getByRole("button", { name: "Reset all filters" });
    await userEvent.click(resetButton);

    expect(screen.queryByRole("button", { name: "Reset all filters" })).not.toBeInTheDocument();
  });

  it("toggles status filter when clicking a metric card", async () => {
    renderDashboard();

    const confirmedCard = screen.getByRole("button", { name: /ready to pack/i });
    await userEvent.click(confirmedCard);

    expect(screen.getByRole("combobox", { name: /fulfillment status/i })).toHaveValue("confirmed");
  });
});
