import { createFileRoute, Link, useNavigate, useSearch } from "@tanstack/react-router";
import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";
import { useAuth } from "@/hooks/useAuth";
import { confirmPasswordFieldError, passwordFieldError } from "@/lib/password-validation";

const Search = z.object({ key: z.string().min(1).optional() });

export const Route = createFileRoute("/invite")({
  validateSearch: (search) => Search.parse(search),
  head: () => ({ meta: [{ title: "Activate invitation · Mini's n More" }] }),
  component: InvitePage,
});

function InvitePage() {
  const { completeInvite } = useAuth();
  const search = useSearch({ from: "/invite" });
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
        <div className="glass-strong rounded-[2rem] p-8 text-center">
          <h1 className="font-display text-3xl mt-2">Invitation link is incomplete</h1>
          <p className="text-sm text-muted-foreground mt-3">Open the full link from your email.</p>
          <Link to="/admin/login" className="mt-6 inline-flex rounded-full bg-primary text-primary-foreground px-6 py-3 text-sm">
            Staff sign in
          </Link>
        </div>
      </div>
    );
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const nextPasswordError = passwordFieldError(password);
    const nextConfirmError = confirmPasswordFieldError(password, confirm);
    setPasswordError(nextPasswordError);
    setConfirmError(nextConfirmError);
    setTouched({ password: true, confirm: true });
    if (nextPasswordError || nextConfirmError) return;
    setBusy(true);
    const { error } = await completeInvite(search.key!, password);
    setBusy(false);
    if (error) {
      toast.error(error);
      return;
    }
    setDone(true);
    toast.success("Account activated. Please sign in.");
    setTimeout(() => nav({ to: "/admin/login", replace: true }), 1200);
  };

  return (
    <div className="px-4 max-w-md mx-auto">
      <div className="glass-strong rounded-[2rem] p-8 md:p-10">
        <div className="text-xs uppercase tracking-[0.28em] text-primary">Staff invitation</div>
        <h1 className="font-display text-3xl mt-2">{done ? "You're in" : "Set your password"}</h1>
        <p className="text-sm text-muted-foreground mt-2">
          {done ? "Redirecting to staff sign in…" : "Activate your Mini Admin account by choosing a password."}
        </p>
        {!done && (
          <form onSubmit={onSubmit} noValidate className="mt-6 space-y-3">
            <div className="relative">
              <input
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Password (min 6)"
                className="w-full rounded-2xl glass px-4 py-3 pr-11 text-sm"
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                className="absolute inset-y-0 right-0 px-3 text-muted-foreground"
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            {passwordError && touched.password ? <p className="text-xs text-destructive">{passwordError}</p> : null}
            <input
              type={showPassword ? "text" : "password"}
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              placeholder="Confirm password"
              className="w-full rounded-2xl glass px-4 py-3 text-sm"
            />
            {confirmError && touched.confirm ? <p className="text-xs text-destructive">{confirmError}</p> : null}
            <button
              disabled={busy}
              type="submit"
              className="w-full rounded-full bg-primary text-primary-foreground py-3 font-medium disabled:opacity-60"
            >
              {busy ? "Please wait…" : "Activate account"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
