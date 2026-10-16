import { beforeEach, describe, expect, it, vi } from "vitest";

const invalidateProducts = vi.fn();
const invalidateBlogPosts = vi.fn();
const invalidatePolicy = vi.fn();
const invalidateBanners = vi.fn();
const invalidateHomeSections = vi.fn();
const invalidateCategories = vi.fn();
const invalidateSiteSettings = vi.fn();
const invalidatePageVisibility = vi.fn();

vi.mock("@/lib/storefront", () => ({
  invalidateProducts,
  invalidateBlogPosts,
  invalidatePolicy,
}));

vi.mock("@/hooks/useBanners", () => ({ invalidateBanners }));
vi.mock("@/hooks/useHomeSections", () => ({ invalidateHomeSections }));
vi.mock("@/hooks/useCategories", () => ({ invalidateCategories }));
vi.mock("@/hooks/useSiteSettings", () => ({ invalidateSiteSettings }));
vi.mock("@/hooks/usePageVisibility", () => ({ invalidatePageVisibility }));

describe("storefront cache sync", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
  });

  it("invalidates all storefront caches locally without re-broadcasting", async () => {
    const broadcastModule = await import("@/lib/storefront-cache-broadcast");
    const broadcastSpy = vi.spyOn(broadcastModule, "broadcastStorefrontInvalidation");
    const { invalidateAllStorefrontCaches } = await import("@/lib/storefront-cache-sync");

    invalidateAllStorefrontCaches({ broadcast: false });

    expect(invalidateProducts).toHaveBeenCalledWith({ broadcast: false });
    expect(invalidateBanners).toHaveBeenCalledWith({ broadcast: false });
    expect(invalidateHomeSections).toHaveBeenCalledWith({ broadcast: false });
    expect(invalidateBlogPosts).toHaveBeenCalledWith({ broadcast: false });
    expect(invalidateCategories).toHaveBeenCalledWith({ broadcast: false });
    expect(invalidateSiteSettings).toHaveBeenCalledWith({ broadcast: false });
    expect(invalidatePageVisibility).toHaveBeenCalledWith({ broadcast: false });
    expect(invalidatePolicy).toHaveBeenCalledWith(undefined, { broadcast: false });
    expect(broadcastSpy).not.toHaveBeenCalled();
  });

  it("broadcasts a scoped invalidation when requested", async () => {
    const broadcastModule = await import("@/lib/storefront-cache-broadcast");
    const broadcastSpy = vi.spyOn(broadcastModule, "broadcastStorefrontInvalidation");
    const { invalidateAllStorefrontCaches } = await import("@/lib/storefront-cache-sync");

    invalidateAllStorefrontCaches({ scope: "products" });

    expect(invalidateProducts).toHaveBeenCalledWith({ broadcast: false });
    expect(invalidateBanners).not.toHaveBeenCalled();
    expect(broadcastSpy).toHaveBeenCalledWith("products");
  });

  it("applies cross-tab invalidation without broadcasting back", async () => {
    class MockBroadcastChannel {
      static instances: MockBroadcastChannel[] = [];
      onmessage: ((event: MessageEvent) => void) | null = null;

      constructor(public readonly name: string) {
        MockBroadcastChannel.instances.push(this);
      }

      postMessage(data: unknown) {
        for (const channel of MockBroadcastChannel.instances) {
          if (channel !== this) channel.onmessage?.({ data } as MessageEvent);
        }
      }

      close() {}
    }

    vi.stubGlobal("BroadcastChannel", MockBroadcastChannel);

    const broadcastModule = await import("@/lib/storefront-cache-broadcast");
    const broadcastSpy = vi.spyOn(broadcastModule, "broadcastStorefrontInvalidation");
    const { initStorefrontCacheSync } = await import("@/lib/storefront-cache-sync");

    initStorefrontCacheSync();
    broadcastModule.broadcastStorefrontInvalidation("banners");

    expect(invalidateBanners).toHaveBeenCalledWith({ broadcast: false });
    expect(broadcastSpy).toHaveBeenCalledTimes(1);
  });
});
