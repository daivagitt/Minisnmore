import { describe, expect, it } from "vitest";
import { clearPendingProfile, resolveSignupProfileSeed, savePendingProfile } from "./pending-profile";

describe("resolveSignupProfileSeed", () => {
  const user = {
    email: "parent@example.com",
    login: "parent@example.com",
    firstName: "Jane",
    lastName: "Doe",
  };

  it("uses pending signup data when it matches the signed-in user", () => {
    expect(
      resolveSignupProfileSeed(user, {
        full_name: "New Parent",
        email: "parent@example.com",
      }),
    ).toEqual({
      full_name: "New Parent",
      email: "parent@example.com",
      subscribed: false,
      stalePending: false,
    });
  });

  it("carries a subscribe preference from pending signup data", () => {
    expect(
      resolveSignupProfileSeed(user, {
        full_name: "New Parent",
        email: "parent@example.com",
        subscribed: true,
      }),
    ).toEqual({
      full_name: "New Parent",
      email: "parent@example.com",
      subscribed: true,
      stalePending: false,
    });
  });

  it("flags stale pending data from a different signup", () => {
    expect(
      resolveSignupProfileSeed(user, {
        full_name: "Other Parent",
        email: "other@example.com",
      }),
    ).toEqual({
      full_name: "Jane Doe",
      email: "parent@example.com",
      subscribed: false,
      stalePending: true,
    });
  });

  it("falls back to auth user names when no pending profile exists", () => {
    expect(resolveSignupProfileSeed(user, null)).toEqual({
      full_name: "Jane Doe",
      email: "parent@example.com",
      subscribed: false,
      stalePending: false,
    });
  });

  it("falls back to the auth user subscribe flag when no pending profile exists", () => {
    expect(resolveSignupProfileSeed({ ...user, subscribed: true }, null)).toEqual({
      full_name: "Jane Doe",
      email: "parent@example.com",
      subscribed: true,
      stalePending: false,
    });
  });

  it("does not clear a newer signup profile when an older request completes", () => {
    savePendingProfile({ full_name: "Newer Parent", email: "newer@example.com" });

    clearPendingProfile("older@example.com");

    expect(sessionStorage.getItem("mnm_pending_profile")).toContain("newer@example.com");
    clearPendingProfile("newer@example.com");
  });
});
