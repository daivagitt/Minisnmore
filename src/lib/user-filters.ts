import { searchTokens } from "@/lib/product-search";

export type UserStatusFilter = "all" | "active" | "pending";
export type UserSubscribedFilter = "all" | "yes" | "no";

export type AdminUserFilterSource = {
  login?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  activated?: boolean;
  subscribed?: boolean | null;
  createdDate?: string | null;
};

export type AdminUserListFilters = {
  query: string;
  status: UserStatusFilter;
  subscribed: UserSubscribedFilter;
};

export const DEFAULT_USER_LIST_FILTERS: AdminUserListFilters = {
  query: "",
  status: "all",
  subscribed: "all",
};

function pushField(parts: string[], value: unknown) {
  if (value == null) return;
  const text = String(value).trim();
  if (text) parts.push(text);
}

/** Lowercase haystack of name, login, and email for customer search. */
export function adminUserSearchHaystack(user: AdminUserFilterSource): string {
  const parts: string[] = [];
  pushField(parts, user.login);
  pushField(parts, user.firstName);
  pushField(parts, user.lastName);
  pushField(parts, user.email);
  const fullName = [user.firstName, user.lastName].filter(Boolean).join(" ").trim();
  pushField(parts, fullName);
  return parts.join(" ").toLowerCase();
}

export function userMatchesSearchQuery(user: AdminUserFilterSource, rawQuery: string): boolean {
  const tokens = searchTokens(rawQuery);
  if (!tokens.length) return true;
  const haystack = adminUserSearchHaystack(user);
  return tokens.every((token) => haystack.includes(token));
}

export function userMatchesStatus(user: AdminUserFilterSource, status: UserStatusFilter): boolean {
  if (status === "all") return true;
  if (status === "active") return user.activated === true;
  return user.activated !== true;
}

export function userMatchesSubscribed(
  user: AdminUserFilterSource,
  subscribed: UserSubscribedFilter,
): boolean {
  if (subscribed === "all") return true;
  const isSubscribed = user.subscribed === true;
  return subscribed === "yes" ? isSubscribed : !isSubscribed;
}

export function matchesAdminUserListFilters(
  user: AdminUserFilterSource,
  filters: AdminUserListFilters,
): boolean {
  return (
    userMatchesSearchQuery(user, filters.query) &&
    userMatchesStatus(user, filters.status) &&
    userMatchesSubscribed(user, filters.subscribed)
  );
}

export function adminUserListFiltersActive(filters: AdminUserListFilters): boolean {
  return filters.query.trim().length > 0 || filters.status !== "all" || filters.subscribed !== "all";
}

/** Stable key for pagination reset when list filters change. */
export function adminUserListFiltersKey(filters: AdminUserListFilters): string {
  return [filters.status, filters.subscribed, filters.query.trim().toLowerCase()].join("|");
}
