import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { toast } from "sonner";
import {
  Save,
  Plug,
  AlertTriangle,
  ExternalLink,
  Warehouse,
  ShoppingCart,
  Truck,
  Loader2,
  Download,
  Upload,
} from "lucide-react";
import { z } from "zod";
import {
  getLedgerlyIntegrationStatus,
  getLedgerlySettings,
  saveLedgerlySettings,
  setLedgerlyPullProductsAutoCreateCategory,
  testLedgerlyConnection,
  syncAllLedgerlyProducts,
  pullLedgerlyInventory,
  pullLedgerlyProducts,
  retryLedgerlyOrders,
  pullLedgerlyOrderTracking,
  syncLedgerlyOrderByNumber,
  formatLedgerlySyncSummary,
  formatLedgerlySyncFailures,
  type LedgerlyIntegrationStatus,
  type LedgerlySyncSummary,
} from "@/lib/backend-data";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";

const LedgerlySchema = z.object({
  api_base_url: z
    .string()
    .trim()
    .url("Enter a valid API base URL (e.g. https://ledgerly.example.com/api)")
    .optional()
    .or(z.literal("")),
  org_id: z.string().trim().max(120).optional(),
  api_token: z.string().trim().optional(),
});

export const Route = createFileRoute("/admin/integrations/ledgerly")({ component: LedgerlyPage });

type SyncAction = "products" | "productsPull" | "inventory" | "orders" | "tracking";

function LedgerlyPage() {
  const [integration, setIntegration] = useState<LedgerlyIntegrationStatus | null>(null);
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [apiToken, setApiToken] = useState("");
  const [orgId, setOrgId] = useState("");
  const [hasToken, setHasToken] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);

  const serverMode = integration?.serverConfigured === true;

  const reload = useCallback(async () => {
    const [status, settings] = await Promise.all([getLedgerlyIntegrationStatus(), getLedgerlySettings()]);
    setIntegration(status);
    if (settings) {
      setApiBaseUrl(settings.apiBaseUrl ?? settings.api_base_url ?? "");
      setOrgId(settings.orgId ?? settings.org_id ?? "");
      setHasToken(!!settings.hasToken);
    }
    return status;
  }, []);

  useEffect(() => {
    reload()
      .catch((error) => showApiError(error, "Could not load Ledgerly settings"))
      .finally(() => setLoading(false));
  }, [reload]);

  const save = async () => {
    const parsed = LedgerlySchema.safeParse({
      api_base_url: apiBaseUrl,
      org_id: orgId,
      api_token: apiToken,
    });
    if (!parsed.success) {
      showValidationErrors(parsed.error.issues.map((issue) => issue.message));
      return;
    }
    if (!hasToken && !apiToken.trim()) {
      showValidationErrors(["Developer token is required on first setup."]);
      return;
    }

    setSaving(true);
    try {
      await saveLedgerlySettings({
        api_base_url: parsed.data.api_base_url || undefined,
        api_token: parsed.data.api_token || undefined,
        org_id: parsed.data.org_id || undefined,
        connected: !!apiToken || integration?.connected,
      });
      toast.success("Ledgerly settings saved");
      setApiToken("");
      await reload();
    } catch (error) {
      showApiError(error, "Could not save Ledgerly settings");
    } finally {
      setSaving(false);
    }
  };

  const testConnection = async () => {
    setTesting(true);
    try {
      const result = await testLedgerlyConnection();
      const connected = result.connected === true || result.ok === true || result.status === "ok";
      const org =
        (typeof result.organizationName === "string" && result.organizationName) ||
        (typeof result.orgName === "string" && result.orgName) ||
        null;
      if (connected) {
        toast.success(org ? `Connected to Ledgerly: ${org}` : "Ledgerly connection OK");
        await reload();
      } else {
        toast.error(
          (typeof result.message === "string" && result.message) ||
            (typeof result.error === "string" && result.error) ||
            "Ledgerly is not connected",
        );
      }
    } catch (error) {
      showApiError(error, "Ledgerly connection test failed");
    } finally {
      setTesting(false);
    }
  };

  if (loading) {
    return (
      <AdminPage eyebrow="Integration" title="Ledgerly">
        <div className="flex justify-center py-12 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin" />
        </div>
      </AdminPage>
    );
  }

  const connected = serverMode ? !!integration?.connected : false;

  return (
    <AdminPage
      eyebrow="Integration"
      title="Ledgerly"
      description={
        serverMode
          ? "Connect once with a developer API token, then sync products and orders from this page. Paid checkout orders sync automatically."
          : "Paste your Ledgerly developer token below, then use the sync actions."
      }
    >
      <div className="space-y-4 max-w-2xl">
        <div className="glass-strong rounded-3xl p-5 space-y-4">
          <div
            className={`flex items-center gap-2 text-sm ${connected ? "text-primary" : "text-muted-foreground"}`}
          >
            {connected ? <Plug className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />}
            {connected
              ? "Ledgerly is connected."
              : serverMode
                ? "Token saved but not marked connected — test connection or save again."
                : "Not connected — add your developer token below."}
          </div>

          <a
            href="https://ledgerly.qualityoutsidethebox.org"
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1.5 text-xs text-primary story-link"
          >
            Open Ledgerly admin <ExternalLink className="h-3 w-3" />
          </a>

          <p className="text-xs text-muted-foreground">
            In Ledgerly go to <span className="text-foreground">Settings → API &amp; Webhooks</span> to create a
            developer token (<span className="font-mono text-[11px]">lt_live_…</span>). The token is stored server-side
            only and never shown again after save.
          </p>

          {orgId ? (
            <p className="text-xs text-muted-foreground">
              Organisation ID: <span className="text-foreground">{orgId}</span>
            </p>
          ) : null}

          <Field
            label="Ledgerly API base URL"
            hint="Leave blank to use the server default. Include /api if your Ledgerly instance uses it."
          >
            <input
              className={inputCls()}
              value={apiBaseUrl}
              onChange={(e) => setApiBaseUrl(e.target.value)}
              placeholder="https://ledgerly.example.com/api"
            />
          </Field>

          <Field
            label="Developer token"
            hint={
              hasToken
                ? "A token is already saved. Paste a new value only to rotate it."
                : "Required on first setup. Paste once — it will not be shown again."
            }
          >
            <input
              className={inputCls()}
              type="password"
              value={apiToken}
              onChange={(e) => setApiToken(e.target.value)}
              placeholder={hasToken ? "•••••••• (leave blank to keep)" : "lt_live_…"}
              autoComplete="off"
            />
          </Field>

          <Field label="Organization ID" hint="Optional. Only needed if your Ledgerly tenant uses org scoping.">
            <input
              className={inputCls()}
              value={orgId}
              onChange={(e) => setOrgId(e.target.value)}
              placeholder="Optional"
            />
          </Field>

          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => void save()}
              disabled={saving || testing}
              className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm flex items-center gap-2"
            >
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              {saving ? "Saving…" : "Save settings"}
            </button>
            <button
              type="button"
              onClick={() => void testConnection()}
              disabled={testing || saving || !hasToken}
              className="rounded-full border border-border px-5 py-2.5 text-sm flex items-center gap-2"
            >
              {testing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plug className="h-4 w-4" />}
              {testing ? "Testing…" : "Test connection"}
            </button>
          </div>

          {serverMode ? (
            <label className="flex items-start gap-2 text-xs text-muted-foreground rounded-2xl border border-border/60 bg-background/40 p-3">
              <input
                type="checkbox"
                className="mt-0.5"
                checked={!!integration?.pullProductsAutoCreateCategory}
                onChange={async (e) => {
                  try {
                    const updated = await setLedgerlyPullProductsAutoCreateCategory(e.target.checked);
                    setIntegration(updated);
                    toast.success(
                      updated.pullProductsAutoCreateCategory
                        ? "Auto-create category on Ledgerly pull enabled"
                        : "Auto-create category on Ledgerly pull disabled",
                    );
                  } catch (error) {
                    showApiError(error, "Could not update Ledgerly pull category setting");
                  }
                }}
              />
              <span>
                Auto-create missing Admin categories when pulling products from Ledgerly.
                <br />
                If off, only existing category matches are assigned.
              </span>
            </label>
          ) : null}
        </div>

        <LedgerlySyncPanel connected={connected && hasToken} />
      </div>
    </AdminPage>
  );
}

