import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { Plus, Save, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";
import {
  deletePackageBox,
  emptyPackageBox,
  getPackageBoxValidationErrors,
  listPackageBoxes,
  savePackageBox,
  type PackageBoxRow,
} from "@/lib/package-boxes";

export const Route = createFileRoute("/admin/package-types")({
  head: () => ({ meta: [{ title: "Package types · Mini's n More" }] }),
  component: AdminPackageTypesPage,
});

type BoxDraft = PackageBoxRow & { clientKey: string };

function withClientKey(row: PackageBoxRow, fallback?: string): BoxDraft {
  return { ...row, clientKey: fallback ?? (row.id ? `id-${row.id}` : `new-${crypto.randomUUID()}`) };
}

function AdminPackageTypesPage() {
  const [rows, setRows] = useState<BoxDraft[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingKey, setSavingKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows((await listPackageBoxes()).map((row) => withClientKey(row)));
    } catch (error) {
      showApiError(error, "Could not load package types");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const update = (clientKey: string, patch: Partial<PackageBoxRow>) => {
    setRows((current) => current.map((row) => (row.clientKey === clientKey ? { ...row, ...patch } : row)));
  };

  const save = async (clientKey: string) => {
    const row = rows.find((item) => item.clientKey === clientKey);
    if (!row) return;
    const errors = getPackageBoxValidationErrors(row);
    if (errors.length) {
      showValidationErrors(errors);
      return;
    }
    setSavingKey(clientKey);
    try {
      await savePackageBox(row);
      toast.success("Package type saved");
      await load();
    } catch (error) {
      showApiError(error, "Could not save package type");
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
    if (!confirm("Delete this package type?")) return;
    try {
      await deletePackageBox(row.id);
      toast.success("Deleted");
      await load();
    } catch (error) {
      showApiError(error, "Could not delete package type");
    }
  };

  return (
    <AdminPage
      eyebrow="Fulfilment"
      title="Package types"
      description="These names appear in Package Type on Create Package and Book Shipment. Choosing one fills dimensions and optional default gross weight."
      action={
        <button
          type="button"
          onClick={() => setRows((current) => [withClientKey(emptyPackageBox()), ...current])}
          className="rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center gap-2"
        >
          <Plus className="h-4 w-4" /> New package type
        </button>
      }
    >
      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <div className="space-y-4">
          {rows.length === 0 ? (
            <div className="glass rounded-2xl p-6 text-sm text-muted-foreground text-center">
              No package types yet. Add a name, centimetre dimensions, and optional gross weight.
            </div>
          ) : null}
          {rows.map((row) => (
            <article key={row.clientKey} className="glass-strong rounded-3xl p-5 space-y-4">
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
                <Field label="Name" required>
                  <input
                    className={inputCls()}
                    value={row.name}
                    placeholder="Small carton"
                    maxLength={80}
                    onChange={(event) => update(row.clientKey, { name: event.target.value })}
                  />
                </Field>
                <Field label="Length (cm)" required>
                  <input
                    className={inputCls()}
                    type="number"
                    min={1}
                    step={1}
                    value={row.length_cm || ""}
                    onChange={(event) => update(row.clientKey, { length_cm: Number(event.target.value) || 0 })}
                  />
                </Field>
                <Field label="Width (cm)" required>
                  <input
                    className={inputCls()}
                    type="number"
                    min={1}
                    step={1}
                    value={row.breadth_cm || ""}
                    onChange={(event) => update(row.clientKey, { breadth_cm: Number(event.target.value) || 0 })}
                  />
                </Field>
                <Field label="Height (cm)" required>
                  <input
                    className={inputCls()}
                    type="number"
                    min={1}
                    step={1}
                    value={row.height_cm || ""}
                    onChange={(event) => update(row.clientKey, { height_cm: Number(event.target.value) || 0 })}
                  />
                </Field>
                <Field label="Gross weight (g)">
                  <input
                    className={inputCls()}
                    type="number"
                    min={1}
                    step={1}
                    placeholder="Optional"
                    value={row.weight_grams || ""}
                    onChange={(event) => update(row.clientKey, { weight_grams: Number(event.target.value) || 0 })}
                  />
                </Field>
              </div>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => void save(row.clientKey)}
                  disabled={savingKey === row.clientKey}
                  className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm flex items-center gap-2 disabled:opacity-50"
                >
                  <Save className="h-4 w-4" />
                  {savingKey === row.clientKey ? "Saving…" : "Save"}
                </button>
                <button
                  type="button"
                  onClick={() => void remove(row.clientKey)}
                  className="rounded-full border border-border bg-background px-4 py-2 text-sm flex items-center gap-2"
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
