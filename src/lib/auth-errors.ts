import { ApiError } from "@/lib/api";

const DEFAULT_AUTH_ERROR = "Something went wrong. Please try again.";

function problemRecord(body: unknown): Record<string, unknown> | null {
  return body && typeof body === "object" ? (body as Record<string, unknown>) : null;
}

function problemText(body: unknown, key: "title" | "detail" | "message" | "errorKey"): string {
  const record = problemRecord(body);
  if (!record) return "";
  const value = record[key];
  return typeof value === "string" ? value.trim() : "";
}

/** Map known auth/register/activate API errors to clear customer-facing copy. */
export function authErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const title = problemText(error.body, "title");
    const detail = problemText(error.body, "detail");
    const messageKey = problemText(error.body, "message");
    const errorKey = problemText(error.body, "errorKey");
    const combined = `${title} ${detail} ${messageKey} ${errorKey} ${error.message}`.toLowerCase();

    if (error.status === 401) {
      if (/not activated/i.test(detail) || /not activated/i.test(combined)) {
        return "Your account is not activated yet. Open the activation link from your email, or resend it below.";
      }
      return "Invalid email or password. Use Forgot password if you need to reset it.";
    }

    if (
      errorKey === "emailexists" ||
      messageKey.includes("emailexists") ||
      /email is already in use/i.test(title) ||
      /email is already in use/i.test(error.message)
    ) {
      return "An account with this email already exists. Sign in, or use Continue with Google if you registered that way.";
    }

    if (
      errorKey === "userexists" ||
      messageKey.includes("userexists") ||
      /login name already used/i.test(title) ||
      /login name already used/i.test(error.message)
    ) {
      return "That email is already registered. Sign in instead, or reset your password if you forgot it.";
    }

    if (
      errorKey === "invalidactivationkey" ||
      messageKey.includes("invalidactivationkey") ||
      /invalid activation key/i.test(title) ||
      /invalid activation key/i.test(error.message)
    ) {
      return "This activation link is invalid or has already been used. Sign in if you already activated, or register again.";
    }

    if (/invalid password|password.*invalid|incorrect password/i.test(combined)) {
      return "Password must be at least 4 characters. Please choose a stronger password.";
    }

    if (error.message.trim()) return error.message.trim();
  }

  if (error instanceof Error && error.message.trim()) return error.message.trim();
  return DEFAULT_AUTH_ERROR;
}

/** Activation-page copy for API failures. */
export function activationErrorMessage(error: unknown): string {
  if (!error) {
    return "This activation link may be invalid or expired. Register again, or sign in if you already activated.";
  }
  return authErrorMessage(error);
}

/** Reset-password-page copy for API failures. */
export function resetPasswordErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const errorKey = problemText(error.body, "errorKey");
    const title = problemText(error.body, "title");
    if (errorKey === "invalidresetkey" || /invalid reset key/i.test(title) || /invalid reset key/i.test(error.message)) {
      return "This password reset link is invalid or has expired. Request a new one.";
    }
    if (/invalid password|password.*invalid/i.test(`${title} ${error.message}`.toLowerCase())) {
      return "Password must be at least 4 characters. Please choose a stronger password.";
    }
  }
  return authErrorMessage(error);
}

/**
 * Sanitize OAuth callback `error` query values so users never see Java/SQL dumps.
 */
export function oauthCallbackErrorMessage(raw: string | undefined | null): string {
  const message = (raw ?? "").trim();
  if (!message) {
    return "Social sign-in failed. Please try again, or create an account with email.";
  }

  const lower = message.toLowerCase();
  if (lower.includes("did not share an email") || lower.includes("did not return an email")) {
    return "Your social account did not share an email address. Allow email access, or create an account with email instead.";
  }
  if (lower.includes("already exists") || lower.includes("duplicate")) {
    return "An account with this email already exists. Sign in with email and password, or try Continue with Google again.";
  }
  if (lower.includes("cancelled") || lower.includes("access_denied")) {
    return "Sign-in was cancelled. You can try again or create an account with email.";
  }
  if (lower.includes("misconfigured") || lower.includes("invalid_client")) {
    return "Social sign-in is temporarily unavailable. Please try again later or use email sign-up.";
  }
  if (lower.includes("redirect") && lower.includes("uri")) {
    return "Social sign-in is misconfigured for this site. Please contact support or use email sign-up.";
  }
  if (
    lower.includes("exception") ||
    lower.includes("sql [") ||
    lower.includes("org.springframework") ||
    lower.includes("unique result") ||
    lower.includes("stacktrace") ||
    message.includes("\n") ||
    message.length > 220
  ) {
    return "Social sign-in failed due to a server error. Please try again in a moment.";
  }

  return message;
}
