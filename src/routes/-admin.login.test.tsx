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
  user: null as null | { id: number; email?: string; login?: string },
  isAdmin: false,
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
    authMock.loading = false;
  });

  it("exposes document metadata", () => {
    expect((Route as any).head().meta[0].title).toBe("Admin Login · Mini's n More");
  });

  it("navigates authenticated admins to admin", async () => {
    authMock.user = { id: 1 };
    authMock.isAdmin = true;
    renderAdminLogin();
    await waitFor(() => expect(navMock).toHaveBeenCalledWith({ to: "/admin" }));
  });

  it("does not redirect signed-in non-admin users", async () => {
    authMock.user = { id: 1, email: "shopper@example.com" };
    authMock.isAdmin = false;
    renderAdminLogin();
    await waitFor(() =>
      expect(screen.getByText(/does not have admin access/i)).toBeInTheDocument(),
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

  it("navigates to admin after a successful sign in", async () => {
    authMock.signIn.mockResolvedValueOnce({ error: null });
    renderAdminLogin();

    await userEvent.type(screen.getByPlaceholderText("you@store.in"), "admin@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(navMock).toHaveBeenCalledWith({ to: "/admin" }));
  });

  it("shows signup copy and switches back to sign in", async () => {
    renderAdminLogin();

    await userEvent.click(screen.getByRole("button", { name: "Need an account? Sign up" }));
    expect(screen.getByRole("heading", { name: "Admin sign up" })).toBeInTheDocument();
    expect(screen.getByText(/grant admin access/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Have an account? Sign in" }));
    expect(screen.getByRole("heading", { name: "Admin sign in" })).toBeInTheDocument();
  });

  it("switches to sign up and starts Google OAuth for admin redirect", async () => {
    authMock.signUp.mockResolvedValueOnce({ error: null });
    renderAdminLogin();

    await userEvent.click(screen.getByRole("button", { name: "Need an account? Sign up" }));
    await userEvent.type(screen.getByPlaceholderText("you@store.in"), "new@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));

    await waitFor(() => expect(authMock.signUp).toHaveBeenCalledWith("new@example.com", "secret1"));
    expect(toastMock.success).toHaveBeenCalledWith(
      "Account created. Check your inbox to confirm.",
    );

    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));
    expect(authMock.signInWithOAuth).toHaveBeenCalledWith("google", "/admin");
  });

  it("resets Google busy state when OAuth start fails", async () => {
    authMock.signInWithOAuth.mockImplementationOnce(() => {
      throw new Error("blocked");
    });
    renderAdminLogin();

    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));

    expect(screen.getByRole("button", { name: "Continue with Google" })).not.toBeDisabled();
  });
});
