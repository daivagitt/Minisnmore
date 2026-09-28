import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import {
  acceptOrderReturnAfterInspection,
  allocateReplacementForOrderReturn,
  approveOrderReturn,
  bookOrderReturnPickup,
  bookOrderReturnOutbound,
  completeOrderReturnExchange,
  closeFailedOrderReturnPickup,
  convertOrderReturnToRefund,
  listAdminOrderReturns,
  markOrderReturnDelivered,
  markOrderReturnInspected,
  markOrderReturnPickupFailed,
  markOrderReturnPickedUp,
  markOrderReturnPickupRequested,
  markOrderReturnReadyToShip,
  markOrderReturnReceived,
  markOrderReturnRepacking,
  markOrderReturnShipped,
  recordOrderReturnCodRefund,
  refundOrderReturn,
  rejectOrderReturn,
  rescheduleOrderReturnPickup,
  settleOrderReturnPriceDifference,
  type AdminOrderReturnRow,
} from "@/lib/orders.functions";
import { showApiError } from "@/lib/ui-errors";
import { ListPagination } from "@/components/ListPagination";
import { useListPagination } from "@/hooks/useListPagination";
import { LIST_PAGE_SIZE } from "@/lib/pagination";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export const Route = createFileRoute("/admin/returns")({ component: AdminReturnsPage });
const RETURNS_UI_ENABLED = true;

function formatCaseAge(row: { case_age_hours?: number; sla_due_at?: string | null; sla_overdue?: boolean }) {
  const age = row.case_age_hours ?? 0;
  const ageLabel = age < 24 ? `${age}h old` : `${Math.floor(age / 24)}d old`;
  if (!row.sla_due_at) {
    return ageLabel;
  }
  const due = new Date(row.sla_due_at).toLocaleString("en-IN", { dateStyle: "short", timeStyle: "short" });
  return row.sla_overdue ? `${ageLabel} · overdue (due ${due})` : `${ageLabel} · due ${due}`;
}

type StageId = "all" | "request" | "pickup" | "inspection" | "exchange" | "closed";

const STAGE_FILTERS: { id: StageId; label: string }[] = [
  { id: "all", label: "All" },
  { id: "request", label: "Request" },
  { id: "pickup", label: "Pickup" },
  { id: "inspection", label: "Inspection" },
  { id: "exchange", label: "Exchange" },
  { id: "closed", label: "Closed" },
];

const STATUS_FILTER_GROUPS: {
  id: Exclude<StageId, "all">;
  label: string;
  options: { value: string; label: string }[];
}[] = [
  {
    id: "request",
    label: "Request",
    options: [
      { value: "requested", label: "Requested" },
      { value: "approved", label: "Approved" },
    ],
  },
  {
    id: "pickup",
    label: "Pickup",
    options: [
      { value: "pickup_requested", label: "Pickup requested" },
      { value: "pickup_scheduled", label: "Pickup scheduled" },
      { value: "pickup_failed", label: "Pickup failed" },
      { value: "picked_up", label: "Picked up" },
    ],
  },
  {
    id: "inspection",
    label: "Inspection",
    options: [
      { value: "received", label: "Received" },
      { value: "inspected", label: "Inspected" },
      { value: "accepted", label: "Accepted" },
    ],
  },
  {
    id: "exchange",
    label: "Exchange",
    options: [
      { value: "item_allocated", label: "Item allocated" },
      { value: "repacking", label: "Repacking" },
      { value: "ready_to_ship", label: "Ready to ship" },
      { value: "shipped", label: "Shipped" },
      { value: "delivered", label: "Delivered" },
    ],
  },
  {
    id: "closed",
    label: "Closed",
    options: [
      { value: "refunded", label: "Refunded" },
      { value: "completed", label: "Completed" },
      { value: "rejected", label: "Rejected" },
      { value: "failed", label: "Failed" },
    ],
  },
];

const STAGE_STATUS_SET: Record<Exclude<StageId, "all">, Set<string>> = Object.fromEntries(
  STATUS_FILTER_GROUPS.map((group) => [group.id, new Set(group.options.map((o) => o.value))]),
) as Record<Exclude<StageId, "all">, Set<string>>;

function statusesForStage(stage: StageId): { value: string; label: string }[] {
  if (stage === "all") {
    return STATUS_FILTER_GROUPS.flatMap((group) => group.options);
  }
  return STATUS_FILTER_GROUPS.find((group) => group.id === stage)?.options ?? [];
}

function stageFilterPillClass(selected: boolean): string {
  return [
    "inline-flex items-center rounded-full border px-3 py-1.5 text-xs font-medium transition-colors",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:ring-offset-1",
    selected
      ? "border-primary bg-primary text-primary-foreground shadow-sm"
      : "border-border/80 bg-background/70 text-foreground/80 hover:border-border hover:bg-muted/70 hover:text-foreground",
  ].join(" ");
}

