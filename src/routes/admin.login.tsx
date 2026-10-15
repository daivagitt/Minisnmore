import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { useState, useEffect } from "react";
import { Eye, EyeOff } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { emailFieldError, passwordFieldError } from "@/lib/password-validation";
import { staffHomePath } from "@/lib/staff-roles";
import { toast } from "sonner";
import loginHero from "@/assets/hero-smiling-baby.jpg";

export const Route = createFileRoute("/admin/login")({
  head: () => ({
    meta: [{ title: "Staff Login · Mini's n More" }],
    links: [{ rel: "preload", as: "image", href: loginHero, fetchPriority: "high" }],
  }),
  component: AdminLogin,
});

function AdminLogin() {
  const { signIn, requestPasswordReset, user, isAdmin, staffRole, loading, signOut } = useAuth();
  const nav = useNavigate();
  const [mode, setMode] = useState<"signin" | "forgot">("signin");
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

  useEffect(() => {
    if (loading || !user) return;
    const home = staffHomePath(staffRole);
    if (home !== "/") {
      void nav({ to: home as never });
    }
  }, [user, isAdmin, staffRole, loading, nav]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const nextEmailError = emailFieldError(email);
    const nextPasswordError = passwordFieldError(password);
    setEmailError(nextEmailError);
    setPasswordError(nextPasswordError);
    setEmailTouched(true);
    setPasswordTouched(true);
    if (nextEmailError || nextPasswordError) {
      toast.error(nextEmailError ?? nextPasswordError);
      return;
    }
    setBusy(true);
    const { error } = await signIn(email, password);
    setBusy(false);
    if (error) return toast.error(error);
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

  return (
    <div className="px-3 md:px-6 py-4 md:py-6">
      <section className="relative overflow-hidden rounded-[2rem] min-h-[calc(100dvh-2rem)] bg-[hsl(var(--secondary)/0.22)]">
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
          className="relative z-10 flex min-h-[calc(100dvh-2rem)] items-end lg:items-center p-4 sm:p-6 lg:p-10 xl:pl-14"
        >
          <div className="w-full max-w-md">
            <p className="lg:hidden font-display text-2xl sm:text-3xl text-white drop-shadow-[0_8px_24px_rgba(40,20,10,0.45)] text-balance mb-4">
              Minis today, memories forever.
            </p>
            <div className="glass-strong rounded-[2rem] p-8 md:p-10 w-full">
              <div className="text-xs uppercase tracking-[0.28em] text-primary">Atelier</div>
              <h1 className="font-display text-3xl md:text-4xl mt-2">
                {mode === "forgot" ? "Reset password" : "Staff sign in"}
              </h1>
              <p className="text-sm text-muted-foreground mt-2">
                {mode === "forgot"
                  ? forgotSent
                    ? "Check your inbox for a link to reset your password."
                    : "Enter your staff email and we'll send a reset link."
                  : "Sign in with your Mini Admin invitation account."}
              </p>
              {!loading && user && !staffRole && (
                <div className="mt-4 rounded-2xl border border-amber-200/80 bg-amber-50/80 px-4 py-3 text-sm text-amber-950">
                  <p>Signed in as {user.email ?? user.login}, but this account does not have staff access.</p>
                  <button
                    type="button"
                    onClick={() => signOut()}
                    className="mt-2 font-medium text-primary story-link"
                  >
                    Sign out and use a different account
                  </button>
                </div>
              )}
              {mode === "forgot" ? (
                forgotSent ? (
                  <div className="mt-6 rounded-2xl border border-primary/30 bg-primary/10 text-primary px-4 py-3 text-sm">
                    If an account exists for {email}, a reset link is on its way.
                  </div>
                ) : (
                  <form onSubmit={onForgotSubmit} noValidate className="mt-6 space-y-3">
                    <StaffEmailField
                      email={email}
                      emailError={emailError}
                      emailTouched={emailTouched}
                      onEmailChange={setEmail}
                      onEmailError={setEmailError}
                      onEmailTouched={setEmailTouched}
                    />
                    <button
                      disabled={forgotBusy}
                      type="submit"
                      className="w-full rounded-full bg-primary text-primary-foreground py-3 font-medium hover:shadow-glow transition disabled:opacity-60"
                    >
                      {forgotBusy ? "Sending…" : "Send reset link"}
                    </button>
                  </form>
                )
              ) : (
                <form onSubmit={onSubmit} noValidate className="mt-6 space-y-3">
                  <StaffEmailField
                    email={email}
                    emailError={emailError}
                    emailTouched={emailTouched}
                    onEmailChange={setEmail}
                    onEmailError={setEmailError}
                    onEmailTouched={setEmailTouched}
                  />
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
                      <button
                        type="button"
                        onClick={() => setShowPassword((v) => !v)}
                        aria-label={showPassword ? "Hide password" : "Show password"}
                        className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
                      >
                        {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                    {passwordError && passwordTouched ? (
                      <p className="mt-1 text-xs text-destructive">{passwordError}</p>
                    ) : null}
                  </div>
                  <div className="text-right -mt-1">
                    <button
                      type="button"
                      onClick={() => {
                        setMode("forgot");
                        setForgotSent(false);
                      }}
                      className="text-xs story-link text-primary"
                    >
                      Forgot password?
                    </button>
                  </div>
                  <button
                    disabled={busy}
                    type="submit"
                    className="w-full rounded-full bg-primary text-primary-foreground py-3 font-medium hover:shadow-glow transition disabled:opacity-60"
                  >
                    {busy ? "Please wait…" : "Sign in"}
                  </button>
                </form>
              )}
              <div className="mt-6 text-center space-y-2">
                {mode === "forgot" ? (
                  <button
                    type="button"
                    onClick={() => {
                      setMode("signin");
                      setForgotSent(false);
                    }}
                    className="block w-full text-sm story-link text-primary"
                  >
                    Back to sign in
                  </button>
                ) : null}
                <Link to="/" className="text-xs text-muted-foreground story-link">
                  ← Back to store
                </Link>
              </div>
            </div>
          </div>
        </motion.div>
      </section>
    </div>
  );
}

function StaffEmailField({
  email,
  emailError,
  emailTouched,
  onEmailChange,
  onEmailError,
  onEmailTouched,
}: {
  email: string;
  emailError: string | undefined;
  emailTouched: boolean;
  onEmailChange: (value: string) => void;
  onEmailError: (value: string | undefined) => void;
  onEmailTouched: (value: boolean) => void;
}) {
  return (
    <div>
      <input
        type="email"
        value={email}
        onChange={(e) => {
          onEmailChange(e.target.value);
          if (emailTouched) onEmailError(emailFieldError(e.target.value));
        }}
        onBlur={() => {
          onEmailTouched(true);
          onEmailError(emailFieldError(email));
        }}
        placeholder="you@store.in"
        aria-invalid={!!emailError && emailTouched}
        className={`w-full rounded-2xl glass px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-primary/40 ${emailError && emailTouched ? "ring-2 ring-destructive/50" : ""}`}
      />
      {emailError && emailTouched ? (
        <p className="mt-1 text-xs text-destructive">{emailError}</p>
      ) : null}
    </div>
  );
}
