import { toast } from "sonner";
import { ZodError } from "zod";
import { extractFieldErrorLines, formatProblemBody, statusFallbackMessage } from "@/lib/api";

type ApiErrorLike = Error & { status: number; body: unknown };

function isApiError(error: unknown): error is ApiErrorLike {
  return (
    error instanceof Error &&
    error.name === "ApiError" &&
    typeof (error as ApiErrorLike).status === "number" &&
    "body" in error
  );
}

export function getUserErrorMessage(error: unknown, fallback: string): string {
  if (isApiError(error)) {
    const specific = resolveSpecificErrorMessage(error, fallback);
    if (specific) return specific;
  }
  if (error instanceof ZodError) {
    const lines = error.issues.map((issue) => issue.message).filter(Boolean);
    if (lines.length) return lines.join("\n");
  }
  if (error instanceof Error && error.message.trim()) return error.message.trim();
  return fallback;
}

export function resolveSpecificErrorMessage(error: unknown, fallback: string): string | null {
  if (isApiError(error)) {
    const fieldLines = extractFieldErrorLines(error.body);
    if (fieldLines.length) return fieldLines.join("\n");

    const formatted = formatProblemBody(error.status, error.body);
    const statusFallback = statusFallbackMessage(error.status);
    if (formatted && formatted !== statusFallback && formatted !== fallback) return formatted;

    if (error.message.trim() && error.message !== statusFallback && error.message !== fallback) {
      return error.message.trim();
    }
    return null;
  }

  if (error instanceof ZodError) {
    const lines = error.issues.map((issue) => issue.message).filter(Boolean);
    if (lines.length) return lines.join("\n");
  }

  if (error instanceof Error && error.message.trim() && error.message.trim() !== fallback) {
    return error.message.trim();
  }

  return null;
}

function showSpecificError(specific: string) {
  const lines = specific
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  if (lines.length === 0) return;
  if (lines.length === 1) {
    toast.error(lines[0]);
    return;
  }
  toast.error(lines[0], { description: lines.slice(1).join("\n") });
}

export function showValidationErrors(errors: string[]) {
  const lines = errors.map((line) => line.trim()).filter(Boolean);
  if (!lines.length) return;
  showSpecificError(lines.join("\n"));
}

export function showApiError(error: unknown, fallback: string) {
  const specific = resolveSpecificErrorMessage(error, fallback);
  if (specific) {
    showSpecificError(specific);
    return;
  }
  toast.error(fallback);
}
