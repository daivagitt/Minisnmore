import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { toast } from "sonner";
import { Loader2, Save } from "lucide-react";
import { deleteVaultSecret, getVaultSecrets, saveVaultSecret, type VaultSecretRow } from "@/lib/backend-data";
import { searchTokens } from "@/lib/product-search";
import { showApiError } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/vault")({ component: VaultPage });

function VaultPage() {
  const [rows, setRows] = useState<VaultSecretRow[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [savingName, setSavingName] = useState<string | null>(null);
  const [filter, setFilter] = useState("");

  const visibleRows = useMemo(() => {
    const tokens = searchTokens(filter);
    if (!tokens.length) return rows;
    return rows.filter((row) => tokens.every((t) => row.name.toLowerCase().includes(t)));
  }, [rows, filter]);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const list = await getVaultSecrets();
      setRows(list);
      setDrafts({});
    } catch (e) {
      showApiError(e, "Could not load vault secrets");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const saveRow = async (name: string) => {
    const value = drafts[name]?.trim();
    if (!value) {
      return;
    }
    setSavingName(name);
    try {
      await saveVaultSecret(name, value);
      toast.success(`${name} updated — restart gateway, store, commerce, and catalog for changes to apply`);
      await reload();
    } catch (e) {
      showApiError(e, `Could not save ${name}`);
    } finally {
      setSavingName(null);
    }
  };

  const clearRow = async (name: string) => {
    if (!window.confirm(`Remove ${name} from the vault? Services will fall back to env file values.`)) {
      return;
    }
    setSavingName(name);
    try {
      await deleteVaultSecret(name);
      toast.success(`${name} removed — restart all Java services for changes to apply`);
      await reload();
    } catch (e) {
      showApiError(e, `Could not delete ${name}`);
    } finally {
      setSavingName(null);
    }
  };

  return (
    <AdminPage
      eyebrow="Security"
      title="Environment vault"
      description="Encrypted secrets stored in the database. OS env / prod.env still overrides vault when set. Zoho OAuth client/refresh tokens are managed under Zoho settings; other ZOHO_* keys belong here. Bootstrap keys (DB passwords, VAULT_MASTER_KEY) stay on the server only."
    >
      {loading ? (
        <p className="text-sm text-muted-foreground flex items-center gap-2">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </p>
      ) : (
        <div className="space-y-6 max-w-3xl">
          <input
            type="search"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Filter by env var name…"
            className="w-full max-w-md rounded-2xl glass px-4 py-2.5 text-sm"
            aria-label="Filter vault keys"
          />
          {visibleRows.map((row) => (
            <div key={row.name} className="rounded-2xl border border-border/60 bg-card/40 p-4 space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-mono text-sm font-medium">{row.name}</p>
                <span className="text-xs text-muted-foreground">
                  {row.hasValue ? "Saved in vault" : "Not set"}
                  {row.updatedAt ? ` · ${new Date(row.updatedAt).toLocaleString()}` : ""}
                </span>
              </div>
              <Field label="New value" hint="Password field — never shown after save. Paste to replace.">
                <input
                  type="password"
                  autoComplete="off"
                  className={inputCls}
                  placeholder={row.hasValue ? "Saved — paste to replace" : "Paste secret value"}
                  value={drafts[row.name] ?? ""}
                  onChange={(e) => setDrafts((d) => ({ ...d, [row.name]: e.target.value }))}
                />
              </Field>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={savingName === row.name || !(drafts[row.name]?.trim())}
                  onClick={() => saveRow(row.name)}
                  className="inline-flex items-center gap-2 rounded-xl bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
                >
                  {savingName === row.name ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                  Save
                </button>
                {row.hasValue && (
                  <button
                    type="button"
                    disabled={savingName === row.name}
                    onClick={() => clearRow(row.name)}
                    className="rounded-xl border border-border px-4 py-2 text-sm text-muted-foreground hover:bg-muted/50"
                  >
                    Remove from vault
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </AdminPage>
  );
}
