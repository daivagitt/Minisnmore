import { createFileRoute, Link } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  cancelOrder,
  syncPaymentStatus,
  retryOrderPayment,
  verifyPayment,
  checkReturnEligibility,
  listMyOrderReturns,
  requestOrderReturn,
  requestOrderExchange,
  type OrderReturnRow,
} from "@/lib/orders.functions";
import { openRazorpayCheckout } from "@/lib/razorpay";
import { formatOrderDate } from "@/lib/dates";
import { formatOrderStatusSummary, formatPaymentStatusLabel } from "@/lib/order-status";
import { useOrderTrackingPoll } from "@/hooks/useOrderTrackingPoll";
import { shouldPollOrderTracking } from "@/lib/order-tracking";
import { useProduct } from "@/hooks/useProducts";
import { defaultSellableSelection } from "@/lib/product-variants";
import { ExchangeReplacementPicker } from "@/components/ExchangeReplacementPicker";
import { showApiError } from "@/lib/ui-errors";
import {
  Loader2,
  ArrowLeft,
  MapPin,
  CreditCard,
  Package2,
  CheckCircle2,
  Clock,
  XCircle,
  Truck,
  Wallet,
  FileText,
  RotateCcw,
} from "lucide-react";
import { toast } from "sonner";
import { LazyImage } from "@/components/LazyImage";
import { CopyButton } from "@/components/CopyButton";
import { OrderTrackingCard } from "@/components/OrderTrackingCard";
import { TrackingRefreshHint } from "@/components/TrackingRefreshHint";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

const OPEN_RETURN_STATUSES = new Set([
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
  "failed",
]);

export const Route = createFileRoute("/account/orders/$orderNumber")({
  validateSearch: (search: Record<string, unknown>) => ({
    pay: search.pay === "1" || search.pay === 1 || search.pay === true,
  }),
  head: ({ params }) => ({ meta: [{ title: `Order ${params.orderNumber} · Mini's n More` }] }),
  component: OrderDetail,
});

