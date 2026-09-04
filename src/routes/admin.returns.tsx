import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import {
  acceptOrderReturnAfterInspection,
  allocateReplacementForOrderReturn,
  approveOrderReturn,
  completeOrderReturnExchange,
  convertOrderReturnToRefund,
  listAdminOrderReturns,
  markOrderReturnDelivered,
  markOrderReturnInspected,
  markOrderReturnPickedUp,
  markOrderReturnPickupRequested,
  markOrderReturnReadyToShip,
  markOrderReturnReceived,
  markOrderReturnRepacking,
  markOrderReturnShipped,
  refundOrderReturn,
  rejectOrderReturn,
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

const STATUS_FILTERS = [
  "",
  "requested",
  "approved",
  "pickup_requested",
  "picked_up",
  "received",
  "inspected",
  "accepted",
  "item_allocated",
  "repacking",
  "ready_to_ship",
  "shipped",
  "delivered",
  "refunded",
  "completed",
  "rejected",
  "failed",
] as const;

/** One primary next-step action (plus an optional secondary one) per current status. */
function nextActions(
  r: AdminOrderReturnRow,
  run: (label: string, action: () => Promise<unknown>) => void,
  openReject: () => void,
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
        primary: {
          label: "Mark pickup requested",
          onClick: () => run("Pickup requested", () => markOrderReturnPickupRequested(r.return_number)),
        },
        secondary: {
          label: "Mark received",
          onClick: () => run("Marked received", () => markOrderReturnReceived(r.return_number)),
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
        primary: {
          label: "Accept",
          onClick: () => run("Accepted", () => acceptOrderReturnAfterInspection(r.return_number)),
        },
        secondary: { label: "Reject", onClick: openReject },
      };
    case "accepted":
      return isExchange
        ? {
            primary: {
              label: "Allocate replacement",
              onClick: () => run("Replacement allocated", () => allocateReplacementForOrderReturn(r.return_number)),
            },
            secondary: {
              label: "Convert to refund",
              onClick: () => run("Converted to refund", () => convertOrderReturnToRefund(r.return_number)),
            },
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
      return {
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

  const [status, setStatus] = useState<string>("requested");
  const [rows, setRows] = useState<AdminOrderReturnRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [rejectTarget, setRejectTarget] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    try {
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

  const paging = useListPagination(rows, { pageSize: LIST_PAGE_SIZE, resetKey: status });

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

  return (
    <AdminPage
      eyebrow="Fulfilment"
      title="Order returns & exchanges"
      description="Review customer return/exchange requests, walk them through pickup, inspection, and refund or replacement."
    >
      <div className="flex flex-wrap gap-2 mb-4">
        {STATUS_FILTERS.map((s) => (
          <button
            key={s || "all"}
            type="button"
            onClick={() => setStatus(s)}
            className={`text-xs rounded-full px-3 py-1.5 border ${
              status === s ? "bg-primary text-primary-foreground border-primary" : "border-border"
            }`}
          >
            {s ? s.replace(/_/g, " ") : "All"}
          </button>
        ))}
      </div>

      {loading ? (
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No returns in this filter.</p>
      ) : (
        <ul id="admin-returns-list" className="space-y-4">
          {paging.pageItems.map((r) => {
            const { primary, secondary } = nextActions(
              r,
              (label, action) => void run(r.return_number, action, label),
              () => {
                setRejectTarget(r.return_number);
                setRejectReason("");
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
                    <p className="text-xs mt-1">
                      Status: <span className="uppercase tracking-wide">{r.status.replace(/_/g, " ")}</span>
                    </p>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {new Date(r.requested_at).toLocaleString("en-IN")}
                  </p>
                </div>
                <p className="text-sm">{r.reason}</p>
                {r.admin_notes ? <p className="text-xs text-muted-foreground whitespace-pre-wrap">{r.admin_notes}</p> : null}
                {r.rejection_reason ? <p className="text-xs text-amber-700">{r.rejection_reason}</p> : null}
                {r.razorpay_refund_id ? (
                  <p className="text-xs text-muted-foreground">Razorpay refund: {r.razorpay_refund_id}</p>
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
      )}
      {!loading && rows.length > 0 && (
        <ListPagination
          page={paging.page}
          totalPages={paging.totalPages}
          totalItems={paging.totalItems}
          pageSize={paging.pageSize}
          scrollToId="admin-returns-list"
          onPageChange={paging.setPage}
        />
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
    </AdminPage>
  );
}
