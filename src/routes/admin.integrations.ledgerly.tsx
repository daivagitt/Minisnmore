import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { toast } from "sonner";
import { Save, Plug, Package, Warehouse, ShoppingCart, Truck, Loader2 } from "lucide-react";
import {
  getLedgerlyIntegrationStatus,
  getLedgerlySettings,
  saveLedgerlySettings,
  testLedgerlyConnection,
  syncAllLedgerlyProducts,
  pullLedgerlyInventory,
  pullLedgerlyProducts,
  retryLedgerlyOrders,
  pullLedgerlyOrderTracking,
  formatLedgerlySyncSummary,
  formatLedgerlySyncFailures,
  type LedgerlyIntegrationStatus,
  type LedgerlySyncSummary,
} from "@/lib/backend-data";
import { showApiError } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/integrations/ledgerly")({ component: LedgerlyPage });

function LedgerlyPage() {
  const [integration, setIntegration] = useState<LedgerlyIntegrationStatus | null>(null);
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [apiToken, setApiToken] = useState("");
  const [orgId, setOrgId] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [syncing, setSyncing] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<{ label: string; summary: LedgerlySyncSummary } | null>(null);

  const reload = useCallback(async () => {
    const [status, settings] = await Promise.all([getLedgerlyIntegrationStatus(), getLedgerlySettings()]);
    setIntegration(status);
    if (settings) {
      setApiBaseUrl(settings.apiBaseUrl ?? settings.api_base_url ?? "");
      setOrgId(settings.orgId ?? settings.org_id ?? "");
    }
    return status;
  }, []);

  useEffect(() => {
    reload()
      .catch(showApiError)
      .finally(() => setLoading(false));
  }, [reload]);

  const run = async (key: string, label: string, fn: () => Promise<LedgerlySyncSummary>) => {
    setSyncing(key);
    try {
      const summary = await fn();
      setLastResult({ label, summary });
      const failed = summary.failureCount ?? 0;
      const success = summary.successCount ?? 0;
      if (failed > 0) {
        toast.warning(formatLedgerlySyncSummary(summary, label));
      } else if ((summary.notices ?? []).length > 0) {
        toast.info(formatLedgerlySyncSummary(summary, label));
      } else if (success > 0) {
        toast.success(formatLedgerlySyncSummary(summary, label));
      } else {
        toast.info(`${label}: nothing to process`);
      }
      await reload();
    } catch (e) {
      showApiError(e);
    } finally {
      setSyncing(null);
    }
  };

  if (loading) {
    return (
      <AdminPage title="Ledgerly">
        <div className="flex justify-center py-12 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin" />
        </div>
      </AdminPage>
    );
  }

  return (
    <AdminPage title="Ledgerly integration" subtitle="Connect Mini's n More to Ledgerly via developer API token">
      <div className="mb-6 rounded-xl border border-white/40 bg-white/50 p-4 text-sm">
        <div className="flex items-center gap-2 font-medium">
          <Plug className="h-4 w-4" />
          {integration?.connected ? "Connected" : "Not connected"}
        </div>
        <p className="mt-1 text-muted-foreground">
          Create a token in Ledgerly Settings → API &amp; Webhooks, then paste it here (stored server-side only).
        </p>
      </div>

      <form
        className="mb-8 grid max-w-xl gap-4"
        onSubmit={async (e) => {
          e.preventDefault();
          setSaving(true);
          try {
            await saveLedgerlySettings({
              api_base_url: apiBaseUrl,
              api_token: apiToken || undefined,
              org_id: orgId,
              connected: !!apiToken || integration?.connected,
            });
            toast.success("Ledgerly settings saved");
            setApiToken("");
            await reload();
          } catch (err) {
            showApiError(err);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Field label="Ledgerly API base URL">
          <input
            className={inputCls}
            value={apiBaseUrl}
            onChange={(e) => setApiBaseUrl(e.target.value)}
            placeholder="https://lender.qualityoutsidethebox.org/api"
          />
        </Field>
        <Field label="Developer token (lt_live_…)">
          <input
            className={inputCls}
            type="password"
            value={apiToken}
            onChange={(e) => setApiToken(e.target.value)}
            placeholder={integration?.serverConfigured ? "•••••••• (leave blank to keep)" : "Paste token once"}
          />
        </Field>
        <Field label="Organization ID (optional)">
          <input className={inputCls} value={orgId} onChange={(e) => setOrgId(e.target.value)} />
        </Field>
        <div className="flex gap-2">
          <button type="submit" className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm text-primary-foreground" disabled={saving}>
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            Save
          </button>
          <button
            type="button"
            className="inline-flex items-center gap-2 rounded-lg border px-4 py-2 text-sm"
            disabled={testing}
            onClick={async () => {
              setTesting(true);
              try {
                await testLedgerlyConnection();
                toast.success("Ledgerly connection OK");
              } catch (e) {
                showApiError(e);
              } finally {
                setTesting(false);
              }
            }}
          >
            {testing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plug className="h-4 w-4" />}
            Test connection
          </button>
        </div>
      </form>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {[
          { key: "products", label: "Sync products", icon: Package, fn: syncAllLedgerlyProducts },
          { key: "pull", label: "Pull products", icon: Package, fn: pullLedgerlyProducts },
          { key: "inv", label: "Pull inventory", icon: Warehouse, fn: pullLedgerlyInventory },
          { key: "orders", label: "Retry orders", icon: ShoppingCart, fn: retryLedgerlyOrders },
          { key: "track", label: "Pull tracking", icon: Truck, fn: pullLedgerlyOrderTracking },
        ].map((action) => (
          <button
            key={action.key}
            type="button"
            className="flex items-center gap-2 rounded-xl border border-white/50 bg-white/60 px-4 py-3 text-left text-sm hover:bg-white/80"
            disabled={syncing !== null}
            onClick={() => run(action.key, action.label, action.fn)}
          >
            {syncing === action.key ? <Loader2 className="h-4 w-4 animate-spin" /> : <action.icon className="h-4 w-4" />}
            {action.label}
          </button>
        ))}
      </div>

      {lastResult && (
        <div className="mt-6 rounded-xl border border-white/40 bg-white/50 p-4 text-sm text-muted-foreground">
          <p>{formatLedgerlySyncSummary(lastResult.summary, lastResult.label)}</p>
          {formatLedgerlySyncFailures(lastResult.summary).length > 0 && (
            <ul className="mt-2 list-disc space-y-1 pl-5">
              {formatLedgerlySyncFailures(lastResult.summary).map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </AdminPage>
  );
}
