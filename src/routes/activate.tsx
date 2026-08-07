import { createFileRoute, Link, useNavigate, useSearch } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import { api } from "@/lib/api";
import { activationErrorMessage } from "@/lib/auth-errors";
import { showApiError } from "@/lib/ui-errors";

/**
 * Coerce activation keys to string. Older emails used numeric keys; a bad
 * stringify once wrapped them in quotes — strip those if present.
 */
const activationKeySchema = z.preprocess((value) => {
  if (value == null || value === "") return undefined;
  const first = Array.isArray(value) ? value[0] : value;
  if (first == null || first === "") return undefined;
  const asString = String(first).trim();
  return asString.replace(/^"(.*)"$/, "$1");
}, z.string().min(1).optional());

const Search = z.object({ key: activationKeySchema });

/** Delay before redirecting to sign-in after successful activation. */
const ACTIVATION_REDIRECT_DELAY_MS = 1200;

/** Deduplicate activation calls (React strict-mode remount, email prefetch, double tab). */
const activationRequests = new Map<string, Promise<void>>();

function activateAccountOnce(key: string): Promise<void> {
  const existing = activationRequests.get(key);
  if (existing) return existing;

  const request = api
    .get<void>(`/api/activate?key=${encodeURIComponent(key)}`, { unwrap: false })
    .then(() => undefined)
    .finally(() => {
      activationRequests.delete(key);
    });

  activationRequests.set(key, request);
  return request;
}

export const Route = createFileRoute("/activate")({
  validateSearch: (search) => Search.parse(search),
  head: () => ({ meta: [{ title: "Activate account · Mini's n More" }] }),
  component: ActivateAccountPage,
});

function ActivateAccountPage() {
  const search = useSearch({ from: "/activate" });
  const navigate = useNavigate();
  const [status, setStatus] = useState<"activating" | "success" | "error">("activating");
  const [errorDetail, setErrorDetail] = useState<string | null>(null);
  const missingKeyToastShown = useRef(false);

  useEffect(() => {
    let timer: number | undefined;
    let active = true;

    if (!search.key) {
      setStatus("error");
      setErrorDetail(
        "This activation link is missing its key. Open the full link from your email, or register again.",
      );
      if (!missingKeyToastShown.current) {
        missingKeyToastShown.current = true;
        toast.error("Activation link is missing a key.");
      }
      return;
    }

    const key = search.key;

    void (async () => {
      try {
        await activateAccountOnce(key);
        if (!active) return;
        setStatus("success");
        setErrorDetail(null);
        toast.success("Your account is active. Please sign in.");
        timer = window.setTimeout(
          () => navigate({ to: "/login", replace: true }),
          ACTIVATION_REDIRECT_DELAY_MS,
        );
      } catch (error) {
        if (!active) return;
        const message = activationErrorMessage(error);
        setStatus("error");
        setErrorDetail(message);
        showApiError(error, message);
      }
    })();

    return () => {
      active = false;
      if (timer !== undefined) {
        window.clearTimeout(timer);
      }
    };
  }, [navigate, search.key]);

  return (
    <div className="px-4 max-w-md mx-auto">
      <div className="glass-strong rounded-[2rem] p-8 md:p-10 text-center">
        <div className="text-xs uppercase tracking-[0.28em] text-primary">Account Activation</div>
        <h1 className="font-display text-3xl md:text-4xl mt-2">
          {status === "activating" && "Activating your account..."}
          {status === "success" && "Account activated"}
          {status === "error" && "Activation failed"}
        </h1>
        <p className="text-sm text-muted-foreground mt-3">
          {status === "activating" && "Please wait while we confirm your email address."}
          {status === "success" && "You can now sign in and continue shopping."}
          {status === "error" &&
            (errorDetail ??
              "This activation link may be invalid or expired. Register again or try signing in if you already activated.")}
        </p>
        {status !== "activating" && (
          <Link
            to="/login"
            className="mt-6 inline-flex rounded-full bg-primary text-primary-foreground px-6 py-3 text-sm font-medium hover:shadow-glow transition"
          >
            Go to sign in
          </Link>
        )}
      </div>
    </div>
  );
}
