/** @vitest-environment jsdom */
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Route, LoginPage } from "./login";

const authMock = vi.hoisted(() => ({
  signIn: vi.fn(),
  signUp: vi.fn(),
  signInWithOAuth: vi.fn(),
  user: null as null | { id: string },
  loading: false,
}));

const navigateMock = vi.hoisted(() => vi.fn());
const searchMock = vi.hoisted(() => ({
  redirect: "/account" as string | undefined,
  error: undefined as string | undefined,
}));

const toastMock = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  message: vi.fn(),
}));

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
  lazyRouteComponent: (importer: unknown) => importer,
  Link: ({ children, to, ...props }: any) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useSearch: () => searchMock,
  useNavigate: () => navigateMock,
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => authMock,
}));

vi.mock("sonner", () => ({
  toast: Object.assign(toastMock.message, {
    error: toastMock.error,
    success: toastMock.success,
  }),
}));

function renderLogin() {
  return render(<LoginPage />);
}

describe("login route", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    authMock.user = null;
    authMock.loading = false;
    searchMock.redirect = "/account";
    searchMock.error = undefined;
  });

  it("validates search params and exposes document metadata", () => {
    expect((Route as any).validateSearch({ redirect: "/checkout" })).toEqual({ redirect: "/checkout" });
    expect((Route as any).validateSearch({})).toEqual({});
    expect((Route as any).head().meta[0].title).toBe("Sign in · Mini's n More");
  });

  it("redirects an already signed-in user to the search redirect target", async () => {
    searchMock.redirect = "/checkout";
    authMock.user = { id: "u1" };
    renderLogin();
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith({ to: "/checkout" }));
  });

  it("redirects an already signed-in user to the default account route", async () => {
    searchMock.redirect = undefined;
    authMock.user = { id: "u1" };
    renderLogin();
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith({ to: "/account" }));
  });

  it("redirects after a successful email sign in", async () => {
    authMock.signIn.mockResolvedValueOnce({ error: null });
    renderLogin();

    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "parent@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith({ to: "/account" }));
  });

  it("submits email/password sign in and shows backend errors", async () => {
    authMock.signIn.mockResolvedValueOnce({ error: "Invalid credentials" });
    renderLogin();

    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "parent@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() =>
      expect(authMock.signIn).toHaveBeenCalledWith("parent@example.com", "secret1"),
    );
    expect(toastMock.error).toHaveBeenCalledWith("Invalid credentials", { id: "login-error" });
  });

  it("switches to sign up mode and submits full name", async () => {
    authMock.signUp.mockResolvedValueOnce({ error: null });
    renderLogin();

    await userEvent.click(screen.getByRole("button", { name: "New here? Create an account" }));
    await userEvent.type(screen.getByPlaceholderText("Full name"), "New Parent");
    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "new@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: /^Create account$/ }));

    await waitFor(() =>
      expect(authMock.signUp).toHaveBeenCalledWith("new@example.com", "secret1", "New Parent"),
    );
    expect(toastMock.success).toHaveBeenCalledWith(
      "Almost there — check your inbox and open the activation link to finish.",
    );
  });

  it("shows sign up errors and can switch back to sign in", async () => {
    authMock.signUp.mockResolvedValueOnce({ error: "Email taken" });
    renderLogin();

    await userEvent.click(screen.getByRole("button", { name: "New here? Create an account" }));
    await userEvent.type(screen.getByPlaceholderText("Full name"), "New Parent");
    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "taken@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: /^Create account$/ }));

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith("Email taken", { id: "login-error" }),
    );
    expect(navigateMock).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "Have an account? Sign in" }));
    expect(screen.getByRole("heading", { name: "Sign in" })).toBeTruthy();
  });

  it("treats a missing error field as a successful sign in", async () => {
    authMock.signIn.mockResolvedValueOnce({});
    renderLogin();

    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "parent@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith({ to: "/account" }));
    expect(toastMock.error).not.toHaveBeenCalled();
  });

  it("navigates to the default account route after sign in when redirect is absent", async () => {
    searchMock.redirect = undefined;
    authMock.signIn.mockResolvedValueOnce({ error: null });
    renderLogin();

    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "parent@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith({ to: "/account" }));
  });

  it("starts Google OAuth with the redirect target", async () => {
    authMock.signInWithOAuth.mockResolvedValueOnce({ error: null });
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));
    await waitFor(() => expect(authMock.signInWithOAuth).toHaveBeenCalledWith("google", "/account"));
  });

  it("starts Apple OAuth with the redirect target", async () => {
    authMock.signInWithOAuth.mockResolvedValueOnce({ error: null });
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Continue with Apple" }));
    await waitFor(() => expect(authMock.signInWithOAuth).toHaveBeenCalledWith("apple", "/account"));
  });

  it("starts Facebook OAuth with the redirect target", async () => {
    authMock.signInWithOAuth.mockResolvedValueOnce({ error: null });
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Continue with Facebook" }));
    await waitFor(() => expect(authMock.signInWithOAuth).toHaveBeenCalledWith("facebook", "/account"));
  });

  it("resets OAuth busy state when provider redirects fail", async () => {
    authMock.signInWithOAuth.mockImplementationOnce(() => {
      throw new Error("blocked");
    });
    renderLogin();

    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));

    expect((screen.getByRole("button", { name: "Continue with Google" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("resets Apple busy state when provider redirects fail", async () => {
    authMock.signInWithOAuth.mockImplementationOnce(() => {
      throw new Error("blocked");
    });
    renderLogin();

    await userEvent.click(screen.getByRole("button", { name: "Continue with Apple" }));

    expect((screen.getByRole("button", { name: "Continue with Apple" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("resets Facebook busy state when provider redirects fail", async () => {
    authMock.signInWithOAuth.mockImplementationOnce(() => {
      throw new Error("blocked");
    });
    renderLogin();

    await userEvent.click(screen.getByRole("button", { name: "Continue with Facebook" }));

    expect((screen.getByRole("button", { name: "Continue with Facebook" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("surfaces OAuth provider errors from resolved responses", async () => {
    authMock.signInWithOAuth.mockResolvedValueOnce({ error: "Google unavailable" });
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));
    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith("Google unavailable", { id: "oauth-error" }),
    );

    authMock.signInWithOAuth.mockResolvedValueOnce({ error: "Apple unavailable" });
    await userEvent.click(screen.getByRole("button", { name: "Continue with Apple" }));
    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith("Apple unavailable", { id: "oauth-error" }),
    );

    authMock.signInWithOAuth.mockResolvedValueOnce({ error: "Facebook unavailable" });
    await userEvent.click(screen.getByRole("button", { name: "Continue with Facebook" }));
    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith("Facebook unavailable", { id: "oauth-error" }),
    );
  });

  it("toasts a clear message when OAuth redirect throws", async () => {
    authMock.signInWithOAuth.mockRejectedValueOnce(new Error("network"));
    renderLogin();

    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith(
        "Google sign-in could not be started. Please try again or use email instead.",
        { id: "oauth-error" },
      ),
    );
  });

  it("shows OAuth callback errors from the login URL", async () => {
    searchMock.error = "Sign-in was cancelled. You can try again.";
    renderLogin();

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith(
        "Sign-in was cancelled. You can try again or create an account with email.",
        { id: "oauth-error" },
      ),
    );
    expect(navigateMock).toHaveBeenCalledWith({
      to: "/login",
      search: { redirect: "/account" },
      replace: true,
    });
  });
});
