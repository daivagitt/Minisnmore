import { useState } from "react";
import { useAuth } from "@/hooks/useAuth";
import { toast } from "sonner";

type Provider = "google" | "apple" | "facebook";

export function OAuthButtons({ redirectTo }: { redirectTo: string }) {
  const { signInWithOAuth } = useAuth();
  const [busy, setBusy] = useState<Provider | null>(null);

  const start = async (provider: Provider) => {
    setBusy(provider);
    try {
      const { error } = await signInWithOAuth(provider, redirectTo);
      if (error) {
        toast.error(error);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unable to start social sign in.";
      toast.error(message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-2">
      <button
        type="button"
        onClick={() => start("google")}
        disabled={busy !== null}
        className="w-full inline-flex items-center justify-center gap-2 rounded-full glass border border-border px-5 py-3 text-sm font-medium hover:bg-white/60 transition disabled:opacity-60"
      >
        <svg className="h-4 w-4" viewBox="0 0 48 48" aria-hidden>
          <path
            fill="#FFC107"
            d="M43.6 20.5H42V20H24v8h11.3C33.7 32.6 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.2-.1-2.3-.4-3.5z"
          />
          <path
            fill="#FF3D00"
            d="M6.3 14.7l6.6 4.8C14.6 16 18.9 13 24 13c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.4 6.3 14.7z"
          />
          <path
            fill="#4CAF50"
            d="M24 44c5.2 0 9.8-2 13.3-5.2l-6.1-5c-2 1.4-4.5 2.2-7.2 2.2-5.2 0-9.6-3.3-11.2-8l-6.5 5C9.5 39.6 16.2 44 24 44z"
          />
          <path
            fill="#1976D2"
            d="M43.6 20.5H42V20H24v8h11.3c-.8 2.3-2.3 4.3-4.2 5.8l6.1 5C40.9 36 44 30.5 44 24c0-1.2-.1-2.3-.4-3.5z"
          />
        </svg>
        {busy === "google" ? "Opening Google…" : "Continue with Google"}
      </button>
      <button
        type="button"
        onClick={() => start("apple")}
        disabled={busy !== null}
        className="w-full inline-flex items-center justify-center gap-2 rounded-full bg-black text-white px-5 py-3 text-sm font-medium hover:bg-black/90 transition disabled:opacity-60"
      >
        <svg className="h-4 w-4" viewBox="0 0 24 24" aria-hidden fill="currentColor">
          <path d="M16.365 1.43c0 1.14-.42 2.22-1.12 3.01-.76.86-2.02 1.53-3.05 1.45-.14-1.12.4-2.3 1.07-3.04.77-.85 2.08-1.5 3.1-1.42zM20.5 17.27c-.55 1.27-.82 1.84-1.53 2.96-1 1.56-2.4 3.5-4.14 3.51-1.55.02-1.95-1.01-4.05-1-2.1.01-2.54 1.02-4.09 1-1.74-.02-3.07-1.77-4.06-3.33C-.06 16.8-.34 11.43 2.06 8.55c1.5-1.8 3.86-2.85 6.08-2.85 2.27 0 3.7 1.24 5.57 1.24 1.82 0 2.92-1.24 5.55-1.24 1.98 0 4.08 1.08 5.58 2.94-4.9 2.69-4.11 9.69-4.34 8.63z" />
        </svg>
        {busy === "apple" ? "Opening Apple…" : "Continue with Apple"}
      </button>
      <button
        type="button"
        onClick={() => start("facebook")}
        disabled={busy !== null}
        className="w-full inline-flex items-center justify-center gap-2 rounded-full bg-[#1877F2] text-white px-5 py-3 text-sm font-medium hover:bg-[#1877F2]/90 transition disabled:opacity-60"
      >
        <svg className="h-4 w-4" viewBox="0 0 24 24" aria-hidden fill="currentColor">
          <path d="M22 12a10 10 0 1 0-11.56 9.88v-6.99H7.9V12h2.54V9.8c0-2.51 1.49-3.9 3.78-3.9 1.1 0 2.24.2 2.24.2v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56V12h2.77l-.44 2.89h-2.33v6.99A10 10 0 0 0 22 12z" />
        </svg>
        {busy === "facebook" ? "Opening Facebook…" : "Continue with Facebook"}
      </button>
    </div>
  );
}
