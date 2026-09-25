import { render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "./useAuth";

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@/lib/api", () => ({
  api: apiMock,
  backendOAuthUrl: (provider: string) => `https://api.test/oauth2/authorization/${provider}`,
  getAuthToken: vi.fn(() => localStorage.getItem("mnm_backend_jwt")),
  setAuthToken: vi.fn((token: string | null) => {
    if (token) localStorage.setItem("mnm_backend_jwt", token);
    else localStorage.removeItem("mnm_backend_jwt");
  }),
  ApiError: class ApiError extends Error {
    status = 400;
    body = null;
    constructor(message: string) {
      super(message);
      this.name = "ApiError";
    }
  },
}));

function Harness() {
  const auth = useAuth();
  return (
    <div>
      <span data-testid="loading">{String(auth.loading)}</span>
      <span data-testid="user">{auth.user?.email ?? "anonymous"}</span>
      <span data-testid="admin">{String(auth.isAdmin)}</span>
      <span data-testid="session">{auth.session?.access_token ?? "none"}</span>
      <button onClick={() => auth.signIn("admin@example.com", "secret")}>sign in</button>
      <button onClick={() => auth.signUp("new@example.com", "secret", "New Parent")}>sign up</button>
      <button onClick={() => auth.signOut()}>sign out</button>
      <button onClick={() => auth.signInWithOAuth("google", "/account")}>oauth</button>
      <button onClick={() => auth.completeOAuthSignIn("oauth-token")}>complete oauth</button>
      <button onClick={() => auth.refreshSession()}>refresh</button>
      <button onClick={() => auth.requestPasswordReset("reset@example.com")}>reset password</button>
      <button onClick={() => auth.resendActivationEmail("activate@example.com")}>resend activation</button>
      <button onClick={() => auth.resendActivationEmail("  ")}>resend activation empty</button>
      <button onClick={() => auth.completePasswordReset("reset-key", "new-secret")}>finish reset</button>
    </div>
  );
}

function renderAuth() {
  return render(
    <AuthProvider>
      <Harness />
    </AuthProvider>,
  );
}

