import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { api, backendOAuthUrl, getAuthToken, setAuthToken } from "@/lib/api";
import { authErrorMessage } from "@/lib/auth-errors";
import { fullNameFieldError } from "@/lib/orders.functions";

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
  requestPasswordReset: (email: string) => Promise<{ error: string | null }>;
  completePasswordReset: (key: string, newPassword: string) => Promise<{ error: string | null }>;
  refreshSession: () => Promise<void>;
};

const AuthCtx = createContext<Ctx | null>(null);

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
    if (fullName !== undefined) {
      const nameError = fullNameFieldError(fullName);
      if (nameError) return { error: nameError };
    }
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

  const requestPasswordReset = async (email: string) => {
    const trimmed = email.trim().toLowerCase();
    if (!trimmed) return { error: "Enter your email address first." };
    try {
      await api.post("/api/account/reset-password/init", JSON.stringify(trimmed), {
        unwrap: false,
        headers: { "Content-Type": "application/json" },
      });
      return { error: null };
    } catch (error) {
      return { error: authErrorMessage(error) };
    }
  };

  const completePasswordReset = async (key: string, newPassword: string) => {
    try {
      await api.post("/api/account/reset-password/finish", { key, newPassword }, { unwrap: false });
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
        requestPasswordReset,
        completePasswordReset,
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
