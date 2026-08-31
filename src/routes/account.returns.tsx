import { createFileRoute, Link } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { RotateCcw, Loader2, ShieldCheck } from "lucide-react";
import { useAccountData } from "@/lib/account";
import {
  cancelOrderReturn,
  checkReturnEligibility,
  getReturnsConfig,
  listMyOrderReturns,
  requestOrderReturn,
  type OrderReturnRow,
} from "@/lib/orders.functions";
import { showApiError } from "@/lib/ui-errors";
import { formatOrderDate } from "@/lib/dates";
import { toast } from "sonner";
import { ListPagination } from "@/components/ListPagination";
import { useListPagination } from "@/hooks/useListPagination";
import { LIST_PAGE_SIZE } from "@/lib/pagination";

export const Route = createFileRoute("/account/returns")({
  head: () => ({ meta: [{ title: "Your account · Mini's n More" }] }),
  component: ReturnsPage,
});

const TERMINAL_STATUSES = ["rejected", "cancelled", "refunded", "completed"];
const RETURNS_UI_ENABLED = false;

function ReturnsPage() {
  if (!RETURNS_UI_ENABLED) return null;

  const { orders: allOrders, loadingOrders } = useAccountData();
  const [config, setConfig] = useState<{ enabled: boolean; window_days: number } | null>(null);
  const [returns, setReturns] = useState<OrderReturnRow[]>([]);
  const [loadingReturns, setLoadingReturns] = useState(true);
  const [eligibility, setEligibility] = useState<Record<string, { eligible: boolean; message?: string }>>({});
  const [reasonByOrder, setReasonByOrder] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState<string | null>(null);

  useEffect(() => {
    void getReturnsConfig()
      .then(setConfig)
      .catch(() => setConfig({ enabled: true, window_days: 3 }));
  }, []);

  const paidOrders = useMemo(
    () => (allOrders ?? []).filter((o: { payment_status?: string }) => o.payment_status === "paid"),
    [allOrders],
  );

  const returnsPaging = useListPagination(returns, { pageSize: LIST_PAGE_SIZE });
  const paidPaging = useListPagination(paidOrders, { pageSize: LIST_PAGE_SIZE });

  const openReturnByOrder = useMemo(() => {
    const map = new Map<string, OrderReturnRow>();
    for (const r of returns) {
      if (r.order_number && r.request_type === "return" && !TERMINAL_STATUSES.includes(r.status)) {
        map.set(r.order_number, r);
      }
    }
    return map;
  }, [returns]);

  const loadReturns = useCallback(async () => {
    if (config && !config.enabled) {
      setLoadingReturns(false);
      return;
    }
    setLoadingReturns(true);
    try {
      const res = await listMyOrderReturns();
      setReturns(res.returns ?? []);
    } catch (error) {
      showApiError(error, "Could not load return requests");
    } finally {
      setLoadingReturns(false);
    }
  }, [config]);

  useEffect(() => {
    void loadReturns();
  }, [loadReturns]);

  useEffect(() => {
    if (config && !config.enabled) return;
    if (!paidOrders.length) return;
    void (async () => {
      const next: Record<string, { eligible: boolean; message?: string }> = {};
      await Promise.all(
        paidOrders.map(async (o: { order_number: string }) => {
          if (openReturnByOrder.has(o.order_number)) return;
          try {
            const res = await checkReturnEligibility(o.order_number);
            next[o.order_number] = { eligible: res.eligible, message: res.message };
          } catch {
            next[o.order_number] = { eligible: false, message: "Could not check eligibility" };
          }
        }),
      );
      setEligibility((prev) => ({ ...prev, ...next }));
    })();
  }, [paidOrders, openReturnByOrder, config]);

  const submitReturn = async (orderNumber: string) => {
    const reason = (reasonByOrder[orderNumber] ?? "").trim();
    if (reason.length < 10) {
      toast.error("Please describe the issue in at least 10 characters");
      return;
    }
    setSubmitting(orderNumber);
    try {
      await requestOrderReturn({ data: { order_number: orderNumber, reason } });
      toast.success("Return request submitted");
      setReasonByOrder((prev) => ({ ...prev, [orderNumber]: "" }));
      await loadReturns();
    } catch (error) {
      showApiError(error, "Could not submit return request");
    } finally {
      setSubmitting(null);
    }
  };

  const cancelReturn = async (returnNumber: string) => {
    setSubmitting(returnNumber);
    try {
      await cancelOrderReturn(returnNumber);
      toast.success("Return request cancelled");
      await loadReturns();
    } catch (error) {
      showApiError(error, "Could not cancel return");
    } finally {
      setSubmitting(null);
    }
  };

  if (config && !config.enabled) {
    return (
      <div className="space-y-4">
        <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 md:p-8">
          <h1 className="font-display text-3xl mb-1 flex items-center gap-2">
            <RotateCcw className="h-6 w-6 text-primary" /> Returns & refunds
          </h1>
          <p className="text-sm text-muted-foreground">
            Returns & refunds are temporarily unavailable. Please reach out to support if you need help with an order.
          </p>
        </div>
      </div>
    );
  }

  const windowDays = config?.window_days ?? 3;

  return (
    <div className="space-y-4">
      <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 md:p-8">
        <h1 className="font-display text-3xl mb-1 flex items-center gap-2">
          <RotateCcw className="h-6 w-6 text-primary" /> Returns & refunds
        </h1>
        <p className="text-sm text-muted-foreground">
          Eligible within {windowDays} day{windowDays === 1 ? "" : "s"} of delivery. Exchanges depend on stock
          availability — if the size you want isn't available, you can convert to a refund instead. Refunds reach
          you in 5–7 business days after we receive the items.
        </p>
        <div className="mt-4 glass rounded-2xl p-3 inline-flex items-center gap-2 text-xs text-muted-foreground">
          <ShieldCheck className="h-4 w-4 text-primary" /> Free reverse pickup across India.
        </div>
      </div>

      <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 md:p-8">
        <h2 className="font-display text-xl mb-4">Your return &amp; exchange requests</h2>
        {loadingReturns && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
        {!loadingReturns && returns.length === 0 && (
          <div className="text-sm text-muted-foreground">No return or exchange requests yet.</div>
        )}
        <div id="account-returns-list" className="space-y-3">
          {returnsPaging.pageItems.map((r) => (
            <div key={r.return_number} className="glass rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="font-medium">
                  {r.request_type === "exchange" ? "Exchange" : "Return"} · {r.return_number}
                </div>
                <div className="text-xs text-muted-foreground">
                  {r.order_number} · {new Date(r.requested_at).toLocaleDateString("en-IN")} ·{" "}
                  <span className="uppercase tracking-wide">{r.status.replace(/_/g, " ")}</span>
                </div>
                {r.product_name ? (
                  <div className="text-xs text-muted-foreground mt-0.5">
                    {r.product_name}
                    {r.replacement_size || r.replacement_color
                      ? ` → ${[r.replacement_size, r.replacement_color].filter(Boolean).join(" · ")}`
                      : ""}
                  </div>
                ) : null}
                {r.rejection_reason ? (
                  <p className="text-xs text-amber-700 mt-1">{r.rejection_reason}</p>
                ) : null}
              </div>
              {r.status === "requested" ? (
                <button
                  type="button"
                  disabled={submitting === r.return_number}
                  onClick={() => cancelReturn(r.return_number)}
                  className="text-xs rounded-full border border-border px-4 py-2"
                >
                  Cancel request
                </button>
              ) : null}
            </div>
          ))}
        </div>
        <ListPagination
          page={returnsPaging.page}
          totalPages={returnsPaging.totalPages}
          totalItems={returnsPaging.totalItems}
          pageSize={returnsPaging.pageSize}
          scrollToId="account-returns-list"
          onPageChange={returnsPaging.setPage}
        />
      </div>

      <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 md:p-8">
        <h2 className="font-display text-xl mb-4">Eligible orders</h2>
        {loadingOrders && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
        {paidOrders.length === 0 && !loadingOrders && (
          <div className="text-sm text-muted-foreground">No paid orders yet.</div>
        )}
        <div id="account-eligible-orders-list" className="space-y-4">
          {paidPaging.pageItems.map((o: { id: string; order_number: string; created_at: string; total: number }) => {
            const open = openReturnByOrder.get(o.order_number);
            const elig = eligibility[o.order_number];
            return (
              <div key={o.id} className="glass rounded-2xl p-4 space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2 sm:gap-4 min-w-0">
                  <div className="min-w-0">
                    <Link
                      to="/account/orders/$orderNumber"
                      params={{ orderNumber: o.order_number }}
                      className="font-medium story-link"
                    >
                      {o.order_number}
                    </Link>
                    <div className="text-xs text-muted-foreground">
                      {formatOrderDate(o.created_at)}{" "}
                      · ₹{Number(o.total).toLocaleString("en-IN")}
                    </div>
                  </div>
                  {open ? (
                    <span className="text-[10px] uppercase tracking-wider rounded-full px-3 py-1 bg-amber-500/15 text-amber-700">
                      {open.status.replace(/_/g, " ")}
                    </span>
                  ) : elig?.eligible ? (
                    <span className="text-[10px] uppercase tracking-wider rounded-full px-3 py-1 bg-primary/10 text-primary">
                      Eligible
                    </span>
                  ) : (
                    <span className="text-[10px] uppercase tracking-wider rounded-full px-3 py-1 bg-muted text-muted-foreground">
                      Not eligible
                    </span>
                  )}
                </div>
                {!open && elig?.eligible ? (
                  <>
                    <textarea
                      className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
                      placeholder="Why are you returning this order?"
                      value={reasonByOrder[o.order_number] ?? ""}
                      onChange={(e) =>
                        setReasonByOrder((prev) => ({ ...prev, [o.order_number]: e.target.value }))
                      }
                    />
                    <button
                      type="button"
                      disabled={submitting === o.order_number}
                      onClick={() => submitReturn(o.order_number)}
                      className="text-xs rounded-full bg-primary text-primary-foreground px-4 py-2"
                    >
                      {submitting === o.order_number ? "Submitting…" : "Request return"}
                    </button>
                  </>
                ) : null}
                {!open && elig && !elig.eligible && elig.message ? (
                  <p className="text-xs text-muted-foreground">{elig.message}</p>
                ) : null}
              </div>
            );
          })}
        </div>
        <ListPagination
          page={paidPaging.page}
          totalPages={paidPaging.totalPages}
          totalItems={paidPaging.totalItems}
          pageSize={paidPaging.pageSize}
          scrollToId="account-eligible-orders-list"
          onPageChange={paidPaging.setPage}
        />
      </div>
    </div>
  );
}
