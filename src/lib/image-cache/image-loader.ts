import { LruBlobCache } from "./lru-blob-cache";
import type { BlobCacheStats } from "./lru-blob-cache";

const cache = new LruBlobCache();
const inflight = new Map<string, Promise<string>>();

export function shouldBypassBlobCache(url: string): boolean {
  const trimmed = url.trim();
  if (!trimmed) return true;
  if (trimmed.startsWith("data:") || trimmed.startsWith("blob:")) return true;
  if (trimmed.startsWith("/assets/")) return true;
  try {
    const base = typeof window !== "undefined" ? window.location.origin : "http://localhost";
    const parsed = new URL(trimmed, base);
    if (parsed.origin === base && parsed.pathname.startsWith("/assets/")) {
      return true;
    }
    // Cross-origin images: let the browser cache handle them (blob fetch needs CORS).
    if (typeof window !== "undefined" && parsed.origin !== window.location.origin) {
      return true;
    }
  } catch {
    return false;
  }
  return false;
}

export function getCachedImageUrl(url: string): string | undefined {
  if (shouldBypassBlobCache(url)) return undefined;
  return cache.get(url);
}

async function fetchBlobUrl(url: string, signal?: AbortSignal): Promise<string | null> {
  try {
    const response = await fetch(url, { signal, credentials: "omit", mode: "cors" });
    if (!response.ok) return null;
    const blob = await response.blob();
    if (signal?.aborted) return null;
    const objectUrl = URL.createObjectURL(blob);
    cache.set(url, objectUrl, blob.size);
    return objectUrl;
  } catch {
    return null;
  }
}

export async function loadImage(url: string, signal?: AbortSignal): Promise<string> {
  const trimmed = url.trim();
  if (!trimmed) return "";

  if (shouldBypassBlobCache(trimmed)) {
    return trimmed;
  }

  const cached = cache.get(trimmed);
  if (cached) return cached;

  const pending = inflight.get(trimmed);
  if (pending) return pending;

  const task = (async () => {
    const blobUrl = await fetchBlobUrl(trimmed, signal);
    return blobUrl ?? trimmed;
  })();

  inflight.set(trimmed, task);
  try {
    return await task;
  } finally {
    if (inflight.get(trimmed) === task) {
      inflight.delete(trimmed);
    }
  }
}

export function imageCacheStats(): BlobCacheStats {
  return cache.stats();
}

export function clearImageCache(): void {
  cache.clear();
  inflight.clear();
}

if (import.meta.env.DEV && typeof window !== "undefined") {
  (window as Window & { __imageCacheStats?: () => BlobCacheStats }).__imageCacheStats = imageCacheStats;
}
