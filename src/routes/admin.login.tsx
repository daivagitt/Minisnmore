import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { useState, useEffect } from "react";
import { Eye, EyeOff } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { emailFieldError, passwordFieldError } from "@/lib/password-validation";
import { staffHomePath } from "@/lib/staff-roles";
import { toast } from "sonner";

export const Route = createFileRoute("/admin/login")({
  head: () => ({ meta: [{ title: "Staff Login · Mini's n More" }] }),
  component: AdminLogin,
});

function AdminLogin() {
  const { signIn, user, isAdmin, staffRole, loading, signOut } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | undefined>();
  const [passwordTouched, setPasswordTouched] = useState(false);
  const [emailError, setEmailError] = useState<string | undefined>();
  const [emailTouched, setEmailTouched] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);

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

  return (
    <div className="px-4 max-w-md mx-auto">
      <div className="glass-strong rounded-[2rem] p-8 md:p-10">
        <div className="text-xs uppercase tracking-[0.28em] text-primary">Atelier</div>
        <h1 className="font-display text-3xl md:text-4xl mt-2">Staff sign in</h1>
        <p className="text-sm text-muted-foreground mt-2">Sign in with your Mini Admin invitation account.</p>
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
        <form onSubmit={onSubmit} noValidate className="mt-6 space-y-3">
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
              placeholder="you@store.in"
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
          <button
            disabled={busy}
            type="submit"
            className="w-full rounded-full bg-primary text-primary-foreground py-3 font-medium hover:shadow-glow transition disabled:opacity-60"
          >
            {busy ? "Please wait…" : "Sign in"}
          </button>
        </form>
        <div className="mt-6 text-center">
          <Link to="/" className="text-xs text-muted-foreground story-link">
            ← Back to store
          </Link>
        </div>
      </div>
    </div>
  );
}
