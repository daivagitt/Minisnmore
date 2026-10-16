/** OAuth providers shown on login screens. Re-enable apple/facebook when ready for this phase. */
export type OAuthProvider = "google" | "apple" | "facebook";

export const ENABLED_OAUTH_PROVIDERS = ["google"] as const satisfies readonly OAuthProvider[];

export function isOAuthProviderEnabled(provider: OAuthProvider): boolean {
  return (ENABLED_OAUTH_PROVIDERS as readonly OAuthProvider[]).includes(provider);
}
