import { createFileRoute, Link, useNavigate, useSearch } from "@tanstack/react-router";
import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";
import { useAuth } from "@/hooks/useAuth";
import { resetPasswordErrorMessage } from "@/lib/auth-errors";
import { confirmPasswordFieldError, passwordFieldError } from "@/lib/password-validation";

const Search = z.object({ key: z.string().min(1).optional() });

export const Route = createFileRoute("/reset-password")({
  validateSearch: (search) => Search.parse(search),
  head: () => ({ meta: [{ title: "Reset password · Mini's n More" }] }),
  component: ResetPasswordPage,
});

function ResetPasswordPage() {
  const { completePasswordReset } = useAuth();
  const search = useSearch({ from: "/reset-password" });
  const nav = useNavigate();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [passwordError, setPasswordError] = useState<string | undefined>();
  const [confirmError, setConfirmError] = useState<string | undefined>();
  const [touched, setTouched] = useState({ password: false, confirm: false });

  if (!search.key) {
    return (
      <div className="px-4 max-w-md mx-auto">
        <div className="glass-strong rounded-[2rem] p-8 md:p-10 text-center">
          <div className="text-xs uppercase tracking-[0.28em] text-primary">Reset password</div>
          <h1 className="font-display text-3xl md:text-4xl mt-2">Link is missing its key</h1>
          <p className="text-sm text-muted-foreground mt-3">
            Open the full link from your email, or request a new one.
          </p>
          <div className="mt-6 flex flex-col items-center gap-3">
            <Link to="/login" className="inline-flex rounded-full bg-primary text-primary-foreground px-6 py-3 text-sm font-medium hover:shadow-glow transition">
              Back to sign in
            </Link>
            <Link to="/admin/login" className="text-xs text-muted-foreground story-link">
              Staff sign in
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const validate = () => {
    const nextPasswordError = passwordFieldError(password);
    const nextConfirmError = confirmPasswordFieldError(password, confirm);
    setPasswordError(nextPasswordError);
    setConfirmError(nextConfirmError);
    setTouched({ password: true, confirm: true });
    return !nextPasswordError && !nextConfirmError;
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setBusy(true);
    const { error } = await completePasswordReset(search.key!, password);
    setBusy(false);
    if (error) {
      toast.error(resetPasswordErrorMessage(new Error(error)), { id: "reset-password-error" });
      return;
    }
    setDone(true);
    toast.success("Password updated. Please sign in.");
    setTimeout(() => nav({ to: "/login", replace: true }), 1500);
  };

  return (
    <div className="px-4 max-w-md mx-auto">
      <div className="glass-strong rounded-[2rem] p-8 md:p-10">
        <div className="text-xs uppercase tracking-[0.28em] text-primary">Reset password</div>
        <h1 className="font-display text-3xl md:text-4xl mt-2">{done ? "All set" : "Choose a new password"}</h1>
        <p className="text-sm text-muted-foreground mt-2">
          {done ? "Redirecting you to sign in…" : "Enter a new password for your account."}
        </p>
        {!done && (
          <form onSubmit={onSubmit} noValidate className="mt-6 space-y-3">
            <div>
              <div className="relative">
                <input
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (touched.password) setPasswordError(passwordFieldError(e.target.value));
                    if (touched.confirm) setConfirmError(confirmPasswordFieldError(e.target.value, confirm));
                  }}
                  onBlur={() => {
                    setTouched((prev) => ({ ...prev, password: true }));
                    setPasswordError(passwordFieldError(password));
                  }}
                  placeholder="New password (min 6)"
                  aria-invalid={!!passwordError && touched.password}
                  className={`w-full rounded-2xl glass px-4 py-3 pr-11 text-sm outline-none focus:ring-2 focus:ring-primary/40 ${passwordError && touched.password ? "ring-2 ring-destructive/50" : ""}`}
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
              {passwordError && touched.password ? (
                <p className="mt-1 text-xs text-destructive">{passwordError}</p>
              ) : null}
            </div>
            <div>
              <input
                type={showPassword ? "text" : "password"}
                value={confirm}
                onChange={(e) => {
                  setConfirm(e.target.value);
                  if (touched.confirm) setConfirmError(confirmPasswordFieldError(password, e.target.value));
                }}
                onBlur={() => {
                  setTouched((prev) => ({ ...prev, confirm: true }));
                  setConfirmError(confirmPasswordFieldError(password, confirm));
                }}
                placeholder="Confirm new password"
                aria-invalid={!!confirmError && touched.confirm}
                className={`w-full rounded-2xl glass px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-primary/40 ${confirmError && touched.confirm ? "ring-2 ring-destructive/50" : ""}`}
              />
              {confirmError && touched.confirm ? (
                <p className="mt-1 text-xs text-destructive">{confirmError}</p>
              ) : null}
            </div>
            <button disabled={busy} type="submit"
              className="w-full rounded-full bg-primary text-primary-foreground py-3 font-medium hover:shadow-glow transition disabled:opacity-60">
              {busy ? "Please wait…" : "Reset password"}
            </button>
          </form>
        )}
        <div className="mt-6 text-center space-y-2">
          <Link to="/login" className="block text-xs text-muted-foreground story-link">← Back to sign in</Link>
          <Link to="/admin/login" className="block text-xs text-muted-foreground story-link">Staff sign in</Link>
        </div>
      </div>
    </div>
  );
}
