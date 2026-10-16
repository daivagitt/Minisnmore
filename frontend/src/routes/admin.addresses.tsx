import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { Plus, Save, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { invalidateSiteSettings } from "@/hooks/useSiteSettings";
import { lookupPincode, normalizePincode } from "@/lib/pincode";
import { normalizeAddressPhone } from "@/lib/account.functions";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";
import {
  deleteStoreAddress,
  emptyStoreAddress,
  getStoreAddressValidationErrors,
  listStoreAddresses,
  saveStoreAddress,
  STORE_ADDRESS_USAGE_LABELS,
  STORE_ADDRESS_USAGES,
  type StoreAddressRow,
  type StoreAddressUsage,
} from "@/lib/store-addresses";

export const Route = createFileRoute("/admin/addresses")({
  head: () => ({ meta: [{ title: "Addresses · Mini's n More" }] }),
  component: AdminAddressesPage,
});

type AddressDraft = StoreAddressRow & { clientKey: string };

function withClientKey(row: StoreAddressRow, fallback?: string): AddressDraft {
  return { ...row, clientKey: fallback ?? (row.id ? `id-${row.id}` : `new-${crypto.randomUUID()}`) };
}

function AdminAddressesPage() {
  const [rows, setRows] = useState<AddressDraft[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingKey, setSavingKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows((await listStoreAddresses()).map((row) => withClientKey(row)));
    } catch (error) {
      showApiError(error, "Could not load addresses");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const update = (clientKey: string, patch: Partial<StoreAddressRow>) => {
    setRows((current) => current.map((row) => (row.clientKey === clientKey ? { ...row, ...patch } : row)));
  };

  const save = async (clientKey: string) => {
    const row = rows.find((item) => item.clientKey === clientKey);
    if (!row) return;
    const errors = getStoreAddressValidationErrors(row);
    if (errors.length) {
      showValidationErrors(errors);
      return;
    }
    setSavingKey(clientKey);
    try {
      const saved = await saveStoreAddress(row);
      toast.success("Address saved");
      invalidateSiteSettings();
      await load();
    } catch (error) {
      showApiError(error, "Could not save address");
    } finally {
      setSavingKey(null);
    }
  };

  const remove = async (clientKey: string) => {
    const row = rows.find((item) => item.clientKey === clientKey);
    if (!row) return;
    if (!row.id) {
      setRows((current) => current.filter((item) => item.clientKey !== clientKey));
      return;
    }
    if (!confirm("Delete this address?")) return;
    try {
      await deleteStoreAddress(row.id);
      toast.success("Deleted");
      invalidateSiteSettings();
      await load();
    } catch (error) {
      showApiError(error, "Could not delete address");
    }
  };

  const onPinChange = async (clientKey: string, value: string) => {
    const pincode = normalizePincode(value);
    update(clientKey, { pincode });
    if (pincode.length !== 6) return;
    try {
      const result = await lookupPincode(pincode);
      if (!result.ok) return;
      setRows((current) =>
        current.map((row) =>
          row.clientKey === clientKey && row.pincode === pincode
            ? { ...row, city: result.lookup.city, state: result.lookup.state }
            : row,
        ),
      );
    } catch {
      /* keep the typed pin; city/state stay editable */
    }
  };

  return (
    <AdminPage
      eyebrow="Company"
      title="Addresses"
      description="Add a company address once, then check which roles it fills (shipment, normal, invoice). Mark it as default for those selected roles."
      action={
        <button
          type="button"
          onClick={() => setRows((current) => [withClientKey(emptyStoreAddress()), ...current])}
          className="rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center gap-2"
        >
          <Plus className="h-4 w-4" /> New address
        </button>
      }
    >
      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <div className="space-y-4">
          {rows.length === 0 ? (
            <div className="glass rounded-2xl p-6 text-sm text-muted-foreground text-center">
              No company addresses yet.
            </div>
          ) : null}
          {rows.map((row) => (
            <article key={row.clientKey} className="glass-strong rounded-3xl p-5 space-y-4">
              <div className="space-y-2">
                <p className="text-sm font-medium">
                  Show as <span className="text-destructive">*</span>
                </p>
                <div className="flex flex-wrap gap-4">
                  {STORE_ADDRESS_USAGES.map((usage) => {
                    const checked = row.usages.includes(usage);
                    return (
                      <label key={usage} className="flex items-center gap-2 text-sm">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(event) => {
                            const next = event.target.checked
                              ? [...row.usages, usage]
                              : row.usages.filter((item) => item !== usage);
                            const usages = STORE_ADDRESS_USAGES.filter((item) => next.includes(item));
                            update(row.clientKey, {
                              usages,
                              ...(usages.length === 0 ? { is_default: false } : {}),
                            });
                          }}
                        />
                        {STORE_ADDRESS_USAGE_LABELS[usage]}
                      </label>
                    );
                  })}
                </div>
              </div>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                <Field label="Label">
                  <input
                    className={inputCls()}
                    value={row.label}
                    placeholder="Warehouse, Registered office…"
                    onChange={(event) => update(row.clientKey, { label: event.target.value })}
                  />
                </Field>
                <Field label="Name" required>
                  <input
                    className={inputCls()}
                    value={row.full_name}
                    onChange={(event) => update(row.clientKey, { full_name: event.target.value })}
                  />
                </Field>
                <Field label="Phone" required>
                  <input
                    className={inputCls()}
                    value={row.phone}
                    inputMode="numeric"
                    maxLength={10}
                    onChange={(event) => update(row.clientKey, { phone: normalizeAddressPhone(event.target.value) })}
                  />
                </Field>
                <Field label="Pincode" required>
                  <input
                    className={inputCls()}
                    value={row.pincode}
                    inputMode="numeric"
                    maxLength={6}
                    onChange={(event) => void onPinChange(row.clientKey, event.target.value)}
                  />
                </Field>
                <Field label="City" required>
                  <input
                    className={inputCls()}
                    value={row.city}
                    onChange={(event) => update(row.clientKey, { city: event.target.value })}
                  />
                </Field>
                <Field label="State" required>
                  <input
                    className={inputCls()}
                    value={row.state}
                    onChange={(event) => update(row.clientKey, { state: event.target.value })}
                  />
                </Field>
                <Field label="Address line 1" required>
                  <input
                    className={inputCls()}
                    value={row.line1}
                    onChange={(event) => update(row.clientKey, { line1: event.target.value })}
                  />
                </Field>
                <Field label="Address line 2">
                  <input
                    className={inputCls()}
                    value={row.line2}
                    onChange={(event) => update(row.clientKey, { line2: event.target.value })}
                  />
                </Field>
              </div>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={row.is_default}
                  onChange={(event) => update(row.clientKey, { is_default: event.target.checked })}
                />
                Default for selected types
              </label>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => void save(row.clientKey)}
                  disabled={savingKey === row.clientKey}
                  className="inline-flex items-center gap-2 rounded-full bg-primary px-4 py-2 text-sm text-primary-foreground disabled:opacity-60"
                >
                  <Save className="h-4 w-4" /> {savingKey === row.clientKey ? "Saving…" : "Save"}
                </button>
                <button
                  type="button"
                  onClick={() => void remove(row.clientKey)}
                  className="inline-flex items-center gap-2 rounded-full border border-border px-4 py-2 text-sm hover:bg-destructive/10 hover:text-destructive"
                >
                  <Trash2 className="h-4 w-4" /> Delete
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </AdminPage>
  );
}
