import { createFileRoute, useNavigate, useSearch } from "@tanstack/react-router";
import { useEffect, useRef } from "react";
import { z } from "zod";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { oauthCallbackErrorMessage } from "@/lib/auth-errors";

const Search = z.object({
  token: z.string().optional(),
  redirect: z.string().optional(),
  error: z.string().optional(),
});

export const Route = createFileRoute("/auth/callback")({
  validateSearch: (search) => Search.parse(search),
  component: AuthCallback,
});

function AuthCallback() {
  const search = useSearch({ from: "/auth/callback" });
  const navigate = useNavigate();
  const { completeOAuthSignIn } = useAuth();
  const handledRef = useRef(false);

  useEffect(() => {
    if (handledRef.current) return;
    handledRef.current = true;

    if (search.error) {
      const message = oauthCallbackErrorMessage(search.error);
      toast.error(message, { id: "oauth-error" });
      navigate({ to: "/login", search: { error: message }, replace: true });
      return;
    }

    if (!search.token) {
      const message = "Social sign-in did not return a login token. Please try again.";
      toast.error(message, { id: "oauth-error" });
      navigate({ to: "/login", search: { error: message }, replace: true });
      return;
    }

    void completeOAuthSignIn(search.token).then(({ error }) => {
      if (error) {
        const message = oauthCallbackErrorMessage(error);
        toast.error(message, { id: "oauth-error" });
        navigate({ to: "/login", search: { error: message }, replace: true });
        return;
      }
      const redirect =
        search.redirect || window.sessionStorage.getItem("mnm_oauth_redirect") || "/account";
      window.sessionStorage.removeItem("mnm_oauth_redirect");
      navigate({ to: redirect, replace: true });
    });
  }, [completeOAuthSignIn, navigate, search.error, search.redirect, search.token]);

  return (
    <div className="px-4 max-w-md mx-auto">
      <div className="glass-strong rounded-[2rem] p-8 text-center text-sm text-muted-foreground">
        Completing sign in...
      </div>
    </div>
  );
}
