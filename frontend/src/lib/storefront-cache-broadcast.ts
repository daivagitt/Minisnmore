export type StorefrontCacheScope =
  | "products"
  | "banners"
  | "homeSections"
  | "blog"
  | "categories"
  | "siteSettings"
  | "pageVisibility"
  | "policies"
  | "all";

const CHANNEL = "mini-storefront-cache-v1";

type InvalidationMessage = { scope: StorefrontCacheScope; ts: number };

export function broadcastStorefrontInvalidation(scope: StorefrontCacheScope = "all") {
  if (typeof BroadcastChannel === "undefined") return;
  const channel = new BroadcastChannel(CHANNEL);
  channel.postMessage({ scope, ts: Date.now() } satisfies InvalidationMessage);
  channel.close();
}

export function subscribeStorefrontInvalidation(
  handler: (scope: StorefrontCacheScope) => void,
): () => void {
  if (typeof BroadcastChannel === "undefined") return () => undefined;
  const channel = new BroadcastChannel(CHANNEL);
  channel.onmessage = (event: MessageEvent<InvalidationMessage>) => {
    handler(event.data?.scope ?? "all");
  };
  return () => channel.close();
}
