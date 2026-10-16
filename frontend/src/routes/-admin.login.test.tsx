import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.login";

const navMock = vi.hoisted(() => vi.fn());
const authMock = vi.hoisted(() => ({
  signIn: vi.fn(),
  signUp: vi.fn(),
  signInWithOAuth: vi.fn(),
  signOut: vi.fn(),
  requestPasswordReset: vi.fn(),
  user: null as null | { id: number; email?: string; login?: string },
  isAdmin: false,
  staffRole: null as null | string,
  loading: false,
}));
const toastMock = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
}));

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
  useNavigate: () => navMock,
  Link: ({ children, to, ...props }: any) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => authMock,
}));

vi.mock("sonner", () => ({
  toast: {
    error: toastMock.error,
    success: toastMock.success,
  },
}));

function renderAdminLogin() {
  const Component = (Route as any).component;
  return render(<Component />);
}

describe("admin login route", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authMock.user = null;
    authMock.isAdmin = false;
    authMock.staffRole = null;
    authMock.loading = false;
  });

  it("exposes document metadata", () => {
    expect((Route as any).head().meta[0].title).toBe("Staff Login · Mini's n More");
    expect((Route as any).head().links[0]).toEqual(
      expect.objectContaining({ rel: "preload", as: "image", fetchPriority: "high" }),
    );
  });

  it("renders a motion kids hero from existing store photography", () => {
    renderAdminLogin();
    const hero = screen.getByRole("img", { name: /smiling baby/i }) as HTMLImageElement;
    expect(hero.getAttribute("src")).toMatch(/hero-smiling-baby/i);
    expect(screen.getAllByText(/minis today, memories forever/i).length).toBeGreaterThan(0);
  });

  it("navigates authenticated admins to admin", async () => {
    authMock.user = { id: 1 };
    authMock.isAdmin = true;
    authMock.staffRole = "ROLE_ADMIN";
    renderAdminLogin();
    await waitFor(() => expect(navMock).toHaveBeenCalledWith({ to: "/admin" }));
  });

  it("does not redirect signed-in non-admin users", async () => {
    authMock.user = { id: 1, email: "shopper@example.com" };
    authMock.isAdmin = false;
    renderAdminLogin();
    await waitFor(() =>
      expect(screen.getByText(/does not have staff access/i)).toBeInTheDocument(),
    );
    expect(navMock).not.toHaveBeenCalled();
  });

  it("shows the login fallback when a signed-in user has no email", async () => {
    authMock.user = { id: 1, login: "login-only@example.com" };
    authMock.isAdmin = false;
    renderAdminLogin();

    await waitFor(() =>
      expect(screen.getByText(/Signed in as login-only@example.com/i)).toBeInTheDocument(),
    );
  });

  it("lets a signed-in shopper sign out from the admin login screen", async () => {
    authMock.user = { id: 1, email: "shopper@example.com", login: "shopper@example.com" };
    authMock.isAdmin = false;
    renderAdminLogin();

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Sign out and use a different account" })).toBeInTheDocument(),
    );
    await userEvent.click(screen.getByRole("button", { name: "Sign out and use a different account" }));
    expect(authMock.signOut).toHaveBeenCalled();
  });

  it("submits admin sign in and reports errors", async () => {
    authMock.signIn.mockResolvedValueOnce({ error: "No access" });
    renderAdminLogin();

    await userEvent.type(screen.getByPlaceholderText("you@store.in"), "admin@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() =>
      expect(authMock.signIn).toHaveBeenCalledWith("admin@example.com", "secret1"),
    );
    expect(toastMock.error).toHaveBeenCalledWith("No access");
  });

  it("signs in without navigating until a staff role is present", async () => {
    authMock.signIn.mockResolvedValueOnce({ error: null });
    renderAdminLogin();

    await userEvent.type(screen.getByPlaceholderText("you@store.in"), "admin@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(authMock.signIn).toHaveBeenCalledWith("admin@example.com", "secret1"));
    expect(navMock).not.toHaveBeenCalled();
  });

  it("does not offer signup", () => {
    renderAdminLogin();
    expect(screen.queryByRole("button", { name: /sign up/i })).toBeNull();
    expect(screen.queryByRole("button", { name: /google/i })).toBeNull();
  });

  it("sends a forgot-password reset link", async () => {
    authMock.requestPasswordReset.mockResolvedValueOnce({ error: null });
    renderAdminLogin();

    await userEvent.click(screen.getByRole("button", { name: /forgot password/i }));
    await userEvent.type(screen.getByPlaceholderText("you@store.in"), "ops@store.in");
    await userEvent.click(screen.getByRole("button", { name: "Send reset link" }));

    await waitFor(() => expect(authMock.requestPasswordReset).toHaveBeenCalledWith("ops@store.in"));
    expect(screen.getByText(/reset link is on its way/i)).toBeInTheDocument();
  });

  it("validates email and password before submit and shows field errors", async () => {
    renderAdminLogin();

    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(toastMock.error).toHaveBeenCalled();
    expect(authMock.signIn).not.toHaveBeenCalled();

    const email = screen.getByPlaceholderText("you@store.in");
    await userEvent.type(email, "bad");
    await userEvent.tab();
    expect(screen.getByText(/valid email/i)).toBeInTheDocument();

    await userEvent.clear(email);
    await userEvent.type(email, "admin@example.com");
    const password = screen.getByPlaceholderText("Password (min 6)");
    await userEvent.type(password, "abc");
    await userEvent.tab();
    expect(screen.getByText(/at least 6 characters/i)).toBeInTheDocument();
  });

  it("toggles password visibility", async () => {
    renderAdminLogin();

    const password = screen.getByPlaceholderText("Password (min 6)");
    expect(password).toHaveAttribute("type", "password");

    await userEvent.click(screen.getByRole("button", { name: "Show password" }));
    expect(password).toHaveAttribute("type", "text");

    await userEvent.click(screen.getByRole("button", { name: "Hide password" }));
    expect(password).toHaveAttribute("type", "password");
  });

  it("reports sign-in errors without navigating", async () => {
    authMock.signIn.mockResolvedValueOnce({ error: "No access" });
    renderAdminLogin();

    await userEvent.type(screen.getByPlaceholderText("you@store.in"), "admin@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith("No access"));
    expect(navMock).not.toHaveBeenCalled();
  });
});
