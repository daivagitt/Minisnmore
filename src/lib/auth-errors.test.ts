import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api";
import { activationErrorMessage, authErrorMessage, oauthCallbackErrorMessage } from "@/lib/auth-errors";

describe("authErrorMessage", () => {
  it("maps duplicate email register errors", () => {
    const error = new ApiError("Email is already in use!", 400, {
      title: "Email is already in use!",
      message: "error.emailexists",
      errorKey: "emailexists",
    });
    expect(authErrorMessage(error)).toMatch(/already exists/i);
  });

  it("maps invalid activation keys", () => {
    const error = new ApiError("Invalid activation key", 400, {
      title: "Invalid activation key",
      errorKey: "invalidactivationkey",
    });
    expect(authErrorMessage(error)).toMatch(/invalid or has already been used/i);
  });

  it("maps not-activated sign-in", () => {
    const error = new ApiError("Unauthorized", 401, { detail: "User not activated" });
    expect(authErrorMessage(error)).toMatch(/not activated yet/i);
  });
});

describe("oauthCallbackErrorMessage", () => {
  it("keeps friendly backend messages", () => {
    expect(oauthCallbackErrorMessage("Sign-in was cancelled. You can try again.")).toMatch(/cancelled/i);
  });

  it("hides technical dumps", () => {
    expect(
      oauthCallbackErrorMessage("org.springframework.dao.IncorrectResultSizeDataAccessException: Query did not return a unique result"),
    ).toMatch(/server error/i);
  });
});

describe("activationErrorMessage", () => {
  it("has a clear fallback", () => {
    expect(activationErrorMessage(null)).toMatch(/invalid or expired/i);
  });
});
