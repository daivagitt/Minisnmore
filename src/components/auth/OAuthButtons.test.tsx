import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { OAuthButtons } from "./OAuthButtons";

const signInWithOAuth = vi.hoisted(() => vi.fn());
const toastError = vi.hoisted(() => vi.fn());

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({ signInWithOAuth }),
}));

vi.mock("sonner", () => ({
  toast: {
    error: toastError,
  },
}));

describe("OAuthButtons", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it.each([
    ["Continue with Google", "google"],
    ["Continue with Apple", "apple"],
    ["Continue with Facebook", "facebook"],
  ] as const)("starts %s with the redirect target", async (label, provider) => {
    render(<OAuthButtons redirectTo="/checkout" />);
    await userEvent.click(screen.getByRole("button", { name: label }));
    expect(signInWithOAuth).toHaveBeenCalledWith(provider, "/checkout");
  });

  it("clears busy state when redirect throws", async () => {
    signInWithOAuth.mockImplementationOnce(() => {
      throw new Error("blocked");
    });
    render(<OAuthButtons redirectTo="/account" />);

    await userEvent.click(screen.getByRole("button", { name: "Continue with Facebook" }));

    expect(screen.getByRole("button", { name: "Continue with Facebook" })).not.toBeDisabled();
  });
});
