import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { getMyProfile, updateMyProfile, ProfileUpdate } from "@/lib/orders.functions";
import { useAuth } from "@/hooks/useAuth";
import { useAccountData } from "@/lib/account";
import { Loader2, User as UserIcon, Save, Check } from "lucide-react";

type FieldErrors = { full_name?: string; phone?: string };

function validateProfile(fullName: string, phone: string): FieldErrors {
  const result = ProfileUpdate.safeParse({ full_name: fullName, phone });
  if (result.success) return {};
  const errors: FieldErrors = {};
  for (const issue of result.error.issues) {
    const key = issue.path[0];
    if (key === "full_name" && !errors.full_name) errors.full_name = issue.message;
    if (key === "phone" && !errors.phone) errors.phone = issue.message;
  }
  return errors;
}

export const Route = createFileRoute("/account/profile")({
  head: () => ({ meta: [{ title: "Profile · Mini's n More" }] }),
  component: ProfilePage,
});

function ProfilePage() {
  const { user } = useAuth();
  const { profile, loadingProfile, refreshProfile, setProfile: setCachedProfile } = useAccountData();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [touched, setTouched] = useState<{ full_name?: boolean; phone?: boolean }>({});

  useEffect(() => {
    if (profile) {
      setFullName(profile.full_name ?? "");
      setPhone(profile.phone ?? "");
      setLoading(false);
      return;
    }

    getMyProfile()
      .then((r) => {
        setFullName(r.profile?.full_name ?? "");
        setPhone(r.profile?.phone ?? "");
        setCachedProfile(r.profile ?? { full_name: "", phone: "" });
      })
      .finally(() => setLoading(false));
  }, [profile, setCachedProfile]);

  useEffect(() => {
    if (!loadingProfile && profile) {
      setFullName(profile.full_name ?? "");
      setPhone(profile.phone ?? "");
      setLoading(false);
    }
  }, [loadingProfile, profile]);

  const onSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setTouched({ full_name: true, phone: true });
    const fieldErrors = validateProfile(fullName, phone);
    setErrors(fieldErrors);
    if (fieldErrors.full_name || fieldErrors.phone) {
      toast.error(fieldErrors.full_name ?? fieldErrors.phone ?? "Please fix the errors below");
      return;
    }
    setSaving(true);
    setSaved(false);
    try {
      await updateMyProfile({ data: { full_name: fullName.trim(), phone: phone.trim() } });
      setCachedProfile({ full_name: fullName.trim(), phone: phone.trim() });
      await refreshProfile();
      setSaved(true);
      setTimeout(() => setSaved(false), 2200);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Could not save your profile. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="glass-strong rounded-[2rem] p-6 md:p-8">
      <div className="mb-6">
        <h1 className="font-display text-3xl mb-1 flex items-center gap-2"><UserIcon className="h-6 w-6 text-primary" /> Your profile</h1>
        <p className="text-xs text-muted-foreground"><span className="text-destructive font-semibold">*</span> indicates a required field</p>
      </div>

      {loading ? (
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      ) : (
        <form onSubmit={onSave} className="space-y-4 max-w-2xl">
          <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label className="text-xs uppercase tracking-wider text-muted-foreground">Email</label>
            <input value={user?.email ?? ""} disabled className="mt-1 w-full rounded-2xl border border-white/50 bg-white/50 px-4 py-3 text-sm" />
          </div>
          <div>
            <label className="text-xs uppercase tracking-wider text-muted-foreground">Full name <span className="text-destructive font-semibold" aria-hidden="true">*</span><span className="sr-only"> (required)</span></label>
            <input
              required
              maxLength={120}
              value={fullName}
              onChange={(e) => {
                setFullName(e.target.value);
                if (touched.full_name) setErrors((prev) => ({ ...prev, full_name: validateProfile(e.target.value, phone).full_name }));
              }}
              onBlur={() => {
                setTouched((prev) => ({ ...prev, full_name: true }));
                setErrors((prev) => ({ ...prev, full_name: validateProfile(fullName, phone).full_name }));
              }}
              aria-invalid={!!errors.full_name}
              className={`mt-1 w-full rounded-2xl border bg-white/70 px-4 py-3 text-sm ${errors.full_name && touched.full_name ? "border-destructive" : "border-white/50"}`}
            />
            {errors.full_name && touched.full_name ? (
              <p className="mt-1 text-xs text-destructive">{errors.full_name}</p>
            ) : null}
          </div>
          <div>
            <label className="text-xs uppercase tracking-wider text-muted-foreground">Phone <span className="text-destructive font-semibold" aria-hidden="true">*</span><span className="sr-only"> (required)</span></label>
            <input
              required
              maxLength={20}
              value={phone}
              onChange={(e) => {
                setPhone(e.target.value);
                if (touched.phone) setErrors((prev) => ({ ...prev, phone: validateProfile(fullName, e.target.value).phone }));
              }}
              onBlur={() => {
                setTouched((prev) => ({ ...prev, phone: true }));
                setErrors((prev) => ({ ...prev, phone: validateProfile(fullName, phone).phone }));
              }}
              aria-invalid={!!errors.phone}
              placeholder="+91 99999 99999"
              className={`mt-1 w-full rounded-2xl border bg-white/70 px-4 py-3 text-sm ${errors.phone && touched.phone ? "border-destructive" : "border-white/50"}`}
            />
            {errors.phone && touched.phone ? (
              <p className="mt-1 text-xs text-destructive">{errors.phone}</p>
            ) : null}
          </div>
          </div>
          <button type="submit" disabled={saving} className="inline-flex items-center gap-2 rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm disabled:opacity-60">
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : saved ? <Check className="h-4 w-4" /> : <Save className="h-4 w-4" />}
            {saved ? "Saved" : "Save changes"}
          </button>
        </form>
      )}
    </div>
  );
}