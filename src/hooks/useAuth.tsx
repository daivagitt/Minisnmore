import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { api, backendOAuthUrl, getAuthToken, setAuthToken, ApiError } from "@/lib/api";

export type AuthUser = {
  id: number;
  login: string;
  email: string | null;
  firstName?: string | null;
  lastName?: string | null;
  activated?: boolean;
};

export type AuthSession = {
  access_token: string;
};

type AccountDto = AuthUser & {
  authorities?: string[];
};

type Ctx = {
  user: AuthUser | null;
  session: AuthSession | null;
  isAdmin: boolean;
  loading: boolean;
  signIn: (email: string, password: string) => Promise<{ error: string | null }>;
  signUp: (email: string, password: string, fullName?: string) => Promise<{ error: string | null }>;
  signInWithOAuth: (provider: "google" | "apple" | "facebook", redirectPath?: string) => Promise<{ error: string | null }>;
  signOut: () => Promise<void>;
  completeOAuthSignIn: (token: string) => Promise<{ error: string | null }>;
  resendActivationEmail: (email: string) => Promise<{ error: string | null }>;
  refreshSession: () => Promise<void>;
};

const AuthCtx = createContext<Ctx | null>(null);

function authErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      const detail =
        typeof error.body === "object" &&
        error.body !== null &&
        "detail" in error.body &&
        typeof (error.body as { detail?: unknown }).detail === "string"
          ? (error.body as { detail: string }).detail
          : "";
      if (/not activated/i.test(detail)) {
        return "Your account is not activated yet. Open the activation link from your email, or resend it below.";
      }
      return "Invalid email or password. New accounts must open the activation link from email before signing in.";
    }
    return error.message;
  }
  if (error instanceof Error && error.message) return error.message;
  return "Something went wrong. Please try again.";
}

async function loadAccountFromToken(token: string): Promise<{ user: AuthUser; isAdmin: boolean } | null> {
  setAuthToken(token);
  try {
    const account = await api.get<AccountDto>("/api/account", { auth: true, unwrap: false });
    const authorities = account.authorities ?? [];
    return {
      user: {
        id: account.id,
        login: account.login,
        email: account.email ?? null,
        firstName: account.firstName,
        lastName: account.lastName,
        activated: account.activated,
      },
      isAdmin: authorities.includes("ROLE_ADMIN"),
    };
  } catch {
    setAuthToken(null);
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [session, setSession] = useState<AuthSession | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);
  const refreshRef = useRef(0);

  const applyAccount = useCallback((account: { user: AuthUser; isAdmin: boolean } | null, token: string | null) => {
    if (!account || !token) {
      setUser(null);
      setSession(null);
      setIsAdmin(false);
      return;
    }
    setUser(account.user);
    setSession({ access_token: token });
    setIsAdmin(account.isAdmin);
  }, []);

  const refreshSession = useCallback(async () => {
    const token = getAuthToken();
    if (!token) {
      applyAccount(null, null);
      return;
    }
    const account = await loadAccountFromToken(token);
    applyAccount(account, account ? token : null);
  }, [applyAccount]);

  useEffect(() => {
    let alive = true;
    const run = async () => {
      const token = getAuthToken();
      if (!token) {
        if (alive) {
          applyAccount(null, null);
          setLoading(false);
        }
        return;
      }
      const account = await loadAccountFromToken(token);
      if (alive) {
        applyAccount(account, account ? token : null);
        setLoading(false);
      }
    };
    void run();
    return () => {
      alive = false;
    };
  }, [applyAccount]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const orig = window.fetch.bind(window);
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      try {
        const url =
          typeof input === "string"
            ? input
            : input instanceof URL
              ? input.toString()
              : input.url;
        if (url?.includes("/_serverFn/")) {
          const token = getAuthToken();
          if (token) {
            const headers = new Headers(
              init?.headers || (input instanceof Request ? input.headers : undefined),
            );
            if (!headers.has("authorization")) {
              headers.set("authorization", `Bearer ${token}`);
            }
            return orig(input, { ...(init || {}), headers });
          }
        }
      } catch (err) {
        if (import.meta.env.DEV) {
          console.error("useAuth fetch interceptor error", err);
        }
      }
      return orig(input, init);
    };
    return () => {
      window.fetch = orig;
    };
  }, []);

  const signIn = async (email: string, password: string) => {
    try {
      const body = await api.post<{ id_token?: string }>(
        "/api/authenticate",
        { username: email.trim(), password, rememberMe: true },
        { unwrap: false },
      );
      const token = body?.id_token;
      if (!token) return { error: "Sign in failed. Please try again." };
      const account = await loadAccountFromToken(token);
      if (!account) return { error: "Sign in failed. Please try again." };
      applyAccount(account, token);
      return { error: null };
    } catch (error) {
      return { error: authErrorMessage(error) };
    }
  };

  const signUp = async (email: string, password: string, fullName?: string) => {
    const trimmed = email.trim().toLowerCase();
    const parts = (fullName ?? "").trim().split(/\s+/).filter(Boolean);
    const firstName = parts[0] ?? "";
    const lastName = parts.slice(1).join(" ") || firstName;
    try {
      await api.post(
        "/api/register",
        {
          login: trimmed,
          email: trimmed,
          password,
          firstName,
          lastName,
          langKey: "en",
        },
        { unwrap: false },
      );
      return { error: null };
    } catch (error) {
      return { error: authErrorMessage(error) };
    }
  };

  const signOut = async () => {
    setAuthToken(null);
    applyAccount(null, null);
    refreshRef.current += 1;
  };

  const signInWithOAuth: Ctx["signInWithOAuth"] = async (provider, redirectPath = "/account") => {
    if (typeof window === "undefined") return { error: "OAuth is only available in the browser." };
    try {
      window.sessionStorage.setItem("mnm_oauth_redirect", redirectPath);
      window.location.href = backendOAuthUrl(provider);
      return { error: null };
    } catch (error) {
      return { error: authErrorMessage(error) };
    }
  };

  const completeOAuthSignIn = async (token: string) => {
    const account = await loadAccountFromToken(token);
    if (!account) return { error: "Sign in failed. Please try again." };
    applyAccount(account, token);
    return { error: null };
  };

  const resendActivationEmail = async (email: string) => {
    const trimmed = email.trim().toLowerCase();
    if (!trimmed) return { error: "Enter your email address first." };
    try {
      await api.post("/api/account/resend-activation", JSON.stringify(trimmed), {
        unwrap: false,
        headers: { "Content-Type": "application/json" },
      });
      return { error: null };
    } catch (error) {
      return { error: authErrorMessage(error) };
    }
  };

  return (
    <AuthCtx.Provider
      value={{
        user,
        session,
        isAdmin,
        loading,
        signIn,
        signUp,
        signInWithOAuth,
        signOut,
        completeOAuthSignIn,
        resendActivationEmail,
        refreshSession,
      }}
    >
      {children}
    </AuthCtx.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