function OrderDetail() {
  const { orderNumber } = Route.useParams();
  const { pay: autoPay } = Route.useSearch();
  const [syncing, setSyncing] = useState(false);
  const [paying, setPaying] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const autoPayStarted = useRef(false);
  const { order, loading, error, lastUpdated, polling, refresh } = useOrderTrackingPoll(
    orderNumber,
    "authenticated",
  );

  const [orderReturns, setOrderReturns] = useState<OrderReturnRow[]>([]);
  const [eligibility, setEligibility] = useState<{ eligible: boolean; message?: string } | null>(null);

  const [returnDialogOpen, setReturnDialogOpen] = useState(false);
  const [returnReason, setReturnReason] = useState("");
  const [submittingReturn, setSubmittingReturn] = useState(false);

  const [exchangeItem, setExchangeItem] = useState<{
    id: number;
    product_slug?: string;
    product_name?: string;
    size?: string;
    color?: string;
  } | null>(null);
  const [exchangeSize, setExchangeSize] = useState<string | null>(null);
  const [exchangeColor, setExchangeColor] = useState<string | null>(null);
  const [exchangeReason, setExchangeReason] = useState("");
  const [submittingExchange, setSubmittingExchange] = useState(false);
  const exchangeProduct = useProduct(exchangeItem?.product_slug);

  const loadReturnsInfo = useCallback(async () => {
    if (!orderNumber) return;
    try {
      const [returnsRes, eligRes] = await Promise.all([
        listMyOrderReturns(),
        checkReturnEligibility(orderNumber),
      ]);
      setOrderReturns((returnsRes.returns ?? []).filter((r) => r.order_number === orderNumber));
      setEligibility({ eligible: eligRes.eligible, message: eligRes.message });
    } catch {
      // non-fatal — the returns card just won't render actions
    }
  }, [orderNumber]);

  useEffect(() => {
    void loadReturnsInfo();
  }, [loadReturnsInfo]);

  const openWholeOrderReturn = useMemo(
    () => orderReturns.find((r) => OPEN_RETURN_STATUSES.has(r.status)),
    [orderReturns],
  );

  const openExchangeByItemId = useMemo(() => {
    const map = new Map<number, OrderReturnRow>();
    for (const r of orderReturns) {
      if (r.request_type === "exchange" && r.order_item_id != null && OPEN_RETURN_STATUSES.has(r.status)) {
        map.set(r.order_item_id, r);
      }
    }
    return map;
  }, [orderReturns]);

  const openExchangeDialog = (item: { id: number; product_slug?: string; product_name?: string; size?: string; color?: string }) => {
    setExchangeItem(item);
    setExchangeSize(item.size ?? null);
    setExchangeColor(item.color ?? null);
    setExchangeReason("");
  };

  useEffect(() => {
    if (!exchangeItem || !exchangeProduct) return;
    if (exchangeSize && exchangeColor) return;
    const defaults = defaultSellableSelection(exchangeProduct);
    setExchangeSize((prev) => prev ?? defaults.size);
    setExchangeColor((prev) => prev ?? defaults.color);
  }, [exchangeItem, exchangeProduct, exchangeSize, exchangeColor]);

  const submitReturn = async () => {
    if (!order?.order_number) return;
    if (returnReason.trim().length < 10) {
      toast.error("Please describe the issue in at least 10 characters");
      return;
    }
    setSubmittingReturn(true);
    try {
      await requestOrderReturn({ data: { order_number: order.order_number, reason: returnReason.trim() } });
      toast.success("Return request submitted");
      setReturnDialogOpen(false);
      setReturnReason("");
      await loadReturnsInfo();
    } catch (e) {
      showApiError(e, "Could not submit return request");
    } finally {
      setSubmittingReturn(false);
    }
  };

  const submitExchange = async () => {
    if (!order?.order_number || !exchangeItem) return;
    if (exchangeReason.trim().length < 10) {
      toast.error("Please describe the issue in at least 10 characters");
      return;
    }
    if (!exchangeSize || !exchangeColor) {
      toast.error("Please choose a replacement size and colour");
      return;
    }
    setSubmittingExchange(true);
    try {
      await requestOrderExchange({
        data: {
          order_number: order.order_number,
          order_item_id: exchangeItem.id,
          reason: exchangeReason.trim(),
          replacement_size: exchangeSize,
          replacement_color: exchangeColor,
        },
      });
      toast.success("Exchange request submitted");
      setExchangeItem(null);
      await loadReturnsInfo();
    } catch (e) {
      showApiError(e, "Could not submit exchange request");
    } finally {
      setSubmittingExchange(false);
    }
  };

  const checkPaymentStatus = async () => {
    if (!order?.id) return;
    setSyncing(true);
    try {
      const res = await syncPaymentStatus({ data: { orderId: order.id } });
      if (res.paid) {
        toast.success("Payment confirmed — your order is now placed.");
        await refresh({ silent: true });
      } else {
        toast("Payment not confirmed yet. If money was debited, wait a minute and try again.", {
          icon: "⏳",
        });
      }
    } catch (e: any) {
      toast.error(e?.message || "Could not check payment status");
    } finally {
      setSyncing(false);
    }
  };

  const payNow = useCallback(async () => {
    if (!order?.id) return;
    setPaying(true);
    try {
      const init = await retryOrderPayment({ data: { orderId: order.id } });
      const addr = order.shipping_address || {};

      if (init.demoMode) {
        const label = init.gatewayMode === "test" ? "TEST MODE" : "DEMO MODE";
        const confirmed = window.confirm(
          `🧪 ${label}\n\nAmount: ₹${(init.amount / 100).toLocaleString("en-IN")}\nOrder: ${init.orderNumber}\n\nClick OK to simulate a successful payment, Cancel to simulate cancellation.\n\nNote: Razorpay is using ${init.gatewayMode === "test" ? "test" : "demo"} keys — no real money will be charged.`,
        );
        if (!confirmed) {
          toast("Payment cancelled.", { icon: "🛒" });
          return;
        }
        await verifyPayment({
          data: {
            orderId: init.orderId,
            razorpay_order_id: init.razorpayOrderId,
            razorpay_payment_id: `pay_demo_${Date.now()}`,
            razorpay_signature: "demo_signature",
          },
        });
        toast.success(`Payment successful ✨ (${init.gatewayMode === "test" ? "test" : "demo"})`);
        await refresh({ silent: true });
        return;
      }

      if (init.gatewayMode === "unknown") {
        throw new Error(
          "Razorpay key format is invalid. Please check the configured Key ID and Secret.",
        );
      }

      await openRazorpayCheckout(init, {
        prefill: { name: addr.full_name ?? "", email: addr.email ?? "", contact: addr.phone ?? "" },
        onSuccess: async () => {
          toast.success("Payment successful ✨");
          await refresh({ silent: true });
        },
        onCancelled: () => toast("Payment cancelled — you can retry anytime.", { icon: "🛒" }),
        onFailed: (reason) => toast.error(`${reason}. Please try a different payment method.`),
      });
    } catch (e: any) {
      toast.error(e?.message || "Could not start payment");
    } finally {
      setPaying(false);
    }
  }, [order, refresh]);

  const cancelCurrentOrder = async () => {
    if (!order?.order_number) return;
    const reason = cancelReason.trim();
    if (reason.length < 3) {
      toast.error("Please tell us why you're cancelling");
      return;
    }
    const wasPaid = order.payment_status === "paid";
    setCancelling(true);
    try {
      await cancelOrder(order.order_number, reason);
      setCancelDialogOpen(false);
      setCancelReason("");
      toast.success(
        wasPaid ? "Order cancelled. Your refund has been initiated." : "Order cancelled.",
      );
      await refresh({ silent: true });
    } catch (e: any) {
      toast.error(e?.message || "Could not cancel this order");
    } finally {
      setCancelling(false);
    }
  };

  useEffect(() => {
    if (!autoPay || autoPayStarted.current || !order) return;
    if (
      order.status === "cancelled" ||
      order.payment_status !== "pending" ||
      order.payment_method === "cod"
    )
      return;
    autoPayStarted.current = true;
    void payNow();
  }, [autoPay, order, payNow]);

  if (error && !order) {
    return <div className="glass-strong rounded-[2rem] p-8 text-sm text-destructive">{error}</div>;
  }
  if (!order) {
    return (
      <div className="glass-strong rounded-[2rem] p-12 grid place-items-center">
        <Loader2 className="h-6 w-6 animate-spin text-primary" />
      </div>
    );
  }

  const addr = order.shipping_address || {};
  const itemCount = (order.order_items ?? []).reduce((sum, it) => sum + Number(it.qty ?? 0), 0);
  const StatusIcon =
    order.status === "cancelled"
      ? XCircle
      : order.payment_status === "paid"
        ? CheckCircle2
        : order.payment_status === "failed"
          ? XCircle
          : Clock;

  return (
    <div className="space-y-4">
      <Link
        to="/account/orders"
        className="text-sm text-muted-foreground hover:text-foreground inline-flex items-center gap-1"
      >
        <ArrowLeft className="h-4 w-4" /> Back to orders
      </Link>

      <div className="glass-strong rounded-[2rem] p-6 md:p-8">
        <div className="flex items-start justify-between flex-wrap gap-3">
          <div>
            <div className="text-xs uppercase tracking-[0.28em] text-primary">Order</div>
            <div className="flex items-center gap-2 mt-1">
              <h1 className="font-display text-3xl">{order.order_number}</h1>
              <CopyButton
                value={order.order_number}
                label="Copy order ID"
                title="Copy"
                successMessage="Order ID copied"
                className="h-7 w-7 shrink-0"
                iconClassName="h-3 w-3"
              />
            </div>
            <div className="text-xs text-muted-foreground mt-1">
              Placed{" "}
              {formatOrderDate(order.created_at, {
                day: "numeric",
                month: "long",
                year: "numeric",
              })}
              {shouldPollOrderTracking(order) && (
                <>
                  {" · "}
                  <Link
                    to="/track"
                    search={{ order: order.order_number }}
                    className="text-primary hover:underline"
                  >
                    Open tracking page
                  </Link>
                </>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <div className="inline-flex items-center gap-2 glass rounded-full px-4 py-2 text-sm">
              <StatusIcon className="h-4 w-4 text-primary" />
              <span>{formatOrderStatusSummary(order)}</span>
            </div>
            {order.can_cancel && (
              <button
                type="button"
                onClick={() => setCancelDialogOpen(true)}
                className="rounded-full border border-destructive/30 px-4 py-2 text-sm text-destructive hover:bg-destructive/10 transition"
              >
                Cancel order
              </button>
            )}
          </div>
        </div>
      </div>

      {order.status === "cancelled" && (
        <div className="glass-strong rounded-[2rem] p-5 md:p-6 border border-muted bg-muted/30">
          <div className="flex items-start gap-3">
            <XCircle className="h-5 w-5 text-muted-foreground mt-0.5" />
            <div>
              <div className="text-sm font-medium">This order has been cancelled</div>
              <p className="text-sm text-muted-foreground mt-1">
                {order.payment_status === "refunded"
                  ? "Your refund has been initiated to the original payment method."
                  : "No payment is due for this order."}
              </p>
              {order.cancellation_reason && (
                <p className="text-sm text-muted-foreground mt-1">Reason: {order.cancellation_reason}</p>
              )}
            </div>
          </div>
        </div>
      )}

      {order.status !== "cancelled" &&
        order.payment_status === "pending" &&
        order.payment_method === "cod" && (
          <div className="glass-strong rounded-[2rem] p-5 md:p-6 border border-primary/20 bg-primary/5">
            <div className="text-sm font-medium">Pay on delivery</div>
            <p className="text-sm text-muted-foreground mt-1">
              Your order is confirmed and being prepared. Pay ₹
              {Number(order.total).toLocaleString("en-IN")} in cash or UPI when it arrives.
            </p>
          </div>
        )}

      {order.status !== "cancelled" &&
        order.payment_status === "pending" &&
        order.payment_method !== "cod" && (
          <div className="glass-strong rounded-[2rem] p-5 md:p-6 border border-amber-500/30 bg-amber-500/10">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <div className="text-sm font-medium text-amber-900">Payment pending</div>
                <p className="text-sm text-muted-foreground mt-1">
                  Complete payment of ₹{Number(order.total).toLocaleString("en-IN")} to confirm this
                  order.
                </p>
              </div>
              <button
                type="button"
                onClick={payNow}
                disabled={paying || syncing}
                className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm inline-flex items-center gap-2 disabled:opacity-50"
              >
                {paying ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Wallet className="h-4 w-4" />
                )}
                {paying ? "Opening payment…" : "Pay now"}
              </button>
            </div>
          </div>
        )}

      {order.payment_status === "paid" && (
        <div className="glass-strong rounded-[2rem] p-6 md:p-8">
          <h2 className="font-display text-xl mb-4 flex items-center gap-2">
            <Truck className="h-5 w-5 text-primary" /> Delivery tracking
          </h2>
          <OrderTrackingCard order={order} showHeader={false} />
          <TrackingRefreshHint lastUpdated={lastUpdated} polling={polling} />
        </div>
      )}

      {order.payment_status === "paid" && (
        <div className="glass-strong rounded-[2rem] p-6 md:p-8">
          <h2 className="font-display text-xl mb-3 flex items-center gap-2">
            <RotateCcw className="h-5 w-5 text-primary" /> Returns &amp; exchange
          </h2>
          {orderReturns.length > 0 ? (
            <div className="space-y-2">
              {orderReturns.map((r) => (
                <div key={r.return_number} className="glass rounded-2xl p-3 flex items-center justify-between gap-3">
                  <div className="text-sm">
                    <span className="font-medium">{r.request_type === "exchange" ? "Exchange" : "Return"}</span>{" "}
                    <span className="text-muted-foreground">{r.return_number}</span>
                    {r.product_name ? <span className="text-muted-foreground"> · {r.product_name}</span> : null}
                  </div>
                  <span className="text-[10px] uppercase tracking-wider rounded-full px-3 py-1 bg-amber-500/15 text-amber-700">
                    {r.status.replace(/_/g, " ")}
                  </span>
                </div>
              ))}
              <Link to="/account/returns" className="text-xs text-primary hover:underline">
                Manage your return &amp; exchange requests
              </Link>
            </div>
          ) : eligibility?.eligible ? (
            <button
              type="button"
              onClick={() => setReturnDialogOpen(true)}
              className="text-xs rounded-full bg-primary text-primary-foreground px-4 py-2"
            >
              Start a return
            </button>
          ) : (
            <p className="text-sm text-muted-foreground">
              {eligibility?.message ?? "Returns aren't available for this order right now."}
            </p>
          )}
        </div>
      )}

      <div className="grid lg:grid-cols-[1fr_320px] gap-4">
        <div className="glass-strong rounded-[2rem] p-6 md:p-8">
          <h2 className="font-display text-xl mb-4 flex items-center gap-2">
            <Package2 className="h-5 w-5 text-primary" />
            Items
            <span className="text-muted-foreground font-normal text-base">({itemCount})</span>
          </h2>
          <div className="space-y-3">
            {order.order_items?.map((it: any) => {
              const openExchange = it.id != null ? openExchangeByItemId.get(it.id) : undefined;
              const canExchange =
                order.payment_status === "paid" &&
                eligibility?.eligible &&
                !openWholeOrderReturn &&
                !openExchange &&
                it.id != null;
              return (
                <div key={it.id} className="glass rounded-2xl p-3 flex flex-col gap-2">
                  <div className="flex gap-3 items-center">
                    {it.product_image && (
                      <LazyImage
                        src={it.product_image}
                        className="h-16 w-16 rounded-xl object-cover"
                        alt=""
                        priority="auto"
                      />
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="font-medium truncate">{it.product_name}</div>
                      <div className="text-xs text-muted-foreground">
                        {[it.size, it.color].filter(Boolean).join(" · ")} · ×{it.qty}
                      </div>
                    </div>
                    <div className="text-sm font-medium">
                      ₹{(it.qty * Number(it.price)).toLocaleString("en-IN")}
                    </div>
                  </div>
                  {openExchange ? (
                    <span className="self-start text-[10px] uppercase tracking-wider rounded-full px-3 py-1 bg-amber-500/15 text-amber-700">
                      Exchange {openExchange.status.replace(/_/g, " ")}
                    </span>
                  ) : canExchange ? (
                    <button
                      type="button"
                      onClick={() =>
                        openExchangeDialog({
                          id: it.id,
                          product_slug: it.product_slug,
                          product_name: it.product_name,
                          size: it.size,
                          color: it.color,
                        })
                      }
                      className="self-start text-xs rounded-full border border-border px-3 py-1.5 hover:bg-white/60 transition"
                    >
                      Request exchange
                    </button>
                  ) : null}
                </div>
              );
            })}
          </div>

          <div className="grid sm:grid-cols-2 gap-4 mt-6">
            <div className="glass rounded-2xl p-4">
              <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted-foreground mb-2">
                <MapPin className="h-4 w-4 text-primary" /> Shipping address
              </div>
              <div className="text-sm font-medium">{addr.full_name}</div>
              <div className="text-sm text-muted-foreground">
                {addr.line1}
                {addr.line2 ? `, ${addr.line2}` : ""}
              </div>
              <div className="text-sm text-muted-foreground">
                {addr.city}, {addr.state} {addr.pincode}
              </div>
              <div className="text-xs text-muted-foreground mt-1">
                {addr.phone} · {addr.email}
              </div>
            </div>
            <div className="glass rounded-2xl p-4">
              <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted-foreground mb-2">
                <CreditCard className="h-4 w-4 text-primary" /> Payment
              </div>
              <div className="text-sm font-medium">
                {order.status === "cancelled" && order.payment_status !== "refunded"
                  ? "No payment due"
                  : formatPaymentStatusLabel(order.payment_status, order.payment_method)}
              </div>
              {order.status !== "cancelled" &&
                order.payment_method === "cod" &&
                order.payment_status === "pending" && (
                  <div className="text-xs text-muted-foreground mt-1">Cash or UPI at doorstep</div>
                )}
              {order.razorpay_payment_id && (
                <div className="text-xs text-muted-foreground break-all">
                  ID: {order.razorpay_payment_id}
                </div>
              )}
              {order.status !== "cancelled" &&
                order.payment_status === "pending" &&
                order.payment_method !== "cod" && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={payNow}
                      disabled={paying || syncing}
                      className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-xs inline-flex items-center gap-2 disabled:opacity-50"
                    >
                      {paying ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <Wallet className="h-3.5 w-3.5" />
                      )}
                      {paying ? "Opening payment…" : "Pay now"}
                    </button>
                    <button
                      type="button"
                      onClick={checkPaymentStatus}
                      disabled={syncing || paying}
                      className="rounded-full glass px-4 py-2 text-xs inline-flex items-center gap-2 disabled:opacity-50 hover:bg-white/60 transition"
                    >
                      {syncing && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                      {syncing ? "Checking…" : "Already paid? Refresh status"}
                    </button>
                  </div>
                )}
              {order.coupon_code && (
                <div className="text-xs text-primary mt-1">Coupon: {order.coupon_code}</div>
              )}
              {order.invoice_pdf_url && (
                <a
                  href={order.invoice_pdf_url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-3 rounded-full glass px-4 py-2 text-xs inline-flex items-center gap-2 hover:bg-white/60 transition"
                >
                  <FileText className="h-3.5 w-3.5" /> View invoice
                </a>
              )}
            </div>
          </div>
        </div>

        <aside className="glass-strong rounded-[2rem] p-6 h-fit space-y-1.5 text-sm">
          <div className="font-display text-xl mb-3">Summary</div>
          <Row label="Subtotal" value={`₹${Number(order.subtotal).toLocaleString("en-IN")}`} />
          {Number(order.discount) > 0 && (
            <Row
              label="Discount"
              value={`− ₹${Number(order.discount).toLocaleString("en-IN")}`}
              accent
            />
          )}
          <Row
            label="Shipping"
            value={
              Number(order.shipping) === 0
                ? "Free"
                : `₹${Number(order.shipping).toLocaleString("en-IN")}`
            }
            muted
          />
          {Number(order.cod_fee) > 0 && (
            <Row
              label="COD handling fee"
              value={`₹${Number(order.cod_fee).toLocaleString("en-IN")}`}
              muted
            />
          )}
          <Row label="GST included" value={`₹${Number(order.tax).toLocaleString("en-IN")}`} muted />
          {Number(order.donation) > 0 && (
            <Row
              label="Donation ♥"
              value={`₹${Number(order.donation).toLocaleString("en-IN")}`}
              accent
            />
          )}
          <div className="border-t border-border/60 mt-2 pt-2 flex justify-between font-display text-lg">
            <span>Total</span>
            <span>₹{Number(order.total).toLocaleString("en-IN")}</span>
          </div>
        </aside>
      </div>

      {loading && (
        <p className="text-[11px] text-muted-foreground text-center">Refreshing order details…</p>
      )}

      <AlertDialog
        open={cancelDialogOpen}
        onOpenChange={(open) => {
          setCancelDialogOpen(open);
          if (!open) setCancelReason("");
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Cancel this order?</AlertDialogTitle>
            <AlertDialogDescription>
              This cannot be undone. The items will be released back to stock
              {order.payment_status === "paid" &&
                " and your payment will be refunded to the original method"}
              .
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="space-y-1.5">
            <label htmlFor="cancel-reason" className="text-sm font-medium">
              Why are you cancelling?
            </label>
            <textarea
              id="cancel-reason"
              className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
              placeholder="Let us know why you're cancelling this order"
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              disabled={cancelling}
            />
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={cancelling}>Keep order</AlertDialogCancel>
            <AlertDialogAction
              onClick={(event) => {
                event.preventDefault();
                void cancelCurrentOrder();
              }}
              disabled={cancelling || cancelReason.trim().length < 3}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {cancelling && <Loader2 className="h-4 w-4 animate-spin" />}
              {cancelling ? "Cancelling…" : "Yes, cancel order"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog
        open={returnDialogOpen}
        onOpenChange={(open) => {
          setReturnDialogOpen(open);
          if (!open) setReturnReason("");
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Request a return</DialogTitle>
          </DialogHeader>
          <div className="space-y-1.5">
            <label htmlFor="return-reason" className="text-sm font-medium">
              Why are you returning this order?
            </label>
            <textarea
              id="return-reason"
              className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
              placeholder="Tell us what went wrong"
              value={returnReason}
              onChange={(e) => setReturnReason(e.target.value)}
              disabled={submittingReturn}
            />
          </div>
          <DialogFooter>
            <button
              type="button"
              onClick={submitReturn}
              disabled={submittingReturn || returnReason.trim().length < 10}
              className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              {submittingReturn ? "Submitting…" : "Submit return request"}
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!exchangeItem} onOpenChange={(open) => !open && setExchangeItem(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Exchange {exchangeItem?.product_name ?? "item"}</DialogTitle>
          </DialogHeader>
          <ExchangeReplacementPicker
            product={exchangeProduct}
            originalSize={exchangeItem?.size}
            originalColor={exchangeItem?.color}
            size={exchangeSize}
            color={exchangeColor}
            onSizeChange={setExchangeSize}
            onColorChange={setExchangeColor}
          />
          <div className="space-y-1.5">
            <label htmlFor="exchange-reason" className="text-sm font-medium">
              Why are you exchanging this item?
            </label>
            <textarea
              id="exchange-reason"
              className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
              placeholder="Tell us what went wrong"
              value={exchangeReason}
              onChange={(e) => setExchangeReason(e.target.value)}
              disabled={submittingExchange}
            />
          </div>
          <DialogFooter>
            <button
              type="button"
              onClick={submitExchange}
              disabled={submittingExchange || exchangeReason.trim().length < 10 || !exchangeSize || !exchangeColor}
              className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              {submittingExchange ? "Submitting…" : "Submit exchange request"}
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Row({
  label,
  value,
  muted,
  accent,
}: {
  label: string;
  value: string;
  muted?: boolean;
  accent?: boolean;
}) {
  return (
    <div
      className={`flex justify-between ${muted ? "text-muted-foreground" : ""} ${accent ? "text-primary" : ""}`}
    >
      <span>{label}</span>
      <span>{value}</span>
    </div>
  );
}
