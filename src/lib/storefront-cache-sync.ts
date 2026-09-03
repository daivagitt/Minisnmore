import {
  broadcastStorefrontInvalidation,
  subscribeStorefrontInvalidation,
  type StorefrontCacheScope,
} from "@/lib/storefront-cache-broadcast";
import { invalidateProducts, invalidateBlogPosts, invalidatePolicy } from "@/lib/storefront";
import { invalidateBanners } from "@/hooks/useBanners";
import { invalidateHomeSections } from "@/hooks/useHomeSections";
import { invalidateCategories } from "@/hooks/useCategories";
import { invalidateSiteSettings } from "@/hooks/useSiteSettings";
import { invalidatePageVisibility } from "@/hooks/usePageVisibility";

export type { StorefrontCacheScope };

type InvalidateOptions = { broadcast?: boolean };

function invalidateScope(scope: StorefrontCacheScope, options: InvalidateOptions = {}) {
  const broadcast = options.broadcast !== false;

  switch (scope) {
    case "products":
      invalidateProducts({ broadcast: false });
      break;
    case "banners":
      invalidateBanners({ broadcast: false });
      break;
    case "homeSections":
      invalidateHomeSections({ broadcast: false });
      break;
    case "blog":
      invalidateBlogPosts({ broadcast: false });
      break;
    case "categories":
      invalidateCategories({ broadcast: false });
      break;
    case "siteSettings":
      invalidateSiteSettings({ broadcast: false });
      break;
    case "pageVisibility":
      invalidatePageVisibility({ broadcast: false });
      break;
    case "policies":
      invalidatePolicy(undefined, { broadcast: false });
      break;
    case "all":
      invalidateProducts({ broadcast: false });
      invalidateBanners({ broadcast: false });
      invalidateHomeSections({ broadcast: false });
      invalidateBlogPosts({ broadcast: false });
      invalidateCategories({ broadcast: false });
      invalidateSiteSettings({ broadcast: false });
      invalidatePageVisibility({ broadcast: false });
      invalidatePolicy(undefined, { broadcast: false });
      break;
    default:
      break;
  }

  if (broadcast) broadcastStorefrontInvalidation(scope);
}

export function invalidateAllStorefrontCaches(options?: InvalidateOptions & { scope?: StorefrontCacheScope }) {
  const scope = options?.scope ?? "all";
  invalidateScope(scope, options);
}

let syncCleanup: (() => void) | null = null;

export function initStorefrontCacheSync() {
  if (syncCleanup) return syncCleanup;
  syncCleanup = subscribeStorefrontInvalidation((scope) => {
    invalidateAllStorefrontCaches({ broadcast: false, scope });
  });
  return syncCleanup;
}
