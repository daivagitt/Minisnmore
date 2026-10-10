import { Link } from "@tanstack/react-router";
import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { AdminCaseSearchFields } from "@/components/admin/AdminCaseSearchFields";
import {
  AdminCaseListTable,
  AdminCaseTd,
  AdminCaseTh,
  adminCaseRowClass,
} from "@/components/admin/AdminCaseListTable";
import { AdminReasonDialog, type ShipmentCancelMode } from "@/components/admin/AdminReasonDialog";
import { BookShipmentDialog } from "@/components/admin/BookShipmentDialog";
import { CreatePackageDialog } from "@/components/admin/CreatePackageDialog";
import { AlertCircle, Ban, CheckCircle2, ChevronDown, CloudUpload, FileText, Loader2, Package, Printer, RefreshCw, Truck, X } from "lucide-react";
import { toast } from "sonner";
import {
  cancelAdminOrder,
  cancelAdminOrderShipment,
  listAdminOrders,
  markCodCollected,
  type AdminOrderRow,
} from "@/lib/orders.functions";
import {
  formatZohoSyncFailures,
  formatZohoSyncSummary,
  retryZohoOrders,
  syncZohoOrderByNumber,
} from "@/lib/backend-data";
import { adminCaseSearchActive, adminOrderApiDateRange } from "@/lib/admin-case-search";
import { showApiError } from "@/lib/ui-errors";
import { ListPagination } from "@/components/ListPagination";
import { LIST_PAGE_SIZE } from "@/lib/pagination";
import { SHOW_M2_POST_ORDER_UI } from "@/lib/feature-flags";
import { isInvoiceAvailable } from "@/lib/invoice-availability";
import { isPackageReady } from "@/lib/fulfillment-docs";
import { trackingStatusLabel } from "@/lib/order-tracking";
import { formatAdminPaymentStatusLabel } from "@/lib/order-status";
import { openPrintableShippingLabel } from "@/lib/print-shipping-label";
import { cn } from "@/lib/utils";

export type OrdersWorkspaceMode = "admin" | "operations" | "support";

const ORDER_STATUS_VALUES = new Set(["pending", "confirmed", "hand_wrapped", "fulfilled", "cancelled"]);
const TRACKING_STATUS_VALUES = new Set([
  "awaiting_pickup",
  "shipped",
  "out_for_delivery",
  "delivery_failed",
  "carrier_cancelled",
]);

const ORDER_STATUS_FILTERS = [
  { value: "", label: "All" },
  { value: "pending", label: "Pending" },
  { value: "confirmed", label: "Confirmed" },
  { value: "hand_wrapped", label: "Hand-wrapped" },
  { value: "awaiting_pickup", label: "Waiting for pickup" },
  { value: "carrier_cancelled", label: "ExpressBees cancelled" },
  { value: "shipped", label: "Shipped" },
  { value: "out_for_delivery", label: "Out for delivery" },
  { value: "delivery_failed", label: "Delivery failed" },
  { value: "fulfilled", label: "Delivered" },
  { value: "cancelled", label: "Cancelled" },
] as const;

function isExpressCarrierCancelled(order: AdminOrderRow) {
  return order.tracking_status === "carrier_cancelled";
}

function normalizedStatus(value: string | null | undefined) {
  return value?.trim().toLowerCase() || "not_synced";
}

