/** Lovable dev CDN paths — not available on self-hosted UAT/prod. */
export function resolveAssetUrl(url: string | null | undefined): string | null {
  if (!url?.trim()) return null;
  if (url.includes("/__l5e/") || url.includes("/l5e/assets-v1/")) return null;
  return url;
}

/** Bundled .asset.json videos — returns null when the URL is a Lovable placeholder. */
export function resolveBundledVideoUrl(url: string | null | undefined): string | null {
  return resolveAssetUrl(url);
}

export function parseFabricStoryVideo(payload: Record<string, unknown> | undefined): string | null {
  if (!payload) return null;
  const raw =
    typeof payload.video === "string"
      ? payload.video
      : typeof payload.video_url === "string"
        ? payload.video_url
        : null;
  return resolveAssetUrl(raw);
}
