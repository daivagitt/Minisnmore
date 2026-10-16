export const MIN_PASSWORD_LENGTH = 6;
export const MIN_PASSWORD_MSG = "Password must be at least 6 characters.";

export function passwordFieldError(value: string): string | undefined {
  const trimmed = value.trim();
  if (!trimmed) return "Password is required.";
  if (trimmed.length < MIN_PASSWORD_LENGTH) return MIN_PASSWORD_MSG;
  return undefined;
}

export function confirmPasswordFieldError(password: string, confirm: string): string | undefined {
  if (!confirm.trim()) return "Please confirm your password.";
  if (password !== confirm) return "Passwords do not match.";
  return undefined;
}

export function emailFieldError(value: string): string | undefined {
  const trimmed = value.trim();
  if (!trimmed) return "Email is required.";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) return "Enter a valid email address.";
  return undefined;
}