describe("useAuth", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    apiMock.get.mockResolvedValue({
      id: 1,
      login: "parent@example.com",
      email: "parent@example.com",
      authorities: ["ROLE_ADMIN"],
    });
    apiMock.post.mockImplementation(async (path: string) => {
      if (path === "/api/authenticate") return { id_token: "token-1" };
      return null;
    });
  });

  it("refreshes the session from storage and clears it when missing", async () => {
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    localStorage.setItem("mnm_backend_jwt", "token-2");
    apiMock.get.mockResolvedValueOnce({
      id: 2,
      login: "refreshed@example.com",
      email: "refreshed@example.com",
      authorities: [],
    });
    await userEvent.click(screen.getByRole("button", { name: "refresh" }));
    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("refreshed@example.com"));

    localStorage.removeItem("mnm_backend_jwt");
    await userEvent.click(screen.getByRole("button", { name: "refresh" }));
    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("anonymous"));
  });

  it("loads account from stored JWT and resolves admin state", async () => {
    localStorage.setItem("mnm_backend_jwt", "token-1");
    renderAuth();

    await waitFor(() => expect(screen.getByTestId("admin")).toHaveTextContent("true"));
    expect(screen.getByTestId("user")).toHaveTextContent("parent@example.com");
    expect(screen.getByTestId("session")).toHaveTextContent("token-1");
    expect(apiMock.get).toHaveBeenCalledWith("/api/account", { auth: true, unwrap: false });
  });

  it("exposes signIn/signUp/signOut/oauth wrappers", async () => {
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await userEvent.click(screen.getByRole("button", { name: "sign in" }));
    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/authenticate",
      { username: "admin@example.com", password: "secret", rememberMe: true },
      { unwrap: false },
    );

    await userEvent.click(screen.getByRole("button", { name: "sign up" }));
    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/register",
      expect.objectContaining({
        login: "new@example.com",
        email: "new@example.com",
        password: "secret",
        subscribed: false,
      }),
      { unwrap: false },
    );

    await userEvent.click(screen.getByRole("button", { name: "sign out" }));
    expect(localStorage.getItem("mnm_backend_jwt")).toBeNull();

    const prev = window.location;
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { ...prev, href: "" },
    });
    await userEvent.click(screen.getByRole("button", { name: "oauth" }));
    expect(window.sessionStorage.getItem("mnm_oauth_redirect")).toBe("/account");
    expect(window.location.href).toBe("https://api.test/oauth2/authorization/google");
    Object.defineProperty(window, "location", { configurable: true, value: prev });
  });

  it("requests password reset through account API", async () => {
    apiMock.post.mockResolvedValueOnce(null);
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await userEvent.click(screen.getByRole("button", { name: "reset password" }));
    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/account/reset-password/init",
      JSON.stringify("reset@example.com"),
      expect.objectContaining({ unwrap: false }),
    );
  });

  it("resends activation email and validates empty input", async () => {
    apiMock.post.mockResolvedValueOnce(null);
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await expect(result.current.resendActivationEmail("activate@example.com")).resolves.toEqual({
      error: null,
    });
    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/account/resend-activation",
      JSON.stringify("activate@example.com"),
      expect.objectContaining({
        unwrap: false,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(result.current.resendActivationEmail("   ")).resolves.toEqual({
      error: "Enter your email address first.",
    });
  });

  it("reports resend activation and password reset API failures", async () => {
    const { ApiError } = await import("@/lib/api");
    apiMock.post.mockRejectedValueOnce(new ApiError("Activation email could not be sent"));
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await expect(result.current.resendActivationEmail("activate@example.com")).resolves.toEqual({
      error: "Activation email could not be sent",
    });

    apiMock.post.mockRejectedValueOnce(new ApiError("Reset request failed"));
    await expect(result.current.requestPasswordReset("reset@example.com")).resolves.toEqual({
      error: "Reset request failed",
    });
  });

  it("completes password reset through account API", async () => {
    apiMock.post.mockResolvedValueOnce(null);
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await userEvent.click(screen.getByRole("button", { name: "finish reset" }));
    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/account/reset-password/finish",
      { key: "reset-key", newPassword: "new-secret" },
      { unwrap: false },
    );
  });

  it("reports password reset completion failures", async () => {
    const { ApiError } = await import("@/lib/api");
    apiMock.post.mockRejectedValueOnce(new ApiError("Reset key expired"));
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await expect(result.current.completePasswordReset("stale-key", "new-secret")).resolves.toEqual({
      error: "Reset key expired",
    });
  });

  it("throws when useAuth is called outside AuthProvider", () => {
    expect(() => renderHook(() => useAuth())).toThrow("useAuth must be used inside AuthProvider");
  });

  it("clears invalid stored tokens during bootstrap", async () => {
    localStorage.setItem("mnm_backend_jwt", "stale-token");
    apiMock.get.mockRejectedValueOnce(new Error("unauthorized"));
    renderAuth();

    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));
    expect(screen.getByTestId("user")).toHaveTextContent("anonymous");
    expect(localStorage.getItem("mnm_backend_jwt")).toBeNull();
  });

  it("reports sign-in failures from the API", async () => {
    const { ApiError } = await import("@/lib/api");
    apiMock.post.mockRejectedValueOnce(new ApiError("Bad credentials"));
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await expect(result.current.signIn("bad@example.com", "nope")).resolves.toEqual({
      error: "Bad credentials",
    });
  });

  it("reports sign-up failures and completes OAuth sign-in", async () => {
    apiMock.post.mockImplementation(async (path: string) => {
      if (path === "/api/register") throw new Error("Email taken");
      return null;
    });
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await expect(result.current.signUp("taken@example.com", "secret")).resolves.toEqual({
      error: "Email taken",
    });

    apiMock.get.mockResolvedValueOnce({
      id: 2,
      login: "oauth@example.com",
      email: "oauth@example.com",
      authorities: [],
    });
    await expect(result.current.completeOAuthSignIn("oauth-token")).resolves.toEqual({ error: null });
    await waitFor(() => expect(result.current.user?.email).toBe("oauth@example.com"));
  });

  it("handles sign-in responses without tokens or accounts", async () => {
    apiMock.post.mockResolvedValueOnce({});
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));
    await expect(result.current.signIn("ghost@example.com", "secret")).resolves.toEqual({
      error: "Sign in failed. Please try again.",
    });

    apiMock.post.mockResolvedValueOnce({ id_token: "bad-token" });
    apiMock.get.mockRejectedValueOnce(new Error("account missing"));
    await expect(result.current.signIn("ghost@example.com", "secret")).resolves.toEqual({
      error: "Sign in failed. Please try again.",
    });
  });

  it("handles OAuth completion failures and sign-up without a full name", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    apiMock.get.mockRejectedValueOnce(new Error("bad token"));
    await expect(result.current.completeOAuthSignIn("bad-token")).resolves.toEqual({
      error: "Sign in failed. Please try again.",
    });

    apiMock.post.mockResolvedValueOnce(null);
    await expect(result.current.signUp("solo@example.com", "secret")).resolves.toEqual({ error: null });
    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/register",
      expect.objectContaining({ firstName: "", lastName: "", subscribed: false }),
      { unwrap: false },
    );
  });

  it("loads non-admin accounts and preserves existing authorization headers", async () => {
    const fetchImpl = vi.fn(async () => new Response("{}"));
    vi.stubGlobal("fetch", fetchImpl);
    localStorage.setItem("mnm_backend_jwt", "token-1");
    apiMock.get.mockResolvedValueOnce({
      id: 3,
      login: "shopper@example.com",
      email: "shopper@example.com",
      authorities: undefined,
    });
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("admin")).toHaveTextContent("false"));

    await window.fetch("/_serverFn/validateCoupon", {
      method: "POST",
      headers: { authorization: "Bearer existing" },
    });
    const preserved = new Headers(fetchImpl.mock.calls.at(-1)?.[1]?.headers);
    expect(preserved.get("authorization")).toBe("Bearer existing");
    vi.unstubAllGlobals();
  });

  it("reads headers from Request objects for server function calls", async () => {
    const fetchImpl = vi.fn(async () => new Response("{}"));
    vi.stubGlobal("fetch", fetchImpl);
    localStorage.setItem("mnm_backend_jwt", "token-1");
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    const request = new Request("http://localhost/_serverFn/from-request", {
      method: "POST",
      headers: { authorization: "Bearer preset" },
    });
    await window.fetch(request);
    const headers = new Headers(fetchImpl.mock.calls.at(-1)?.[1]?.headers);
    expect(headers.get("authorization")).toBe("Bearer preset");
    vi.unstubAllGlobals();
  });

  it("supports URL-based server function fetch requests", async () => {
    const fetchImpl = vi.fn(async () => new Response("{}"));
    vi.stubGlobal("fetch", fetchImpl);
    localStorage.setItem("mnm_backend_jwt", "token-1");
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await window.fetch(new URL("http://localhost/_serverFn/from-url"));
    const headers = new Headers(fetchImpl.mock.calls.at(-1)?.[1]?.headers);
    expect(headers.get("authorization")).toBe("Bearer token-1");
    vi.unstubAllGlobals();
  });

  it("injects authorization headers for server function fetch calls", async () => {
    const fetchImpl = vi.fn(async () => new Response("{}"));
    vi.stubGlobal("fetch", fetchImpl);
    localStorage.setItem("mnm_backend_jwt", "token-1");
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await window.fetch("/_serverFn/validateCoupon", { method: "POST" });

    const [, init] = fetchImpl.mock.calls.at(-1) ?? [];
    expect(new Headers(init?.headers).get("authorization")).toBe("Bearer token-1");
    vi.unstubAllGlobals();
  });

  it("maps unknown auth errors and OAuth storage failures", async () => {
    apiMock.post.mockRejectedValueOnce("network-down");
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));
    await expect(result.current.signIn("x@example.com", "secret")).resolves.toEqual({
      error: "Something went wrong. Please try again.",
    });

    const prevLocation = window.location;
    Object.defineProperty(window, "location", {
      configurable: true,
      value: {
        ...prevLocation,
        set href(_value: string) {
          throw new Error("redirect blocked");
        },
        get href() {
          return "";
        },
      },
    });
    await expect(result.current.signInWithOAuth("google", "/account")).resolves.toEqual({
      error: "redirect blocked",
    });
    await expect(result.current.signInWithOAuth("facebook", "/account")).resolves.toEqual({
      error: "This sign-in option is not available right now.",
    });
    Object.defineProperty(window, "location", { configurable: true, value: prevLocation });
  });

  it("bootstraps profile from pending signup data when a session loads", async () => {
    sessionStorage.setItem(
      "mnm_pending_profile",
      JSON.stringify({ full_name: "New Parent", email: "parent@example.com" }),
    );
    localStorage.setItem("mnm_backend_jwt", "token-1");
    apiMock.get.mockResolvedValue({
      id: 1,
      login: "parent@example.com",
      email: "parent@example.com",
      firstName: "Jane",
      lastName: "Doe",
      authorities: [],
    });
    apiMock.post.mockImplementation(async (path: string, body?: { full_name?: string; email?: string }) => {
      if (path === "/api/v1/account/profile/bootstrap") return { ok: true };
      if (path === "/api/authenticate") return { id_token: "token-1" };
      return null;
    });

    renderAuth();
    await waitFor(() =>
      expect(apiMock.post).toHaveBeenCalledWith(
        "/api/v1/account/profile/bootstrap",
        { full_name: "New Parent", email: "parent@example.com" },
        { auth: true },
      ),
    );
    expect(sessionStorage.getItem("mnm_pending_profile")).toBeNull();
  });

  it("bootstraps a subscribe preference from pending signup data", async () => {
    sessionStorage.setItem(
      "mnm_pending_profile",
      JSON.stringify({ full_name: "New Parent", email: "parent@example.com", subscribed: true }),
    );
    localStorage.setItem("mnm_backend_jwt", "token-1");
    apiMock.get.mockResolvedValue({
      id: 1,
      login: "parent@example.com",
      email: "parent@example.com",
      firstName: "Jane",
      lastName: "Doe",
      authorities: [],
    });
    apiMock.post.mockImplementation(async (path: string) => {
      if (path === "/api/v1/account/profile/bootstrap") return { ok: true };
      return null;
    });

    renderAuth();
    await waitFor(() =>
      expect(apiMock.post).toHaveBeenCalledWith(
        "/api/v1/account/profile/bootstrap",
        { full_name: "New Parent", email: "parent@example.com", subscribed: true },
        { auth: true },
      ),
    );
    expect(sessionStorage.getItem("mnm_pending_profile")).toBeNull();
  });

  it("does not bootstrap administrator accounts as customers", async () => {
    sessionStorage.setItem(
      "mnm_pending_profile",
      JSON.stringify({ full_name: "Store Admin", email: "admin@example.com" }),
    );
    localStorage.setItem("mnm_backend_jwt", "admin-token");
    apiMock.get.mockResolvedValue({
      id: 7,
      login: "admin@example.com",
      email: "admin@example.com",
      firstName: "Store",
      lastName: "Admin",
      authorities: ["ROLE_ADMIN"],
    });

    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    expect(apiMock.post).not.toHaveBeenCalledWith(
      "/api/v1/account/profile/bootstrap",
      expect.anything(),
      expect.anything(),
    );
    expect(sessionStorage.getItem("mnm_pending_profile")).not.toBeNull();
  });

  it("deduplicates profile bootstrap while one request is in flight", async () => {
    sessionStorage.setItem(
      "mnm_pending_profile",
      JSON.stringify({ full_name: "New Parent", email: "parent@example.com" }),
    );
    localStorage.setItem("mnm_backend_jwt", "token-1");
    apiMock.get.mockResolvedValue({
      id: 1,
      login: "parent@example.com",
      email: "parent@example.com",
      authorities: [],
    });
    let finishBootstrap!: (value: { ok: boolean }) => void;
    const bootstrap = new Promise<{ ok: boolean }>((resolve) => {
      finishBootstrap = resolve;
    });
    apiMock.post.mockImplementation(async (path: string) => {
      if (path === "/api/v1/account/profile/bootstrap") return bootstrap;
      return null;
    });

    renderAuth();
    await waitFor(() =>
      expect(apiMock.post).toHaveBeenCalledWith(
        "/api/v1/account/profile/bootstrap",
        { full_name: "New Parent", email: "parent@example.com" },
        { auth: true },
      ),
    );
    await userEvent.click(screen.getByText("refresh"));
    await waitFor(() => expect(apiMock.get).toHaveBeenCalledTimes(2));
    expect(apiMock.post).toHaveBeenCalledTimes(1);

    finishBootstrap({ ok: true });
    await waitFor(() => expect(sessionStorage.getItem("mnm_pending_profile")).toBeNull());
  });

  it("retries profile bootstrap after a failed attempt on refresh", async () => {
    sessionStorage.setItem(
      "mnm_pending_profile",
      JSON.stringify({ full_name: "New Parent", email: "parent@example.com" }),
    );
    localStorage.setItem("mnm_backend_jwt", "token-1");
    apiMock.get.mockResolvedValue({
      id: 1,
      login: "parent@example.com",
      email: "parent@example.com",
      authorities: [],
    });
    apiMock.post.mockImplementation(async (path: string) => {
      if (path === "/api/v1/account/profile/bootstrap") throw new Error("network down");
      return null;
    });

    renderAuth();
    await waitFor(() =>
      expect(apiMock.post).toHaveBeenCalledWith(
        "/api/v1/account/profile/bootstrap",
        { full_name: "New Parent", email: "parent@example.com" },
        { auth: true },
      ),
    );
    expect(sessionStorage.getItem("mnm_pending_profile")).not.toBeNull();

    apiMock.post.mockImplementation(async (path: string) => {
      if (path === "/api/v1/account/profile/bootstrap") return { ok: true };
      return null;
    });

    await userEvent.click(screen.getByText("refresh"));
    await waitFor(() => expect(sessionStorage.getItem("mnm_pending_profile")).toBeNull());
    expect(apiMock.post).toHaveBeenCalledTimes(2);
  });

  it("clears stale pending signup data and falls back to auth user names", async () => {
    sessionStorage.setItem(
      "mnm_pending_profile",
      JSON.stringify({ full_name: "Other Parent", email: "other@example.com" }),
    );
    localStorage.setItem("mnm_backend_jwt", "token-1");
    apiMock.get.mockResolvedValue({
      id: 1,
      login: "parent@example.com",
      email: "parent@example.com",
      firstName: "Jane",
      lastName: "Doe",
      authorities: [],
    });
    apiMock.post.mockImplementation(async (path: string) => {
      if (path === "/api/v1/account/profile/bootstrap") return { ok: true };
      return null;
    });

    renderAuth();
    await waitFor(() =>
      expect(apiMock.post).toHaveBeenCalledWith(
        "/api/v1/account/profile/bootstrap",
        { full_name: "Jane Doe", email: "parent@example.com" },
        { auth: true },
      ),
    );
    expect(sessionStorage.getItem("mnm_pending_profile")).toBeNull();
  });

  it("adds auth headers to server function requests made with Request objects", async () => {
    const fetchImpl = vi.fn(async () => new Response("{}"));
    vi.stubGlobal("fetch", fetchImpl);
    localStorage.setItem("mnm_backend_jwt", "token-1");
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    const request = new Request("http://localhost/_serverFn/validateCoupon", { method: "POST" });
    await window.fetch(request);

    const [, init] = fetchImpl.mock.calls.at(-1) ?? [];
    expect(new Headers(init?.headers).get("authorization")).toBe("Bearer token-1");
    vi.unstubAllGlobals();
  });

  it("reports complete OAuth sign-in failures when account loading fails", async () => {
    apiMock.get.mockRejectedValueOnce(new Error("OAuth failed"));
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await expect(result.current.completeOAuthSignIn("oauth-token")).resolves.toEqual({
      error: "Sign in failed. Please try again.",
    });
  });

  it("logs interceptor failures in development", async () => {
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const fetchImpl = vi.fn(async () => new Response("{}"));
    vi.stubGlobal("fetch", fetchImpl);
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await window.fetch({
      get url() {
        throw new Error("bad input");
      },
    } as RequestInfo);

    expect(consoleSpy).toHaveBeenCalledWith("useAuth fetch interceptor error", expect.any(Error));
    consoleSpy.mockRestore();
    vi.unstubAllGlobals();
  });

  it("logs profile bootstrap failures in development", async () => {
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    sessionStorage.setItem(
      "mnm_pending_profile",
      JSON.stringify({ full_name: "New Parent", email: "parent@example.com" }),
    );
    localStorage.setItem("mnm_backend_jwt", "token-1");
    apiMock.get.mockResolvedValue({
      id: 1,
      login: "parent@example.com",
      email: "parent@example.com",
      authorities: [],
    });
    apiMock.post.mockImplementation(async (path: string) => {
      if (path === "/api/v1/account/profile/bootstrap") throw new Error("network down");
      return null;
    });

    renderAuth();
    await waitFor(() =>
      expect(apiMock.post).toHaveBeenCalledWith(
        "/api/v1/account/profile/bootstrap",
        { full_name: "New Parent", email: "parent@example.com" },
        { auth: true },
      ),
    );
    expect(warnSpy).toHaveBeenCalledWith("profile bootstrap failed", expect.any(Error));
    warnSpy.mockRestore();
  });

  it("clears session when refresh cannot load the account", async () => {
    localStorage.setItem("mnm_backend_jwt", "stale-token");
    apiMock.get.mockRejectedValueOnce(new Error("unauthorized"));
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    apiMock.get.mockRejectedValueOnce(new Error("still bad"));
    await userEvent.click(screen.getByRole("button", { name: "refresh" }));
    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("anonymous"));
    expect(localStorage.getItem("mnm_backend_jwt")).toBeNull();
  });

  it("uses a single-word sign-up name for both first and last name fields", async () => {
    apiMock.post.mockResolvedValueOnce(null);
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await expect(result.current.signUp("solo@example.com", "secret", "Madonna")).resolves.toEqual({
      error: null,
    });
    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/register",
      expect.objectContaining({ firstName: "Madonna", lastName: "Madonna", subscribed: false }),
      { unwrap: false },
    );
  });

  it("sends subscribe preference on register", async () => {
    apiMock.post.mockResolvedValueOnce(null);
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await expect(result.current.signUp("new@example.com", "secret", "New Parent", true)).resolves.toEqual({
      error: null,
    });
    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/register",
      expect.objectContaining({
        login: "new@example.com",
        email: "new@example.com",
        subscribed: true,
      }),
      { unwrap: false },
    );
  });

  it("ignores bootstrap results after the provider unmounts", async () => {
    apiMock.get.mockImplementation(() => new Promise(() => {}));
    localStorage.setItem("mnm_backend_jwt", "token-1");
    const { unmount } = renderAuth();
    unmount();
    await Promise.resolve();
  });

  it("validates sign-up full names and empty password-reset email", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await expect(result.current.signUp("new@example.com", "secret", "A")).resolves.toEqual({
      error: expect.stringMatching(/at least 2 characters/i),
    });
    await expect(result.current.requestPasswordReset("   ")).resolves.toEqual({
      error: "Enter your email address first.",
    });
  });

  it("injects authorization into server function calls that use plain header objects", async () => {
    const fetchImpl = vi.fn(async () => new Response("{}"));
    vi.stubGlobal("fetch", fetchImpl);
    localStorage.setItem("mnm_backend_jwt", "token-1");
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await window.fetch("/_serverFn/validateCoupon", {
      method: "POST",
      headers: { "x-test": "1" },
    });

    const [, init] = fetchImpl.mock.calls.at(-1) ?? [];
    const headers = new Headers(init?.headers);
    expect(headers.get("authorization")).toBe("Bearer token-1");
    expect(headers.get("x-test")).toBe("1");
    vi.unstubAllGlobals();
  });

  it("leaves fetch untouched for non-server requests and when no token exists", async () => {
    const fetchImpl = vi.fn(async () => new Response("{}"));
    vi.stubGlobal("fetch", fetchImpl);
    renderAuth();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await window.fetch("/api/products");
    const [, init] = fetchImpl.mock.calls.at(-1) ?? [];
    expect(new Headers(init?.headers).get("authorization")).toBeNull();
    vi.unstubAllGlobals();
  });
});
