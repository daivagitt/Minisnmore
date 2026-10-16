/** @vitest-environment jsdom */
import { cleanup, render, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./account";

const authMock = vi.hoisted(() => ({
  user: null as null | { id: string; email: string },
  loading: false,
  signOut: vi.fn(),
}));

const navigateMock = vi.hoisted(() => vi.fn());
const locationMock = vi.hoisted(() => ({
  pathname: "/account/orders/MNM-1001",
  href: "/account/orders/MNM-1001",
  searchStr: "",
}));

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
  Link: ({ children, to, ...props }: any) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  Outlet: () => null,
  useLocation: () => locationMock,
  useNavigate: () => navigateMock,
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => authMock,
}));

vi.mock("@/lib/account", () => ({
  AccountDataProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock("@/assets/cat-newborn.webp", () => ({
  default: "newborn.webp",
}));

const AccountLayout = (Route as unknown as { component: React.ComponentType }).component;

describe("account layout auth guard", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    authMock.user = null;
    authMock.loading = false;
    locationMock.pathname = "/account/orders/MNM-1001";
    locationMock.href = "/account/orders/MNM-1001";
    locationMock.searchStr = "";
  });

  it("preserves deep account paths in the login redirect", async () => {
    render(<AccountLayout />);

    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith({
        to: "/login",
        search: { redirect: "/account/orders/MNM-1001" },
      }),
    );
  });

  it("preserves query strings in the login redirect", async () => {
    locationMock.pathname = "/account/orders/MNM-1001";
    locationMock.href = "/account/orders/MNM-1001?pay=1";
    locationMock.searchStr = "?pay=1";

    render(<AccountLayout />);

    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith({
        to: "/login",
        search: { redirect: "/account/orders/MNM-1001?pay=1" },
      }),
    );
  });

  it("does not redirect unauthenticated users on activation routes", async () => {
    locationMock.pathname = "/account/activate";

    render(<AccountLayout />);

    await waitFor(() => expect(navigateMock).not.toHaveBeenCalled());
  });
});
