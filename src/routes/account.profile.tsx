import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { getMyProfile, updateMyProfile } from "@/lib/orders.functions";
import { useAuth } from "@/hooks/useAuth";
import { useAccountData } from "@/lib/account";
import { Loader2, User as UserIcon, Save, Check } from "lucide-react";

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
    setSaving(true);
    setSaved(false);
    try {
      await updateMyProfile({ data: { full_name: fullName, phone } });
      setCachedProfile({ full_name: fullName, phone });
      await refreshProfile();
      setSaved(true);
      setTimeout(() => setSaved(false), 2200);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="glass-strong rounded-[2rem] p-6 md:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-3xl mb-1 flex items-center gap-2"><UserIcon className="h-6 w-6 text-primary" /> Your profile</h1>
          <p className="text-sm text-muted-foreground">Keep your details up to date for faster checkout.</p>
        </div>
        <div className="glass rounded-2xl px-4 py-3 text-xs text-muted-foreground">
          Synced to your account for faster checkout and order updates.
        </div>
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
            <label className="text-xs uppercase tracking-wider text-muted-foreground">Full name</label>
            <input value={fullName} onChange={(e) => setFullName(e.target.value)} className="mt-1 w-full rounded-2xl border border-white/50 bg-white/70 px-4 py-3 text-sm" />
          </div>
          <div>
            <label className="text-xs uppercase tracking-wider text-muted-foreground">Phone</label>
            <input value={phone} onChange={(e) => setPhone(e.target.value)} className="mt-1 w-full rounded-2xl border border-white/50 bg-white/70 px-4 py-3 text-sm" />
          </div>
          <div className="glass rounded-2xl p-4 text-sm text-muted-foreground self-end">
            Use the same Google account anytime and your saved profile will be ready instantly.
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