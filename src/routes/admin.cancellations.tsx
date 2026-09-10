import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import {
  approveCancellationRequest,
  listAdminCancellationRequests,
  rejectCancellationRequest,
  type AdminCancellationRequestRow,
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

export const Route = createFileRoute("/admin/cancellations")({ component: AdminCancellationsPage });

const STATUS_FILTERS = ["", "requested", "approved", "rejected", "withdrawn"] as const;

function AdminCancellationsPage() {
  const [status, setStatus] = useState<string>("requested");
  const [rows, setRows] = useState<AdminCancellationRequestRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [rejectTarget, setRejectTarget] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listAdminCancellationRequests(status || undefined);
      setRows(res.requests ?? []);
    } catch (error) {
      showApiError(error, "Could not load cancellation requests");
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    void load();
  }, [load]);

  const paging = useListPagination(rows, { pageSize: LIST_PAGE_SIZE, resetKey: status });

  const run = async (requestNumber: string, action: () => Promise<unknown>, label: string) => {
    setBusy(requestNumber);
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
    await run(
      rejectTarget,
      () => rejectCancellationRequest(rejectTarget, rejectReason.trim()),
      "Cancellation rejected",
    );
    setRejectTarget(null);
    setRejectReason("");
  };

  return (
    <AdminPage
      eyebrow="Fulfilment"
      title="Cancellation requests"
      description="Review customer cancellation requests before refund and stock release. ExpressBees is not involved in pre-dispatch cancellations."
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
        <p className="text-sm text-muted-foreground">No cancellation requests in this filter.</p>
      ) : (
        <ul id="admin-cancellations-list" className="space-y-4">
          {paging.pageItems.map((r) => (
            <li key={r.request_number} className="glass-strong rounded-2xl p-4 space-y-3">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-medium">{r.request_number}</p>
                  <p className="text-xs text-muted-foreground">
                    Order {r.order_number} · {r.customer_login} · ₹
                    {Number(r.order_total ?? 0).toLocaleString("en-IN")}
                  </p>
                  <p className="text-xs mt-1">
                    Status: <span className="uppercase tracking-wide">{r.status}</span>
                  </p>
                </div>
                <p className="text-xs text-muted-foreground">
                  {new Date(r.requested_at).toLocaleString("en-IN")}
                </p>
              </div>
              <p className="text-sm">{r.reason}</p>
              {r.rejection_reason ? (
                <p className="text-xs text-amber-700">{r.rejection_reason}</p>
              ) : null}
              {r.status === "requested" ? (
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    disabled={busy === r.request_number}
                    className="text-xs rounded-full bg-primary text-primary-foreground px-3 py-1.5"
                    onClick={() =>
                      void run(
                        r.request_number,
                        () => approveCancellationRequest(r.request_number),
                        "Cancellation approved",
                      )
                    }
                  >
                    Approve & cancel order
                  </button>
                  <button
                    type="button"
                    disabled={busy === r.request_number}
                    className="text-xs rounded-full border border-border px-3 py-1.5"
                    onClick={() => {
                      setRejectTarget(r.request_number);
                      setRejectReason("");
                    }}
                  >
                    Reject
                  </button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {!loading && rows.length > 0 && (
        <ListPagination
          page={paging.page}
          totalPages={paging.totalPages}
          totalItems={paging.totalItems}
          pageSize={paging.pageSize}
          scrollToId="admin-cancellations-list"
          onPageChange={paging.setPage}
        />
      )}

      <Dialog open={!!rejectTarget} onOpenChange={(open) => !open && setRejectTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject cancellation</DialogTitle>
          </DialogHeader>
          <textarea
            className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            placeholder="Reason shown to the customer"
          />
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
