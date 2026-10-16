import { createFileRoute } from "@tanstack/react-router";
import { Fragment, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { BookShipmentDialog } from "@/components/admin/BookShipmentDialog";
import { CreatePackageDialog } from "@/components/admin/CreatePackageDialog";
import { ReturnsSummaryCards } from "@/components/admin/ReturnsSummaryCards";
import { ReturnImageGallery } from "@/components/ReturnImageGallery";
import { AdminCaseSearchFields } from "@/components/admin/AdminCaseSearchFields";
import {
  AdminCaseListTable,
  AdminCaseTd,
  AdminCaseTh,
  adminCaseRowClass,
} from "@/components/admin/AdminCaseListTable";
import { ArrowRight, ChevronDown, Loader2, Printer } from "lucide-react";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import {
  adminCaseListFetchStatus,
  adminCaseSearchActive,
  matchesLocalDateRange,
  matchesOrderQuery,
} from "@/lib/admin-case-search";
import {
  acceptOrderReturnAfterInspection,
  allocateReplacementForOrderReturn,
  approveOrderReturn,
  bookOrderReturnPickup,
  completeOrderReturnExchange,
  completeOrderReturnPackage,
  closeFailedOrderReturnPickup,
  convertOrderReturnToRefund,
  getAdminOrderReturnsSummary,
  listAdminOrderReturns,
  markOrderReturnDelivered,
  markOrderReturnInspected,
  markOrderReturnPickupFailed,
  markOrderReturnPickedUp,
  markOrderReturnPickupRequested,
  previewOrderReturnPackageRates,
  markOrderReturnReceived,
  markOrderReturnShipped,
  recordOrderReturnCodRefund,
  refundOrderReturn,
  rejectOrderReturn,
  rescheduleOrderReturnPickup,
  settleOrderReturnPriceDifference,
  type AdminOrderReturnRow,
  type AdminOrderReturnsSummary,
} from "@/lib/orders.functions";
import { openPrintableShippingLabel } from "@/lib/print-shipping-label";
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
import { formatReturnVariant } from "@/lib/return-variant";
import { SHOW_M2_POST_ORDER_UI } from "@/lib/feature-flags";
import { isPackageReady } from "@/lib/fulfillment-docs";
import { bookExchangeFulfillmentShipment, exchangeToFulfillmentOrder } from "@/lib/exchange-fulfillment";
import { formatCaseAge } from "@/lib/admin-case-format";

export const Route = createFileRoute("/admin/returns")({ component: AdminReturnsPage });

type StageId = "all" | "request" | "pickup" | "inspection" | "exchange" | "closed";
type RequestTypeFilter = "" | "return" | "exchange";

const TYPE_FILTERS: { id: RequestTypeFilter; label: string }[] = [
  { id: "", label: "All cases" },
  { id: "return", label: "Returns" },
  { id: "exchange", label: "Exchanges" },
];

const DEFAULT_RETURNS_LIST_FILTER = {
  stage: "request" as StageId,
  status: "requested",
  requestType: "" as RequestTypeFilter,
};

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
      { value: "repacking", label: "Exchange pack" },
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

function formatInr(amount: number | null | undefined): string {
  return `₹${Number(amount ?? 0).toLocaleString("en-IN")}`;
}

function returnStatusPillClass(status: string, overdue?: boolean): string {
  if (overdue) return "bg-amber-500/15 text-amber-800 ring-1 ring-amber-500/25";
  switch (status) {
    case "rejected":
    case "failed":
    case "pickup_failed":
      return "bg-destructive/15 text-destructive ring-1 ring-destructive/20";
    case "requested":
    case "inspected":
    case "pickup_requested":
      return "bg-amber-500/15 text-amber-800 ring-1 ring-amber-500/25";
    case "refunded":
    case "completed":
    case "accepted":
    case "delivered":
      return "bg-emerald-500/15 text-emerald-800 ring-1 ring-emerald-500/20";
    default:
      return "bg-primary/15 text-primary ring-1 ring-primary/20";
  }
}

function priceDifferenceClass(diff: number): string {
  if (diff > 0) return "border-amber-200/80 bg-amber-50/90 text-amber-950";
  if (diff < 0) return "border-emerald-200/80 bg-emerald-50/90 text-emerald-950";
  return "border-border/70 bg-muted/50 text-foreground";
}

function usesRazorpayPriceDifferenceRefund(r: AdminOrderReturnRow | null | undefined): boolean {
  return r?.can_razorpay_refund_price_difference === true;
}

function hasPendingFailedSettlement(r: AdminOrderReturnRow): boolean {
  return r.settlement_status === "pending" && Boolean(r.settlement_reference?.startsWith("rfnd_"));
}

type SettlementMode = "razorpay" | "manual" | "waive" | "collect";

function settlementMode(
  target: AdminOrderReturnRow | null | undefined,
  waived: boolean,
  manualOverride: boolean,
): SettlementMode {
  if (waived) return "waive";
  if (Number(target?.price_difference ?? 0) > 0) return "collect";
  if (usesRazorpayPriceDifferenceRefund(target) && !manualOverride) return "razorpay";
  return "manual";
}

function HighlightBlock({
  label,
  children,
  className,
  labelClassName,
}: {
  label: string;
  children: ReactNode;
  className?: string;
  labelClassName?: string;
}) {
  return (
    <div className={cn("rounded-xl border px-3 py-2", className)}>
      <p className={cn("text-[10px] font-semibold uppercase tracking-[0.16em]", labelClassName)}>{label}</p>
      <div className="mt-0.5 text-sm leading-relaxed">{children}</div>
    </div>
  );
}

function ProductHighlight({
  name,
  imageUrl,
  variant,
  replacement,
  qty,
  acceptedQty,
  refundAmount,
  disposition,
}: {
  name: string;
  imageUrl?: string | null;
  variant?: string;
  replacement?: string;
  qty?: number;
  acceptedQty?: number | null;
  refundAmount?: number;
  disposition?: string | null;
}) {
  return (
    <div className="flex gap-3">
      {imageUrl ? (
        <a href={imageUrl} target="_blank" rel="noreferrer" className="shrink-0">
          <img
            src={imageUrl}
            alt={name}
            referrerPolicy="no-referrer"
            className="h-16 w-16 rounded-xl object-cover border border-border"
          />
        </a>
      ) : null}
      <div className="min-w-0 space-y-1">
        <p className="text-sm font-semibold text-foreground">{name}</p>
        {variant ? <p className="text-xs text-muted-foreground">{variant}</p> : null}
        {replacement ? (
          <p className="inline-flex items-center gap-1 text-xs font-medium text-primary">
            <ArrowRight className="h-3.5 w-3.5 shrink-0" aria-hidden />
            Exchange for {replacement}
          </p>
        ) : null}
        {qty != null ? (
          <p className="text-xs text-muted-foreground">
            Qty {qty} requested
            {acceptedQty != null ? ` · ${acceptedQty} accepted` : ""}
            {refundAmount != null ? (
              <>
                {" · "}
                <span className="font-semibold text-foreground">refund {formatInr(refundAmount)}</span>
              </>
            ) : null}
            {disposition ? ` · ${disposition.replace(/_/g, " ")}` : ""}
          </p>
        ) : null}
      </div>
    </div>
  );
}

function returnCasePaymentPill(r: AdminOrderReturnRow): string {
  if (r.status === "repacking") {
    return "exchange pack";
  }
  if (r.status !== "refunded" || !r.razorpay_refund_id) {
    return r.status.replace(/_/g, " ");
  }
  const payment = r.payment_status?.trim().toLowerCase();
  if (payment === "refunded") return "refunded";
  if (payment === "partially_refunded") return "partially refunded";
  if (payment === "refund_failed") return "refund failed";
  return "refund initiated";
}

function hasReverseShipment(r: AdminOrderReturnRow): boolean {
  return Boolean(r.reverse_awb?.trim() || r.logistics_mode?.trim());
}

const EXCHANGE_OUTBOUND_PACK_STATUSES = new Set(["item_allocated", "repacking", "ready_to_ship"]);

function ReturnCaseDetails({ r }: { r: AdminOrderReturnRow }) {
  const items = r.items ?? [];
  const headerVariant = items.length === 1 ? formatReturnVariant(items[0]) : formatReturnVariant(r);
  const replacement = [r.replacement_size, r.replacement_color].filter(Boolean).join(" · ");
  const priceDiff = r.price_difference == null ? null : Number(r.price_difference);
  return (
    <div className="space-y-3">
      {Number(r.order_shipping) > 0 ? (
        <p className="text-xs text-muted-foreground">
          Delivery {formatInr(r.order_shipping)} is not refunded
          {Number(r.order_cod_fee) > 0 ? ` · COD fee ${formatInr(r.order_cod_fee)} is not refunded` : ""}
        </p>
      ) : Number(r.order_cod_fee) > 0 ? (
        <p className="text-xs text-muted-foreground">COD fee {formatInr(r.order_cod_fee)} is not refunded</p>
      ) : null}
      {items.length > 0 || r.product_name ? (
        <div className="space-y-2.5 rounded-xl border border-primary/15 bg-primary/5 p-3">
          {items.length > 0
            ? items.map((line, index) => (
                <div key={line.id} className={index > 0 ? "border-t border-primary/10 pt-2.5" : undefined}>
                  <ProductHighlight
                    name={line.product_name}
                    imageUrl={line.product_image}
                    variant={formatReturnVariant(line)}
                    replacement={items.length === 1 ? replacement : undefined}
                    qty={line.requested_qty}
                    acceptedQty={line.accepted_qty}
                    refundAmount={line.refund_amount}
                    disposition={line.disposition}
                  />
                </div>
              ))
            : r.product_name ? (
                <ProductHighlight
                  name={r.product_name}
                  imageUrl={r.product_image}
                  variant={headerVariant}
                  replacement={replacement}
                />
              ) : null}
        </div>
      ) : null}
      {r.request_type === "exchange" && priceDiff != null ? (
        <HighlightBlock
          label="Price difference"
          className={priceDifferenceClass(priceDiff)}
          labelClassName="opacity-80"
        >
          <p className="font-semibold">
            {formatInr(Math.abs(priceDiff))}
            {priceDiff > 0 ? " to collect" : priceDiff < 0 ? " to refund" : ""}
            {r.settlement_status ? (
              <span className="ml-1.5 text-xs font-medium opacity-80">
                · {r.settlement_status.replace(/_/g, " ")}
              </span>
            ) : null}
          </p>
        </HighlightBlock>
      ) : null}
      {r.reason ? (
        <HighlightBlock
          label="Customer reason"
          className="border-primary/20 bg-primary/8 text-foreground"
          labelClassName="text-primary"
        >
          {r.reason}
        </HighlightBlock>
      ) : null}
      <HighlightBlock
        label="Customer photos"
        className="border-border/70 bg-muted/30 text-foreground"
        labelClassName="text-muted-foreground"
      >
        {r.image_urls && r.image_urls.length > 0 ? (
          <ReturnImageGallery
            urls={r.image_urls}
            thumbClassName="h-20 w-20"
            alt={r.request_type === "exchange" ? "Customer exchange photo" : "Customer return photo"}
          />
        ) : (
          <p className="text-xs text-muted-foreground">No photos attached</p>
        )}
      </HighlightBlock>
      {r.admin_notes ? (
        <HighlightBlock
          label="Admin notes"
          className="border-border/70 bg-muted/40 text-foreground"
          labelClassName="text-muted-foreground"
        >
          <p className="whitespace-pre-wrap text-xs">{r.admin_notes}</p>
        </HighlightBlock>
      ) : null}
      {r.rejection_reason ? (
        <HighlightBlock
          label="Rejection reason"
          className="border-amber-200/80 bg-amber-50/90 text-amber-950"
          labelClassName="text-amber-800"
        >
          {r.rejection_reason}
        </HighlightBlock>
      ) : null}
      {r.pickup_failure_reason ? (
        <HighlightBlock
          label="Pickup failed"
          className="border-amber-200/80 bg-amber-50/90 text-amber-950"
          labelClassName="text-amber-800"
        >
          {r.pickup_failure_reason}
        </HighlightBlock>
      ) : null}
      {r.reverse_awb || r.tracking_url ? (
        <p className="text-xs text-muted-foreground">
          Pickup AWB: {r.reverse_awb ?? "—"}
          {r.pickup_window ? ` · ${r.pickup_window}` : ""}
          {r.tracking_url ? (
            <>
              {" · "}
              <a href={r.tracking_url} target="_blank" rel="noreferrer" className="underline">
                Track pickup
              </a>
            </>
          ) : null}
        </p>
      ) : null}
      {r.outbound_awb || r.outbound_tracking_url || r.outbound_label_url ? (
        <p className="text-xs text-muted-foreground">
          Outbound AWB: {r.outbound_awb ?? "—"}
          {r.outbound_tracking_url ? (
            <>
              {" · "}
              <a href={r.outbound_tracking_url} target="_blank" rel="noreferrer" className="underline">
                Track
              </a>
            </>
          ) : null}
        </p>
      ) : r.request_type === "exchange" && EXCHANGE_OUTBOUND_PACK_STATUSES.has(r.status) ? (
        <p className="text-xs text-muted-foreground">
          {isPackageReady(r)
            ? `Packed ${r.package_weight_grams || r.default_package_weight_grams || "—"} g${
                r.package_length_cm && r.package_breadth_cm && r.package_height_cm
                  ? ` · ${r.package_length_cm}×${r.package_breadth_cm}×${r.package_height_cm} cm`
                  : ""
              }${r.package_courier_name ? ` · ${r.package_courier_name}` : ""}. Book a shipment to generate the AWB and label.`
            : "Create a package first. Booking a shipment is blocked until the package exists."}
        </p>
      ) : null}
      {r.razorpay_refund_id ? (
        <p className="text-xs text-muted-foreground">Razorpay refund: {r.razorpay_refund_id}</p>
      ) : null}
      {r.settlement_reference && r.settlement_status === "refunded" ? (
        <p className="text-xs text-muted-foreground">
          Exchange settlement refund{r.settlement_method ? ` (${r.settlement_method})` : ""}: {r.settlement_reference}
        </p>
      ) : null}
      {r.manual_refund_reference ? (
        <p className="text-xs text-muted-foreground">
          COD refund: {r.manual_refund_method?.replace(/_/g, " ")} · {r.manual_refund_reference}
          {r.manual_refund_recorded_by ? ` · by ${r.manual_refund_recorded_by}` : ""}
        </p>
      ) : null}
    </div>
  );
}

/** One primary next-step action (plus an optional secondary one) per current status. */
function nextActions(
  r: AdminOrderReturnRow,
  run: (label: string, action: () => Promise<unknown>) => void,
  openReject: () => void,
  openBookPickup: () => void,
  openBookOutbound: () => void,
  openCreatePackage: () => void,
  openAccept: () => void,
  openPickupFailed: () => void,
  openSettlement: () => void,
  openCodRefund: () => void,
  canFulfill: boolean,
): {
  primary?: { label: string; onClick: () => void };
  secondary?: { label: string; onClick: () => void };
  tertiary?: { label: string; onClick: () => void };
} {
  const isExchange = r.request_type === "exchange";
  if (r.status === "requested") {
    return {
      primary: { label: "Approve", onClick: () => run("Approved", () => approveOrderReturn(r.return_number)) },
      secondary: { label: "Reject", onClick: openReject },
    };
  }
  if (!canFulfill) return {};
  switch (r.status) {
    case "approved":
      return {
        primary: { label: "Book pickup", onClick: openBookPickup },
      };
    case "pickup_scheduled":
      return hasReverseShipment(r)
        ? {
            primary: {
              label: "Mark picked up",
              onClick: () => run("Marked picked up", () => markOrderReturnPickedUp(r.return_number)),
            },
            secondary: isExchange
              ? { label: "Pickup failed", onClick: openPickupFailed }
              : {
                  label: "Mark received",
                  onClick: () => run("Marked received", () => markOrderReturnReceived(r.return_number)),
                },
            tertiary: isExchange ? undefined : { label: "Pickup failed", onClick: openPickupFailed },
          }
        : {
            primary: { label: "Book pickup", onClick: openBookPickup },
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
      return hasReverseShipment(r)
        ? {
            primary: {
              label: "Mark picked up",
              onClick: () => run("Marked picked up", () => markOrderReturnPickedUp(r.return_number)),
            },
            secondary: isExchange
              ? undefined
              : {
                  label: "Mark received",
                  onClick: () => run("Marked received", () => markOrderReturnReceived(r.return_number)),
                },
          }
        : {
            primary: { label: "Book pickup", onClick: openBookPickup },
          };
    case "picked_up":
      return hasReverseShipment(r)
        ? {
            primary: {
              label: "Mark received",
              onClick: () => run("Marked received", () => markOrderReturnReceived(r.return_number)),
            },
          }
        : {
            primary: { label: "Book pickup", onClick: openBookPickup },
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
              primary: {
                label: usesRazorpayPriceDifferenceRefund(r) ? "Refund via Razorpay" : "Settle price difference",
                onClick: openSettlement,
              },
              ...(hasPendingFailedSettlement(r)
                ? {}
                : {
                    secondary: {
                      label: "Convert to refund",
                      onClick: () => run("Converted to refund", () => convertOrderReturnToRefund(r.return_number)),
                    },
                  }),
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
                onClick: () => run("Refund initiated", () => refundOrderReturn(r.return_number)),
              },
            };
    case "item_allocated":
    case "repacking":
      return {
        primary: {
          label: "Create package",
          onClick: openCreatePackage,
        },
      };
    case "ready_to_ship":
      return isExchange
        ? isPackageReady(r)
          ? r.can_book_outbound
            ? {
                primary: { label: "Book shipment", onClick: openBookOutbound },
                secondary: { label: "Update package", onClick: openCreatePackage },
                tertiary: r.outbound_awb
                  ? {
                      label: "Mark shipped (manual)",
                      onClick: () => run("Marked shipped", () => markOrderReturnShipped(r.return_number)),
                    }
                  : undefined,
              }
            : r.outbound_awb
              ? {
                  primary: {
                    label: "Mark shipped (manual)",
                    onClick: () => run("Marked shipped", () => markOrderReturnShipped(r.return_number)),
                  },
                  secondary: { label: "Update package", onClick: openCreatePackage },
                }
              : {
                  primary: { label: "Update package", onClick: openCreatePackage },
                }
          : {
              primary: { label: "Create package", onClick: openCreatePackage },
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
                onClick: () => run("Refund initiated", () => refundOrderReturn(r.return_number)),
              },
            };
    default:
      return {};
  }
}

