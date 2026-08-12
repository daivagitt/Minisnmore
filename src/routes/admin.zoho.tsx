import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState, type Dispatch, type SetStateAction } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { toast } from "sonner";
import {
  Save,
  Plug,
  AlertTriangle,
  ExternalLink,
  Package,
  Warehouse,
  ShoppingCart,
  Truck,
  Loader2,
} from "lucide-react";
import { z } from "zod";
import {
  completeZohoOAuth,
  formatZohoSyncFailures,
  formatZohoSyncSummary,
  getZohoConnectUrl,
  getZohoIntegrationStatus,
  getZohoOAuthSetup,
  pullZohoProducts,
  setZohoPullProductsAutoCreateCategory,
  getZohoSettings,
  pullZohoInventory,
  pullZohoOrderTracking,
  retryZohoOrders,
  syncZohoOrderByNumber,
  saveZohoSettings,
  syncAllZohoProducts,
  testZohoConnection,
  type ZohoIntegrationStatus,
  type ZohoOAuthSetup,
  type ZohoSyncSummary,
} from "@/lib/backend-data";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";

const ZohoSchema = z.object({
  org_id: z
    .string()
    .trim()
    .regex(/^\d{6,15}$/, "Org ID must be 6–15 digits"),
  client_id: z.string().trim().min(10, "Client ID looks too short").max(200),
  client_secret: z.string().trim().min(10, "Client Secret looks too short").max(500),
  refresh_token: z.string().trim().min(20, "Refresh token looks too short").max(500),
  data_center: z.enum(["in", "com", "eu", "com.au"]),
});

export const Route = createFileRoute("/admin/zoho")({ component: ZohoPage });

type ZohoSettings = {
  org_id: string | null;
  client_id: string | null;
  client_secret: string | null;
  refresh_token: string | null;
  data_center: string | null;
  connected: boolean;
};

type SyncAction = "products" | "productsPull" | "inventory" | "orders" | "tracking";

function ZohoPage() {
  const [integration, setIntegration] = useState<ZohoIntegrationStatus | null>(null);
  const [s, setS] = useState<ZohoSettings>({
    org_id: "",
    client_id: "",
    client_secret: "",
    refresh_token: "",
    data_center: "in",
    connected: false,
  });
  const [oauthSetup, setOauthSetup] = useState<ZohoOAuthSetup | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [connecting, setConnecting] = useState(false);

  const serverMode = integration?.serverConfigured === true;

  const reloadStatus = useCallback(async () => {
    const status = await getZohoIntegrationStatus();
    setIntegration(status);
    return status;
  }, []);

  useEffect(() => {
    (async () => {
      try {
        const params = new URLSearchParams(window.location.search);
        const code = params.get("code");
        if (code) {
          const status = await completeZohoOAuth(code);
          setIntegration(status);
          window.history.replaceState({}, "", "/admin/zoho");
          toast.success("Zoho Books connected");
          setLoading(false);
          return;
        }

        const [status, data, setup] = await Promise.all([
          getZohoIntegrationStatus(),
          getZohoSettings(),
          getZohoOAuthSetup(),
        ]);
        setIntegration(status);
        if (data) setS(data as ZohoSettings);
        setOauthSetup(setup);
      } catch (error) {
        showApiError(error, "Could not load Zoho settings");
      }
      setLoading(false);
    })();
  }, []);

  const save = async () => {
    const parsed = ZohoSchema.safeParse({
      org_id: s.org_id ?? "",
      client_id: s.client_id ?? "",
      client_secret: s.client_secret ?? "",
      refresh_token: s.refresh_token ?? "",
      data_center: s.data_center ?? "in",
    });
    if (!parsed.success) {
      showValidationErrors(parsed.error.issues.map((issue) => issue.message));
      return;
    }
    setSaving(true);
    try {
      await saveZohoSettings({ ...parsed.data, connected: true });
      toast.success("Zoho credentials saved");
      setS((p) => ({ ...p, ...parsed.data, connected: true }));
      setIntegration((p) => (p ? { ...p, connected: true } : p));
    } catch (error) {
      showApiError(error, "Could not save Zoho settings");
    } finally {
      setSaving(false);
    }
  };

  const connectZoho = async () => {
    setConnecting(true);
    try {
      const { url } = await getZohoConnectUrl();
      window.location.href = url;
    } catch (error) {
      showApiError(error, "Could not start Zoho sign-in");
      setConnecting(false);
    }
  };

  const testConnection = async () => {
    setTesting(true);
    try {
      const result = await testZohoConnection();
      if (result.connected) {
        toast.success(
          result.organizationName
            ? `Connected to Zoho Books: ${result.organizationName}`
            : result.message || "Connected to Zoho Books",
        );
        await reloadStatus();
      } else {
        toast.error(result.message || "Zoho Books is not connected");
      }
    } catch (error) {
      showApiError(error, "Zoho connection test failed");
    } finally {
      setTesting(false);
    }
  };

  if (loading) return <div className="text-sm text-muted-foreground">Loading…</div>;

  const connected = serverMode ? !!integration?.connected : s.connected;

  return (
    <AdminPage
      eyebrow="Integration"
      title="Zoho Books"
      description={
        serverMode
          ? "Connect once, then sync products and orders from this page. Paid checkout orders sync automatically."
          : "Connect Zoho Books, then use the sync actions below."
      }
    >
      <div className="space-y-4 max-w-2xl">
        <div className="glass-strong rounded-3xl p-5 space-y-4">
          <div
            className={`flex items-center gap-2 text-sm ${connected ? "text-primary" : "text-muted-foreground"}`}
          >
            {connected ? <Plug className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />}
            {connected
              ? "Zoho Books is connected."
              : serverMode
                ? "Not connected — click Connect to Zoho Books below."
                : "Not connected — save credentials below."}
          </div>

          {serverMode ? (
            <SimpleConnectPanel
              integration={integration}
              connecting={connecting}
              testing={testing}
              onConnect={connectZoho}
              onTest={testConnection}
            />
          ) : (
            <ManualConnectPanel
              s={s}
              setS={setS}
              oauthSetup={oauthSetup}
              saving={saving}
              testing={testing}
              onSave={save}
              onTest={testConnection}
            />
          )}

          {serverMode ? (
            <label className="flex items-start gap-2 text-xs text-muted-foreground rounded-2xl border border-border/60 bg-background/40 p-3">
              <input
                type="checkbox"
                className="mt-0.5"
                checked={!!integration?.pullProductsAutoCreateCategory}
                onChange={async (e) => {
                  try {
                    const updated = await setZohoPullProductsAutoCreateCategory(e.target.checked);
                    setIntegration(updated);
                    toast.success(
                      updated.pullProductsAutoCreateCategory
                        ? "Auto-create category on Zoho pull enabled"
                        : "Auto-create category on Zoho pull disabled",
                    );
                  } catch (error) {
                    showApiError(error, "Could not update Zoho pull category setting");
                  }
                }}
              />
              <span>
                Auto-create missing Admin categories when pulling products from Zoho.
                <br />
                If off, only existing category matches are assigned.
              </span>
            </label>
          ) : null}
        </div>

        <ZohoSyncPanel connected={connected} />
      </div>
    </AdminPage>
  );
}

