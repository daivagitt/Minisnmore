import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { MapPin, Plus, Trash2, StarOff, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import {
  clearLegacyLocalAddresses,
  createMyAddress,
  deleteMyAddress,
  listMyAddresses,
  normalizeAddressDraft,
  normalizeAddressPhone,
  readLegacyLocalAddresses,
  setMyDefaultAddress,
  syncMyAddresses,
  updateMyAddress,
  validateAddressForm,
  type AddressFieldErrors,
  type AddressFieldName,
  type SavedAddress,
} from "@/lib/account.functions";
import { lookupPincode, normalizePincode } from "@/lib/pincode";

export const Route = createFileRoute("/account/addresses")({
  head: () => ({ meta: [{ title: "Addresses · Mini's n More" }] }),
  component: AddressesPage,
});

type AddressDraft = Omit<SavedAddress, "id"> & { id?: number };

const REQUIRED_FIELDS: AddressFieldName[] = [
  "full_name",
  "phone",
  "line1",
  "pincode",
  "city",
  "state",
];

function AddressesPage() {
  const { user } = useAuth();
  const storageKey = `mnm_addresses_${user?.id ?? "guest"}`;
  const [items, setItems] = useState<SavedAddress[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<AddressDraft>(blank());
  const [errors, setErrors] = useState<AddressFieldErrors>({});
  const [touched, setTouched] = useState<Partial<Record<AddressFieldName, boolean>>>({});
  const [pinLoading, setPinLoading] = useState(false);
  const [pinLookupError, setPinLookupError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const firstFieldRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    requestAnimationFrame(() => {
      formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      firstFieldRef.current?.focus({ preventScroll: true });
    });
  }, [open]);

  const openForm = (nextDraft: AddressDraft) => {
    setDraft(nextDraft);
    setErrors({});
    setTouched({});
    setPinLookupError(null);
    setOpen(true);
  };

  const closeForm = () => {
    setOpen(false);
    setDraft(blank());
    setErrors({});
    setTouched({});
    setPinLookupError(null);
  };

  const fieldError = (name: AddressFieldName) => (touched[name] ? errors[name] : undefined);

  const revalidateFields = (nextDraft: AddressDraft, fields: AddressFieldName[]) => {
    const fieldErrors = validateAddressForm(nextDraft);
    setErrors((prev) => {
      const next = { ...prev };
      for (const field of fields) next[field] = fieldErrors[field];
      return next;
    });
  };

  const touchField = (name: AddressFieldName, nextDraft: AddressDraft = draft) => {
    setTouched((prev) => ({ ...prev, [name]: true }));
    revalidateFields(nextDraft, [name]);
  };

  const updateDraft = (patch: Partial<AddressDraft>) => {
    const next = { ...draft, ...patch };
    setDraft(next);
    const changed = Object.keys(patch) as AddressFieldName[];
    const fieldsToCheck = changed.filter((field) => touched[field]);
    if (fieldsToCheck.length > 0) revalidateFields(next, fieldsToCheck);
  };

  const checkPinDelivery = async (pinValue: string, opts?: { toastOnError?: boolean }) => {
    const clean = normalizePincode(pinValue);
    if (clean.length !== 6) {
      setPinLookupError(null);
      return;
    }
    setPinLoading(true);
    setPinLookupError(null);
    try {
      const result = await lookupPincode(clean);
      if (result.ok) {
        setDraft((prev) => ({
          ...prev,
          city: result.lookup.city,
          state: result.lookup.state,
          pincode: clean,
        }));
        setPinLookupError(null);
      } else {
        setDraft((prev) => ({ ...prev, city: "", state: "", pincode: clean }));
        setPinLookupError(result.error);
        if (opts?.toastOnError !== false) toast.error(result.error);
      }
    } catch {
      setPinLookupError("Couldn't check pincode");
      if (opts?.toastOnError !== false) toast.error("Couldn't check pincode");
    } finally {
      setPinLoading(false);
    }
  };

  const onPinChange = async (value: string) => {
    const clean = normalizePincode(value);
    updateDraft({ pincode: clean });
    if (clean.length === 6) {
      await checkPinDelivery(clean);
    } else {
      setPinLookupError(null);
    }
  };

  useEffect(() => {
    if (!open) return;
    if (normalizePincode(draft.pincode).length === 6) {
      void checkPinDelivery(draft.pincode, { toastOnError: false });
    }
    // Only run when the form opens or the loaded draft changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, draft.id]);

  const loadAddresses = useCallback(async (opts?: { silent?: boolean }) => {
    if (!user) {
      setItems([]);
      setLoading(false);
      return;
    }
    if (!opts?.silent) setLoading(true);
    try {
      let { addresses } = await listMyAddresses();
      if (addresses.length === 0) {
        const legacy = readLegacyLocalAddresses(storageKey);
        if (legacy.length > 0) {
          const synced = await syncMyAddresses(
            legacy.map((a) => ({
              label: a.label ?? "Home",
              full_name: a.full_name,
              phone: a.phone,
              line1: a.line1,
              line2: a.line2 ?? "",
              city: a.city,
              state: a.state,
              pincode: a.pincode,
              is_default: a.is_default,
            })),
          );
          addresses = synced.addresses;
          clearLegacyLocalAddresses(storageKey);
        }
      }
      setItems(addresses);
    } catch {
      if (!opts?.silent) {
        toast.error("Could not load your saved addresses.");
        setItems([]);
      }
    } finally {
      setLoading(false);
    }
  }, [storageKey, user]);

  useEffect(() => {
    void loadAddresses();
  }, [loadAddresses]);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    const allTouched = Object.fromEntries(REQUIRED_FIELDS.map((field) => [field, true])) as Record<
      AddressFieldName,
      boolean
    >;
    setTouched((prev) => ({ ...prev, ...allTouched, label: true }));
    const fieldErrors = validateAddressForm(draft);
    setErrors(fieldErrors);
    const firstError = REQUIRED_FIELDS.map((field) => fieldErrors[field]).find(Boolean);
    if (firstError || pinLookupError) {
      toast.error(firstError ?? pinLookupError ?? "Please fix the errors below");
      return;
    }
    setSaving(true);
    try {
      const payload = normalizeAddressDraft(draft);
      if (draft.id) {
        const { address } = await updateMyAddress(draft.id, payload);
        setItems((list) => {
          const next = list.map((a) => (Number(a.id) === Number(address.id) ? address : a));
          return next.some((a) => Number(a.id) === Number(address.id)) ? next : [...next, address];
        });
      } else {
        const { address } = await createMyAddress(payload);
        setItems((list) => [...list.filter((a) => Number(a.id) !== Number(address.id)), address]);
      }
      closeForm();
      toast.success("Address saved to your account.");
      await loadAddresses({ silent: true });
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Could not save address.");
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: number) => {
    try {
      await deleteMyAddress(id);
      setItems((list) => list.filter((a) => a.id !== id));
    } catch {
      toast.error("Could not delete address.");
    }
  };

  const setDefault = async (id: number) => {
    try {
      await setMyDefaultAddress(id);
      setItems((list) => list.map((a) => ({ ...a, is_default: Number(a.id) === Number(id) })));
      await loadAddresses({ silent: true });
    } catch {
      toast.error("Could not update default address.");
    }
  };

  return (
    <div className="glass-strong rounded-[2rem] p-6 md:p-8">
      <div className="flex items-center justify-between flex-wrap gap-3 mb-6">
        <div>
          <h1 className="font-display text-3xl mb-1 flex items-center gap-2"><MapPin className="h-6 w-6 text-primary" /> Addresses</h1>
          <p className="text-sm text-muted-foreground">Saved to your account for faster checkout on any device.</p>
        </div>
        <button onClick={() => openForm(blank())} className="inline-flex items-center gap-2 rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm">
          <Plus className="h-4 w-4" /> Add address
        </button>
      </div>

      {loading ? (
        <div className="grid place-items-center py-12">
          <Loader2 className="h-6 w-6 animate-spin text-primary" />
        </div>
      ) : items.length === 0 && !open ? (
        <div className="glass rounded-2xl p-8 text-center text-sm text-muted-foreground">No saved addresses yet.</div>
      ) : (
        <div className="grid sm:grid-cols-2 gap-3">
          {items.map((a) => (
            <div key={a.id} className="glass rounded-2xl p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="text-xs uppercase tracking-wider text-primary">{a.label || "Home"}</div>
                {a.is_default ? (
                  <span className="text-[10px] rounded-full bg-primary/15 text-primary px-2 py-0.5 uppercase tracking-wider">Default</span>
                ) : (
                  <button onClick={() => void setDefault(a.id)} className="text-xs text-muted-foreground hover:text-foreground inline-flex items-center gap-1"><StarOff className="h-3 w-3" /> Set default</button>
                )}
              </div>
              <div className="mt-2 text-sm font-medium">{a.full_name}</div>
              <div className="text-sm text-muted-foreground">{a.line1}{a.line2 ? `, ${a.line2}` : ""}</div>
              <div className="text-sm text-muted-foreground">{a.city}, {a.state} {a.pincode}</div>
              <div className="text-xs text-muted-foreground mt-1">{a.phone}</div>
              <div className="mt-3 flex items-center gap-2">
                <button onClick={() => openForm(a)} className="text-xs rounded-full border border-border px-3 py-1.5 hover:bg-muted">Edit</button>
                <button onClick={() => void remove(a.id)} className="text-xs rounded-full border border-border px-3 py-1.5 hover:bg-destructive/10 hover:text-destructive inline-flex items-center gap-1"><Trash2 className="h-3 w-3" /> Delete</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {open && (
        <form ref={formRef} onSubmit={(e) => void save(e)} className="glass rounded-2xl p-4 mt-6 grid sm:grid-cols-2 gap-3 scroll-mt-28" noValidate>
          <p className="sm:col-span-2 text-xs text-muted-foreground"><span className="text-destructive font-semibold">*</span> indicates a required field</p>
          <Field
            label="Label (Home / Office)"
            value={draft.label}
            onChange={(v) => updateDraft({ label: v })}
            onBlur={() => touchField("label")}
            error={fieldError("label")}
            maxLength={40}
          />
          <Field
            label="Full name"
            value={draft.full_name}
            onChange={(v) => updateDraft({ full_name: v })}
            onBlur={() => touchField("full_name")}
            error={fieldError("full_name")}
            required
            maxLength={120}
            inputRef={firstFieldRef}
          />
          <Field
            label="Phone"
            value={draft.phone}
            onChange={(v) => updateDraft({ phone: normalizeAddressPhone(v) })}
            onBlur={() => touchField("phone")}
            error={fieldError("phone")}
            required
            type="tel"
            inputMode="numeric"
            autoComplete="tel"
            maxLength={10}
            placeholder="9999999999"
          />
          <Field
            label="Pincode"
            value={draft.pincode}
            onChange={(v) => void onPinChange(v)}
            onBlur={() => touchField("pincode")}
            error={fieldError("pincode") ?? (touched.pincode ? pinLookupError ?? undefined : undefined)}
            required
            inputMode="numeric"
            maxLength={6}
            hint={pinLoading ? "Looking up pincode…" : undefined}
          />
          <Field
            label="Address line 1"
            value={draft.line1}
            onChange={(v) => updateDraft({ line1: v })}
            onBlur={() => touchField("line1")}
            error={fieldError("line1")}
            required
            maxLength={200}
            className="sm:col-span-2"
            placeholder="Flat 12, Tulip Apartments"
          />
          <Field
            label="Address line 2"
            value={draft.line2 ?? ""}
            onChange={(v) => updateDraft({ line2: v })}
            onBlur={() => touchField("line2")}
            error={fieldError("line2")}
            maxLength={200}
            className="sm:col-span-2"
            placeholder="MG Road (optional)"
          />
          <Field
            label="City"
            value={draft.city}
            onChange={(v) => updateDraft({ city: v })}
            onBlur={() => touchField("city")}
            error={fieldError("city")}
            required
            maxLength={80}
          />
          <Field
            label="State"
            value={draft.state}
            onChange={(v) => updateDraft({ state: v })}
            onBlur={() => touchField("state")}
            error={fieldError("state")}
            required
            maxLength={80}
          />
          <label className="sm:col-span-2 mt-1 inline-flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(draft.is_default)}
              onChange={(e) => setDraft({ ...draft, is_default: e.target.checked })}
            />
            Set as default address
          </label>
          <div className="sm:col-span-2 flex items-center gap-2 mt-2">
            <button type="submit" disabled={saving || pinLoading} className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm inline-flex items-center gap-2 disabled:opacity-60">
              {saving && <Loader2 className="h-4 w-4 animate-spin" />}
              Save address
            </button>
            <button type="button" onClick={closeForm} className="rounded-full border border-border px-5 py-2.5 text-sm">Cancel</button>
          </div>
        </form>
      )}
    </div>
  );
}

function blank(): AddressDraft {
  return { label: "Home", full_name: "", phone: "", line1: "", line2: "", city: "", state: "", pincode: "", is_default: false };
}

function Field({
  label,
  value,
  onChange,
  onBlur,
  error,
  required,
  className,
  inputRef,
  hint,
  ...rest
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  onBlur?: () => void;
  error?: string;
  required?: boolean;
  className?: string;
  inputRef?: React.Ref<HTMLInputElement>;
  hint?: string;
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "onBlur">) {
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
        {hint ? <span className="text-primary normal-case"> · {hint}</span> : null}
      </span>
      <input
        ref={inputRef}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onBlur={onBlur}
        aria-invalid={!!error}
        className={`mt-1 w-full rounded-xl border bg-background px-4 py-2.5 text-sm ${error ? "border-destructive" : "border-border"}`}
        {...rest}
      />
      {error ? <p className="mt-1 text-xs text-destructive">{error}</p> : null}
    </label>
  );
}
