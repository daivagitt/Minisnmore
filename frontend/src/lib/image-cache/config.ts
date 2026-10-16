export type ImageCacheLimits = {
  maxEntries: number;
  maxBytes: number;
};

const DEFAULT_MAX_BYTES = 48 * 1024 * 1024;
const MAX_ENTRIES = 200;

function bytesForDeviceMemory(gb: number): number {
  if (gb <= 2) return 24 * 1024 * 1024;
  if (gb <= 4) return 48 * 1024 * 1024;
  return 96 * 1024 * 1024;
}

export function resolveImageCacheLimits(): ImageCacheLimits {
  const memory = typeof navigator === "undefined" ? undefined
    : (navigator as Navigator & { deviceMemory?: number }).deviceMemory;
  if (typeof memory === "number") {
    return {
      maxEntries: MAX_ENTRIES,
      maxBytes: bytesForDeviceMemory(memory),
    };
  }
  return { maxEntries: MAX_ENTRIES, maxBytes: DEFAULT_MAX_BYTES };
}