export function AdminReturnsPage({ mode = "admin" }: { mode?: "admin" | "operations" | "support" } = {}) {
  if (!SHOW_M2_POST_ORDER_UI) return null;
  return <AdminReturnsPageContent mode={mode} />;
}

function AdminReturnsPageContent({ mode = "admin" }: { mode?: "admin" | "operations" | "support" } = {}) {
  const canFulfill = mode !== "support";
  const eyebrow = mode === "support" ? "Customer Support" : mode === "operations" ? "Operations" : "Fulfilment";

  const [stage, setStage] = useState<StageId>(DEFAULT_RETURNS_LIST_FILTER.stage);
  const [status, setStatus] = useState<string>(DEFAULT_RETURNS_LIST_FILTER.status);
  const [requestType, setRequestType] = useState<RequestTypeFilter>(DEFAULT_RETURNS_LIST_FILTER.requestType);
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
  const [bookOutboundTarget, setBookOutboundTarget] = useState<AdminOrderReturnRow | null>(null);
  const [packageTarget, setPackageTarget] = useState<AdminOrderReturnRow | null>(null);
  const [settlementTarget, setSettlementTarget] = useState<AdminOrderReturnRow | null>(null);
  const [settlementMethod, setSettlementMethod] = useState("");
  const [settlementReference, setSettlementReference] = useState("");
  const [settlementWaived, setSettlementWaived] = useState(false);
  const [settlementManualOverride, setSettlementManualOverride] = useState(false);
  const [codRefundTarget, setCodRefundTarget] = useState<string | null>(null);
  const [codRefundMethod, setCodRefundMethod] = useState<"upi" | "bank_transfer" | "cash" | "other">("upi");
  const [codRefundReference, setCodRefundReference] = useState("");
  const [summary, setSummary] = useState<AdminOrderReturnsSummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [orderQueryInput, setOrderQueryInput] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [expandedOverride, setExpandedOverride] = useState<string | null | undefined>(undefined);
  const hasRowsRef = useRef(false);
  const loadGenRef = useRef(0);
  const searchActive = adminCaseSearchActive(orderQueryInput, dateFrom, dateTo);
  hasRowsRef.current = rows.length > 0;

  const stageStatusOptions = useMemo(() => statusesForStage(stage), [stage]);

  const loadSummary = useCallback(async () => {
    setSummaryLoading(true);
    try {
      setSummary(await getAdminOrderReturnsSummary());
    } catch (error) {
      showApiError(error, "Could not load returns summary");
    } finally {
      setSummaryLoading(false);
    }
  }, []);

  const load = useCallback(async () => {
    const gen = ++loadGenRef.current;
    const keepList = hasRowsRef.current;
    if (!keepList) setLoading(true);
    try {
      // Exact status → API filter. Search / stage-only / All → fetch all, filter client-side.
      const res = await listAdminOrderReturns(adminCaseListFetchStatus(searchActive, status));
      if (gen !== loadGenRef.current) return;
      setRows(res.returns ?? []);
    } catch (error) {
      if (gen !== loadGenRef.current) return;
      showApiError(error, "Could not load returns");
    } finally {
      if (gen === loadGenRef.current) setLoading(false);
    }
  }, [searchActive, status]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void loadSummary();
  }, [loadSummary]);

  const filteredRows = useMemo(() => {
    let next = rows;
    if (!searchActive) {
      if (requestType) {
        next = next.filter((row) => row.request_type === requestType);
      }
      if (!status && stage !== "all") {
        const allowed = STAGE_STATUS_SET[stage];
        next = next.filter((row) => allowed.has(row.status));
      }
    }
    return next.filter(
      (row) =>
        matchesOrderQuery(orderQueryInput, { orderNumber: row.order_number, orderId: row.order_id }) &&
        matchesLocalDateRange(row.requested_at, dateFrom, dateTo),
    );
  }, [rows, requestType, stage, status, searchActive, orderQueryInput, dateFrom, dateTo]);

  const paging = useListPagination(filteredRows, {
    pageSize: LIST_PAGE_SIZE,
    resetKey: `${requestType}|${stage}|${status}|${orderQueryInput}|${dateFrom}|${dateTo}`,
  });
  const pageItemKey = paging.pageItems.map((row) => row.return_number).join("|");
  useEffect(() => {
    setExpandedOverride(undefined);
  }, [pageItemKey]);
  const autoExpandedNumber = paging.pageItems.length === 1 ? paging.pageItems[0].return_number : null;
  const expandedNumber = expandedOverride === undefined ? autoExpandedNumber : expandedOverride;

  const selectStage = (next: StageId, nextStatus?: string, nextType?: RequestTypeFilter) => {
    setStage(next);
    setStatus(nextStatus ?? "");
    if (nextType !== undefined) {
      setRequestType(nextType);
    }
  };

  const filtersActive =
    stage !== DEFAULT_RETURNS_LIST_FILTER.stage ||
    status !== DEFAULT_RETURNS_LIST_FILTER.status ||
    requestType !== DEFAULT_RETURNS_LIST_FILTER.requestType ||
    adminCaseSearchActive(orderQueryInput, dateFrom, dateTo);

  const run = async (returnNumber: string, action: () => Promise<unknown>, label: string) => {
    setBusy(returnNumber);
    try {
      await action();
      toast.success(label);
      await Promise.all([load(), loadSummary()]);
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
    const mode = settlementMode(settlementTarget, settlementWaived, settlementManualOverride);
    const status = mode === "waive" ? "waived" : mode === "collect" ? "collected" : "refunded";
    const needsManualProof = mode === "manual" || mode === "collect";
    if (needsManualProof && (!settlementMethod.trim() || !settlementReference.trim())) {
      toast.error("Settlement method and transaction reference are required");
      return;
    }
    await run(
      settlementTarget.return_number,
      () =>
        settleOrderReturnPriceDifference(settlementTarget.return_number, {
          status,
          amount: mode === "waive" ? 0 : Math.abs(difference),
          method: needsManualProof ? settlementMethod.trim() : undefined,
          reference: needsManualProof ? settlementReference.trim() : undefined,
          ...(mode === "manual" && usesRazorpayPriceDifferenceRefund(settlementTarget)
            ? { force_manual: true }
            : {}),
        }),
      mode === "razorpay" ? "Razorpay refund initiated" : "Price difference settled",
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
      eyebrow={eyebrow}
      title="Returns & exchanges"
      description="Review customer photos, then walk each case through pickup, inspection, and refund or replacement."
    >
      <ReturnsSummaryCards summary={summary} loading={summaryLoading} onSelectStage={selectStage} />

      <section
        className="glass-strong mb-5 space-y-4 rounded-3xl p-4 sm:p-5"
        aria-label="Filter returns"
      >
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground">
            Case type
          </p>
          <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter by case type">
            {TYPE_FILTERS.map((option) => (
              <button
                key={option.id || "all-cases"}
                type="button"
                aria-pressed={requestType === option.id}
                onClick={() => setRequestType(option.id)}
                className={stageFilterPillClass(requestType === option.id)}
              >
                {option.label}
                {summary ? (
                  <span className="ml-1 tabular-nums opacity-80">
                    {option.id === ""
                      ? summary.total
                      : option.id === "return"
                        ? summary.returns
                        : summary.exchanges}
                  </span>
                ) : null}
              </button>
            ))}
          </div>
        </div>

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
                  {summary ? (
                    <span className="ml-1 tabular-nums opacity-80">
                      {option.id === "all" ? summary.total : summary.stages[option.id]}
                    </span>
                  ) : null}
                </button>
              );
            })}
          </div>
        </div>

        {stage === "all" ? (
          <p className="text-xs text-muted-foreground">
            Choose a stage to see its statuses. Return cases close after inspection; exchange cases continue through replacement shipping.
          </p>
        ) : (
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground">
              {STAGE_FILTERS.find((option) => option.id === stage)?.label} status
            </p>
            <div
              className="flex flex-wrap gap-1.5"
              role="group"
              aria-label={`Filter by ${STAGE_FILTERS.find((option) => option.id === stage)?.label.toLowerCase()} status`}
            >
              <button
                type="button"
                aria-pressed={status === ""}
                onClick={() => setStatus("")}
                className={stageFilterPillClass(status === "")}
              >
                All
              </button>
              {stageStatusOptions.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  aria-pressed={status === option.value}
                  onClick={() => setStatus(option.value)}
                  className={stageFilterPillClass(status === option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>
        )}

        <AdminCaseSearchFields
          orderQuery={orderQueryInput}
          onOrderQueryChange={setOrderQueryInput}
          from={dateFrom}
          to={dateTo}
          onFromChange={setDateFrom}
          onToChange={setDateTo}
        />

        <div className="flex flex-wrap items-end gap-3">
          {filtersActive && (
            <button
              type="button"
              onClick={() => {
                setStage(DEFAULT_RETURNS_LIST_FILTER.stage);
                setStatus(DEFAULT_RETURNS_LIST_FILTER.status);
                setRequestType(DEFAULT_RETURNS_LIST_FILTER.requestType);
                setOrderQueryInput("");
                setDateFrom("");
                setDateTo("");
              }}
              className="inline-flex h-10 items-center rounded-xl border border-border bg-background px-3 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              Clear
            </button>
          )}
          {!loading && (
            <p className="ml-auto text-xs text-muted-foreground" aria-live="polite">
              {paging.totalItems === 0
                ? "0 cases"
                : paging.totalPages > 1
                  ? `Showing ${paging.rangeStart}–${paging.rangeEnd} of ${paging.totalItems}`
                  : `${paging.totalItems} case${paging.totalItems === 1 ? "" : "s"}`}
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
        <AdminCaseListTable id="admin-returns-list" caption="Returns and exchanges">
          <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <AdminCaseTh className="w-10">
                <span className="sr-only">Details</span>
              </AdminCaseTh>
              <AdminCaseTh>Case</AdminCaseTh>
              <AdminCaseTh>Order ID</AdminCaseTh>
              <AdminCaseTh>Customer</AdminCaseTh>
              <AdminCaseTh>Amount</AdminCaseTh>
              <AdminCaseTh>Status</AdminCaseTh>
              <AdminCaseTh>Requested</AdminCaseTh>
              <AdminCaseTh>Actions</AdminCaseTh>
            </tr>
          </thead>
          <tbody>
            {paging.pageItems.map((r) => {
            const { primary, secondary, tertiary } = nextActions(
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
                setBookOutboundTarget(r);
              },
              () => {
                setPackageTarget(r);
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
                setSettlementManualOverride(false);
              },
              () => {
                setCodRefundTarget(r.return_number);
                setCodRefundMethod("upi");
                setCodRefundReference("");
              },
              canFulfill,
            );
            const expanded = expandedNumber === r.return_number;
            return (
              <Fragment key={r.return_number}>
                <tr className={adminCaseRowClass}>
                  <AdminCaseTd className="w-10 pr-0">
                    <button
                      type="button"
                      aria-expanded={expanded}
                      aria-label={`Details for ${r.return_number}`}
                      onClick={() => setExpandedOverride(expanded ? null : r.return_number)}
                      className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground"
                    >
                      <ChevronDown className={cn("h-4 w-4 transition-transform", expanded && "rotate-180")} />
                    </button>
                  </AdminCaseTd>
                  <AdminCaseTd className="min-w-[10rem] font-medium">
                    <p className="whitespace-nowrap">
                      {r.request_type === "exchange" ? "Exchange" : "Return"} · {r.return_number}
                    </p>
                    {r.reason ? (
                      <p className="mt-1 max-w-[14rem] truncate text-xs text-muted-foreground" title={r.reason}>
                        {r.reason}
                      </p>
                    ) : null}
                    {r.image_urls && r.image_urls.length > 0 ? (
                      <p className="mt-0.5 text-[11px] text-muted-foreground">
                        {r.image_urls.length} customer photo{r.image_urls.length === 1 ? "" : "s"}
                      </p>
                    ) : null}
                  </AdminCaseTd>
                  <AdminCaseTd className="whitespace-nowrap font-medium">{r.order_number ?? "—"}</AdminCaseTd>
                  <AdminCaseTd className="whitespace-nowrap">{r.customer_login || "—"}</AdminCaseTd>
                  <AdminCaseTd className="whitespace-nowrap tabular-nums">{formatInr(r.order_total)}</AdminCaseTd>
                  <AdminCaseTd>
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span
                        className={cn(
                          "inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide",
                          returnStatusPillClass(r.status, r.sla_overdue),
                        )}
                      >
                        {returnCasePaymentPill(r)}
                      </span>
                      {r.cod_refund_pending ? (
                        <span className="inline-flex items-center rounded-full bg-amber-500/15 px-2.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-800 ring-1 ring-amber-500/25">
                          COD refund pending
                        </span>
                      ) : null}
                    </div>
                    <p className={r.sla_overdue ? "mt-1 text-xs font-medium text-amber-700" : "mt-1 text-xs text-muted-foreground"}>
                      {formatCaseAge(r)}
                    </p>
                  </AdminCaseTd>
                  <AdminCaseTd className="whitespace-nowrap text-xs text-muted-foreground">
                    {new Date(r.requested_at).toLocaleString("en-IN")}
                  </AdminCaseTd>
                  <AdminCaseTd className="min-w-[12rem]">
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
                      {tertiary ? (
                        <button
                          type="button"
                          disabled={busy === r.return_number}
                          className="text-xs rounded-full border border-border px-3 py-1.5"
                          onClick={tertiary.onClick}
                        >
                          {tertiary.label}
                        </button>
                      ) : null}
                      {r.outbound_label_url ? (
                        <>
                          <a
                            href={r.outbound_label_url}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1.5 text-xs rounded-full border border-border px-3 py-1.5"
                          >
                            Open label
                          </a>
                          <button
                            type="button"
                            onClick={() => {
                              const labelUrl = r.outbound_label_url;
                              if (labelUrl) void openPrintableShippingLabel(labelUrl);
                            }}
                            className="inline-flex items-center gap-1.5 text-xs rounded-full border border-border px-3 py-1.5"
                          >
                            <Printer className="h-3.5 w-3.5" /> Print label
                          </button>
                        </>
                      ) : null}
                    </div>
                  </AdminCaseTd>
                </tr>
                {expanded ? (
                  <tr className="border-t border-border/40 bg-muted/15">
                    <AdminCaseTd colSpan={8} className="px-3 pb-4 pt-0">
                      <ReturnCaseDetails r={r} />
                    </AdminCaseTd>
                  </tr>
                ) : null}
              </Fragment>
            );
          })}
          </tbody>
        </AdminCaseListTable>
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
              <span className="text-destructive" aria-hidden="true"> *</span>
            </label>
            <textarea
              id="admin-reject-reason"
              className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              disabled={busy === rejectTarget}
              required
              aria-required="true"
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

      <CreatePackageDialog
        order={packageTarget ? exchangeToFulfillmentOrder(packageTarget) : null}
        open={!!packageTarget}
        onOpenChange={(open) => !open && setPackageTarget(null)}
        onSaved={async () => {
          await load();
          await loadSummary();
        }}
        completePackage={completeOrderReturnPackage}
        description="Reduce packed quantity if a replacement unit is damaged. Unshipped units are refunded automatically. Package date is display only."
      />

      <BookShipmentDialog
        order={bookOutboundTarget ? exchangeToFulfillmentOrder(bookOutboundTarget) : null}
        open={!!bookOutboundTarget}
        requiresOverride={false}
        onOpenChange={(open) => !open && setBookOutboundTarget(null)}
        onSaved={async () => {
          await load();
          await loadSummary();
        }}
        title="New replacement shipment"
        previewRates={previewOrderReturnPackageRates}
        bookShipment={bookExchangeFulfillmentShipment}
      />

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
                {formatReturnVariant(line) ? (
                  <span className="block text-xs text-muted-foreground">{formatReturnVariant(line)}</span>
                ) : null}
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
            <DialogTitle>
              {usesRazorpayPriceDifferenceRefund(settlementTarget)
                ? "Refund exchange price difference"
                : "Settle exchange price difference"}
            </DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            {settlementMode(settlementTarget, settlementWaived, settlementManualOverride) === "razorpay"
              ? `Refund ₹${Math.abs(Number(settlementTarget?.price_difference ?? 0)).toLocaleString("en-IN")} to the original prepaid payment via Razorpay before allocating the replacement.`
              : `${Number(settlementTarget?.price_difference ?? 0) > 0 ? "Collect" : "Refund"} ₹${Math.abs(Number(settlementTarget?.price_difference ?? 0)).toLocaleString("en-IN")} before allocating the replacement.`}
          </p>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={settlementWaived}
              onChange={(event) => {
                setSettlementWaived(event.target.checked);
                if (event.target.checked) setSettlementManualOverride(false);
              }}
            />
            Waive this difference with an audit record
          </label>
          {usesRazorpayPriceDifferenceRefund(settlementTarget) &&
          settlementMode(settlementTarget, settlementWaived, settlementManualOverride) !== "waive" ? (
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={settlementManualOverride}
                onChange={(event) => setSettlementManualOverride(event.target.checked)}
              />
              Record manually instead (Razorpay unavailable or already refunded)
            </label>
          ) : null}
          {settlementMode(settlementTarget, settlementWaived, settlementManualOverride) === "manual" ||
          settlementMode(settlementTarget, settlementWaived, settlementManualOverride) === "collect" ? (
            <div className="grid gap-3">
              <label className="text-sm font-medium">
                Method
                <input
                  value={settlementMethod}
                  onChange={(event) => setSettlementMethod(event.target.value)}
                  placeholder="UPI, bank transfer, Razorpay dashboard"
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
              {settlementMode(settlementTarget, settlementWaived, settlementManualOverride) === "razorpay"
                ? "Refund via Razorpay"
                : "Record settlement"}
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
          <div className="space-y-1.5">
            <label htmlFor="admin-pickup-failed-reason" className="text-sm font-medium">
              Failure reason
              <span className="text-destructive" aria-hidden="true"> *</span>
            </label>
            <textarea
              id="admin-pickup-failed-reason"
              className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
              value={pickupFailedReason}
              onChange={(e) => setPickupFailedReason(e.target.value)}
              placeholder="Customer unavailable, no product, etc."
              required
              aria-required="true"
            />
          </div>
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
