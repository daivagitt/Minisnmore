import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { MapPin, Plus, Trash2, Star, StarOff } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";

export const Route = createFileRoute("/account/addresses")({
  head: () => ({ meta: [{ title: "Addresses · Mini's n More" }] }),
  component: AddressesPage,
});

type Address = {
  id: string;
  label: string;
  full_name: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  pincode: string;
  is_default?: boolean;
};

function AddressesPage() {
  const { user } = useAuth();
  const key = `mnm_addresses_${user?.id ?? "guest"}`;
  const [items, setItems] = useState<Address[]>([]);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<Address>(blank());

  useEffect(() => {
    try {
      const raw = localStorage.getItem(key);
      if (raw) setItems(JSON.parse(raw));
    } catch {}
  }, [key]);

  const persist = (list: Address[]) => {
    setItems(list);
    try { localStorage.setItem(key, JSON.stringify(list)); } catch {}
  };

  const save = (e: React.FormEvent) => {
    e.preventDefault();
    const next = [...items];
    const idx = next.findIndex((a) => a.id === draft.id);
    if (idx >= 0) next[idx] = draft; else next.push({ ...draft, is_default: next.length === 0 });
    persist(next);
    setOpen(false);
    setDraft(blank());
  };

  const remove = (id: string) => persist(items.filter((a) => a.id !== id));
  const setDefault = (id: string) => persist(items.map((a) => ({ ...a, is_default: a.id === id })));

  return (
    <div className="glass-strong rounded-[2rem] p-6 md:p-8">
      <div className="flex items-center justify-between flex-wrap gap-3 mb-6">
        <div>
          <h1 className="font-display text-3xl mb-1 flex items-center gap-2"><MapPin className="h-6 w-6 text-primary" /> Addresses</h1>
          <p className="text-sm text-muted-foreground">Saved on this device for faster checkout.</p>
        </div>
        <button onClick={() => { setDraft(blank()); setOpen(true); }} className="inline-flex items-center gap-2 rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm">
          <Plus className="h-4 w-4" /> Add address
        </button>
      </div>

      {items.length === 0 && !open && (
        <div className="glass rounded-2xl p-8 text-center text-sm text-muted-foreground">No saved addresses yet.</div>
      )}

      <div className="grid sm:grid-cols-2 gap-3">
        {items.map((a) => (
          <div key={a.id} className="glass rounded-2xl p-4">
            <div className="flex items-start justify-between gap-2">
              <div className="text-xs uppercase tracking-wider text-primary">{a.label || "Home"}</div>
              {a.is_default ? (
                <span className="text-[10px] rounded-full bg-primary/15 text-primary px-2 py-0.5 uppercase tracking-wider">Default</span>
              ) : (
                <button onClick={() => setDefault(a.id)} className="text-xs text-muted-foreground hover:text-foreground inline-flex items-center gap-1"><StarOff className="h-3 w-3" /> Set default</button>
              )}
            </div>
            <div className="mt-2 text-sm font-medium">{a.full_name}</div>
            <div className="text-sm text-muted-foreground">{a.line1}{a.line2 ? `, ${a.line2}` : ""}</div>
            <div className="text-sm text-muted-foreground">{a.city}, {a.state} {a.pincode}</div>
            <div className="text-xs text-muted-foreground mt-1">{a.phone}</div>
            <div className="mt-3 flex items-center gap-2">
              <button onClick={() => { setDraft(a); setOpen(true); }} className="text-xs rounded-full border border-border px-3 py-1.5 hover:bg-muted">Edit</button>
              <button onClick={() => remove(a.id)} className="text-xs rounded-full border border-border px-3 py-1.5 hover:bg-destructive/10 hover:text-destructive inline-flex items-center gap-1"><Trash2 className="h-3 w-3" /> Delete</button>
            </div>
          </div>
        ))}
      </div>

      {open && (
        <form onSubmit={save} className="glass rounded-2xl p-4 mt-6 grid sm:grid-cols-2 gap-3">
          <p className="sm:col-span-2 text-xs text-muted-foreground"><span className="text-destructive font-semibold">*</span> indicates a required field</p>
          <Field label="Label (Home / Office)" value={draft.label} onChange={(v) => setDraft({ ...draft, label: v })} />
          <Field label="Full name" value={draft.full_name} onChange={(v) => setDraft({ ...draft, full_name: v })} required />
          <Field label="Phone" value={draft.phone} onChange={(v) => setDraft({ ...draft, phone: v })} required />
          <Field label="Pincode" value={draft.pincode} onChange={(v) => setDraft({ ...draft, pincode: v })} required />
          <Field label="Address line 1" value={draft.line1} onChange={(v) => setDraft({ ...draft, line1: v })} required className="sm:col-span-2" />
          <Field label="Address line 2" value={draft.line2 ?? ""} onChange={(v) => setDraft({ ...draft, line2: v })} className="sm:col-span-2" />
          <Field label="City" value={draft.city} onChange={(v) => setDraft({ ...draft, city: v })} required />
          <Field label="State" value={draft.state} onChange={(v) => setDraft({ ...draft, state: v })} required />
          <div className="sm:col-span-2 flex items-center gap-2 mt-2">
            <button type="submit" className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm">Save address</button>
            <button type="button" onClick={() => { setOpen(false); setDraft(blank()); }} className="rounded-full border border-border px-5 py-2.5 text-sm">Cancel</button>
          </div>
        </form>
      )}
    </div>
  );
}

function blank(): Address {
  return { id: crypto.randomUUID(), label: "Home", full_name: "", phone: "", line1: "", line2: "", city: "", state: "", pincode: "" };
}

function Field({ label, value, onChange, required, className }: { label: string; value: string; onChange: (v: string) => void; required?: boolean; className?: string }) {
  return (
    <label className={`block ${className ?? ""}`}>
      <span className="text-xs uppercase tracking-wider text-muted-foreground">
        {label}
        {required && (
          <>
            {" "}
            <span className="text-destructive font-semibold" aria-hidden="true">*</span>
            <span className="sr-only"> (required)</span>
          </>
        )}
      </span>
      <input value={value} onChange={(e) => onChange(e.target.value)} required={required}
        className="mt-1 w-full rounded-xl border border-border bg-background px-4 py-2.5 text-sm" />
    </label>
  );
}