/** One primary next-step action (plus an optional secondary one) per current status. */
function nextActions(
  r: AdminOrderReturnRow,
  run: (label: string, action: () => Promise<unknown>) => void,
  openReject: () => void,
  openBookPickup: () => void,
  openBookOutbound: () => void,
  openAccept: () => void,
  openPickupFailed: () => void,
  openSettlement: () => void,
  openCodRefund: () => void,
): { primary?: { label: string; onClick: () => void }; secondary?: { label: string; onClick: () => void } } {
  const isExchange = r.request_type === "exchange";
  switch (r.status) {
    case "requested":
      return {
        primary: { label: "Approve", onClick: () => run("Approved", () => approveOrderReturn(r.return_number)) },
        secondary: { label: "Reject", onClick: openReject },
      };
    case "approved":
      return {
        primary: { label: "Book pickup", onClick: openBookPickup },
        secondary: {
          label: "Mark received",
          onClick: () => run("Marked received", () => markOrderReturnReceived(r.return_number)),
        },
      };
    case "pickup_scheduled":
      return {
        primary: {
          label: "Mark picked up",
          onClick: () => run("Marked picked up", () => markOrderReturnPickedUp(r.return_number)),
        },
        secondary: { label: "Pickup failed", onClick: openPickupFailed },
      };
    case "pickup_failed":
      return {
        primary: {
          label: "Reschedule pickup",
          onClick: () => run("Rescheduled", () => rescheduleOrderReturnPickup(r.return_number)),
        },
        secondary: {
          label: "Close case",
          onClick: () => run("Closed", () => closeFailedOrderReturnPickup(r.return_number)),
        },
      };
    case "pickup_requested":
      return {
        primary: {
          label: "Mark picked up",
          onClick: () => run("Marked picked up", () => markOrderReturnPickedUp(r.return_number)),
        },
        secondary: {
          label: "Mark received",
          onClick: () => run("Marked received", () => markOrderReturnReceived(r.return_number)),
        },
      };
    case "picked_up":
      return {
        primary: {
          label: "Mark received",
          onClick: () => run("Marked received", () => markOrderReturnReceived(r.return_number)),
        },
      };
    case "received":
      return {
        primary: {
          label: "Mark inspected",
          onClick: () => run("Marked inspected", () => markOrderReturnInspected(r.return_number)),
        },
      };
    case "inspected":
      return {
        primary: { label: "Accept", onClick: openAccept },
        secondary: { label: "Reject", onClick: openReject },
      };
    case "accepted":
      return isExchange
        ? r.settlement_status === "pending"
          ? {
              primary: { label: "Settle price difference", onClick: openSettlement },
              secondary: {
                label: "Convert to refund",
                onClick: () => run("Converted to refund", () => convertOrderReturnToRefund(r.return_number)),
              },
            }
          : {
            primary: {
              label: "Allocate replacement",
              onClick: () => run("Replacement allocated", () => allocateReplacementForOrderReturn(r.return_number)),
            },
            secondary: {
              label: "Convert to refund",
              onClick: () => run("Converted to refund", () => convertOrderReturnToRefund(r.return_number)),
            },
          }
        : r.cod_refund_pending
          ? {
              primary: { label: "Record COD refund", onClick: openCodRefund },
            }
          : {
              primary: {
                label: "Issue refund",
                onClick: () => run("Refund processed", () => refundOrderReturn(r.return_number)),
              },
            };
    case "item_allocated":
      return {
        primary: {
          label: "Mark repacking",
          onClick: () => run("Marked repacking", () => markOrderReturnRepacking(r.return_number)),
        },
      };
    case "repacking":
      return {
        primary: {
          label: "Mark ready to ship",
          onClick: () => run("Marked ready to ship", () => markOrderReturnReadyToShip(r.return_number)),
        },
      };
    case "ready_to_ship":
      return isExchange
        ? {
            primary: { label: "Book outbound", onClick: openBookOutbound },
            secondary: {
              label: "Mark shipped (manual)",
              onClick: () => run("Marked shipped", () => markOrderReturnShipped(r.return_number)),
            },
          }
        : {
            primary: {
              label: "Mark shipped",
              onClick: () => run("Marked shipped", () => markOrderReturnShipped(r.return_number)),
            },
          };
    case "shipped":
      return {
        primary: {
          label: "Mark delivered",
          onClick: () => run("Marked delivered", () => markOrderReturnDelivered(r.return_number)),
        },
      };
    case "delivered":
      return {
        primary: {
          label: "Complete",
          onClick: () => run("Completed", () => completeOrderReturnExchange(r.return_number)),
        },
      };
    case "failed":
      return isExchange
        ? {
            primary: {
              label: "Retry convert to refund",
              onClick: () => run("Converted to refund", () => convertOrderReturnToRefund(r.return_number)),
            },
          }
        : r.cod_refund_pending
          ? {
              primary: { label: "Record COD refund", onClick: openCodRefund },
            }
          : {
              primary: {
                label: "Retry refund",
                onClick: () => run("Refund processed", () => refundOrderReturn(r.return_number)),
              },
            };
    default:
      return {};
  }
}

