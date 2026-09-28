import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.users";

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
}));

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

vi.mock("@/lib/api", () => ({
  api: {
    get: (...args: unknown[]) => apiMock.get(...args),
  },
}));

function daysAgo(days: number): string {
  const date = new Date();
  date.setHours(12, 0, 0, 0);
  date.setDate(date.getDate() - days);
  return date.toISOString();
}

function localDateDaysAgo(days: number): string {
  const date = new Date();
  date.setHours(12, 0, 0, 0);
  date.setDate(date.getDate() - days);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function renderUsers() {
  const Component = (Route as any).component;
  return render(<Component />);
}

const defaultUsers = [
  {
    id: 1,
    login: "today.customer",
    firstName: "Today",
    lastName: "Customer",
    email: "today@example.com",
    activated: true,
    subscribed: true,
    authorities: ["ROLE_USER"],
    createdDate: daysAgo(0),
  },
  {
    id: 2,
    login: "week.customer",
    firstName: "Week",
    lastName: "Customer",
    email: "week@example.com",
    activated: true,
    subscribed: false,
    authorities: ["ROLE_USER"],
    createdDate: daysAgo(3),
  },
  {
    id: 3,
    login: "older.customer",
    firstName: "Older",
    lastName: "Customer",
    email: "older@example.com",
    activated: false,
    subscribed: false,
    authorities: ["ROLE_USER"],
    createdDate: daysAgo(40),
  },
  {
    id: 4,
    login: "staff.admin",
    firstName: "Staff",
    lastName: "Admin",
    email: "staff@example.com",
    activated: true,
    subscribed: true,
    authorities: ["ROLE_ADMIN"],
    createdDate: daysAgo(0),
  },
];

function mockAdminApis(users = defaultUsers, orderedLogins: string[] = ["today.customer"]) {
  apiMock.get.mockImplementation((url: unknown) => {
    const path = String(url);
    if (path.includes("/ordered-logins")) {
      return Promise.resolve({ logins: orderedLogins });
    }
    return Promise.resolve(users);
  });
}

describe("admin users route", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
    mockAdminApis();
  });

  it("shows customer join stats and keeps staff out of the customer counts", async () => {
    renderUsers();

    await waitFor(() => expect(screen.getByRole("button", { name: "Customers: 3" })).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Joined today: 1" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Last 7 days: 2" })).toBeInTheDocument();
    expect(screen.getByText("today@example.com")).toBeInTheDocument();
    expect(screen.queryByText("staff@example.com")).toBeNull();
    expect(screen.getByRole("heading", { name: "Join timeline" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Ordered" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "RBAC" })).toHaveAttribute("href", "/admin/rbac");
  });

  it("shows whether each customer has previously ordered", async () => {
    renderUsers();

    await waitFor(() => expect(screen.getByText("today@example.com")).toBeInTheDocument());
    const rows = screen.getAllByRole("row");
    const todayRow = rows.find((row) => row.textContent?.includes("today@example.com"));
    const weekRow = rows.find((row) => row.textContent?.includes("week@example.com"));
    expect(todayRow?.textContent).toMatch(/Yes/);
    expect(weekRow?.textContent).toMatch(/No/);
  });

  it("still loads customers when ordered-logins fails", async () => {
    apiMock.get.mockImplementation((url: unknown) => {
      const path = String(url);
      if (path.includes("/ordered-logins")) {
        return Promise.reject(new Error("ordered-logins unavailable"));
      }
      return Promise.resolve(defaultUsers);
    });

    renderUsers();

    await waitFor(() => expect(screen.getByText("today@example.com")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Customers: 3" })).toBeInTheDocument();
    const rows = screen.getAllByRole("row");
    const todayRow = rows.find((row) => row.textContent?.includes("today@example.com"));
    expect(todayRow?.textContent).toMatch(/No/);
  });

  it("filters the customer list when a join window is selected", async () => {
    renderUsers();
    await waitFor(() => expect(screen.getByRole("button", { name: "Last 7 days: 2" })).toBeInTheDocument());

    await userEvent.click(screen.getByRole("button", { name: "Last 7 days: 2" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "Joined in the last 7 days" })).toBeInTheDocument());
    expect(screen.getByText("today@example.com")).toBeInTheDocument();
    expect(screen.getByText("week@example.com")).toBeInTheDocument();
    expect(screen.queryByText("older@example.com")).toBeNull();

    await userEvent.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(screen.getByText("older@example.com")).toBeInTheDocument();
  });

  it("filters customers by search, status, and newsletter", async () => {
    renderUsers();
    await waitFor(() => expect(screen.getByText("today@example.com")).toBeInTheDocument());

    await userEvent.type(
      screen.getByRole("textbox", { name: "Search customers by name, email, or login" }),
      "older",
    );
    await waitFor(() => expect(screen.getByText("older@example.com")).toBeInTheDocument());
    expect(screen.queryByText("today@example.com")).toBeNull();
    expect(screen.queryByText("week@example.com")).toBeNull();

    await userEvent.clear(
      screen.getByRole("textbox", { name: "Search customers by name, email, or login" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "Pending" }));
    await waitFor(() => expect(screen.getByText("older@example.com")).toBeInTheDocument());
    expect(screen.queryByText("today@example.com")).toBeNull();
    expect(screen.getByRole("heading", { name: /pending/i })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "All status" }));
    await userEvent.click(screen.getByRole("button", { name: "Subscribed" }));
    await waitFor(() => expect(screen.getByText("today@example.com")).toBeInTheDocument());
    expect(screen.queryByText("week@example.com")).toBeNull();
    expect(screen.queryByText("older@example.com")).toBeNull();
    expect(screen.queryByText("staff@example.com")).toBeNull();

    await userEvent.click(screen.getByRole("button", { name: "Clear filters" }));
    await waitFor(() => expect(screen.getByText("older@example.com")).toBeInTheDocument());
    expect(screen.getByText("week@example.com")).toBeInTheDocument();
  });

  it("filters customers by custom joined date range and does not select the Customers card", async () => {
    renderUsers();
    await waitFor(() => expect(screen.getByText("today@example.com")).toBeInTheDocument());

    const from = screen.getByLabelText("Joined from date");
    const to = screen.getByLabelText("Joined to date");
    await userEvent.clear(from);
    await userEvent.type(from, localDateDaysAgo(0));
    await userEvent.clear(to);
    await userEvent.type(to, localDateDaysAgo(0));

    await waitFor(() => expect(screen.getByText("today@example.com")).toBeInTheDocument());
    expect(screen.queryByText("week@example.com")).toBeNull();
    expect(screen.queryByText("older@example.com")).toBeNull();
    expect(screen.getByRole("heading", { name: /joined/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Customers: 3" })).toHaveAttribute("aria-pressed", "false");

    await userEvent.clear(from);
    await userEvent.type(from, localDateDaysAgo(45));
    await userEvent.clear(to);
    await userEvent.type(to, localDateDaysAgo(35));
    await waitFor(() => expect(screen.getByText("older@example.com")).toBeInTheDocument());
    expect(screen.queryByText("today@example.com")).toBeNull();

    await userEvent.click(screen.getByRole("button", { name: "Clear filters" }));
    await waitFor(() => expect(screen.getByText("week@example.com")).toBeInTheDocument());
    expect((screen.getByLabelText("Joined from date") as HTMLInputElement).value).toBe("");
  });

  it("clears custom dates when a join card is selected", async () => {
    renderUsers();
    await waitFor(() => expect(screen.getByText("today@example.com")).toBeInTheDocument());

    const from = screen.getByLabelText("Joined from date");
    await userEvent.type(from, localDateDaysAgo(0));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Customers: 3" })).toHaveAttribute("aria-pressed", "false"),
    );

    await userEvent.click(screen.getByRole("button", { name: "Last 7 days: 2" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "Joined in the last 7 days" })).toBeInTheDocument());
    expect((screen.getByLabelText("Joined from date") as HTMLInputElement).value).toBe("");
    expect(screen.getByText("week@example.com")).toBeInTheDocument();
  });

  it("ANDs a join preset with list status filters", async () => {
    renderUsers();
    await waitFor(() => expect(screen.getByRole("button", { name: "Last 7 days: 2" })).toBeInTheDocument());

    await userEvent.click(screen.getByRole("button", { name: "Last 7 days: 2" }));
    await userEvent.click(screen.getByRole("button", { name: "Pending" }));

    await waitFor(() => expect(screen.getByRole("heading", { name: /last 7 days.*pending/i })).toBeInTheDocument());
    expect(screen.queryByText("today@example.com")).toBeNull();
    expect(screen.queryByText("week@example.com")).toBeNull();
    expect(screen.queryByText("older@example.com")).toBeNull();
    expect(screen.getByText("No customers match these filters.")).toBeInTheDocument();
  });

  it("paginates the customer list", async () => {
    const manyCustomers = Array.from({ length: 12 }, (_, index) => ({
      id: index + 10,
      login: `customer${index}@example.com`,
      firstName: "Customer",
      lastName: String(index + 1),
      email: `customer${index}@example.com`,
      activated: true,
      authorities: ["ROLE_USER"],
      createdDate: daysAgo(index),
    }));
    mockAdminApis([
      ...manyCustomers,
      {
        id: 99,
        login: "staff.admin",
        firstName: "Staff",
        lastName: "Admin",
        email: "staff@example.com",
        activated: true,
        authorities: ["ROLE_ADMIN"],
        createdDate: daysAgo(0),
      },
    ]);

    renderUsers();
    await waitFor(() => expect(screen.getByText(/Showing 1–10 of 12/)).toBeInTheDocument());
    expect(screen.getByText("customer0@example.com")).toBeInTheDocument();
    expect(screen.queryByText("customer11@example.com")).toBeNull();

    await userEvent.click(screen.getByRole("button", { name: "Go to page 2" }));
    await waitFor(() => expect(screen.getByText(/Showing 11–12 of 12/)).toBeInTheDocument());
    expect(screen.getByText("customer11@example.com")).toBeInTheDocument();
    expect(screen.queryByText("customer0@example.com")).toBeNull();
  });
});
