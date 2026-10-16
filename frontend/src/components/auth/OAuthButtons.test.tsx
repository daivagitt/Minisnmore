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

  it("starts Google with the redirect target", async () => {
    render(<OAuthButtons redirectTo="/checkout" />);
    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));
    expect(signInWithOAuth).toHaveBeenCalledWith("google", "/checkout");
  });

  it("clears busy state when redirect throws", async () => {
    signInWithOAuth.mockImplementationOnce(() => {
      throw new Error("blocked");
    });
    render(<OAuthButtons redirectTo="/account" />);

    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));

    expect(screen.getByRole("button", { name: "Continue with Google" })).not.toBeDisabled();
  });
});