function SyncBadge({ status, eligible }: { status: string; eligible: boolean }) {
  if (!eligible) {
    return (
      <span className="rounded-full bg-muted px-2.5 py-1 text-[11px] font-medium text-muted-foreground">
        Not due
      </span>
    );
  }
  const normalized = normalizedStatus(status);
  const styles =
    normalized === "synced"
      ? "bg-emerald-100 text-emerald-800"
      : normalized === "pending"
        ? "bg-amber-100 text-amber-800"
        : "bg-red-100 text-red-800";
  const label =
    normalized === "not_synced" ? "Not synced" : normalized[0].toUpperCase() + normalized.slice(1);
  return (
    <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${styles}`}>{label}</span>
  );
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <label className="block min-w-[9.5rem]">
      <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        {label}
      </span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
      >
        {options.map((option) => (
          <option key={option.value || "all"} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}

export function OrdersWorkspace({
  mode,
  invoiceHref,
}: {
  mode: OrdersWorkspaceMode;
  invoiceHref: (orderNumber: string) => string;
}) {
  const canFulfill = mode === "admin" || mode === "operations";
  const canSyncZoho = mode === "admin";
  const [rows, setRows] = useState<AdminOrderRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [packageOrder, setPackageOrder] = useState<AdminOrderRow | null>(null);
  const [shipmentOrder, setShipmentOrder] = useState<AdminOrderRow | null>(null);
  const [bookRequiresOverride, setBookRequiresOverride] = useState(false);
  const [cancelShipmentTarget, setCancelShipmentTarget] = useState<AdminOrderRow | null>(null);
  const [cancelShipmentMode, setCancelShipmentMode] = useState<"xpressbees" | "manual">("xpressbees");
  const [cancelShipmentReason, setCancelShipmentReason] = useState("");
  const [cancelOrderTarget, setCancelOrderTarget] = useState<AdminOrderRow | null>(null);
  const [cancelOrderReason, setCancelOrderReason] = useState("");
  const [cancelOrderShipmentMode, setCancelOrderShipmentMode] = useState<ShipmentCancelMode>("xpressbees");
  const [page, setPage] = useState(1);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [expressCancellationCount, setExpressCancellationCount] = useState(0);
  const [orderQueryInput, setOrderQueryInput] = useState("");
  const [q, setQ] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [status, setStatus] = useState("");
  const [paymentStatus, setPaymentStatus] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("");
  const [zohoSyncStatus, setZohoSyncStatus] = useState("");
  const [expandedOverride, setExpandedOverride] = useState<string | null | undefined>(undefined);
  const range = useMemo(() => adminOrderApiDateRange(dateFrom, dateTo), [dateFrom, dateTo]);
  const loadGen = useRef(0);
  const hasRowsRef = useRef(false);
  const orderSearchActive = adminCaseSearchActive(orderQueryInput, dateFrom, dateTo);
  const orderFetchSearchActive = adminCaseSearchActive(q, dateFrom, dateTo);
  hasRowsRef.current = rows.length > 0;
  const filtersKey = `${dateFrom}:${dateTo}:${q}:${status}:${paymentStatus}:${paymentMethod}:${zohoSyncStatus}`;
  const filtersActive = Boolean(
    q || status || paymentStatus || paymentMethod || zohoSyncStatus || dateFrom || dateTo,
  );

  useEffect(() => {
    const timer = window.setTimeout(() => setQ(orderQueryInput.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [orderQueryInput]);

  useEffect(() => {
    setPage(1);
  }, [filtersKey]);

  const load = useCallback(async () => {
    const gen = ++loadGen.current;
    const keepList = hasRowsRef.current;
    if (!keepList) setLoading(true);
    try {
      const res = await listAdminOrders(range.from, range.to, {
        page,
        size: LIST_PAGE_SIZE,
        q,
        status:
          !orderFetchSearchActive && ORDER_STATUS_VALUES.has(status) ? status : undefined,
        trackingStatus:
          !orderFetchSearchActive && TRACKING_STATUS_VALUES.has(status) ? status : undefined,
        paymentStatus,
        paymentMethod,
        zohoSyncStatus,
      });
      if (gen !== loadGen.current) return;
      setRows(res.orders ?? []);
      setTotalItems(res.totalItems);
      setTotalPages(Math.max(1, res.totalPages));
      setExpressCancellationCount(res.express_cancellation_count ?? 0);
      const nextPage = Math.min(Math.max(1, res.page), Math.max(1, res.totalPages));
      if (nextPage !== page) setPage(nextPage);
    } catch (error) {
      if (gen !== loadGen.current) return;
      showApiError(error, "Could not load orders");
    } finally {
      if (gen === loadGen.current) setLoading(false);
    }
  }, [
    range.from,
    range.to,
    page,
    q,
    orderFetchSearchActive,
    status,
    paymentStatus,
    paymentMethod,
    zohoSyncStatus,
  ]);

  useEffect(() => {
    void load();
  }, [load]);

  const collect = async (orderNumber: string) => {
    setBusy(`collect:${orderNumber}`);
    try {
      await markCodCollected(orderNumber);
      toast.success(`${orderNumber} marked as collected`);
      await load();
    } catch (error) {
      showApiError(error, "Could not mark order as collected");
    } finally {
      setBusy(null);
    }
  };

  const openBookShipment = (order: AdminOrderRow, requiresOverride: boolean) => {
    if (!isPackageReady(order)) {
      toast.error("Create a package before booking a shipment");
      return;
    }
    setBookRequiresOverride(requiresOverride);
    setShipmentOrder(order);
  };

  const submitCancelShipment = async () => {
    if (!cancelShipmentTarget || cancelShipmentReason.trim().length < 5) return;
    setBusy(`cancel-ship:${cancelShipmentTarget.order_number}`);
    try {
      await cancelAdminOrderShipment(cancelShipmentTarget.order_number, {
        mode: cancelShipmentMode,
        reason: cancelShipmentReason.trim(),
      });
      toast.success(`${cancelShipmentTarget.order_number} shipment cancelled`);
      setCancelShipmentTarget(null);
      setCancelShipmentReason("");
      await load();
    } catch (error) {
      showApiError(error, "Could not cancel shipment");
    } finally {
      setBusy(null);
    }
  };

  const cancelOrderAwbAlreadyVoided =
    !!cancelOrderTarget && isExpressCarrierCancelled(cancelOrderTarget);
  const cancelOrderNeedsLiveAwbVoid =
    Boolean(cancelOrderTarget?.tracking_number) && !cancelOrderAwbAlreadyVoided;

  const submitCancelOrder = async () => {
    if (!cancelOrderTarget || cancelOrderReason.trim().length < 5) return;
    setBusy(`cancel-order:${cancelOrderTarget.order_number}`);
    try {
      // Mode selector is UX-only for live AWBs. Backend resolveShipmentCancelMode is
      // authoritative for carrier_cancelled (forces local MANUAL clear).
      await cancelAdminOrder(cancelOrderTarget.order_number, {
        reason: cancelOrderReason.trim(),
        ...(cancelOrderNeedsLiveAwbVoid ? { shipment_mode: cancelOrderShipmentMode } : {}),
      });
      toast.success(`${cancelOrderTarget.order_number} cancelled`);
      setCancelOrderTarget(null);
      setCancelOrderReason("");
      await load();
    } catch (error) {
      showApiError(error, "Could not cancel order");
    } finally {
      setBusy(null);
    }
  };

  const sync = async (orderNumber: string) => {
    setBusy(`zoho:${orderNumber}`);
    try {
      const result = await syncZohoOrderByNumber(orderNumber);
      if (normalizedStatus(result.zohoSyncStatus) !== "synced") {
        throw new Error(result.zohoSyncError || "Zoho sync did not complete");
      }
      toast.success(`${orderNumber} synced to Zoho`);
      await load();
    } catch (error) {
      showApiError(error, `Could not sync ${orderNumber} to Zoho`);
    } finally {
      setBusy(null);
    }
  };

  const syncPending = async () => {
    setBusy("zoho:pending");
    try {
      const summary = await retryZohoOrders();
      toast.success(formatZohoSyncSummary(summary, "Order sync"));
      const notes = formatZohoSyncFailures(summary);
      if (notes.length > 0) {
        toast.message(notes.slice(0, 3).join(" · "));
      }
      await load();
    } catch (error) {
      showApiError(error, "Could not sync pending orders to Zoho");
    } finally {
      setBusy(null);
    }
  };

  const attentionOrders = useMemo(
    () =>
      rows.filter(
        (order) => order.zoho_sync_eligible && normalizedStatus(order.zoho_sync_status) !== "synced",
      ),
    [rows],
  );
  const zohoEligible = rows.filter((order) => order.zoho_sync_eligible);
  const zohoSynced = zohoEligible.filter(
    (order) => normalizedStatus(order.zoho_sync_status) === "synced",
  ).length;
  const pageItemKey = rows.map((order) => order.order_number).join("|");
  useEffect(() => {
    setExpandedOverride(undefined);
  }, [pageItemKey]);
  const autoExpandedNumber = rows.length === 1 ? rows[0].order_number : null;
  const expandedNumber = expandedOverride === undefined ? autoExpandedNumber : expandedOverride;

  return (
    <AdminPage
      eyebrow={mode === "support" ? "Customer Support" : "Fulfilment"}
      title="Orders"
      description={
        mode === "support"
          ? "View and filter orders, inspect details, and print available invoices."
          : "Create a package first, then book an XpressBees or manual shipment, and sync with Zoho Books."
      }
      action={
        <div className="flex flex-wrap gap-2">
          {canSyncZoho ? (
            <button
              type="button"
              onClick={() => void syncPending()}
              disabled={loading || busy !== null}
              className="inline-flex h-10 items-center gap-2 rounded-xl border border-border bg-background px-3 text-sm font-medium disabled:opacity-60"
            >
              {busy === "zoho:pending" ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <CloudUpload className="h-4 w-4" />
              )}
              {busy === "zoho:pending" ? "Syncing…" : "Sync pending to Zoho"}
            </button>
          ) : null}
          <button
            type="button"
            onClick={() => void load()}
            disabled={loading}
            className="inline-flex h-10 items-center gap-2 rounded-xl border border-border bg-background px-3 text-sm font-medium disabled:opacity-60"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} /> Refresh
          </button>
        </div>
      }
    >
      <section
        className="glass-strong mb-5 space-y-4 rounded-3xl p-4 sm:p-5"
        aria-label="Filter orders"
      >
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground">
            Order status
          </p>
          <div className="flex flex-wrap gap-2">
            {ORDER_STATUS_FILTERS.map((option) => (
              <button
                key={option.value || "all"}
                type="button"
                onClick={() => setStatus(option.value)}
                className={`rounded-full border px-3 py-1.5 text-xs transition ${
                  status === option.value
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border hover:bg-muted/60"
                }`}
              >
                {option.label}
                {option.value === "carrier_cancelled" && expressCancellationCount > 0 ? (
                  <span className="ml-1.5 rounded-full bg-destructive/15 px-1.5 py-0.5 text-[10px] font-semibold text-destructive">
                    {expressCancellationCount}
                  </span>
                ) : null}
              </button>
            ))}
          </div>
        </div>

        <AdminCaseSearchFields
          orderQuery={orderQueryInput}
          onOrderQueryChange={setOrderQueryInput}
          from={dateFrom}
          to={dateTo}
          onFromChange={setDateFrom}
          onToChange={setDateTo}
          placeholder="Order no., name, phone, city, email"
          searchAriaLabel="Search orders by number, name, phone, city, or email"
          hint={
            orderSearchActive
              ? "Searching across statuses — order status chips pause until you clear search or dates."
              : null
          }
        />

        <div className="flex flex-wrap items-end gap-3">
          <FilterSelect
            label="Payment"
            value={paymentStatus}
            onChange={setPaymentStatus}
            options={[
              { value: "", label: "All payments" },
              { value: "pending", label: "Pending" },
              { value: "paid", label: "Paid" },
              { value: "failed", label: "Failed" },
            ]}
          />
          <FilterSelect
            label="Method"
            value={paymentMethod}
            onChange={setPaymentMethod}
            options={[
              { value: "", label: "All methods" },
              { value: "online", label: "Online" },
              { value: "cod", label: "COD" },
            ]}
          />
          {canSyncZoho && (
            <FilterSelect
              label="Zoho"
              value={zohoSyncStatus}
              onChange={setZohoSyncStatus}
              options={[
                { value: "", label: "All Zoho" },
                { value: "attention", label: "Needs sync" },
                { value: "not_synced", label: "Not synced" },
                { value: "pending", label: "Pending" },
                { value: "synced", label: "Synced" },
                { value: "failed", label: "Failed" },
              ]}
            />
          )}
          {filtersActive && (
            <button
              type="button"
              onClick={() => {
                setOrderQueryInput("");
                setQ("");
                setDateFrom("");
                setDateTo("");
                setStatus("");
                setPaymentStatus("");
                setPaymentMethod("");
                setZohoSyncStatus("");
              }}
              className="inline-flex h-10 items-center gap-1.5 rounded-xl border border-border bg-background px-3 text-sm font-medium"
            >
              <X className="h-4 w-4" /> Clear filters
            </button>
          )}
          {!loading && (
            <p className="ml-auto text-xs text-muted-foreground" aria-live="polite">
              {totalItems === 0
                ? "0 orders"
                : totalPages > 1
                  ? `Showing page ${page} of ${totalPages} · ${totalItems} orders`
                  : `${totalItems} order${totalItems === 1 ? "" : "s"}`}
            </p>
          )}
        </div>
      </section>

      {expressCancellationCount > 0 && (
        <button
          type="button"
          onClick={() => {
            setDateFrom("");
            setDateTo("");
            setStatus("carrier_cancelled");
          }}
          className="mb-5 flex w-full flex-wrap items-center gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-left"
        >
          <AlertCircle className="h-5 w-5 text-destructive" />
          <div>
            <p className="font-semibold">
              {expressCancellationCount} ExpressBees{" "}
              {expressCancellationCount === 1 ? "cancellation" : "cancellations"}
            </p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              Courier voided the AWB before pickup. Rebook a shipment or cancel the order.
            </p>
          </div>
        </button>
      )}

      {canSyncZoho && !loading && rows.length > 0 && (
        <div
          className={`mb-5 rounded-2xl border p-4 ${attentionOrders.length === 0 ? "border-emerald-200 bg-emerald-50/80" : "border-amber-200 bg-amber-50/80"}`}
        >
          <div className="flex flex-wrap items-center gap-3">
            {attentionOrders.length === 0 ? (
              <CheckCircle2 className="h-5 w-5 text-emerald-700" />
            ) : (
              <AlertCircle className="h-5 w-5 text-amber-700" />
            )}
            <p className="font-semibold">
              {attentionOrders.length === 0
                ? "Eligible orders on this page are synced to Zoho"
                : `${attentionOrders.length} ${attentionOrders.length === 1 ? "order on this page needs" : "orders on this page need"} Zoho sync attention`}
            </p>
            <div className="ml-auto flex gap-2 text-xs text-muted-foreground">
              <span className="rounded-full bg-white/70 px-2.5 py-1">
                Zoho {zohoSynced}/{zohoEligible.length} on this page
              </span>
            </div>
          </div>
        </div>
      )}

      {loading ? (
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No orders match the current filters.</p>
      ) : (
        <AdminCaseListTable id="admin-orders-list" caption="Orders">
          <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <AdminCaseTh className="w-10">
                <span className="sr-only">Details</span>
              </AdminCaseTh>
              <AdminCaseTh>Order ID</AdminCaseTh>
              <AdminCaseTh>Customer</AdminCaseTh>
              <AdminCaseTh>Amount</AdminCaseTh>
              <AdminCaseTh>Status</AdminCaseTh>
              <AdminCaseTh>Payment</AdminCaseTh>
              <AdminCaseTh>Placed</AdminCaseTh>
              <AdminCaseTh>Actions</AdminCaseTh>
            </tr>
          </thead>
          <tbody>
            {rows.map((order) => {
              const isPendingCod =
                order.payment_method === "cod" && order.payment_status === "pending";
              const alreadySynced = normalizedStatus(order.zoho_sync_status) === "synced";
              const canSync = order.zoho_sync_eligible;
              const expanded = expandedNumber === order.order_number;
              return (
                <Fragment key={order.order_number}>
                  <tr className={adminCaseRowClass}>
                    <AdminCaseTd className="w-10 pr-0">
                      <button
                        type="button"
                        aria-expanded={expanded}
                        aria-label={`Details for ${order.order_number}`}
                        onClick={() => setExpandedOverride(expanded ? null : order.order_number)}
                        className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground"
                      >
                        <ChevronDown className={cn("h-4 w-4 transition-transform", expanded && "rotate-180")} />
                      </button>
                    </AdminCaseTd>
                    <AdminCaseTd className="whitespace-nowrap font-medium">{order.order_number}</AdminCaseTd>
                    <AdminCaseTd className="min-w-[10rem]">
                      <p className="font-medium">{order.full_name || "—"}</p>
                      <p className="text-xs text-muted-foreground">
                        {order.phone || "—"}
                        {order.city ? ` · ${order.city}` : ""}
                      </p>
                    </AdminCaseTd>
                    <AdminCaseTd className="whitespace-nowrap tabular-nums">
                      ₹{Number(order.total ?? 0).toLocaleString("en-IN")}
                      {Number(order.cod_fee ?? 0) > 0 ? (
                        <p className="text-xs text-muted-foreground">
                          +₹{Number(order.cod_fee).toLocaleString("en-IN")} COD
                        </p>
                      ) : null}
                    </AdminCaseTd>
                    <AdminCaseTd>
                      <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium uppercase">
                        {trackingStatusLabel(order)}
                      </span>
                      {order.coupon_code?.trim() ? (
                        <p className="mt-1 text-[11px] font-semibold uppercase tracking-wide text-primary">
                          Coupon {order.coupon_code.trim()}
                        </p>
                      ) : null}
                    </AdminCaseTd>
                    <AdminCaseTd>
                      <p className="text-xs font-medium uppercase">
                        {formatAdminPaymentStatusLabel(order.payment_status)}
                      </p>
                      <p className="text-[11px] uppercase text-muted-foreground">{order.payment_method}</p>
                    </AdminCaseTd>
                    <AdminCaseTd className="whitespace-nowrap text-xs text-muted-foreground">
                      {order.placed_at
                        ? new Date(order.placed_at).toLocaleString("en-IN")
                        : "Date unavailable"}
                    </AdminCaseTd>
                    <AdminCaseTd className="min-w-[12rem]">
                      <div className="flex flex-wrap gap-2">
                        {isPendingCod && canFulfill ? (
                          <button
                            type="button"
                            disabled={busy !== null}
                            onClick={() => void collect(order.order_number)}
                            className="rounded-full bg-primary px-3 py-1.5 text-xs text-primary-foreground disabled:opacity-60"
                          >
                            {busy === `collect:${order.order_number}` ? "Updating…" : "Mark COD collected"}
                          </button>
                        ) : null}
                        {isInvoiceAvailable(order) ? (
                          <a
                            href={invoiceHref(order.order_number)}
                            className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium"
                          >
                            <FileText className="h-3.5 w-3.5" /> Invoice
                          </a>
                        ) : null}
                        {canFulfill && order.can_complete_package ? (
                          <button
                            type="button"
                            disabled={busy !== null}
                            onClick={() => setPackageOrder(order)}
                            className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium disabled:opacity-60"
                          >
                            <Package className="h-3.5 w-3.5" />
                            {order.packed_at ? "Update package" : "Create package"}
                          </button>
                        ) : null}
                        {canFulfill &&
                        (order.can_book_expressbees_shipment ||
                          order.can_book_shipment ||
                          order.can_rebook_carrier_cancelled_shipment) &&
                        isPackageReady(order) ? (
                          <button
                            type="button"
                            disabled={busy !== null}
                            onClick={() => openBookShipment(order, false)}
                            className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium disabled:opacity-60"
                          >
                            <Truck className="h-3.5 w-3.5" />{" "}
                            {isExpressCarrierCancelled(order) ? "Rebook shipment" : "Book shipment"}
                          </button>
                        ) : null}
                        {SHOW_M2_POST_ORDER_UI &&
                        canFulfill &&
                        (order.can_book_expressbees_shipment_with_override ||
                          order.can_book_shipment_with_override) &&
                        isPackageReady(order) ? (
                          <button
                            type="button"
                            disabled={busy !== null}
                            onClick={() => openBookShipment(order, true)}
                            className="inline-flex items-center gap-1.5 rounded-full border border-amber-300 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-900 disabled:opacity-60"
                          >
                            <Truck className="h-3.5 w-3.5" /> Override hold & book
                          </button>
                        ) : null}
                        {order.shipment_label_url ? (
                          <>
                            <a
                              href={order.shipment_label_url}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium"
                            >
                              <Printer className="h-3.5 w-3.5" /> Label
                            </a>
                            <button
                              type="button"
                              onClick={() => {
                                const labelUrl = order.shipment_label_url;
                                if (labelUrl) void openPrintableShippingLabel(labelUrl);
                              }}
                              className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium"
                            >
                              <Printer className="h-3.5 w-3.5" /> Print
                            </button>
                          </>
                        ) : null}
                        {canFulfill &&
                        order.tracking_number &&
                        order.status !== "fulfilled" &&
                        !isExpressCarrierCancelled(order) ? (
                          <button
                            type="button"
                            disabled={busy !== null}
                            onClick={() => {
                              setCancelShipmentTarget(order);
                              setCancelShipmentMode(
                                order.shipment_logistics_mode === "xpressbees" ? "xpressbees" : "manual",
                              );
                              setCancelShipmentReason("");
                            }}
                            className="inline-flex items-center gap-1.5 rounded-full border border-destructive/40 px-3 py-1.5 text-xs font-medium text-destructive"
                          >
                            <X className="h-3.5 w-3.5" /> Cancel shipment
                          </button>
                        ) : null}
                        {SHOW_M2_POST_ORDER_UI &&
                        canFulfill &&
                        (order.can_admin_cancel ?? order.can_cancel) &&
                        order.status !== "cancelled" ? (
                          <button
                            type="button"
                            disabled={busy !== null}
                            onClick={() => {
                              setCancelOrderTarget(order);
                              setCancelOrderReason("");
                              setCancelOrderShipmentMode("xpressbees");
                            }}
                            className="inline-flex items-center gap-1.5 rounded-full border border-destructive/40 px-3 py-1.5 text-xs font-medium text-destructive"
                          >
                            <Ban className="h-3.5 w-3.5" /> Cancel order
                          </button>
                        ) : null}
                        {canSyncZoho ? (
                          <button
                            type="button"
                            disabled={busy !== null || !canSync}
                            onClick={() => void sync(order.order_number)}
                            className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium disabled:opacity-60"
                          >
                            {busy === `zoho:${order.order_number}` ? (
                              <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            ) : (
                              <CloudUpload className="h-3.5 w-3.5" />
                            )}
                            Zoho
                          </button>
                        ) : null}
                      </div>
                    </AdminCaseTd>
                  </tr>
                  {expanded ? (
                    <tr className="border-t border-border/40 bg-muted/15">
                      <AdminCaseTd colSpan={8} className="px-3 pb-4 pt-0">
                        <div className="space-y-3 pt-3">
                          {order.order_items && order.order_items.length > 0 ? (
                            <div className="rounded-xl border border-border/70 bg-background/60 p-3">
                              <p className="mb-2 text-sm font-semibold">
                                Products &amp; Variants (
                                {order.order_items.reduce((acc, it) => acc + (it.qty || 1), 0)}{" "}
                                {order.order_items.reduce((acc, it) => acc + (it.qty || 1), 0) === 1
                                  ? "item"
                                  : "items"}
                                )
                              </p>
                              <div className="space-y-2 divide-y divide-border/40">
                                {order.order_items.map((item, idx) => (
                                  <div
                                    key={item.id ?? idx}
                                    className="flex items-center justify-between gap-3 pt-2 text-xs first:pt-0"
                                  >
                                    <div className="flex min-w-0 items-center gap-2.5">
                                      {item.product_image ? (
                                        <img
                                          src={item.product_image}
                                          alt={item.product_name ?? "Product"}
                                          className="h-9 w-9 shrink-0 rounded-lg border border-border/50 object-cover"
                                        />
                                      ) : null}
                                      <div className="min-w-0">
                                        <p className="truncate font-medium text-foreground">
                                          {item.product_name || "Product"}
                                        </p>
                                        <div className="mt-0.5 flex flex-wrap gap-1.5 text-[11px] text-muted-foreground">
                                          {item.sku ? <span className="font-mono">SKU: {item.sku}</span> : null}
                                          {item.size ? <span>· Size: {item.size}</span> : null}
                                          {item.color ? <span>· Color: {item.color}</span> : null}
                                          <span>· Qty: {item.qty || 1}</span>
                                        </div>
                                      </div>
                                    </div>
                                    {item.price != null ? (
                                      <span className="shrink-0 font-medium text-foreground">
                                        ₹{(Number(item.price) * (item.qty || 1)).toLocaleString("en-IN")}
                                      </span>
                                    ) : null}
                                  </div>
                                ))}
                              </div>
                            </div>
                          ) : null}

                          <div className="rounded-xl border border-border/70 bg-background/60 p-3">
                            <div className="flex items-center justify-between gap-2">
                              <p className="text-sm font-semibold">Shipment</p>
                              {order.tracking_number ? (
                                <span
                                  className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${
                                    isExpressCarrierCancelled(order)
                                      ? "bg-destructive/15 text-destructive"
                                      : "bg-emerald-100 text-emerald-800"
                                  }`}
                                >
                                  {isExpressCarrierCancelled(order)
                                    ? "ExpressBees cancelled"
                                    : order.tracking_status === "delivered"
                                      ? "Delivered"
                                      : order.tracking_status === "out_for_delivery"
                                        ? "Out for delivery"
                                        : order.tracking_status === "delivery_failed"
                                          ? "Delivery attempt unsuccessful"
                                          : order.tracking_status === "shipped"
                                            ? "In transit"
                                            : "Pickup pending"}
                                </span>
                              ) : (
                                <span className="rounded-full bg-muted px-2.5 py-1 text-[11px] font-medium text-muted-foreground">
                                  Not booked
                                </span>
                              )}
                            </div>
                            {order.tracking_number ? (
                              <div className="mt-2 space-y-1 text-xs text-muted-foreground">
                                <p>
                                  {order.tracking_carrier || "Carrier"} · AWB {order.tracking_number}
                                </p>
                                {order.tracking_url ? (
                                  <a
                                    href={order.tracking_url}
                                    target="_blank"
                                    rel="noreferrer"
                                    className="underline"
                                  >
                                    Open tracking
                                  </a>
                                ) : null}
                              </div>
                            ) : (
                              <p className="mt-2 text-xs text-muted-foreground">
                                {canFulfill
                                  ? isPackageReady(order)
                                    ? "Package created. Book a shipment to generate the AWB and label."
                                    : "Create a package first. Booking a shipment is blocked until the package exists."
                                  : "Shipment not booked yet."}
                              </p>
                            )}
                            {order.packed_at && !order.tracking_number ? (
                              <p className="mt-2 text-xs text-muted-foreground">
                                Packed {order.package_weight_grams || order.default_package_weight_grams} g
                                {order.package_length_cm && order.package_breadth_cm && order.package_height_cm
                                  ? ` · ${order.package_length_cm}×${order.package_breadth_cm}×${order.package_height_cm} cm`
                                  : ""}
                                {order.package_courier_name ? ` · ${order.package_courier_name}` : ""}
                              </p>
                            ) : null}
                            {isExpressCarrierCancelled(order) ? (
                              <div className="mt-2 rounded-xl border border-destructive/25 bg-destructive/5 p-3 text-xs">
                                <p className="font-semibold text-destructive">ExpressBees cancelled this shipment</p>
                                <p className="mt-1 text-muted-foreground">
                                  The AWB is void. Rebook a new shipment or cancel the customer order. The order stays open until you act.
                                </p>
                              </div>
                            ) : null}
                            {SHOW_M2_POST_ORDER_UI &&
                            order.pending_cancellation &&
                            !order.tracking_number &&
                            mode === "admin" ? (
                              <div className="mt-2 rounded-xl border border-amber-200 bg-amber-50/80 p-3 text-xs text-amber-900">
                                <p className="font-semibold">Cancellation requested — shipment on hold</p>
                                <p className="mt-1">
                                  A customer cancellation is pending review. Reject it on{" "}
                                  <Link to="/admin/cancellations" className="underline">
                                    Cancellations
                                  </Link>{" "}
                                  or book with an audited override if fulfilment must continue.
                                </p>
                              </div>
                            ) : null}
                            {canFulfill &&
                            order.can_complete_package &&
                            !isPackageReady(order) &&
                            !order.tracking_number &&
                            !(order.can_book_expressbees_shipment || order.can_book_shipment) ? (
                              <p className="mt-2 text-xs text-muted-foreground">
                                Create a package before booking a shipment.
                              </p>
                            ) : null}
                          </div>

                          {canSyncZoho ? (
                            <div className="rounded-xl border border-border/70 bg-background/60 p-3">
                              <div className="flex items-center justify-between gap-2">
                                <p className="text-sm font-semibold">Zoho</p>
                                <SyncBadge status={order.zoho_sync_status} eligible={order.zoho_sync_eligible} />
                              </div>
                              {order.zoho_sync_error ? (
                                <p className="mt-2 text-xs text-destructive" title={order.zoho_sync_error}>
                                  {order.zoho_sync_error}
                                </p>
                              ) : null}
                              {order.zoho_last_synced_at ? (
                                <p className="mt-2 text-[11px] text-muted-foreground">
                                  Last synced {new Date(order.zoho_last_synced_at).toLocaleString("en-IN")}
                                </p>
                              ) : null}
                              <p className="mt-2 text-xs text-muted-foreground">
                                Use the Zoho action in the row above to sync or void.
                              </p>
                            </div>
                          ) : null}
                        </div>
                      </AdminCaseTd>
                    </tr>
                  ) : null}
                </Fragment>
              );
            })}
          </tbody>
        </AdminCaseListTable>
      )}
      {!loading && totalItems > 0 && (
        <ListPagination
          page={page}
          totalPages={totalPages}
          totalItems={totalItems}
          pageSize={LIST_PAGE_SIZE}
          scrollToId="admin-orders-list"
          onPageChange={setPage}
        />
      )}
      <CreatePackageDialog
        order={packageOrder}
        open={!!packageOrder}
        onOpenChange={(open) => {
          if (!open) setPackageOrder(null);
        }}
        onSaved={load}
      />
      <BookShipmentDialog
        order={shipmentOrder}
        open={!!shipmentOrder}
        requiresOverride={bookRequiresOverride}
        onOpenChange={(open) => {
          if (!open) {
            setShipmentOrder(null);
            setBookRequiresOverride(false);
          }
        }}
        onSaved={load}
      />

      <AdminReasonDialog
        open={!!cancelShipmentTarget}
        onOpenChange={(open) => !open && setCancelShipmentTarget(null)}
        title={`Cancel shipment · ${cancelShipmentTarget?.order_number ?? ""}`}
        description="Use ExpressBees to cancel the AWB, or choose manual after confirming cancellation directly with the carrier."
        reason={cancelShipmentReason}
        onReasonChange={setCancelShipmentReason}
        submitLabel="Cancel shipment"
        onSubmit={() => void submitCancelShipment()}
        submitDisabled={busy !== null}
        showShipmentMode
        shipmentMode={cancelShipmentMode}
        onShipmentModeChange={setCancelShipmentMode}
      />

      {SHOW_M2_POST_ORDER_UI ? (
      <AdminReasonDialog
        open={!!cancelOrderTarget}
        onOpenChange={(open) => !open && setCancelOrderTarget(null)}
        title={`Cancel order · ${cancelOrderTarget?.order_number ?? ""}`}
        description={
          cancelOrderAwbAlreadyVoided
            ? "ExpressBees already voided this AWB. Cancelling restores stock and initiates a Razorpay refund for online payments. No further carrier cancel API call is made."
            : "Cancels this order before dispatch. Stock is restored, and a Razorpay refund is initiated automatically for online payments. If a label was already booked, we void the AWB with the carrier first, then cancel the order and initiate any refund."
        }
        reason={cancelOrderReason}
        onReasonChange={setCancelOrderReason}
        reasonPlaceholder="Why is this order being cancelled?"
        submitLabel="Cancel order"
        onSubmit={() => void submitCancelOrder()}
        submitDisabled={busy !== null}
        showShipmentMode={cancelOrderNeedsLiveAwbVoid}
        shipmentMode={cancelOrderShipmentMode}
        onShipmentModeChange={setCancelOrderShipmentMode}
      />
      ) : null}
    </AdminPage>
  );
}
