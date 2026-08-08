import { createFileRoute, Link, useNavigate, useSearch } from "@tanstack/react-router";
import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";
import { useAuth } from "@/hooks/useAuth";
import { resetPasswordErrorMessage } from "@/lib/auth-errors";

const Search = z.object({ key: z.string().min(1).optional() });

const MIN_PASSWORD_MSG = "Password must be at least 6 characters.";

function setPasswordValidity(el: HTMLInputElement) {
  if (el.validity.tooShort) {
    el.setCustomValidity(MIN_PASSWORD_MSG);
  } else {
    el.setCustomValidity("");
  }
}

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

  if (!search.key) {
    return (
      <div className="px-4 max-w-md mx-auto">
        <div className="glass-strong rounded-[2rem] p-8 md:p-10 text-center">
          <div className="text-xs uppercase tracking-[0.28em] text-primary">Reset password</div>
          <h1 className="font-display text-3xl md:text-4xl mt-2">Link is missing its key</h1>
          <p className="text-sm text-muted-foreground mt-3">
            Open the full link from your email, or request a new one.
          </p>
          <Link to="/login" className="mt-6 inline-flex rounded-full bg-primary text-primary-foreground px-6 py-3 text-sm font-medium hover:shadow-glow transition">
            Back to sign in
          </Link>
        </div>
      </div>
    );
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password.length < 6) {
      toast.error(MIN_PASSWORD_MSG);
      return;
    }
    if (password !== confirm) {
      toast.error("Passwords do not match.");
      return;
    }
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
          <form onSubmit={onSubmit} className="mt-6 space-y-3">
            <div className="relative">
              <input
                type={showPassword ? "text" : "password"}
                required
                minLength={6}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onInvalid={(e) => setPasswordValidity(e.currentTarget)}
                onInput={(e) => e.currentTarget.setCustomValidity("")}
                placeholder="New password (min 6)"
                className="w-full rounded-2xl glass px-4 py-3 pr-11 text-sm outline-none focus:ring-2 focus:ring-primary/40"
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
            <input
              type={showPassword ? "text" : "password"}
              required
              minLength={6}
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              onInvalid={(e) => setPasswordValidity(e.currentTarget)}
              onInput={(e) => e.currentTarget.setCustomValidity("")}
              placeholder="Confirm new password"
              className="w-full rounded-2xl glass px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-primary/40"
            />
            <button disabled={busy} type="submit"
              className="w-full rounded-full bg-primary text-primary-foreground py-3 font-medium hover:shadow-glow transition disabled:opacity-60">
              {busy ? "Please wait…" : "Reset password"}
            </button>
          </form>
        )}
        <div className="mt-6 text-center">
          <Link to="/login" className="text-xs text-muted-foreground story-link">← Back to sign in</Link>
        </div>
      </div>
    </div>
  );
}
