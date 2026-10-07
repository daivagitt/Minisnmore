import { beforeEach, describe, expect, it, vi } from "vitest";

async function loadFlags(env: Record<string, string> = {}) {
  vi.resetModules();
  for (const [key, value] of Object.entries(env)) {
    vi.stubEnv(key, value);
  }
  return import("./feature-flags");
}

describe("feature-flags", () => {
  beforeEach(() => {
    vi.unstubAllEnvs();
  });

  it("keeps M2 UI on in tests unless explicitly disabled", async () => {
    const { SHOW_M2_POST_ORDER_UI } = await loadFlags({ VITE_SITE_ENV: "uat" });
    expect(SHOW_M2_POST_ORDER_UI).toBe(true);
  });

  it("can hide M2 UI even in tests with an explicit override", async () => {
    const { SHOW_M2_POST_ORDER_UI } = await loadFlags({ VITE_SHOW_M2_POST_ORDER_UI: "false" });
    expect(SHOW_M2_POST_ORDER_UI).toBe(false);
  });

  it("shows M2 UI on UAT in production builds by default", async () => {
    vi.stubEnv("MODE", "production");
    const { SHOW_M2_POST_ORDER_UI } = await loadFlags({ VITE_SITE_ENV: "uat" });
    expect(SHOW_M2_POST_ORDER_UI).toBe(true);
  });

  it("can hide M2 UI on UAT with an explicit override", async () => {
    vi.stubEnv("MODE", "production");
    const { SHOW_M2_POST_ORDER_UI } = await loadFlags({
      VITE_SITE_ENV: "uat",
      VITE_SHOW_M2_POST_ORDER_UI: "false",
    });
    expect(SHOW_M2_POST_ORDER_UI).toBe(false);
  });

  it("identifies M2 admin nav paths", async () => {
    const { isM2AdminNavPath } = await loadFlags();
    expect(isM2AdminNavPath("/admin/cancellations")).toBe(true);
    expect(isM2AdminNavPath("/admin/returns")).toBe(true);
    expect(isM2AdminNavPath("/admin/orders")).toBe(false);
  });
});
