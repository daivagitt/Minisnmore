import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LruBlobCache } from "./lru-blob-cache";

describe("LruBlobCache", () => {
  let revoke: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    revoke = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
    vi.spyOn(URL, "createObjectURL").mockImplementation((blob: Blob | MediaSource) => `blob:${blob instanceof Blob ? blob.size : 0}`);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns cached object URLs and promotes LRU order", () => {
    const cache = new LruBlobCache({ maxEntries: 2, maxBytes: 1024 });
    cache.set("a", "blob:a", 100);
    cache.set("b", "blob:b", 100);

    expect(cache.get("a")).toBe("blob:a");
    cache.set("c", "blob:c", 100);

    expect(cache.get("b")).toBeUndefined();
    expect(cache.get("a")).toBe("blob:a");
    expect(cache.get("c")).toBe("blob:c");
    expect(revoke).toHaveBeenCalledWith("blob:b");
  });

  it("evicts by byte budget", () => {
    const cache = new LruBlobCache({ maxEntries: 10, maxBytes: 250 });
    cache.set("a", "blob:a", 100);
    cache.set("b", "blob:b", 100);
    cache.set("c", "blob:c", 100);

    expect(cache.get("a")).toBeUndefined();
    expect(cache.get("b")).toBe("blob:b");
    expect(cache.get("c")).toBe("blob:c");
    expect(revoke).toHaveBeenCalledWith("blob:a");
  });

  it("revokes object URLs on delete and clear", () => {
    const cache = new LruBlobCache({ maxEntries: 10, maxBytes: 1024 });
    cache.set("a", "blob:a", 50);
    cache.delete("a");
    expect(revoke).toHaveBeenCalledWith("blob:a");

    cache.set("b", "blob:b", 50);
    cache.clear();
    expect(revoke).toHaveBeenCalledWith("blob:b");
    expect(cache.stats().entries).toBe(0);
  });
});

describe("image loader", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:loaded");
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("dedupes concurrent loads for the same URL", async () => {
    vi.stubGlobal("window", { location: { origin: "https://example.com" } });
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    let resolveFetch: (value: Response) => void = () => {};
    fetchMock.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
    );

    const { loadImage, clearImageCache } = await import("./image-loader");
    clearImageCache();

    const url = "https://example.com/media/a.jpg";
    const first = loadImage(url);
    const second = loadImage(url);

    resolveFetch({
      ok: true,
      blob: async () => new Blob(["x".repeat(10)], { type: "image/jpeg" }),
    } as Response);

    const [one, two] = await Promise.all([first, second]);
    expect(one).toBe("blob:loaded");
    expect(two).toBe("blob:loaded");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("falls back to direct URL when blob fetch fails", async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));
    vi.stubGlobal("fetch", fetchMock);
    const { loadImage, clearImageCache } = await import("./image-loader");
    clearImageCache();
    await expect(loadImage("https://cdn.example.com/a.jpg")).resolves.toBe("https://cdn.example.com/a.jpg");
  });

  it("bypasses blob cache for cross-origin URLs", async () => {
    vi.stubGlobal("window", { location: { origin: "https://uat.minisnmore.com" } });
    const { shouldBypassBlobCache } = await import("./image-loader");
    expect(shouldBypassBlobCache("https://cdn.example.com/a.jpg")).toBe(true);
  });

  it("bypasses blob cache for bundled asset URLs", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const { loadImage, shouldBypassBlobCache } = await import("./image-loader");
    expect(shouldBypassBlobCache("/assets/hero-abc.webp")).toBe(true);
    await expect(loadImage("/assets/hero-abc.webp")).resolves.toBe("/assets/hero-abc.webp");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
