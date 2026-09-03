/** @vitest-environment jsdom */
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Route, LoginPage } from "./login";

const authMock = vi.hoisted(() => ({
  signIn: vi.fn(),
  signUp: vi.fn(),
  signInWithOAuth: vi.fn(),
  requestPasswordReset: vi.fn(),
  resendActivationEmail: vi.fn(),
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

const oauthEnabledMock = vi.hoisted(() => vi.fn((provider: string) => provider === "google"));

vi.mock("@/lib/oauth-providers", () => ({
  isOAuthProviderEnabled: (provider: string) => oauthEnabledMock(provider),
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

  it("rejects an invalid full name during sign up", async () => {
    renderLogin();

    await userEvent.click(screen.getByRole("button", { name: "New here? Create an account" }));
    await userEvent.type(screen.getByPlaceholderText("Full name"), "A");
    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "new@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: /^Create account$/ }));

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith(
        expect.stringMatching(/at least 2 characters/i),
        { id: "login-error" },
      ),
    );
    expect(authMock.signUp).not.toHaveBeenCalled();
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

  it("starts Google OAuth with the search redirect target", async () => {
    searchMock.redirect = "/account/orders/MNM-1001?pay=1";
    authMock.signInWithOAuth.mockResolvedValueOnce({ error: null });
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));
    await waitFor(() =>
      expect(authMock.signInWithOAuth).toHaveBeenCalledWith(
        "google",
        "/account/orders/MNM-1001?pay=1",
      ),
    );
  });

  it("falls back to the account page for an external redirect target", async () => {
    searchMock.redirect = "https://evil.example/steal";
    authMock.signInWithOAuth.mockResolvedValueOnce({ error: null });
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));
    await waitFor(() =>
      expect(authMock.signInWithOAuth).toHaveBeenCalledWith("google", "/account"),
    );
  });

  it("resets OAuth busy state when provider redirects fail", async () => {
    authMock.signInWithOAuth.mockImplementationOnce(() => {
      throw new Error("blocked");
    });
    renderLogin();

    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));

    expect((screen.getByRole("button", { name: "Continue with Google" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("surfaces OAuth provider errors from resolved responses", async () => {
    authMock.signInWithOAuth.mockResolvedValueOnce({ error: "Google unavailable" });
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));
    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith("Google unavailable", { id: "oauth-error" }),
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
    }    );
  });

  it("forgotPassword_flow_sendsResetAndShowsConfirmation", async () => {
    authMock.requestPasswordReset.mockResolvedValueOnce({ error: null });
    renderLogin();

    await userEvent.click(screen.getByRole("button", { name: "Forgot password?" }));
    expect(screen.getByRole("heading", { name: "Reset password" })).toBeTruthy();

    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "parent@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Send reset link" }));

    await waitFor(() =>
      expect(authMock.requestPasswordReset).toHaveBeenCalledWith("parent@example.com")
    );
    expect(screen.getByText(/reset link is on its way/i)).toBeTruthy();
  });

  it("forgotPassword_invalidEmail_showsValidationError", async () => {
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Forgot password?" }));
    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "not-an-email");
    await userEvent.click(screen.getByRole("button", { name: "Send reset link" }));

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith(expect.stringMatching(/email/i), { id: "forgot-password-error" })
    );
    expect(authMock.requestPasswordReset).not.toHaveBeenCalled();
  });

  it("togglesPasswordVisibility", async () => {
    renderLogin();
    const password = screen.getByPlaceholderText("Password (min 6)") as HTMLInputElement;
    expect(password.type).toBe("password");

    await userEvent.click(screen.getByRole("button", { name: "Show password" }));
    expect(password.type).toBe("text");

    await userEvent.click(screen.getByRole("button", { name: "Hide password" }));
    expect(password.type).toBe("password");
  });

  it("backToSignIn_fromForgotMode", async () => {
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Forgot password?" }));
    await userEvent.click(screen.getByRole("button", { name: "Back to sign in" }));
    expect(screen.getByRole("heading", { name: "Sign in" })).toBeTruthy();
  });

  it("validates email and password before sign in", async () => {
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith(expect.stringMatching(/email/i), { id: "login-error" }),
    );
    expect(authMock.signIn).not.toHaveBeenCalled();
  });

  it("shows forgot password API errors", async () => {
    authMock.requestPasswordReset.mockResolvedValueOnce({ error: "Rate limited" });
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Forgot password?" }));
    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "parent@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Send reset link" }));
    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith("Rate limited", { id: "forgot-password-error" }),
    );
  });

  it("resend activation flow handles success, validation, and API errors", async () => {
    authMock.signIn.mockResolvedValueOnce({ error: "Account is not activated yet" });
    authMock.resendActivationEmail.mockResolvedValueOnce({ error: null });
    renderLogin();

    await userEvent.type(screen.getByPlaceholderText("you@email.com"), "parent@example.com");
    await userEvent.type(screen.getByPlaceholderText("Password (min 6)"), "secret1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Resend activation email" })).toBeTruthy(),
    );

    await userEvent.click(screen.getByRole("button", { name: "Resend activation email" }));
    await waitFor(() =>
      expect(authMock.resendActivationEmail).toHaveBeenCalledWith("parent@example.com"),
    );
    expect(toastMock.success).toHaveBeenCalledWith(
      "If your account is not active yet, a new activation link is on its way.",
    );

    authMock.resendActivationEmail.mockResolvedValueOnce({ error: "Mailbox unavailable" });
    await userEvent.click(screen.getByRole("button", { name: "Resend activation email" }));
    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith("Mailbox unavailable", { id: "resend-activation-error" }),
    );

    await userEvent.clear(screen.getByPlaceholderText("you@email.com"));
    await userEvent.click(screen.getByRole("button", { name: "Resend activation email" }));
    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith(expect.stringMatching(/email/i), {
        id: "resend-activation-error",
      }),
    );
  });

  it("validates forgot-password email on blur", async () => {
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Forgot password?" }));
    const email = screen.getByPlaceholderText("you@email.com");
    await userEvent.type(email, "bad");
    await userEvent.tab();
    expect(screen.getByText(/valid email/i)).toBeInTheDocument();
  });

  it("starts Apple OAuth when enabled", async () => {
    oauthEnabledMock.mockImplementation((provider: string) => provider === "apple");
    authMock.signInWithOAuth.mockResolvedValueOnce({ error: null });
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Continue with Apple" }));
    await waitFor(() =>
      expect(authMock.signInWithOAuth).toHaveBeenCalledWith("apple", "/account"),
    );
    oauthEnabledMock.mockImplementation((provider: string) => provider === "google");
  });

  it("starts Facebook OAuth when enabled", async () => {
    oauthEnabledMock.mockImplementation((provider: string) => provider === "facebook");
    authMock.signInWithOAuth.mockResolvedValueOnce({ error: null });
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: "Continue with Facebook" }));
    await waitFor(() =>
      expect(authMock.signInWithOAuth).toHaveBeenCalledWith("facebook", "/account"),
    );
    oauthEnabledMock.mockImplementation((provider: string) => provider === "google");
  });
});
