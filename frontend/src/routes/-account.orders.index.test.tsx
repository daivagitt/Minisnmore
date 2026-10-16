/** @vitest-environment jsdom */
import { render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./account.orders.index";

const accountData = vi.hoisted(() => ({
  orders: [] as any[],
  loadingOrders: false,
  refreshOrders: vi.fn(async () => undefined),
}));

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
  Link: ({ children, to, ...props }: any) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/lib/account", () => ({
  useAccountData: () => accountData,
}));

const OrdersPage = (Route as unknown as { component: React.ComponentType }).component;

describe("orders list freshness", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    accountData.orders = [];
    accountData.loadingOrders = false;
  });

  it("revalidates cached orders whenever the tab is opened", async () => {
    render(<OrdersPage />);

    await waitFor(() => expect(accountData.refreshOrders).toHaveBeenCalled());
  });
});
