const KEY = "mnm_pending_profile";

export type PendingProfile = { full_name: string; email: string };

export type SignupProfileUser = {
  email: string | null;
  login: string;
  firstName?: string | null;
  lastName?: string | null;
};

function normalizeEmail(value: string | null | undefined): string {
  return (value ?? "").trim().toLowerCase();
}

function displayNameFromUser(user: SignupProfileUser): string {
  const first = user.firstName?.trim() ?? "";
  const last = user.lastName?.trim() ?? "";
  if (first && last && first !== last) return `${first} ${last}`;
  return first || last;
}

/** Returns seed data for the signed-in user, ignoring stale pending profiles from another signup. */
export function resolveSignupProfileSeed(
  user: SignupProfileUser,
  pending: PendingProfile | null,
): { full_name: string; email: string; stalePending: boolean } | null {
  const userEmail = normalizeEmail(user.email ?? user.login);
  const pendingEmail = pending ? normalizeEmail(pending.email) : "";
  const pendingMatchesUser = Boolean(pendingEmail && pendingEmail === userEmail);
  const stalePending = Boolean(pending && !pendingMatchesUser);

  const email = pendingMatchesUser ? pendingEmail : userEmail;
  const full_name =
    (pendingMatchesUser ? pending!.full_name.trim() : "") || displayNameFromUser(user);

  if (!full_name || !email) return stalePending ? { full_name: "", email: "", stalePending: true } : null;
  return { full_name, email, stalePending };
}

export function savePendingProfile(profile: PendingProfile) {
  if (typeof window === "undefined") return;
  sessionStorage.setItem(KEY, JSON.stringify(profile));
}

export function readPendingProfile(): PendingProfile | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<PendingProfile>;
    if (typeof parsed.full_name === "string" && typeof parsed.email === "string") {
      return { full_name: parsed.full_name, email: parsed.email };
    }
  } catch {
    // Ignore corrupt session data.
  }
  return null;
}

export function clearPendingProfile(expectedEmail?: string) {
  if (typeof window === "undefined") return;
  if (expectedEmail) {
    const current = readPendingProfile();
    if (normalizeEmail(current?.email) !== normalizeEmail(expectedEmail)) return;
  }
  sessionStorage.removeItem(KEY);
}
