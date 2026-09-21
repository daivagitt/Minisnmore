import { useState, type ChangeEvent, type FormEvent } from "react";
import { Mail, Phone, Send } from "lucide-react";
import { toast } from "sonner";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { submitContactMessage } from "@/lib/contact-messages";
import { resolveContactEmail } from "@/lib/utils";
import { showApiError } from "@/lib/ui-errors";

const fieldCls =
  "w-full rounded-2xl bg-background/70 border border-border px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-primary/40";

const empty = { name: "", email: "", phone: "", address: "", comment: "" };

export function HomeContactForm() {
  const settings = useSiteSettings();
  const contactEmail = resolveContactEmail(settings.contact_email);
  const contactPhone = settings.contact_phone?.trim() || null;
  const [form, setForm] = useState(empty);
  const [saving, setSaving] = useState(false);

  const set = (key: keyof typeof empty) => (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((current) => ({ ...current, [key]: e.target.value }));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await submitContactMessage(form);
      toast.success("Thanks — we have saved your message.");
      setForm(empty);
    } catch (error) {
      showApiError(error, "Could not send your message");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section id="write-to-us" className="px-4 md:px-6 scroll-mt-28">
      <div className="glass-strong rounded-[2rem] p-6 md:p-10 grid md:grid-cols-[0.9fr_1.1fr] gap-8 md:gap-12">
        <div>
          <div className="text-xs uppercase tracking-[0.28em] text-primary">Communication</div>
          <h2 className="font-display text-3xl md:text-4xl mt-2 text-balance">
            Write to us — we&apos;d <span className="font-script text-primary">love</span> to hear from you.
          </h2>
          <p className="mt-3 text-sm text-muted-foreground leading-relaxed">
            Share your comments with your email, contact number and address. We save every message so our care team can follow up.
          </p>
          <div className="mt-6 space-y-3 text-sm">
            <a href={`mailto:${contactEmail}`} className="flex items-center gap-2 text-foreground hover:text-primary">
              <Mail className="h-4 w-4 text-primary" /> {contactEmail}
            </a>
            {contactPhone ? (
              <a href={`tel:${contactPhone.replace(/\s+/g, "")}`} className="flex items-center gap-2 text-foreground hover:text-primary">
                <Phone className="h-4 w-4 text-primary" /> {contactPhone}
              </a>
            ) : null}
          </div>
        </div>
        <form onSubmit={onSubmit} className="grid gap-3">
          <label className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
            Name
            <input className={`${fieldCls} mt-1.5`} value={form.name} onChange={set("name")} autoComplete="name" maxLength={120} required />
          </label>
          <div className="grid sm:grid-cols-2 gap-3">
            <label className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
              Email
              <input
                className={`${fieldCls} mt-1.5`}
                type="email"
                value={form.email}
                onChange={set("email")}
                autoComplete="email"
                maxLength={180}
                required
              />
            </label>
            <label className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
              Contact number
              <input
                className={`${fieldCls} mt-1.5`}
                type="tel"
                value={form.phone}
                onChange={set("phone")}
                autoComplete="tel"
                maxLength={15}
                required
              />
            </label>
          </div>
          <label className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
            Address
            <textarea className={`${fieldCls} mt-1.5 min-h-20 resize-y`} value={form.address} onChange={set("address")} maxLength={1000} required />
          </label>
          <label className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
            Comments
            <textarea className={`${fieldCls} mt-1.5 min-h-28 resize-y`} value={form.comment} onChange={set("comment")} maxLength={4000} required />
          </label>
          <button
            type="submit"
            disabled={saving}
            className="mt-1 inline-flex items-center justify-center gap-2 rounded-full bg-primary text-primary-foreground px-5 py-3 text-sm font-medium disabled:opacity-60"
          >
            <Send className="h-4 w-4" /> {saving ? "Sending…" : "Send message"}
          </button>
        </form>
      </div>
    </section>
  );
}
