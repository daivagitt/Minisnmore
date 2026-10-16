import { describe, expect, it } from "vitest";
import {
  confirmPasswordFieldError,
  emailFieldError,
  MIN_PASSWORD_MSG,
  passwordFieldError,
} from "./password-validation";

describe("password-validation", () => {
  it("validates password length and presence", () => {
    expect(passwordFieldError("")).toMatch(/required/i);
    expect(passwordFieldError("abc")).toBe(MIN_PASSWORD_MSG);
    expect(passwordFieldError("secret1")).toBeUndefined();
  });

  it("validates confirm password", () => {
    expect(confirmPasswordFieldError("secret1", "")).toMatch(/confirm/i);
    expect(confirmPasswordFieldError("secret1", "other")).toMatch(/do not match/i);
    expect(confirmPasswordFieldError("secret1", "secret1")).toBeUndefined();
  });

  it("validates email", () => {
    expect(emailFieldError("")).toMatch(/required/i);
    expect(emailFieldError("not-an-email")).toMatch(/valid email/i);
    expect(emailFieldError("parent@example.com")).toBeUndefined();
  });
});
