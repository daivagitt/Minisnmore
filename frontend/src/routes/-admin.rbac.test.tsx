import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.rbac";

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));
const authMock = vi.hoisted(() => ({
  staffRole: "ROLE_ADMIN" as const,
}));

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => authMock,
}));

vi.mock("@/lib/api", () => ({
  api: {
    get: (...args: unknown[]) => apiMock.get(...args),
    post: (...args: unknown[]) => apiMock.post(...args),
    put: (...args: unknown[]) => apiMock.put(...args),
  },
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

function renderRbac() {
  const Component = (Route as unknown as { component: React.ComponentType }).component;
  return render(<Component />);
}

describe("admin rbac route", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
    apiMock.get.mockResolvedValue([
      {
        id: 1,
        login: "today.customer",
        firstName: "Today",
        lastName: "Customer",
        email: "today@example.com",
        activated: true,
        authorities: ["ROLE_USER"],
        createdDate: "2026-09-21T12:00:00.000Z",
      },
      {
        id: 4,
        login: "staff.sales",
        firstName: "Staff",
        lastName: "Sales",
        email: "sales@example.com",
        activated: true,
        authorities: ["ROLE_SALES"],
        createdDate: "2026-09-01T12:00:00.000Z",
      },
    ]);
  });

  it("lists staff and keeps customers off the RBAC table", async () => {
    renderRbac();

    await waitFor(() => expect(screen.getByText("sales@example.com")).toBeInTheDocument());
    expect(screen.getByRole("heading", { name: "RBAC" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Staff" })).toBeInTheDocument();
    expect(screen.queryByText("today@example.com")).toBeNull();
    expect(screen.getByRole("link", { name: "Customers" })).toHaveAttribute("href", "/admin/customers");
  });

  it("sends a staff invitation for an assignable role", async () => {
    apiMock.post.mockResolvedValue({});
    renderRbac();
    await waitFor(() => expect(screen.getByText("sales@example.com")).toBeInTheDocument());

    const form = screen.getByRole("button", { name: "Send invitation" }).closest("form");
    expect(form).toBeTruthy();
    const fields = within(form as HTMLElement);
    const [firstName, , email] = fields.getAllByRole("textbox");
    await userEvent.type(firstName, "Priya");
    await userEvent.type(email, "ops@example.com");
    await userEvent.selectOptions(fields.getByRole("combobox"), "ROLE_OPERATIONS");
    await userEvent.click(screen.getByRole("button", { name: "Send invitation" }));

    await waitFor(() => expect(apiMock.post).toHaveBeenCalled());
    expect(apiMock.post.mock.calls[0][0]).toBe("/api/admin/users");
    expect(apiMock.post.mock.calls[0][1]).toMatchObject({
      email: "ops@example.com",
      firstName: "Priya",
      authorities: ["ROLE_OPERATIONS"],
      activated: false,
    });
  });
});
