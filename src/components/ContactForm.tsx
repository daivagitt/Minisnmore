import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { motion } from "framer-motion";
import { Send } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { normalizeAddressPhone } from "@/lib/account.functions";
import { submitContactMessage } from "@/lib/contact-messages";
import { getMyProfile } from "@/lib/orders.functions";
import { showApiError } from "@/lib/ui-errors";

const fieldCls =
  "w-full rounded-xl bg-background/80 border border-border/80 px-4 py-3 text-sm text-foreground outline-none transition-[border-color,box-shadow,background-color] duration-200 placeholder:text-muted-foreground/55 focus:border-primary/45 focus:bg-background focus:ring-2 focus:ring-primary/25";

const empty = { name: "", email: "", phone: "", address: "", comment: "" };

const ease = [0.22, 1, 0.36, 1] as const;

function displayNameFromAuth(user: {
  firstName?: string | null;
  lastName?: string | null;
}): string {
  return [user.firstName, user.lastName].filter(Boolean).join(" ").trim();
}

export function ContactForm() {
  const { user, loading: authLoading } = useAuth();
  const [form, setForm] = useState(empty);
  const [saving, setSaving] = useState(false);
  const prefilledForUser = useRef<string | null>(null);
  /** Snapshot of login-derived fields so a successful submit can clear message fields only. */
  const loginDefaults = useRef(empty);

  useEffect(() => {
    // Wait for session hydrate so we do not treat "loading" as logged-out and later clobber typed fields.
    if (authLoading) return;

    if (!user) {
      // Clear PII when the session ends so the next visitor never sees prior account details.
      if (prefilledForUser.current !== null) {
        prefilledForUser.current = null;
        loginDefaults.current = empty;
        setForm(empty);
      }
      return;
    }

    const accountKey = String(user.id);
    if (prefilledForUser.current === accountKey) return;
    const switchingAccounts = prefilledForUser.current !== null;
    prefilledForUser.current = accountKey;

    const email = (user.email ?? user.login ?? "").trim();
    const authName = displayNameFromAuth(user);
    loginDefaults.current = { ...empty, email, name: authName };
    // On account switch, replace identity fields. On first session hydrate, keep anything the
    // visitor already typed during auth loading so we do not clobber in-progress input.
    setForm((current) => ({
      ...current,
      email: switchingAccounts || !current.email.trim() ? email : current.email,
      name: switchingAccounts || !current.name.trim() ? authName : current.name,
      phone: switchingAccounts ? "" : current.phone,
    }));

    let cancelled = false;
    getMyProfile()
      .then((r) => {
        if (cancelled || prefilledForUser.current !== accountKey) return;
        const phone = normalizeAddressPhone(r.profile?.phone ?? "");
        const fullName = (r.profile?.full_name ?? "").trim();
        const resolvedName = fullName || authName;
        loginDefaults.current = {
          ...loginDefaults.current,
          name: resolvedName,
          phone,
        };
        setForm((current) => ({
          ...current,
          // Keep in-progress edits; only fill blanks left after the auth seed above.
          phone: current.phone || phone,
          name: current.name === authName || !current.name.trim() ? resolvedName : current.name,
        }));
      })
      .catch(() => {
        /* Keep auth email/name if profile fetch fails. */
      });

    return () => {
      cancelled = true;
    };
  }, [user, authLoading]);

  const set = (key: keyof typeof empty) => (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((current) => ({ ...current, [key]: e.target.value }));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await submitContactMessage(form);
      toast.success("Thanks — we have saved your message.");
      setForm({
        ...empty,
        name: loginDefaults.current.name,
        email: loginDefaults.current.email,
        phone: loginDefaults.current.phone,
      });
    } catch (error) {
      showApiError(error, "Could not send your message");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={onSubmit} className="grid gap-4" noValidate={false}>
      <motion.label
        initial={{ opacity: 0, y: 10 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.4, ease }}
        className="block text-[11px] uppercase tracking-[0.2em] text-muted-foreground"
      >
        Name
        <input
          className={`${fieldCls} mt-2`}
          value={form.name}
          onChange={set("name")}
          autoComplete="name"
          maxLength={120}
          required
          placeholder="Your name"
        />
      </motion.label>

      <div className="grid sm:grid-cols-2 gap-4">
        <motion.label
          initial={{ opacity: 0, y: 10 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.4, delay: 0.05, ease }}
          className="block text-[11px] uppercase tracking-[0.2em] text-muted-foreground"
        >
          Email
          <input
            className={`${fieldCls} mt-2`}
            type="email"
            value={form.email}
            onChange={set("email")}
            autoComplete="email"
            maxLength={180}
            required
            placeholder="you@example.com"
          />
        </motion.label>
        <motion.label
          initial={{ opacity: 0, y: 10 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.4, delay: 0.1, ease }}
          className="block text-[11px] uppercase tracking-[0.2em] text-muted-foreground"
        >
          Contact number
          <input
            className={`${fieldCls} mt-2`}
            type="tel"
            value={form.phone}
            onChange={(e) =>
              setForm((current) => ({ ...current, phone: normalizeAddressPhone(e.target.value) }))
            }
            autoComplete="tel"
            maxLength={15}
            required
            placeholder="10-digit mobile"
          />
        </motion.label>
      </div>

      <motion.label
        initial={{ opacity: 0, y: 10 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.4, delay: 0.12, ease }}
        className="block text-[11px] uppercase tracking-[0.2em] text-muted-foreground"
      >
        Address
        <textarea
          className={`${fieldCls} mt-2 min-h-[5.5rem] resize-y`}
          value={form.address}
          onChange={set("address")}
          maxLength={1000}
          required
          placeholder="City, state / delivery address"
        />
      </motion.label>

      <motion.label
        initial={{ opacity: 0, y: 10 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.4, delay: 0.16, ease }}
        className="block text-[11px] uppercase tracking-[0.2em] text-muted-foreground"
      >
        Comments
        <textarea
          className={`${fieldCls} mt-2 min-h-[7.5rem] resize-y`}
          value={form.comment}
          onChange={set("comment")}
          maxLength={4000}
          required
          placeholder="How can we help?"
        />
      </motion.label>

      <motion.div
        initial={{ opacity: 0, y: 10 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.45, delay: 0.2, ease }}
        className="pt-1"
      >
        <button
          type="submit"
          disabled={saving}
          className="group inline-flex w-full sm:w-auto items-center justify-center gap-2.5 rounded-full bg-primary text-primary-foreground px-7 py-3.5 text-sm font-medium shadow-[0_14px_28px_-16px_oklch(0.45_0.10_35_/_55%)] transition-[transform,box-shadow,opacity] duration-300 hover:shadow-[0_18px_32px_-14px_oklch(0.45_0.10_35_/_60%)] hover:-translate-y-0.5 disabled:opacity-60 disabled:hover:translate-y-0 disabled:hover:shadow-[0_14px_28px_-16px_oklch(0.45_0.10_35_/_55%)]"
        >
          <Send className="h-4 w-4 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
          {saving ? "Sending…" : "Send message"}
        </button>
        <p className="mt-3 text-xs text-muted-foreground leading-relaxed">
          We never share your details. Expect a reply within one business day.
        </p>
      </motion.div>
    </form>
  );
}