function LedgerlySyncPanel({ connected }: { connected: boolean }) {
  const [busy, setBusy] = useState<SyncAction | null>(null);
  const [lastResult, setLastResult] = useState<{ label: string; summary: LedgerlySyncSummary } | null>(null);
  const [orderNumber, setOrderNumber] = useState("");
  const [syncingOrder, setSyncingOrder] = useState(false);

  const run = async (action: SyncAction, label: string, fn: () => Promise<LedgerlySyncSummary>) => {
    setBusy(action);
    try {
      const summary = await fn();
      setLastResult({ label, summary });
      const failed = summary.failureCount ?? 0;
      const success = summary.successCount ?? 0;
      const skipped = summary.skippedCount ?? 0;
      if (failed > 0) {
        toast.warning(formatLedgerlySyncSummary(summary, label));
      } else if ((summary.notices ?? []).length > 0 || skipped > 0) {
        toast.info(formatLedgerlySyncSummary(summary, label));
      } else if (success > 0) {
        toast.success(formatLedgerlySyncSummary(summary, label));
      } else {
        toast.info(`${label}: nothing to process`);
      }
    } catch (error) {
      showApiError(error, `${label} failed`);
    } finally {
      setBusy(null);
    }
  };

  const actions: {
    id: SyncAction;
    label: string;
    description: string;
    icon: typeof Package;
    run: () => Promise<LedgerlySyncSummary>;
  }[] = [
    {
      id: "products",
      label: "Sync products to Ledgerly",
      description:
        "Push active store products to Ledgerly (SKU and category required). Inactive products previously linked to Ledgerly are marked inactive there — they are not recreated.",
      icon: Upload,
      run: syncAllLedgerlyProducts,
    },
    {
      id: "productsPull",
      label: "Pull products from Ledgerly",
      description:
        "Import or update items from Ledgerly into Admin products. Matches by Ledgerly item id, then SKU. Products removed in Ledgerly are deactivated on the storefront.",
      icon: Download,
      run: pullLedgerlyProducts,
    },
    {
      id: "inventory",
      label: "Pull inventory from Ledgerly",
      description: "Update stock for active products linked to Ledgerly (by item id or store SKU). Response shows updated / failed / skipped unlinked.",
      icon: Warehouse,
      run: pullLedgerlyInventory,
    },
    {
      id: "orders",
      label: "Sync orders to Ledgerly",
      description:
        "Create sales orders and invoices in Ledgerly for paid store orders not yet linked (batch). Run if automatic sync failed.",
      icon: ShoppingCart,
      run: retryLedgerlyOrders,
    },
    {
      id: "tracking",
      label: "Pull order tracking",
      description: "Refresh shipment status and tracking numbers from Ledgerly for open paid orders.",
      icon: Truck,
      run: pullLedgerlyOrderTracking,
    },
  ];

  return (
    <div className="glass-strong rounded-3xl p-5 space-y-4">
      <div>
        <h2 className="text-base font-medium text-foreground">Catalogue &amp; orders</h2>
        <p className="text-xs text-muted-foreground mt-1">
          Paid checkout orders sync to Ledgerly automatically when payment is verified. If nothing appears in
          Ledgerly, run Sync orders or enter an order number below.
        </p>
      </div>

      {!connected ? (
        <p className="text-sm text-muted-foreground rounded-2xl border border-dashed border-border p-4">
          Save your developer token and test the connection above before running sync.
        </p>
      ) : (
        <ul className="space-y-3">
          {actions.map(({ id, label, description, icon: Icon, run: runAction }) => {
            const isBusy = busy === id;
            const disabled = busy !== null && !isBusy;
            return (
              <li
                key={id}
                className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 rounded-2xl border border-border/60 bg-background/40 p-4"
              >
                <div className="flex gap-3 min-w-0">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                    <Icon className="h-4 w-4" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-foreground">{label}</p>
                    <p className="text-xs text-muted-foreground">{description}</p>
                  </div>
                </div>
                <button
                  type="button"
                  disabled={disabled}
                  onClick={() => run(id, label, runAction)}
                  className="shrink-0 rounded-full border border-border px-4 py-2 text-sm hover:bg-muted/50 disabled:opacity-50 flex items-center justify-center gap-2 min-w-30"
                >
                  {isBusy ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      Running…
                    </>
                  ) : (
                    "Run"
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {connected ? (
        <div className="rounded-2xl border border-border/60 bg-background/40 p-4 space-y-3">
          <div>
            <p className="text-sm font-medium text-foreground">Sync one order</p>
            <p className="text-xs text-muted-foreground mt-0.5">
              Store order number (e.g. MNM-2606-A4A0B). Creates the Ledgerly sales order and invoice if missing.
            </p>
          </div>
          <div className="flex flex-col sm:flex-row gap-2">
            <input
              type="text"
              value={orderNumber}
              onChange={(e) => setOrderNumber(e.target.value)}
              placeholder="Order number"
              className={inputCls()}
              disabled={syncingOrder || busy !== null}
            />
            <button
              type="button"
              disabled={syncingOrder || busy !== null || !orderNumber.trim()}
              onClick={async () => {
                const trimmed = orderNumber.trim();
                if (!trimmed) return;
                setSyncingOrder(true);
                try {
                  const result = await syncLedgerlyOrderByNumber(trimmed);
                  if (result.ok === false || result.error) {
                    toast.error(
                      (typeof result.error === "string" && result.error) || "Ledgerly order sync failed",
                    );
                  } else {
                    toast.success(`Synced ${result.orderNumber ?? trimmed} to Ledgerly`);
                  }
                } catch (error) {
                  showApiError(error, "Could not sync order to Ledgerly");
                } finally {
                  setSyncingOrder(false);
                }
              }}
              className="shrink-0 rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm hover:opacity-90 disabled:opacity-50 flex items-center justify-center gap-2 min-w-36"
            >
              {syncingOrder ? (
                <>
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  Syncing…
                </>
              ) : (
                "Sync order"
              )}
            </button>
          </div>
        </div>
      ) : null}

      {lastResult ? (
        <div className="text-xs text-muted-foreground rounded-xl bg-muted/30 px-3 py-2 space-y-1">
          <p>Last run — {formatLedgerlySyncSummary(lastResult.summary, lastResult.label)}.</p>
          {formatLedgerlySyncFailures(lastResult.summary).length > 0 ? (
            <ul className="list-disc pl-4 space-y-0.5 text-amber-800/90 dark:text-amber-200/90">
              {formatLedgerlySyncFailures(lastResult.summary).map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
