import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { AdminCaseSearchFields } from "@/components/admin/AdminCaseSearchFields";
import {
  AdminCaseListHead,
  AdminCaseListTable,
  AdminCaseTd,
  adminCaseRowClass,
} from "@/components/admin/AdminCaseListTable";
import { SHOW_M2_POST_ORDER_UI } from "@/lib/feature-flags";
import {
  adminCaseListFetchStatus,
  adminCaseSearchActive,
  matchesLocalDateRange,
  matchesOrderQuery,
} from "@/lib/admin-case-search";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import {
  approveCancellationRequest,
  listAdminCancellationRequests,
  recordCancellationCodRefund,
  rejectCancellationRequest,
  retryCancellationRefund,
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
import { formatCaseAge } from "@/lib/admin-case-format";

export const Route = createFileRoute("/admin/cancellations")({ component: AdminCancellationsPage });

const STATUS_FILTERS = ["", "requested", "approved", "rejected", "withdrawn"] as const;

export function AdminCancellationsPage({ mode = "admin" }: { mode?: "admin" | "operations" | "support" } = {}) {
  if (!SHOW_M2_POST_ORDER_UI) return null;
  return <AdminCancellationsPageContent mode={mode} />;
}

function AdminCancellationsPageContent({ mode = "admin" }: { mode?: "admin" | "operations" | "support" } = {}) {
  const canFulfill = mode !== "support";
  const eyebrow = mode === "support" ? "Customer Support" : mode === "operations" ? "Operations" : "Fulfilment";

  const [status, setStatus] = useState<string>("requested");
  const [rows, setRows] = useState<AdminCancellationRequestRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [rejectTarget, setRejectTarget] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [codRefundTarget, setCodRefundTarget] = useState<string | null>(null);
  const [codRefundMethod, setCodRefundMethod] = useState<"upi" | "bank_transfer" | "cash" | "other">("upi");
  const [codRefundReference, setCodRefundReference] = useState("");
  const [orderQueryInput, setOrderQueryInput] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const hasRowsRef = useRef(false);
  const loadGenRef = useRef(0);
  const searchActive = adminCaseSearchActive(orderQueryInput, dateFrom, dateTo);
  hasRowsRef.current = rows.length > 0;

  const load = useCallback(async () => {
    const gen = ++loadGenRef.current;
    const keepList = hasRowsRef.current;
    if (!keepList) setLoading(true);
    try {
      const res = await listAdminCancellationRequests(adminCaseListFetchStatus(searchActive, status));
      if (gen !== loadGenRef.current) return;
      setRows(res.requests ?? []);
    } catch (error) {
      if (gen !== loadGenRef.current) return;
      showApiError(error, "Could not load cancellation requests");
    } finally {
      if (gen === loadGenRef.current) setLoading(false);
    }
  }, [searchActive, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const filteredRows = useMemo(() => {
    return rows.filter(
      (row) =>
        matchesOrderQuery(orderQueryInput, { orderNumber: row.order_number, orderId: row.order_id }) &&
        matchesLocalDateRange(row.requested_at, dateFrom, dateTo),
    );
  }, [rows, orderQueryInput, dateFrom, dateTo]);

  const paging = useListPagination(filteredRows, {
    pageSize: LIST_PAGE_SIZE,
    resetKey: `${status}|${orderQueryInput}|${dateFrom}|${dateTo}`,
  });
  const filtersActive = status !== "requested" || adminCaseSearchActive(orderQueryInput, dateFrom, dateTo);

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

  const submitCodRefund = async () => {
    if (!codRefundTarget || codRefundReference.trim().length < 4) {
      toast.error("Refund reference must be at least 4 characters");
      return;
    }
    await run(
      codRefundTarget,
      () =>
        recordCancellationCodRefund(codRefundTarget, {
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
      title="Cancellation requests"
      description="Review customer cancellation requests before refund and stock release. ExpressBees is not involved in pre-dispatch cancellations."
    >
      <section className="glass-strong mb-5 space-y-4 rounded-3xl p-4 sm:p-5" aria-label="Filter cancellations">
        <div className="flex flex-wrap gap-2">
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
        <AdminCaseSearchFields
          orderQuery={orderQueryInput}
          onOrderQueryChange={setOrderQueryInput}
          from={dateFrom}
          to={dateTo}
          onFromChange={setDateFrom}
          onToChange={setDateTo}
        />
        <div className="flex flex-wrap items-end gap-3">
          {filtersActive ? (
            <button
              type="button"
              onClick={() => {
                setStatus("requested");
                setOrderQueryInput("");
                setDateFrom("");
                setDateTo("");
              }}
              className="inline-flex h-10 items-center rounded-xl border border-border bg-background px-3 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              Clear
            </button>
          ) : null}
          {!loading && (
            <p className="ml-auto text-xs text-muted-foreground" aria-live="polite">
              {paging.totalItems === 0
                ? "0 requests"
                : paging.totalPages > 1
                  ? `Showing ${paging.rangeStart}–${paging.rangeEnd} of ${paging.totalItems}`
                  : `${paging.totalItems} request${paging.totalItems === 1 ? "" : "s"}`}
            </p>
          )}
        </div>
      </section>

      {loading ? (
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      ) : filteredRows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No cancellation requests in this filter.</p>
      ) : (
        <AdminCaseListTable id="admin-cancellations-list" caption="Cancellation requests">
          <AdminCaseListHead
            columns={["Request", "Order ID", "Customer", "Amount", "Status", "Requested", "Reason", "Actions"]}
          />
          <tbody>
            {paging.pageItems.map((r) => (
              <tr key={r.request_number} className={adminCaseRowClass}>
                <AdminCaseTd className="font-medium whitespace-nowrap">{r.request_number}</AdminCaseTd>
                <AdminCaseTd className="whitespace-nowrap font-medium">{r.order_number ?? "—"}</AdminCaseTd>
                <AdminCaseTd className="whitespace-nowrap">{r.customer_login || "—"}</AdminCaseTd>
                <AdminCaseTd className="whitespace-nowrap tabular-nums">
                  ₹{Number(r.order_total ?? 0).toLocaleString("en-IN")}
                </AdminCaseTd>
                <AdminCaseTd>
                  <p className="uppercase tracking-wide text-xs font-semibold">{r.status}</p>
                  <p className={r.sla_overdue ? "text-xs text-amber-700 font-medium" : "text-xs text-muted-foreground"}>
                    {formatCaseAge(r)}
                  </p>
                  {r.cod_refund_pending ? <p className="text-xs text-amber-700">COD refund pending</p> : null}
                  {r.payment_status === "refund_failed" ? (
                    <p className="text-xs text-destructive">Refund failed</p>
                  ) : null}
                </AdminCaseTd>
                <AdminCaseTd className="whitespace-nowrap text-xs text-muted-foreground">
                  {new Date(r.requested_at).toLocaleString("en-IN")}
                </AdminCaseTd>
                <AdminCaseTd className="max-w-[16rem]">
                  <p className="line-clamp-2" title={r.reason}>{r.reason}</p>
                  {r.manual_refund_reference ? (
                    <p className="mt-1 text-xs text-muted-foreground">
                      COD refund: {r.manual_refund_method?.replace(/_/g, " ")} · {r.manual_refund_reference}
                      {r.manual_refund_recorded_by ? ` · by ${r.manual_refund_recorded_by}` : ""}
                    </p>
                  ) : null}
                  {r.rejection_reason ? <p className="mt-1 text-xs text-amber-700">{r.rejection_reason}</p> : null}
                </AdminCaseTd>
                <AdminCaseTd className="min-w-[11rem]">
                  <div className="flex flex-wrap gap-2">
                    {canFulfill && r.status === "approved" && r.payment_status === "refund_failed" ? (
                      <button
                        type="button"
                        disabled={busy === r.request_number}
                        className="text-xs rounded-full bg-primary text-primary-foreground px-3 py-1.5"
                        onClick={() =>
                          void run(
                            r.request_number,
                            () => retryCancellationRefund(r.request_number),
                            "Refund retry initiated",
                          )
                        }
                      >
                        Retry refund
                      </button>
                    ) : null}
                    {canFulfill && r.cod_refund_pending ? (
                      <button
                        type="button"
                        disabled={busy === r.request_number}
                        className="text-xs rounded-full bg-primary text-primary-foreground px-3 py-1.5"
                        onClick={() => {
                          setCodRefundTarget(r.request_number);
                          setCodRefundMethod("upi");
                          setCodRefundReference("");
                        }}
                      >
                        Record COD refund
                      </button>
                    ) : null}
                    {r.status === "requested" ? (
                      <>
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
                      </>
                    ) : null}
                  </div>
                </AdminCaseTd>
              </tr>
            ))}
          </tbody>
        </AdminCaseListTable>
      )}
      {!loading && filteredRows.length > 0 && (
        <ListPagination
          page={paging.page}
          totalPages={paging.totalPages}
          totalItems={paging.totalItems}
          pageSize={paging.pageSize}
          scrollToId="admin-cancellations-list"
          onPageChange={paging.setPage}
        />
      )}

      <Dialog open={!!codRefundTarget} onOpenChange={(open) => !open && setCodRefundTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Record collected COD refund</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            The order was cancelled after COD collection. Record the refund channel and payment reference for finance audit.
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