function AdminReturnsPage() {
  if (!RETURNS_UI_ENABLED) return null;

  const [stage, setStage] = useState<StageId>("request");
  const [status, setStatus] = useState<string>("requested");
  const [rows, setRows] = useState<AdminOrderReturnRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [rejectTarget, setRejectTarget] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [bookPickupTarget, setBookPickupTarget] = useState<string | null>(null);
  const [pickupMode, setPickupMode] = useState<"manual" | "self_ship" | "xpressbees">("xpressbees");
  const [pickupCarrier, setPickupCarrier] = useState("");
  const [pickupAwb, setPickupAwb] = useState("");
  const [pickupTrackingUrl, setPickupTrackingUrl] = useState("");
  const [pickupWindow, setPickupWindow] = useState("");
  const [acceptTarget, setAcceptTarget] = useState<string | null>(null);
  const [acceptDisposition, setAcceptDisposition] = useState("restockable");
  const [acceptQuantities, setAcceptQuantities] = useState<Record<number, number>>({});
  const [acceptDispositions, setAcceptDispositions] = useState<Record<number, string>>({});
  const [pickupFailedTarget, setPickupFailedTarget] = useState<string | null>(null);
  const [pickupFailedReason, setPickupFailedReason] = useState("");
  const [bookOutboundTarget, setBookOutboundTarget] = useState<string | null>(null);
  const [outboundMode, setOutboundMode] = useState<"manual" | "xpressbees">("xpressbees");
  const [outboundCarrier, setOutboundCarrier] = useState("");
  const [outboundAwb, setOutboundAwb] = useState("");
  const [outboundTrackingUrl, setOutboundTrackingUrl] = useState("");
  const [outboundLabelUrl, setOutboundLabelUrl] = useState("");
  const [settlementTarget, setSettlementTarget] = useState<AdminOrderReturnRow | null>(null);
  const [settlementMethod, setSettlementMethod] = useState("");
  const [settlementReference, setSettlementReference] = useState("");
  const [settlementWaived, setSettlementWaived] = useState(false);
  const [codRefundTarget, setCodRefundTarget] = useState<string | null>(null);
  const [codRefundMethod, setCodRefundMethod] = useState<"upi" | "bank_transfer" | "cash" | "other">("upi");
  const [codRefundReference, setCodRefundReference] = useState("");

  const stageStatusOptions = useMemo(() => statusesForStage(stage), [stage]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      // Exact status → API filter. Stage-only / All → fetch all, filter client-side.
      const res = await listAdminOrderReturns(status || undefined);
      setRows(res.returns ?? []);
    } catch (error) {
      showApiError(error, "Could not load returns");
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    void load();
  }, [load]);

  const filteredRows = useMemo(() => {
    if (status) return rows;
    if (stage === "all") return rows;
    const allowed = STAGE_STATUS_SET[stage];
    return rows.filter((row) => allowed.has(row.status));
  }, [rows, stage, status]);

  const paging = useListPagination(filteredRows, {
    pageSize: LIST_PAGE_SIZE,
    resetKey: `${stage}|${status}`,
  });

  const selectStage = (next: StageId) => {
    setStage(next);
    setStatus("");
  };

  const run = async (returnNumber: string, action: () => Promise<unknown>, label: string) => {
    setBusy(returnNumber);
    try {
      await action();
      toast.success(label);
      await load();
    } catch (error) {
      showApiError(error, label + " failed");
    } finally {
      setBusy(null);
    }
  };

  const submitReject = async () => {
    if (!rejectTarget || rejectReason.trim().length < 5) {
      toast.error("Rejection reason must be at least 5 characters");
      return;
    }
    await run(rejectTarget, () => rejectOrderReturn(rejectTarget, rejectReason.trim()), "Rejected");
    setRejectTarget(null);
    setRejectReason("");
  };

  const submitBookPickup = async () => {
    if (!bookPickupTarget) return;
    if (pickupMode !== "xpressbees" && pickupAwb.trim().length < 3) {
      toast.error("Reverse AWB or tracking reference is required");
      return;
    }
    await run(
      bookPickupTarget,
      () =>
        bookOrderReturnPickup(bookPickupTarget, {
          mode: pickupMode,
          carrier_name: pickupCarrier.trim() || undefined,
          reverse_awb: pickupAwb.trim() || undefined,
          tracking_url: pickupTrackingUrl.trim() || undefined,
          pickup_window: pickupWindow.trim() || undefined,
        }),
      pickupMode === "xpressbees" ? "ExpressBees pickup booked" : "Pickup booked",
    );
    setBookPickupTarget(null);
    setPickupCarrier("");
    setPickupAwb("");
    setPickupTrackingUrl("");
    setPickupWindow("");
  };

  const submitAccept = async () => {
    if (!acceptTarget) return;
    const target = rows.find((row) => row.return_number === acceptTarget);
    const items = (target?.items ?? []).map((line) => ({
      return_item_id: line.id,
      accepted_quantity: acceptQuantities[line.id] ?? line.requested_qty,
      disposition: acceptDispositions[line.id] ?? acceptDisposition,
    }));
    await run(
      acceptTarget,
      () => acceptOrderReturnAfterInspection(acceptTarget, acceptDisposition, undefined, items),
      "Accepted",
    );
    setAcceptTarget(null);
    setAcceptDisposition("restockable");
  };

  const submitSettlement = async () => {
    if (!settlementTarget) return;
    const difference = Number(settlementTarget.price_difference ?? 0);
    const status = settlementWaived ? "waived" : difference > 0 ? "collected" : "refunded";
    if (!settlementWaived && (!settlementMethod.trim() || !settlementReference.trim())) {
      toast.error("Settlement method and transaction reference are required");
      return;
    }
    await run(
      settlementTarget.return_number,
      () =>
        settleOrderReturnPriceDifference(settlementTarget.return_number, {
          status,
          amount: settlementWaived ? 0 : Math.abs(difference),
          method: settlementMethod.trim() || undefined,
          reference: settlementReference.trim() || undefined,
        }),
      "Price difference settled",
    );
    setSettlementTarget(null);
  };

  const submitPickupFailed = async () => {
    if (!pickupFailedTarget || pickupFailedReason.trim().length < 5) {
      toast.error("Failure reason must be at least 5 characters");
      return;
    }
    await run(
      pickupFailedTarget,
      () => markOrderReturnPickupFailed(pickupFailedTarget, pickupFailedReason.trim()),
      "Pickup marked failed",
    );
    setPickupFailedTarget(null);
    setPickupFailedReason("");
  };

  const submitBookOutbound = async () => {
    if (!bookOutboundTarget) return;
    if (outboundMode !== "xpressbees" && outboundAwb.trim().length < 3) {
      toast.error("Outbound AWB or tracking reference is required");
      return;
    }
    await run(
      bookOutboundTarget,
      () =>
        bookOrderReturnOutbound(bookOutboundTarget, {
          mode: outboundMode,
          carrier_name: outboundCarrier.trim() || undefined,
          outbound_awb: outboundAwb.trim() || undefined,
          tracking_url: outboundTrackingUrl.trim() || undefined,
          label_url: outboundLabelUrl.trim() || undefined,
        }),
      outboundMode === "xpressbees" ? "ExpressBees outbound booked" : "Outbound shipment booked",
    );
    setBookOutboundTarget(null);
    setOutboundCarrier("");
    setOutboundAwb("");
    setOutboundTrackingUrl("");
    setOutboundLabelUrl("");
  };

  const acceptingRow = rows.find((row) => row.return_number === acceptTarget);

  const submitCodRefund = async () => {
    if (!codRefundTarget || codRefundReference.trim().length < 4) {
      toast.error("Refund reference must be at least 4 characters");
      return;
    }
    await run(
      codRefundTarget,
      () =>
        recordOrderReturnCodRefund(codRefundTarget, {
          refund_method: codRefundMethod,
          refund_reference: codRefundReference.trim(),
        }),
      "COD refund recorded",
    );
    setCodRefundTarget(null);
    setCodRefundReference("");
  };

  return (
    <AdminPage
      eyebrow="Fulfilment"
      title="Order returns & exchanges"
      description="Review customer return/exchange requests, walk them through pickup, inspection, and refund or replacement."
    >
      <section
        className="glass-strong mb-5 space-y-4 rounded-3xl p-4 sm:p-5"
        aria-label="Filter returns"
      >
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground">
            Stage
          </p>
          <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter by stage">
            {STAGE_FILTERS.map((option) => {
              const selected = stage === option.id;
              return (
                <button
                  key={option.id}
                  type="button"
                  aria-pressed={selected}
                  onClick={() => selectStage(option.id)}
                  className={stageFilterPillClass(selected)}
                >
                  {option.label}
                </button>
              );
            })}
          </div>
        </div>

        <div className="flex flex-wrap items-end gap-3">
          <label className="block min-w-[14rem] flex-1 sm:max-w-xs">
            <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground">
              Status
            </span>
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value)}
              aria-label="Filter by return status"
              className="h-10 w-full rounded-xl border border-border bg-background px-3 text-sm outline-none transition focus:ring-2 focus:ring-primary/30"
            >
              <option value="">
                {stage === "all" ? "All statuses" : `All ${STAGE_FILTERS.find((s) => s.id === stage)?.label.toLowerCase()} statuses`}
              </option>
              {stage === "all"
                ? STATUS_FILTER_GROUPS.map((group) => (
                    <optgroup key={group.id} label={group.label}>
                      {group.options.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </optgroup>
                  ))
                : stageStatusOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
            </select>
          </label>
          {(stage !== "all" || status !== "") && (
            <button
              type="button"
              onClick={() => {
                setStage("all");
                setStatus("");
              }}
              className="inline-flex h-10 items-center rounded-xl border border-border bg-background px-3 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              Clear
            </button>
          )}
          {!loading && (
            <p className="ml-auto text-xs text-muted-foreground" aria-live="polite">
              {paging.totalItems === 0
                ? "0 returns"
                : paging.totalPages > 1
                  ? `Showing ${paging.rangeStart}–${paging.rangeEnd} of ${paging.totalItems}`
                  : `${paging.totalItems} return${paging.totalItems === 1 ? "" : "s"}`}
            </p>
          )}
        </div>
      </section>

      {loading ? (
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      ) : filteredRows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No returns in this filter.</p>
      ) : (
        <>
        <ul id="admin-returns-list" className="space-y-4">
          {paging.pageItems.map((r) => {
            const { primary, secondary } = nextActions(
              r,
              (label, action) => void run(r.return_number, action, label),
              () => {
                setRejectTarget(r.return_number);
                setRejectReason("");
              },
              () => {
                setBookPickupTarget(r.return_number);
                setPickupMode("xpressbees");
                setPickupCarrier("");
                setPickupAwb("");
                setPickupTrackingUrl("");
                setPickupWindow("");
              },
              () => {
                setBookOutboundTarget(r.return_number);
                setOutboundMode("xpressbees");
                setOutboundCarrier("");
                setOutboundAwb("");
                setOutboundTrackingUrl("");
                setOutboundLabelUrl("");
              },
              () => {
                setAcceptTarget(r.return_number);
                setAcceptDisposition("restockable");
                setAcceptQuantities(
                  Object.fromEntries((r.items ?? []).map((line) => [line.id, line.requested_qty])),
                );
                setAcceptDispositions(
                  Object.fromEntries((r.items ?? []).map((line) => [line.id, "restockable"])),
                );
              },
              () => {
                setPickupFailedTarget(r.return_number);
                setPickupFailedReason("");
              },
              () => {
                setSettlementTarget(r);
                setSettlementMethod("");
                setSettlementReference("");
                setSettlementWaived(false);
              },
              () => {
                setCodRefundTarget(r.return_number);
                setCodRefundMethod("upi");
                setCodRefundReference("");
              },
            );
            return (
              <li key={r.return_number} className="glass-strong rounded-2xl p-4 space-y-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="font-medium">
                      {r.request_type === "exchange" ? "Exchange" : "Return"} · {r.return_number}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      Order {r.order_number} · {r.customer_login} · ₹
                      {Number(r.order_total ?? 0).toLocaleString("en-IN")}
                    </p>
                    {r.product_name ? (
                      <p className="text-xs text-muted-foreground">
                        {r.product_name}
                        {r.original_size || r.original_color
                          ? ` (${[r.original_size, r.original_color].filter(Boolean).join(" · ")})`
                          : ""}
                        {r.replacement_size || r.replacement_color
                          ? ` → ${[r.replacement_size, r.replacement_color].filter(Boolean).join(" · ")}`
                          : ""}
                      </p>
                    ) : null}
                    {(r.items ?? []).length > 0 ? (
                      <div className="mt-2 space-y-1 rounded-xl border border-border/70 p-2">
                        {r.items!.map((line) => (
                          <p key={line.id} className="text-xs text-muted-foreground">
                            {line.product_name} · {line.requested_qty} requested
                            {line.accepted_qty != null ? ` · ${line.accepted_qty} accepted` : ""}
                            {" · "}refund ₹{Number(line.refund_amount).toLocaleString("en-IN")}
                            {line.disposition ? ` · ${line.disposition.replace(/_/g, " ")}` : ""}
                          </p>
                        ))}
                      </div>
                    ) : null}
                    {r.request_type === "exchange" && r.price_difference != null ? (
                      <p className="text-xs text-muted-foreground mt-1">
                        Price difference: ₹{Math.abs(Number(r.price_difference)).toLocaleString("en-IN")}{" "}
                        {Number(r.price_difference) > 0 ? "to collect" : Number(r.price_difference) < 0 ? "to refund" : ""}
                        {" · "}
                        {r.settlement_status?.replace(/_/g, " ")}
                      </p>
                    ) : null}
                    <p className="text-xs mt-1">
                      Status: <span className="uppercase tracking-wide">{r.status.replace(/_/g, " ")}</span>
                      {r.cod_refund_pending ? (
                        <span className="ml-2 text-amber-700">· COD refund pending</span>
                      ) : null}
                    </p>
                  </div>
                  <div className="text-xs text-right">
                    <p className={r.sla_overdue ? "text-amber-700 font-medium" : "text-muted-foreground"}>
                      {formatCaseAge(r)}
                    </p>
                    <p className="text-muted-foreground">
                      {new Date(r.requested_at).toLocaleString("en-IN")}
                    </p>
                  </div>
                </div>
                <p className="text-sm">{r.reason}</p>
                {r.admin_notes ? <p className="text-xs text-muted-foreground whitespace-pre-wrap">{r.admin_notes}</p> : null}
                {r.rejection_reason ? <p className="text-xs text-amber-700">{r.rejection_reason}</p> : null}
                {r.reverse_awb ? (
                  <p className="text-xs text-muted-foreground">
                    Pickup AWB: {r.reverse_awb}
                    {r.pickup_window ? ` · ${r.pickup_window}` : ""}
                  </p>
                ) : null}
                {r.outbound_awb ? (
                  <p className="text-xs text-muted-foreground">
                    Outbound AWB: {r.outbound_awb}
                    {r.outbound_tracking_url ? (
                      <>
                        {" · "}
                        <a href={r.outbound_tracking_url} target="_blank" rel="noreferrer" className="underline">
                          Track
                        </a>
                      </>
                    ) : null}
                  </p>
                ) : null}
                {r.pickup_failure_reason ? (
                  <p className="text-xs text-amber-700">{r.pickup_failure_reason}</p>
                ) : null}
                {r.razorpay_refund_id ? (
                  <p className="text-xs text-muted-foreground">Razorpay refund: {r.razorpay_refund_id}</p>
                ) : null}
                {r.manual_refund_reference ? (
                  <p className="text-xs text-muted-foreground">
                    COD refund: {r.manual_refund_method?.replace(/_/g, " ")} · {r.manual_refund_reference}
                    {r.manual_refund_recorded_by ? ` · by ${r.manual_refund_recorded_by}` : ""}
                  </p>
                ) : null}
                <div className="flex flex-wrap gap-2">
                  {primary ? (
                    <button
                      type="button"
                      disabled={busy === r.return_number}
                      className="text-xs rounded-full bg-primary text-primary-foreground px-3 py-1.5"
                      onClick={primary.onClick}
                    >
                      {primary.label}
                    </button>
                  ) : null}
                  {secondary ? (
                    <button
                      type="button"
                      disabled={busy === r.return_number}
                      className="text-xs rounded-full border border-border px-3 py-1.5"
                      onClick={secondary.onClick}
                    >
                      {secondary.label}
                    </button>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
        <ListPagination
          page={paging.page}
          totalPages={paging.totalPages}
          totalItems={paging.totalItems}
          pageSize={paging.pageSize}
          scrollToId="admin-returns-list"
          onPageChange={paging.setPage}
        />
        </>
      )}

      <Dialog open={!!rejectTarget} onOpenChange={(open) => !open && setRejectTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject request</DialogTitle>
          </DialogHeader>
          <div className="space-y-1.5">
            <label htmlFor="admin-reject-reason" className="text-sm font-medium">
              Rejection reason (shown to the customer)
            </label>
            <textarea
              id="admin-reject-reason"
              className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              disabled={busy === rejectTarget}
            />
          </div>
          <DialogFooter>
            <button
              type="button"
              onClick={submitReject}
              disabled={busy === rejectTarget || rejectReason.trim().length < 5}
              className="rounded-full bg-destructive text-destructive-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              Reject
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!bookPickupTarget} onOpenChange={(open) => !open && setBookPickupTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Book reverse pickup</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            Book reverse pickup through ExpressBees when configured, or enter manual carrier details. Customers only see the stored AWB and tracking link.
          </p>
          <div className="space-y-3">
            <label className="text-sm font-medium block">
              Mode
              <select
                className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                value={pickupMode}
                onChange={(e) => setPickupMode(e.target.value as "manual" | "self_ship" | "xpressbees")}
              >
                <option value="xpressbees">ExpressBees (admin API)</option>
                <option value="manual">Manual carrier</option>
                <option value="self_ship">Customer self-ship</option>
              </select>
            </label>
            {pickupMode !== "xpressbees" ? (
              <>
                <label className="text-sm font-medium block">
                  Carrier name
                  <input
                    className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                    value={pickupCarrier}
                    onChange={(e) => setPickupCarrier(e.target.value)}
                    placeholder="e.g. Delhivery"
                  />
                </label>
                <label className="text-sm font-medium block">
                  Reverse AWB / reference *
                  <input
                    className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                    value={pickupAwb}
                    onChange={(e) => setPickupAwb(e.target.value)}
                  />
                </label>
                <label className="text-sm font-medium block">
                  Tracking URL
                  <input
                    className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                    value={pickupTrackingUrl}
                    onChange={(e) => setPickupTrackingUrl(e.target.value)}
                  />
                </label>
                <label className="text-sm font-medium block">
                  Pickup window
                  <input
                    className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                    value={pickupWindow}
                    onChange={(e) => setPickupWindow(e.target.value)}
                    placeholder="e.g. Tomorrow 10am–2pm"
                  />
                </label>
              </>
            ) : (
              <label className="text-sm font-medium block">
                Pre-assigned AWB (optional)
                <input
                  className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                  value={pickupAwb}
                  onChange={(e) => setPickupAwb(e.target.value)}
                  placeholder="Leave blank if ExpressBees generates the AWB"
                />
              </label>
            )}
          </div>
          <DialogFooter>
            <button
              type="button"
              onClick={submitBookPickup}
              disabled={busy === bookPickupTarget || (pickupMode !== "xpressbees" && pickupAwb.trim().length < 3)}
              className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              Book pickup
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!bookOutboundTarget} onOpenChange={(open) => !open && setBookOutboundTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Book replacement outbound</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            Book the replacement shipment through ExpressBees when configured, or enter manual carrier AWB and tracking. The exchange moves to shipped once booked.
          </p>
          <div className="space-y-3">
            <label className="text-sm font-medium block">
              Mode
              <select
                className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                value={outboundMode}
                onChange={(e) => setOutboundMode(e.target.value as "manual" | "xpressbees")}
              >
                <option value="xpressbees">ExpressBees (admin API)</option>
                <option value="manual">Manual carrier</option>
              </select>
            </label>
            {outboundMode !== "xpressbees" ? (
              <>
                <label className="text-sm font-medium block">
                  Carrier name
                  <input
                    className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                    value={outboundCarrier}
                    onChange={(e) => setOutboundCarrier(e.target.value)}
                    placeholder="e.g. Delhivery"
                  />
                </label>
                <label className="text-sm font-medium block">
                  Outbound AWB / reference *
                  <input
                    className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                    value={outboundAwb}
                    onChange={(e) => setOutboundAwb(e.target.value)}
                  />
                </label>
                <label className="text-sm font-medium block">
                  Tracking URL
                  <input
                    className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                    value={outboundTrackingUrl}
                    onChange={(e) => setOutboundTrackingUrl(e.target.value)}
                  />
                </label>
                <label className="text-sm font-medium block">
                  Label URL
                  <input
                    className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                    value={outboundLabelUrl}
                    onChange={(e) => setOutboundLabelUrl(e.target.value)}
                    placeholder="Optional printable label link"
                  />
                </label>
              </>
            ) : (
              <label className="text-sm font-medium block">
                Pre-assigned AWB (optional)
                <input
                  className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                  value={outboundAwb}
                  onChange={(e) => setOutboundAwb(e.target.value)}
                  placeholder="Leave blank if ExpressBees generates the AWB"
                />
              </label>
            )}
          </div>
          <DialogFooter>
            <button
              type="button"
              onClick={submitBookOutbound}
              disabled={busy === bookOutboundTarget || (outboundMode !== "xpressbees" && outboundAwb.trim().length < 3)}
              className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              Book outbound
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!acceptTarget} onOpenChange={(open) => !open && setAcceptTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Accept after inspection</DialogTitle>
          </DialogHeader>
          <label className="text-sm font-medium block">
            Default disposition
            <select
              className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
              value={acceptDisposition}
              onChange={(e) => setAcceptDisposition(e.target.value)}
            >
              <option value="restockable">Restockable — add back to sellable stock</option>
              <option value="quarantine">Quarantine</option>
              <option value="repair">Repair</option>
              <option value="discard">Discard</option>
              <option value="return_to_vendor">Return to vendor</option>
            </select>
          </label>
          {(acceptingRow?.items ?? []).map((line) => (
            <div key={line.id} className="grid grid-cols-[1fr_auto_auto] items-end gap-2 rounded-xl border border-border p-3">
              <span className="text-sm">
                {line.product_name}
                <span className="block text-xs text-muted-foreground">
                  Requested: {line.requested_qty} · estimated refund ₹
                  {Number(line.refund_amount).toLocaleString("en-IN")}
                </span>
              </span>
              <label className="text-xs">
                Accepted
                <select
                  value={acceptQuantities[line.id] ?? line.requested_qty}
                  onChange={(event) =>
                    setAcceptQuantities((current) => ({ ...current, [line.id]: Number(event.target.value) }))
                  }
                  className="block rounded-lg border border-border bg-background px-2 py-1"
                >
                  {Array.from({ length: line.requested_qty + 1 }, (_, quantity) => (
                    <option key={quantity} value={quantity}>{quantity}</option>
                  ))}
                </select>
              </label>
              <label className="text-xs">
                Disposition
                <select
                  value={acceptDispositions[line.id] ?? acceptDisposition}
                  onChange={(event) =>
                    setAcceptDispositions((current) => ({ ...current, [line.id]: event.target.value }))
                  }
                  className="block rounded-lg border border-border bg-background px-2 py-1"
                >
                  <option value="restockable">Restockable</option>
                  <option value="quarantine">Quarantine</option>
                  <option value="repair">Repair</option>
                  <option value="discard">Discard</option>
                  <option value="return_to_vendor">Return to vendor</option>
                </select>
              </label>
            </div>
          ))}
          <DialogFooter>
            <button
              type="button"
              onClick={submitAccept}
              disabled={busy === acceptTarget}
              className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              Accept
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!settlementTarget} onOpenChange={(open) => !open && setSettlementTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Settle exchange price difference</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            {Number(settlementTarget?.price_difference ?? 0) > 0 ? "Collect" : "Refund"} ₹
            {Math.abs(Number(settlementTarget?.price_difference ?? 0)).toLocaleString("en-IN")} before allocating the replacement.
          </p>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={settlementWaived}
              onChange={(event) => setSettlementWaived(event.target.checked)}
            />
            Waive this difference with an audit record
          </label>
          {!settlementWaived ? (
            <div className="grid gap-3">
              <label className="text-sm font-medium">
                Method
                <input
                  value={settlementMethod}
                  onChange={(event) => setSettlementMethod(event.target.value)}
                  placeholder="UPI, bank transfer, Razorpay"
                  className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2"
                />
              </label>
              <label className="text-sm font-medium">
                Transaction reference
                <input
                  value={settlementReference}
                  onChange={(event) => setSettlementReference(event.target.value)}
                  className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2"
                />
              </label>
            </div>
          ) : null}
          <DialogFooter>
            <button
              type="button"
              onClick={submitSettlement}
              disabled={busy === settlementTarget?.return_number}
              className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              Record settlement
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!codRefundTarget} onOpenChange={(open) => !open && setCodRefundTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Record collected COD refund</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            Record how the customer was refunded for this collected COD order. A payment reference is required for audit.
          </p>
          <div className="grid gap-3">
            <label className="text-sm font-medium block">
              Refund method
              <select
                className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                value={codRefundMethod}
                onChange={(e) => setCodRefundMethod(e.target.value as "upi" | "bank_transfer" | "cash" | "other")}
              >
                <option value="upi">UPI</option>
                <option value="bank_transfer">Bank transfer</option>
                <option value="cash">Cash</option>
                <option value="other">Other</option>
              </select>
            </label>
            <label className="text-sm font-medium block">
              Payment reference
              <input
                className="mt-1 w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm"
                value={codRefundReference}
                onChange={(e) => setCodRefundReference(e.target.value)}
                placeholder="UPI txn id, bank UTR, etc."
              />
            </label>
          </div>
          <DialogFooter>
            <button
              type="button"
              onClick={() => void submitCodRefund()}
              disabled={busy === codRefundTarget || codRefundReference.trim().length < 4}
              className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              Record refund
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!pickupFailedTarget} onOpenChange={(open) => !open && setPickupFailedTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Pickup failed</DialogTitle>
          </DialogHeader>
          <textarea
            className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
            value={pickupFailedReason}
            onChange={(e) => setPickupFailedReason(e.target.value)}
            placeholder="Customer unavailable, no product, etc."
          />
          <DialogFooter>
            <button
              type="button"
              onClick={submitPickupFailed}
              disabled={busy === pickupFailedTarget || pickupFailedReason.trim().length < 5}
              className="rounded-full bg-destructive text-destructive-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              Mark failed
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AdminPage>
  );
}