function ZohoSyncPanel({ connected }: { connected: boolean }) {
  const [busy, setBusy] = useState<SyncAction | null>(null);
  const [lastResult, setLastResult] = useState<{ label: string; summary: ZohoSyncSummary } | null>(null);
  const [orderNumber, setOrderNumber] = useState("");
  const [syncingOrder, setSyncingOrder] = useState(false);

  const run = async (action: SyncAction, label: string, fn: () => Promise<ZohoSyncSummary>) => {
    setBusy(action);
    try {
      const summary = await fn();
      setLastResult({ label, summary });
      if (summary.failed > 0) {
        toast.warning(formatZohoSyncSummary(summary, label));
      } else if ((summary.notices?.length ?? 0) > 0) {
        toast.info(formatZohoSyncSummary(summary, label));
      } else if (summary.success > 0) {
        toast.success(formatZohoSyncSummary(summary, label));
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
    run: () => Promise<ZohoSyncSummary>;
  }[] = [
    {
      id: "products",
      label: "Sync products to Zoho",
      description:
        "Push all active products (SKU and category required). Syncs stock and the first product image when enabled in Zoho Books (Locations + Item Image).",
      icon: Package,
      run: syncAllZohoProducts,
    },
    {
      id: "productsPull",
      label: "Pull products from Zoho",
      description:
        "Import/update items from Zoho into Admin products. Existing products are matched by Zoho item id, then SKU.",
      icon: Package,
      run: pullZohoProducts,
    },
    {
      id: "inventory",
      label: "Pull inventory from Zoho",
      description:
        "Update stock for active products linked to Zoho (by item id or store SKU). Zoho SKU is optional; inventory tracking must be on in Zoho Books.",
      icon: Warehouse,
      run: pullZohoInventory,
    },
    {
      id: "orders",
      label: "Sync orders to Zoho",
      description:
        "Create sales orders and invoices in Zoho for paid store orders not yet linked (up to 25). Run after checkout if automatic sync failed.",
      icon: ShoppingCart,
      run: retryZohoOrders,
    },
    {
      id: "tracking",
      label: "Pull order tracking",
      description: "Refresh shipment status from Zoho for open paid orders.",
      icon: Truck,
      run: pullZohoOrderTracking,
    },
  ];

  return (
    <div className="glass-strong rounded-3xl p-5 space-y-4">
      <div>
        <h2 className="text-base font-medium text-foreground">Catalogue & orders</h2>
        <p className="text-xs text-muted-foreground mt-1">
          Paid checkout orders sync to Zoho automatically when payment is verified. If nothing appears
          in Zoho Books, run Sync orders to Zoho or enter an order number below.
        </p>
      </div>

      {!connected ? (
        <p className="text-sm text-muted-foreground rounded-2xl border border-dashed border-border p-4">
          Connect Zoho Books above before running sync.
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
              Store order number (e.g. MNM-20260502-ABC). Creates the Zoho sales order and invoice if missing.
            </p>
          </div>
          <div className="flex flex-col sm:flex-row gap-2">
            <input
              type="text"
              value={orderNumber}
              onChange={(e) => setOrderNumber(e.target.value)}
              placeholder="Order number"
              className={inputCls}
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
                  const result = await syncZohoOrderByNumber(trimmed);
                  if (result.zohoSyncStatus === "FAILED" || result.zohoSyncError) {
                    toast.error(result.zohoSyncError ?? "Zoho order sync failed");
                  } else {
                    toast.success(
                      `Synced ${result.orderNumber ?? trimmed} → SO ${result.zohoSalesOrderId ?? "—"}, invoice ${result.zohoInvoiceId ?? "—"}`
                    );
                  }
                } catch (error) {
                  showApiError(error, "Could not sync order to Zoho");
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
          <p>Last run — {formatZohoSyncSummary(lastResult.summary, lastResult.label)}.</p>
          {formatZohoSyncFailures(lastResult.summary).length > 0 ? (
            <ul className="list-disc pl-4 space-y-0.5 text-amber-800/90 dark:text-amber-200/90">
              {formatZohoSyncFailures(lastResult.summary).map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function SimpleConnectPanel({
  integration,
  connecting,
  testing,
  onConnect,
  onTest,
}: {
  integration: ZohoIntegrationStatus | null;
  connecting: boolean;
  testing: boolean;
  onConnect: () => void;
  onTest: () => void;
}) {
  return (
    <div className="space-y-4 text-sm">
      {integration?.organizationId ? (
        <p className="text-xs text-muted-foreground">
          Organisation ID: <span className="text-foreground">{integration.organizationId}</span> · Data
          centre: <span className="text-foreground">{integration.dataCenter}</span>
        </p>
      ) : null}
      <p className="text-xs text-muted-foreground">
        Sign in with Zoho once. Each product needs a SKU and category (Admin → Products) before catalogue sync.
      </p>
      <div className="flex flex-wrap gap-3">
        <button
          type="button"
          onClick={onConnect}
          disabled={connecting || testing}
          className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm"
        >
          {connecting ? "Redirecting…" : integration?.connected ? "Reconnect Zoho Books" : "Connect to Zoho Books"}
        </button>
        <button
          type="button"
          onClick={onTest}
          disabled={testing || connecting}
          className="rounded-full border border-border px-5 py-2.5 text-sm"
        >
          {testing ? "Testing…" : "Test connection"}
        </button>
      </div>
    </div>
  );
}

function ManualConnectPanel({
  s,
  setS,
  oauthSetup,
  saving,
  testing,
  onSave,
  onTest,
}: {
  s: ZohoSettings;
  setS: Dispatch<SetStateAction<ZohoSettings>>;
  oauthSetup: ZohoOAuthSetup | null;
  saving: boolean;
  testing: boolean;
  onSave: () => void;
  onTest: () => void;
}) {
  return (
    <>
      <a
        href={oauthSetup?.apiConsoleUrl ?? "https://api-console.zoho.in/"}
        target="_blank"
        rel="noreferrer"
        className="inline-flex items-center gap-1.5 text-xs text-primary story-link"
      >
        Open Zoho API Console <ExternalLink className="h-3 w-3" />
      </a>
      <Field label="Organisation ID">
        <input
          className={inputCls()}
          value={s.org_id ?? ""}
          onChange={(e) => setS({ ...s, org_id: e.target.value })}
          placeholder="e.g. 60073093410"
        />
      </Field>
      <Field label="Client ID">
        <input
          className={inputCls()}
          value={s.client_id ?? ""}
          onChange={(e) => setS({ ...s, client_id: e.target.value })}
        />
      </Field>
      <Field label="Client Secret">
        <input
          className={inputCls()}
          value={s.client_secret ?? ""}
          onChange={(e) => setS({ ...s, client_secret: e.target.value })}
          type="password"
        />
      </Field>
      <Field label="Refresh token">
        <input
          className={inputCls()}
          value={s.refresh_token ?? ""}
          onChange={(e) => setS({ ...s, refresh_token: e.target.value })}
          type="password"
        />
      </Field>
      <Field label="Data centre">
        <select
          className={inputCls()}
          value={s.data_center ?? "in"}
          onChange={(e) => setS({ ...s, data_center: e.target.value })}
        >
          <option value="in">India (zoho.in)</option>
          <option value="com">US (zoho.com)</option>
          <option value="eu">EU (zoho.eu)</option>
          <option value="com.au">Australia (zoho.com.au)</option>
        </select>
      </Field>
      <div className="flex flex-wrap gap-3">
        <button
          onClick={onSave}
          disabled={saving}
          className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm flex items-center gap-2"
        >
          <Save className="h-4 w-4" /> {saving ? "Saving…" : "Save"}
        </button>
        <button
          type="button"
          onClick={onTest}
          disabled={testing || saving}
          className="rounded-full border border-border px-5 py-2.5 text-sm"
        >
          {testing ? "Testing…" : "Test connection"}
        </button>
      </div>
    </>
  );
}
