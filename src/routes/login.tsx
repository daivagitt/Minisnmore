import { createFileRoute, Link, useNavigate, useSearch } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/hooks/useAuth";
import { oauthCallbackErrorMessage } from "@/lib/auth-errors";
import { fullNameFieldError } from "@/lib/orders.functions";
import { emailFieldError, passwordFieldError } from "@/lib/password-validation";
import { resolveInternalSitePath } from "@/lib/internal-links";
import { isOAuthProviderEnabled } from "@/lib/oauth-providers";
import { savePendingProfile } from "@/lib/pending-profile";
import { toast } from "sonner";
import { z } from "zod";
import { Eye, EyeOff } from "lucide-react";
import { SubscribeOptIn } from "@/components/SubscribeOptIn";
import loginHero from "@/assets/hero-smiling-baby.jpg";


const Search = z.object({
  redirect: z.string().optional(),
  error: z.string().optional(),
});

export const Route = createFileRoute("/login")({
  validateSearch: (s) => Search.parse(s),
  head: () => ({
    meta: [{ title: "Sign in · Mini's n More" }],
    links: [{ rel: "preload", as: "image", href: loginHero, fetchPriority: "high" }],
  }),
  component: LoginPage,
});

export function LoginPage() {
  const { signIn, signUp, signInWithOAuth, requestPasswordReset, resendActivationEmail, user, loading } = useAuth();
  const defaultAfterLogin = "/account";
  const nav = useNavigate();
  const search = useSearch({ from: "/login" });
  const afterLogin = resolveInternalSitePath(search.redirect, defaultAfterLogin);
  const [mode, setMode] = useState<"signin" | "signup" | "forgot">("signin");
  const [fullName, setFullName] = useState("");
  const [fullNameError, setFullNameError] = useState<string | undefined>();
  const [fullNameTouched, setFullNameTouched] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | undefined>();
  const [passwordTouched, setPasswordTouched] = useState(false);
  const [emailError, setEmailError] = useState<string | undefined>();
  const [emailTouched, setEmailTouched] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [forgotBusy, setForgotBusy] = useState(false);
  const [forgotSent, setForgotSent] = useState(false);
  const [googleBusy, setGoogleBusy] = useState(false);
  const [signInError, setSignInError] = useState<string | null>(null);
  const [resendBusy, setResendBusy] = useState(false);
  const [subscribed, setSubscribed] = useState(false);
  const oauthErrorShown = useRef(false);

  useEffect(() => {
    if (!loading && user) {
      void nav({ to: afterLogin as any });
    }
  }, [user, loading, nav, afterLogin]);

  useEffect(() => {
    if (!search.error || oauthErrorShown.current) return;
    oauthErrorShown.current = true;
    toast.error(oauthCallbackErrorMessage(search.error), { id: "oauth-error" });
    // Clear the error from the URL so refresh doesn't re-toast forever.
    void nav({
      to: "/login",
      search: search.redirect ? { redirect: search.redirect } : {},
      replace: true,
    });
  }, [nav, search.error, search.redirect]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const nextEmailError = emailFieldError(email);
    const nextPasswordError = passwordFieldError(password);
    setEmailError(nextEmailError);
    setPasswordError(nextPasswordError);
    setEmailTouched(true);
    setPasswordTouched(true);
    if (mode === "signup") {
      setFullNameTouched(true);
      const nameError = fullNameFieldError(fullName);
      setFullNameError(nameError);
      if (nameError) {
        toast.error(nameError, { id: "login-error" });
        return;
      }
    }
    if (nextEmailError || nextPasswordError) {
      toast.error(nextEmailError ?? nextPasswordError, { id: "login-error" });
      return;
    }
    setBusy(true);
    setSignInError(null);
    const { error } = mode === "signin"
      ? await signIn(email, password)
      : await signUp(email, password, fullName.trim(), subscribed);
    setBusy(false);
    if (error) {
      if (mode === "signin") setSignInError(error);
      toast.error(error, { id: "login-error" });
      return;
    }
    if (mode === "signup") {
      savePendingProfile({
        full_name: fullName.trim(),
        email: email.trim().toLowerCase(),
        subscribed,
      });
      toast.success("Almost there — check your inbox and open the activation link to finish.");
      setMode("signin");
      return;
    }
    nav({ to: afterLogin as any });
  };

  const onForgotSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const nextEmailError = emailFieldError(email);
    setEmailError(nextEmailError);
    setEmailTouched(true);
    if (nextEmailError) {
      toast.error(nextEmailError, { id: "forgot-password-error" });
      return;
    }
    setForgotBusy(true);
    const { error } = await requestPasswordReset(email);
    setForgotBusy(false);
    if (error) {
      toast.error(error, { id: "forgot-password-error" });
      return;
    }
    setForgotSent(true);
  };

  const onResendActivation = async () => {
    const nextEmailError = emailFieldError(email);
    setEmailError(nextEmailError);
    setEmailTouched(true);
    if (nextEmailError) {
      toast.error(nextEmailError, { id: "resend-activation-error" });
      return;
    }
    setResendBusy(true);
    const { error } = await resendActivationEmail(email);
    setResendBusy(false);
    if (error) {
      toast.error(error, { id: "resend-activation-error" });
      return;
    }
    toast.success("If your account is not active yet, a new activation link is on its way.");
  };

  const startOAuth = async (provider: "google" | "apple" | "facebook", label: string) => {
    try {
      const { error } = await signInWithOAuth(provider, afterLogin);
      if (error) toast.error(error, { id: "oauth-error" });
    } catch (err) {
      if (import.meta.env.DEV) {
        console.error(`${label} OAuth failed`, err);
      }
      toast.error(
        `${label} sign-in could not be started. Please try again or use email instead.`,
        { id: "oauth-error" },
      );
    }
  };

  const onGoogle = async () => {
    setGoogleBusy(true);
    try {
      await startOAuth("google", "Google");
    } finally {
      setGoogleBusy(false);
    }
  };

  return (
    <div className="px-3 md:px-6 pb-10">
      <section className="relative overflow-hidden rounded-[2rem] min-h-[calc(100dvh-9.5rem)] bg-[hsl(var(--secondary)/0.22)]">
        <motion.img
          src={loginHero}
          alt="Smiling baby wrapped in organic muslin"
          className="absolute inset-0 h-full w-full object-cover object-[80%_top] md:object-[74%_8%] lg:object-[70%_6%]"
          initial={{ scale: 1 }}
          animate={{ scale: 1.04 }}
          transition={{ duration: 22, ease: [0.22, 1, 0.36, 1] }}
          style={{ transformOrigin: "70% 18%" }}
        />
        <div className="absolute inset-0 bg-gradient-to-b from-background/20 via-background/45 to-background/90 lg:bg-gradient-to-r lg:from-background/92 lg:via-background/55 lg:to-transparent pointer-events-none" />
        <motion.p
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.3 }}
          className="hidden lg:block absolute bottom-10 right-10 xl:right-14 max-w-md text-right font-display text-3xl xl:text-4xl text-white drop-shadow-[0_8px_24px_rgba(40,20,10,0.45)] text-balance"
        >
          Minis today, memories forever.
        </motion.p>
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, delay: 0.1 }}
          className="relative z-10 flex min-h-[calc(100dvh-9.5rem)] items-end lg:items-center p-4 sm:p-6 lg:p-10 xl:pl-14"
        >
          <div className="w-full max-w-md">
            <p className="lg:hidden font-display text-2xl sm:text-3xl text-white drop-shadow-[0_8px_24px_rgba(40,20,10,0.45)] text-balance mb-4">
              Minis today, memories forever.
            </p>
          <div className="glass-strong rounded-[2rem] p-8 md:p-10 w-full">
        <div className="text-xs uppercase tracking-[0.28em] text-primary">Welcome</div>
        <h1 className="font-display text-3xl md:text-4xl mt-2">
          {mode === "signin" ? "Sign in" : mode === "signup" ? "Create account" : "Reset password"}
        </h1>
        <p className="text-sm text-muted-foreground mt-2">
          {mode === "signin" && "Pick up where you left off."}
          {mode === "signup" && "Save addresses, track orders, and more."}
          {mode === "forgot" && (forgotSent
            ? "Check your inbox for a link to reset your password."
            : "Enter your email and we'll send you a link to reset your password.")}
        </p>

        {mode === "forgot" ? (
          forgotSent ? (
            <div className="mt-6 rounded-2xl border border-primary/30 bg-primary/10 text-primary px-4 py-3 text-sm">
              If an account exists for {email}, a reset link is on its way.
            </div>
          ) : (
            <form onSubmit={onForgotSubmit} noValidate className="mt-6 space-y-3">
              <div>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (emailTouched) setEmailError(emailFieldError(e.target.value));
                  }}
                  onBlur={() => {
                    setEmailTouched(true);
                    setEmailError(emailFieldError(email));
                  }}
                  placeholder="you@email.com"
                  aria-invalid={!!emailError && emailTouched}
                  className={`w-full rounded-2xl glass px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-primary/40 ${emailError && emailTouched ? "ring-2 ring-destructive/50" : ""}`}
                />
                {emailError && emailTouched ? (
                  <p className="mt-1 text-xs text-destructive">{emailError}</p>
                ) : null}
              </div>
              <button disabled={forgotBusy} type="submit"
                className="w-full rounded-full bg-primary text-primary-foreground py-3 font-medium hover:shadow-glow transition disabled:opacity-60">
                {forgotBusy ? "Sending…" : "Send reset link"}
              </button>
            </form>
          )
        ) : (
          <form onSubmit={onSubmit} noValidate className="mt-6 space-y-3">
            {mode === "signup" && (
              <div>
                <input
                  required
                  maxLength={120}
                  value={fullName}
                  onChange={(e) => {
                    setFullName(e.target.value);
                    if (fullNameTouched) setFullNameError(fullNameFieldError(e.target.value));
                  }}
                  onBlur={() => {
                    setFullNameTouched(true);
                    setFullNameError(fullNameFieldError(fullName));
                  }}
                  placeholder="Full name"
                  aria-invalid={!!fullNameError && fullNameTouched}
                  className={`w-full rounded-2xl glass px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-primary/40 ${fullNameError && fullNameTouched ? "ring-2 ring-destructive/50" : ""}`}
                />
                {fullNameError && fullNameTouched ? (
                  <p className="mt-1 text-xs text-destructive">{fullNameError}</p>
                ) : null}
              </div>
            )}
            <div>
              <input
                type="email"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  if (emailTouched) setEmailError(emailFieldError(e.target.value));
                }}
                onBlur={() => {
                  setEmailTouched(true);
                  setEmailError(emailFieldError(email));
                }}
                placeholder="you@email.com"
                aria-invalid={!!emailError && emailTouched}
                className={`w-full rounded-2xl glass px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-primary/40 ${emailError && emailTouched ? "ring-2 ring-destructive/50" : ""}`}
              />
              {emailError && emailTouched ? (
                <p className="mt-1 text-xs text-destructive">{emailError}</p>
              ) : null}
            </div>
            <div>
              <div className="relative">
                <input
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (passwordTouched) setPasswordError(passwordFieldError(e.target.value));
                  }}
                  onBlur={() => {
                    setPasswordTouched(true);
                    setPasswordError(passwordFieldError(password));
                  }}
                  placeholder="Password (min 6)"
                  aria-invalid={!!passwordError && passwordTouched}
                  className={`w-full rounded-2xl glass px-4 py-3 pr-11 text-sm outline-none focus:ring-2 focus:ring-primary/40 ${passwordError && passwordTouched ? "ring-2 ring-destructive/50" : ""}`}
                />
                <button type="button" onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground">
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {passwordError && passwordTouched ? (
                <p className="mt-1 text-xs text-destructive">{passwordError}</p>
              ) : null}
            </div>
            {mode === "signup" && (
              <SubscribeOptIn checked={subscribed} onChange={setSubscribed} />
            )}
            {mode === "signin" && (
              <div className="text-right -mt-1 space-y-1">
                <button type="button" onClick={() => { setMode("forgot"); setForgotSent(false); setSignInError(null); }} className="text-xs story-link text-primary">
                  Forgot password?
                </button>
                {signInError && /not activated/i.test(signInError) ? (
                  <div>
                    <button
                      type="button"
                      onClick={onResendActivation}
                      disabled={resendBusy}
                      className="text-xs story-link text-primary disabled:opacity-60"
                    >
                      {resendBusy ? "Sending activation email…" : "Resend activation email"}
                    </button>
                  </div>
                ) : null}
              </div>
            )}
            <button disabled={busy} type="submit"
              className="w-full rounded-full bg-primary text-primary-foreground py-3 font-medium hover:shadow-glow transition disabled:opacity-60">
              {busy ? "Please wait…" : mode === "signin" ? "Sign in" : "Create account"}
            </button>
          </form>
        )}

        {mode !== "forgot" && (
          <>
            <div className="my-5 flex items-center gap-3 text-[11px] uppercase tracking-[0.22em] text-muted-foreground">
              <div className="h-px flex-1 bg-border" /> or <div className="h-px flex-1 bg-border" />
            </div>
            <button type="button" onClick={onGoogle} disabled={googleBusy}
              className="w-full inline-flex items-center justify-center gap-2 rounded-full glass border border-border px-5 py-3 text-sm font-medium hover:bg-white/60 transition disabled:opacity-60">
              <svg className="h-4 w-4" viewBox="0 0 48 48" aria-hidden><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.6 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.2-.1-2.3-.4-3.5z"/><path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.6 16 18.9 13 24 13c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.4 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.8-2 13.3-5.2l-6.1-5c-2 1.4-4.5 2.2-7.2 2.2-5.2 0-9.6-3.3-11.2-8l-6.5 5C9.5 39.6 16.2 44 24 44z"/><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.3-2.3 4.3-4.2 5.8l6.1 5C40.9 36 44 30.5 44 24c0-1.2-.1-2.3-.4-3.5z"/></svg>
              {googleBusy ? "Opening Google…" : "Continue with Google"}
            </button>
            {isOAuthProviderEnabled("apple") ? (
              <button type="button" onClick={() => startOAuth("apple", "Apple")} disabled={googleBusy}
                className="mt-2 w-full inline-flex items-center justify-center gap-2 rounded-full bg-black text-white px-5 py-3 text-sm font-medium hover:bg-black/90 transition disabled:opacity-60">
                <svg className="h-4 w-4" viewBox="0 0 24 24" aria-hidden fill="currentColor"><path d="M16.365 1.43c0 1.14-.42 2.22-1.12 3.01-.76.86-2.02 1.53-3.05 1.45-.14-1.12.4-2.3 1.07-3.04.77-.85 2.08-1.5 3.1-1.42zM20.5 17.27c-.55 1.27-.82 1.84-1.53 2.96-1 1.56-2.4 3.5-4.14 3.51-1.55.02-1.95-1.01-4.05-1-2.1.01-2.54 1.02-4.09 1-1.74-.02-3.07-1.77-4.06-3.33C-.06 16.8-.34 11.43 2.06 8.55c1.5-1.8 3.86-2.85 6.08-2.85 2.27 0 3.7 1.24 5.57 1.24 1.82 0 2.92-1.24 5.55-1.24 1.98 0 4.08 1.08 5.58 2.94-4.9 2.69-4.11 9.69-4.34 8.63z"/></svg>
                Continue with Apple
              </button>
            ) : null}
            {isOAuthProviderEnabled("facebook") ? (
              <button type="button" onClick={() => startOAuth("facebook", "Facebook")} disabled={googleBusy}
                className="mt-2 w-full inline-flex items-center justify-center gap-2 rounded-full bg-[#1877F2] text-white px-5 py-3 text-sm font-medium hover:bg-[#1877F2]/90 transition disabled:opacity-60">
                <svg className="h-4 w-4" viewBox="0 0 24 24" aria-hidden fill="currentColor"><path d="M22 12a10 10 0 1 0-11.56 9.88v-6.99H7.9V12h2.54V9.8c0-2.51 1.49-3.9 3.78-3.9 1.1 0 2.24.2 2.24.2v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56V12h2.77l-.44 2.89h-2.33v6.99A10 10 0 0 0 22 12z"/></svg>
                Continue with Facebook
              </button>
            ) : null}
          </>
        )}
        <div className="mt-4 text-sm text-center">
          {mode === "forgot" ? (
            <button onClick={() => setMode("signin")} className="story-link text-primary">
              Back to sign in
            </button>
          ) : (
            <button onClick={() => {
              setMode(mode === "signin" ? "signup" : "signin");
              setSignInError(null);
              setFullNameError(undefined);
              setFullNameTouched(false);
              setEmailError(undefined);
              setEmailTouched(false);
              setPasswordError(undefined);
              setPasswordTouched(false);
              setSubscribed(false);
            }} className="story-link text-primary">
              {mode === "signin" ? "New here? Create an account" : "Have an account? Sign in"}
            </button>
          )}
        </div>
        <div className="mt-6 text-center">
          <Link to="/" className="text-xs text-muted-foreground story-link">← Back to store</Link>
        </div>
          </div>
          </div>
        </motion.div>
      </section>
    </div>
  );
}
