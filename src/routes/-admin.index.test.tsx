/** @vitest-environment jsdom */
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.index";

const getAdminDashboard = vi.hoisted(() => vi.fn());
const getAdminOrderReturnsSummary = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
  Link: ({ children, to, ...props }: { children: React.ReactNode; to: string }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/lib/backend-data", () => ({
  getAdminDashboard: (...args: unknown[]) => getAdminDashboard(...args),
}));

vi.mock("@/lib/orders.functions", () => ({
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
}));

vi.mock("@/lib/ui-errors", () => ({
  showApiError: vi.fn(),
}));

function renderDashboard() {
  const Component = (Route as { component: React.ComponentType }).component;
  return render(<Component />);
}

describe("admin dashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getAdminDashboard.mockResolvedValue({
      categories: 2,
      products: 10,
      activeProducts: 8,
      inactiveProducts: 2,
      productsWithSku: 9,
      coupons: 3,
      blogPosts: 4,
      pageVisibility: 6,
      visiblePages: 5,
      banners: 1,
      policies: 2,
    });
    getAdminOrderReturnsSummary.mockResolvedValue({
      total: 12,
      returns: 8,
      exchanges: 4,
      open: 7,
      closed: 5,
      overdue: 2,
      stages: { request: 3, pickup: 2, inspection: 1, exchange: 1, closed: 5 },
    });
  });

  it("shows return and exchange counts", async () => {
    renderDashboard();

    await waitFor(() => expect(screen.getByText("Returns")).toBeInTheDocument());
    expect(screen.getByText("Exchanges")).toBeInTheDocument();
    expect(screen.getByText("7 open · 2 overdue")).toBeInTheDocument();
    expect(screen.getByText("12 total cases")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Returns & exchanges/i })).toHaveAttribute("href", "/admin/returns");
  });
});
