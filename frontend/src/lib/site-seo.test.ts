import { beforeEach, describe, expect, it, vi } from "vitest";

async function loadSiteSeo(env: Record<string, string> = {}) {
  vi.resetModules();
  for (const [key, value] of Object.entries(env)) {
    vi.stubEnv(key, value);
  }
  return import("./site-seo");
}

describe("site-seo", () => {
  beforeEach(() => {
    vi.unstubAllEnvs();
  });

  it("defaults SITE_ORIGIN to production", async () => {
    const { SITE_ORIGIN } = await loadSiteSeo();
    expect(SITE_ORIGIN).toBe("https://minisnmore.com");
  });

  it("builds canonical URLs from SITE_ORIGIN", async () => {
    const { canonicalUrl, SITE_ORIGIN } = await loadSiteSeo();
    expect(canonicalUrl("/")).toBe(`${SITE_ORIGIN}/`);
    expect(canonicalUrl("/shop")).toBe(`${SITE_ORIGIN}/shop`);
  });

  it("emits noindex meta only on UAT builds", async () => {
    const { IS_UAT, uatNoindexMeta } = await loadSiteSeo({ VITE_SITE_ENV: "uat" });
    expect(IS_UAT).toBe(true);
    expect(uatNoindexMeta()).toEqual({ name: "robots", content: "noindex, nofollow" });
  });

  it("omits noindex meta on production builds", async () => {
    const { IS_UAT, uatNoindexMeta } = await loadSiteSeo({ VITE_SITE_ENV: "production" });
    expect(IS_UAT).toBe(false);
    expect(uatNoindexMeta()).toBeNull();
  });
});
