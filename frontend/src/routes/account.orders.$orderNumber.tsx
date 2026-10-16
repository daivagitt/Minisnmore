import { parseOrderPaymentSearch } from "@/lib/order-payment-search";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  cancelOrder,
  withdrawCancelRequest,
  syncPaymentStatus,
  retryOrderPayment,
  verifyPayment,
  checkReturnEligibility,
  listMyOrderReturns,
  requestOrderReturn,
  requestOrderExchange,
  type OrderReturnRow,
  type ReturnEligibility,
} from "@/lib/orders.functions";
import { openRazorpayCheckout } from "@/lib/razorpay";
import { isInvoiceAvailable } from "@/lib/invoice-availability";
import { formatOrderDate } from "@/lib/dates";
import {
  formatOrderStatusSummary,
  formatAccountOrderPaymentLabel,
  isCancelledOrder,
  isCodCancelledUncollected,
  isOnlineRefundAwaitingBank,
  isOnlineRefundFailed,
  isOnlineRefundProcessedAwaitingBank,
  isPrepaidCancelledPaymentOnlySummary,
  shouldShowAccountDeliveryTracking,
  shouldShowContactCareBanner,
} from "@/lib/order-status";
import { refundProcessedMessage } from "@/lib/refund-policy";
import { useOrderTracking } from "@/hooks/useOrderTracking";
import { shouldShowOrderTrackingLink, trackingStatusLabel } from "@/lib/order-tracking";
import {
  closedReturnMessage,
  isOpenReturnForOrderUi,
  isRejectedReturn,
  returnStatusLabel,
} from "@/lib/return-tracking";
import { useProduct } from "@/hooks/useProducts";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { defaultSellableSelection } from "@/lib/product-variants";
import { ExchangeReplacementPicker } from "@/components/ExchangeReplacementPicker";
import { ReturnConditionNotice } from "@/components/ReturnConditionNotice";
import { ReturnImageUpload } from "@/components/ReturnImageUpload";
import { showApiError } from "@/lib/ui-errors";
import {
  Loader2,
  ArrowLeft,
  ArrowLeftRight,
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
import { OrderProductThumb } from "@/components/OrderProductThumb";
import { CopyButton } from "@/components/CopyButton";
import { OrderTrackingCard } from "@/components/OrderTrackingCard";
import { ReturnTrackingCard } from "@/components/ReturnTrackingCard";
import { RefundFailedNotice, RefundInitiatedNotice } from "@/components/RefundInitiatedNotice";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
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
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { SHOW_M2_POST_ORDER_UI } from "@/lib/feature-flags";

export const Route = createFileRoute("/account/orders/$orderNumber")({
  validateSearch: parseOrderPaymentSearch,
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
  const { order, loading, error, refresh } = useOrderTracking(
    orderNumber,
    "authenticated",
  );
  const settings = useSiteSettings();
  const refundTimeline = {
    min: settings.refund_min_business_days,
    max: settings.refund_max_business_days,
  };

  const [orderReturns, setOrderReturns] = useState<OrderReturnRow[]>([]);
  const [returnsLoadError, setReturnsLoadError] = useState(false);
  const [eligibilityLoadError, setEligibilityLoadError] = useState(false);
  const [eligibility, setEligibility] = useState<ReturnEligibility | null>(null);
  const returnsLoadGen = useRef(0);
  const hasFailedReturnRefund = orderReturns.some((row) => row.status === "failed");

  const [returnDialogOpen, setReturnDialogOpen] = useState(false);
  const [returnReason, setReturnReason] = useState("");
  const [returnQuantities, setReturnQuantities] = useState<Record<number, number>>({});
  const [returnImages, setReturnImages] = useState<string[]>([]);
  const [returnImagesUploading, setReturnImagesUploading] = useState(false);
  const [submittingReturn, setSubmittingReturn] = useState(false);

  const [exchangeItem, setExchangeItem] = useState<{
    id: number;
    product_slug?: string;
    product_name?: string;
    product_image?: string;
    size?: string;
    color?: string;
    qty?: number;
    price?: number;
  } | null>(null);
  const [exchangeSize, setExchangeSize] = useState<string | null>(null);
  const [exchangeColor, setExchangeColor] = useState<string | null>(null);
  const [exchangeReason, setExchangeReason] = useState("");
  const [exchangeImages, setExchangeImages] = useState<string[]>([]);
  const [exchangeImagesUploading, setExchangeImagesUploading] = useState(false);
  const [submittingExchange, setSubmittingExchange] = useState(false);
  const exchangeProduct = useProduct(exchangeItem?.product_slug);

  const loadReturnsInfo = useCallback(async () => {
    if (!SHOW_M2_POST_ORDER_UI || !orderNumber) return;
    const gen = returnsLoadGen.current;
    setReturnsLoadError(false);
    setEligibilityLoadError(false);
    const [returnsResult, eligResult] = await Promise.allSettled([
      listMyOrderReturns(),
      checkReturnEligibility(orderNumber),
    ]);
    if (gen !== returnsLoadGen.current) return;
    if (returnsResult.status === "fulfilled") {
      setOrderReturns((returnsResult.value.returns ?? []).filter((r) => r.order_number === orderNumber));
    } else {
      setOrderReturns([]);
      setReturnsLoadError(true);
    }
    if (eligResult.status === "fulfilled") {
      setEligibility(eligResult.value);
    } else {
      setEligibility(null);
      setEligibilityLoadError(true);
    }
  }, [orderNumber]);

  useEffect(() => {
    returnsLoadGen.current += 1;
    setOrderReturns([]);
    setReturnsLoadError(false);
    setEligibilityLoadError(false);
    setEligibility(null);
  }, [orderNumber]);

  useEffect(() => {
    void loadReturnsInfo();
  }, [loadReturnsInfo]);

  const openWholeOrderReturn = useMemo(
    () =>
      orderReturns.find(
        (r) => r.request_type === "return" && isOpenReturnForOrderUi(r),
      ),
    [orderReturns],
  );

  const openExchangeByItemId = useMemo(() => {
    const map = new Map<number, OrderReturnRow>();
    for (const r of orderReturns) {
      if (r.request_type === "exchange" && r.order_item_id != null && isOpenReturnForOrderUi(r)) {
        map.set(r.order_item_id, r);
      }
    }
    return map;
  }, [orderReturns]);

  const openReturnDialog = (focusItemId?: number) => {
    setReturnQuantities(
      Object.fromEntries(
        (order?.order_items ?? []).map((item) => [
          item.id,
          focusItemId == null || item.id === focusItemId ? Number(item.qty ?? 1) : 0,
        ]),
      ),
    );
    setReturnReason("");
    setReturnImages([]);
    setReturnDialogOpen(true);
  };

  const openExchangeDialog = (item: {
    id: number;
    product_slug?: string;
    product_name?: string;
    product_image?: string;
    size?: string;
    color?: string;
    qty?: number;
    price?: number;
  }) => {
    setExchangeItem(item);
    setExchangeSize(item.size ?? null);
    setExchangeColor(item.color ?? null);
    setExchangeReason("");
    setExchangeImages([]);
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
    const items = (order.order_items ?? [])
      .flatMap((item) => item.id == null ? [] : [{ order_item_id: item.id, quantity: returnQuantities[item.id] ?? 0 }])
      .filter((item) => item.quantity > 0);
    if (items.length === 0) {
      toast.error("Select at least one item to return");
      return;
    }
    setSubmittingReturn(true);
    try {
      await requestOrderReturn({
        data: { order_number: order.order_number, reason: returnReason.trim(), items, image_urls: returnImages },
      });
      toast.success("Return request submitted");
      setReturnDialogOpen(false);
      setReturnReason("");
      setReturnImages([]);
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
          image_urls: exchangeImages,
        },
      });
      toast.success("Exchange request submitted");
      setExchangeItem(null);
      setExchangeImages([]);
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
    setCancelling(true);
    try {
      await cancelOrder(order.order_number, reason);
      setCancelDialogOpen(false);
      setCancelReason("");
      toast.success("Cancellation request submitted. We'll review it shortly.");
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
      isCancelledOrder(order) ||
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

  const paymentOrder = { ...order, has_failed_refund: hasFailedReturnRefund || undefined };
  const addr = order.shipping_address || {};
  const itemCount = (order.order_items ?? []).reduce((sum, it) => sum + Number(it.qty ?? 0), 0);
  const StatusIcon = isCancelledOrder(order)
    ? XCircle
    : order.payment_status === "paid"
        ? CheckCircle2
        : order.payment_status === "failed"
          ? XCircle
          : Clock;

  return (
    <div className="space-y-3 sm:space-y-4 min-w-0">
      <Link
        to="/account/orders"
        className="text-sm text-muted-foreground hover:text-foreground inline-flex items-center gap-1"
      >
        <ArrowLeft className="h-4 w-4" /> Back to orders
      </Link>

      <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 md:p-8 min-w-0">
        <div className="flex items-start justify-between flex-wrap gap-3">
          <div className="min-w-0">
            <div className="text-xs uppercase tracking-[0.28em] text-primary">Order</div>
            <div className="flex items-center gap-2 mt-1 min-w-0">
              <h1 className="font-display text-xl sm:text-3xl break-all">{order.order_number}</h1>
              <CopyButton
                value={order.order_number ?? orderNumber}
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
              {shouldShowOrderTrackingLink(order) && (
                <>
                  {" · "}
                  <Link
                    to="/track"
                    search={{ order: order.order_number ?? orderNumber }}
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
              <span>{formatOrderStatusSummary(paymentOrder)}</span>
            </div>
          </div>
        </div>
      </div>

      {SHOW_M2_POST_ORDER_UI && order.can_cancel && (
        <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-5 md:p-6 border-2 border-destructive/50 bg-destructive/10">
          <div className="flex flex-col sm:flex-row sm:flex-wrap sm:items-center justify-between gap-3 sm:gap-4">
            <div className="min-w-0">
              <div className="text-sm font-semibold text-destructive flex items-center gap-2">
                <XCircle className="h-4 w-4 shrink-0" />
                Need to cancel this order?
              </div>
              <p className="text-sm text-muted-foreground mt-1">
                Submit a cancellation request before a shipment is booked. Our team will review it before processing any refund.
              </p>
            </div>
            <button
              type="button"
              onClick={() => setCancelDialogOpen(true)}
              className="rounded-full bg-destructive text-destructive-foreground px-5 py-2.5 text-sm font-medium inline-flex items-center justify-center gap-2 hover:bg-destructive/90 transition shadow-sm w-full sm:w-auto shrink-0"
            >
              <XCircle className="h-4 w-4" />
              Request cancellation
            </button>
          </div>
        </div>
      )}

      {(shouldShowContactCareBanner(order, { m2PostOrderUi: SHOW_M2_POST_ORDER_UI })) && (
        <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-5 md:p-6 border border-border bg-muted/40">
          <div className="flex flex-col sm:flex-row sm:flex-wrap sm:items-center justify-between gap-3 sm:gap-4">
            <div className="min-w-0">
              <div className="text-sm font-semibold flex items-center gap-2">
                <XCircle className="h-4 w-4 shrink-0" />
                Need to cancel this order?
              </div>
              <p className="text-sm text-muted-foreground mt-1">Please contact Customer Care</p>
            </div>
            <Link
              to="/contact-us"
              className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm font-medium inline-flex items-center justify-center hover:bg-primary/90 transition shadow-sm w-full sm:w-auto shrink-0"
            >
              Contact Customer Care
            </Link>
          </div>
        </div>
      )}

      {SHOW_M2_POST_ORDER_UI && order.pending_cancellation && order.cancellation_request?.status === "requested" && (
        <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-5 md:p-6 border border-amber-500/30 bg-amber-500/10">
          <div className="flex flex-col sm:flex-row sm:flex-wrap sm:items-center justify-between gap-3 sm:gap-4">
            <div className="min-w-0">
              <div className="text-sm font-medium text-amber-900">Cancellation requested</div>
              <p className="text-sm text-muted-foreground mt-1">
                Your request is pending admin review. Fulfillment is on hold until we approve or reject it.
              </p>
              {order.cancellation_request?.reason ? (
                <p className="text-sm text-muted-foreground mt-1">
                  Reason: {order.cancellation_request.reason}
                </p>
              ) : null}
            </div>
            <button
              type="button"
              onClick={async () => {
                try {
                  await withdrawCancelRequest(order.order_number ?? orderNumber);
                  toast.success("Cancellation request withdrawn");
                  await refresh({ silent: true });
                } catch (e: any) {
                  toast.error(e?.message || "Could not withdraw request");
                }
              }}
              className="rounded-full border border-border px-5 py-2.5 text-sm w-full sm:w-auto shrink-0"
            >
              Withdraw request
            </button>
          </div>
        </div>
      )}

      {SHOW_M2_POST_ORDER_UI && order.cancellation_request?.status === "rejected" && !isCancelledOrder(order) && (
        <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-5 md:p-6 border border-muted bg-muted/30">
          <div className="text-sm font-medium">Cancellation request declined</div>
          <p className="text-sm text-muted-foreground mt-1">
            {order.cancellation_request.rejection_reason?.trim()
              ? `Reason: ${order.cancellation_request.rejection_reason.trim()}`
              : "We could not approve this cancellation request."}
          </p>
        </div>
      )}

      {isCancelledOrder(order) && (
        <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-5 md:p-6 border border-muted bg-muted/30">
          <div className="flex items-start gap-3">
            <XCircle className="h-5 w-5 text-muted-foreground mt-0.5" />
            <div>
              <div className="text-sm font-medium">This order has been cancelled</div>
              {isOnlineRefundFailed(paymentOrder) ? (
                <RefundFailedNotice className="mt-3" />
              ) : isPrepaidCancelledPaymentOnlySummary(order) ? (
                <RefundInitiatedNotice
                  className="mt-3"
                  orderPaymentStatus={order.payment_status}
                  timeline={refundTimeline}
                />
              ) : isCodCancelledUncollected(order) ? null : (
                <p className="text-sm text-muted-foreground mt-1">No payment is due for this order.</p>
              )}
              {order.cancellation_reason && (
                <p className="text-sm text-muted-foreground mt-1">Reason: {order.cancellation_reason}</p>
              )}
            </div>
          </div>
        </div>
      )}

      {!isCancelledOrder(order) &&
        order.payment_status === "pending" &&
        order.payment_method === "cod" && (
          <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-5 md:p-6 border border-primary/20 bg-primary/5">
            <div className="text-sm font-medium">Pay on delivery</div>
            <p className="text-sm text-muted-foreground mt-1">
              Your order is confirmed and being prepared. Pay ₹
              {Number(order.total).toLocaleString("en-IN")} in cash or UPI when it arrives.
            </p>
          </div>
        )}

      {!isCancelledOrder(order) && isOnlineRefundFailed(paymentOrder) && <RefundFailedNotice />}

      {!isCancelledOrder(order) && isOnlineRefundAwaitingBank(order) && !isOnlineRefundFailed(paymentOrder) && (
        <RefundInitiatedNotice orderPaymentStatus={order.payment_status} timeline={refundTimeline} />
      )}

      {!isCancelledOrder(order) &&
        order.payment_status === "pending" &&
        order.payment_method !== "cod" && (
          <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-5 md:p-6 border border-amber-500/30 bg-amber-500/10">
            <div className="flex flex-col sm:flex-row sm:flex-wrap sm:items-center justify-between gap-3 sm:gap-4">
              <div className="min-w-0">
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
                className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm inline-flex items-center justify-center gap-2 disabled:opacity-50 w-full sm:w-auto"
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

      {shouldShowAccountDeliveryTracking(order) && (
        <Accordion
          type="single"
          collapsible
          className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 md:p-8"
        >
          <AccordionItem value="delivery" className="border-0">
            <AccordionTrigger className="py-1 hover:no-underline">
              <span className="flex min-w-0 flex-1 items-center gap-2 pr-2 text-left">
                <Truck className="h-5 w-5 text-primary shrink-0" />
                <span className="font-display text-lg sm:text-xl">Delivery tracking</span>
                <span className="ml-auto shrink-0 text-[10px] uppercase tracking-wider rounded-full px-3 py-1 bg-amber-500/15 text-amber-700">
                  {trackingStatusLabel(order)}
                </span>
              </span>
            </AccordionTrigger>
            <AccordionContent className="pt-4 pb-0">
              <OrderTrackingCard order={order} showHeader={false} />
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      )}

      {SHOW_M2_POST_ORDER_UI && shouldShowAccountDeliveryTracking(order) && (
        <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 md:p-8">
          <h2 className="font-display text-lg sm:text-xl mb-3 flex items-center gap-2">
            <RotateCcw className="h-5 w-5 text-primary shrink-0" /> Returns &amp; exchanges
          </h2>
          {returnsLoadError ? (
            <p className="text-sm text-amber-800 mb-3">
              We couldn&apos;t load your return history right now. Refresh the page or visit{" "}
              <Link to="/account/returns" className="text-primary hover:underline">
                Returns &amp; exchanges
              </Link>{" "}
              to see open requests before starting a new one.
            </p>
          ) : null}
          {eligibilityLoadError && !returnsLoadError ? (
            <p className="text-sm text-amber-800 mb-3">
              We couldn&apos;t confirm return eligibility right now. Refresh the page before starting a return or
              exchange.
            </p>
          ) : null}
          {orderReturns.length > 0 ? (
            <div className="space-y-3">
              <Accordion type="multiple" className="space-y-2">
                {orderReturns.map((r) => (
                  <AccordionItem
                    key={r.return_number}
                    value={r.return_number}
                    className="glass rounded-2xl border-0 px-3 sm:px-4"
                  >
                    <AccordionTrigger className="py-3 hover:no-underline">
                      <span className="flex min-w-0 flex-1 flex-col items-stretch gap-1 pr-2 text-left">
                        <span className="flex min-w-0 items-center gap-2">
                          <span className="min-w-0 truncate font-medium text-sm">
                            {r.request_type === "exchange" ? "Exchange" : "Return"} {r.return_number}
                          </span>
                          <span className="ml-auto shrink-0 text-[10px] uppercase tracking-wider rounded-full px-3 py-1 bg-amber-500/15 text-amber-700">
                            {returnStatusLabel(r)}
                          </span>
                        </span>
                        {r.reverse_awb ? (
                          <span className="text-xs font-normal normal-case tracking-normal text-muted-foreground">
                            Pickup AWB {r.reverse_awb}
                          </span>
                        ) : null}
                        {isRejectedReturn(r) ? (
                          <span className="text-xs font-normal normal-case tracking-normal text-amber-800">
                            {closedReturnMessage(r)}
                          </span>
                        ) : null}
                      </span>
                    </AccordionTrigger>
                    <AccordionContent className="pb-3">
                      <ReturnTrackingCard item={r} showHeader={false} omitClosedMessage={isRejectedReturn(r)} />
                    </AccordionContent>
                  </AccordionItem>
                ))}
              </Accordion>
              <Link to="/account/returns" className="text-xs text-primary hover:underline">
                Manage your return &amp; exchange requests
              </Link>
            </div>
          ) : eligibility?.eligible ? (
            <div className="space-y-3">
              {eligibility.return_deadline ? (
                <p className="text-sm text-muted-foreground">
                  Return and exchange window is open until {formatOrderDate(eligibility.return_deadline)}.
                </p>
              ) : null}
              <ReturnConditionNotice />
              <p className="text-sm text-muted-foreground">
                Choose <span className="font-medium text-foreground">Return</span> or{" "}
                <span className="font-medium text-foreground">Exchange</span> next to an item below.
              </p>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              {eligibility?.message ?? "Returns and exchanges aren't available for this order right now."}
            </p>
          )}
        </div>
      )}

      <div className="grid lg:grid-cols-[minmax(0,1fr)_320px] gap-4 min-w-0">
        <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 md:p-8 min-w-0">
          <h2 className="font-display text-lg sm:text-xl mb-4 flex items-center gap-2">
            <Package2 className="h-5 w-5 text-primary shrink-0" />
            Items
            <span className="text-muted-foreground font-normal text-base">({itemCount})</span>
          </h2>
          <div className="space-y-3">
            {order.order_items?.map((it: any) => {
              const openExchange = it.id != null ? openExchangeByItemId.get(it.id) : undefined;
              const canAct =
                SHOW_M2_POST_ORDER_UI &&
                order.payment_status === "paid" &&
                eligibility?.eligible &&
                !eligibilityLoadError &&
                !openWholeOrderReturn &&
                !openExchange &&
                it.id != null;
              return (
                <div key={it.id} className="glass rounded-2xl p-3 flex flex-col gap-2 min-w-0">
                  <div className="flex gap-3 items-start sm:items-center min-w-0">
                    <OrderProductThumb
                      src={it.product_image}
                      alt={it.product_name ?? ""}
                      className="h-14 w-14 sm:h-16 sm:w-16"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="font-medium break-words">{it.product_name}</div>
                      <div className="text-xs text-muted-foreground">
                        {[it.size, it.color].filter(Boolean).join(" · ")}
                        {it.qty != null ? ` · Qty ${it.qty}` : ""}
                      </div>
                    </div>
                    <div className="text-sm font-medium shrink-0 tabular-nums">
                      ₹{(it.qty * Number(it.price)).toLocaleString("en-IN")}
                    </div>
                  </div>
                  {openExchange ? (
                    <span className="self-start text-[10px] uppercase tracking-wider rounded-full px-3 py-1 bg-amber-500/15 text-amber-700">
                      Exchange {openExchange.status.replace(/_/g, " ")}
                    </span>
                  ) : canAct ? (
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => openReturnDialog(it.id)}
                        className="inline-flex items-center gap-1.5 text-xs rounded-full border border-border px-3 py-1.5 hover:bg-white/60 transition"
                      >
                        <RotateCcw className="h-3.5 w-3.5" aria-hidden />
                        Return
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          openExchangeDialog({
                            id: it.id,
                            product_slug: it.product_slug,
                            product_name: it.product_name,
                            product_image: it.product_image,
                            size: it.size,
                            color: it.color,
                            qty: Number(it.qty ?? 1),
                            price: Number(it.price ?? 0),
                          })
                        }
                        className="inline-flex items-center gap-1.5 text-xs rounded-full border border-border px-3 py-1.5 hover:bg-white/60 transition"
                      >
                        <ArrowLeftRight className="h-3.5 w-3.5" aria-hidden />
                        Exchange
                      </button>
                    </div>
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
              <div className="text-sm font-medium break-words">{addr.full_name}</div>
              <div className="text-sm text-muted-foreground break-words">
                {addr.line1}
                {addr.line2 ? `, ${addr.line2}` : ""}
              </div>
              <div className="text-sm text-muted-foreground break-words">
                {addr.city}, {addr.state} {addr.pincode}
              </div>
              <div className="text-xs text-muted-foreground mt-1 break-all">
                {addr.phone} · {addr.email}
              </div>
            </div>
            <div className="glass rounded-2xl p-4">
              <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted-foreground mb-2">
                <CreditCard className="h-4 w-4 text-primary" /> Payment
              </div>
              {!isCodCancelledUncollected(order) && (
                <>
                  <div className="text-sm font-medium">{formatAccountOrderPaymentLabel(paymentOrder)}</div>
                  {isOnlineRefundProcessedAwaitingBank(paymentOrder) && (
                    <div className="text-xs text-muted-foreground mt-1">
                      {refundProcessedMessage(refundTimeline.min, refundTimeline.max)}
                    </div>
                  )}
                </>
              )}
              {!isCancelledOrder(order) &&
                order.payment_method === "cod" &&
                order.payment_status === "pending" && (
                  <div className="text-xs text-muted-foreground mt-1">Cash or UPI at doorstep</div>
                )}
              {order.razorpay_payment_id && (
                <div className="text-xs text-muted-foreground break-all">
                  ID: {order.razorpay_payment_id}
                </div>
              )}
              {!isCancelledOrder(order) &&
                order.payment_status === "pending" &&
                order.payment_method !== "cod" && (
                  <div className="mt-3 flex flex-col sm:flex-row flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={payNow}
                      disabled={paying || syncing}
                      className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-xs inline-flex items-center justify-center gap-2 disabled:opacity-50"
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
                      className="rounded-full glass px-4 py-2 text-xs inline-flex items-center justify-center gap-2 disabled:opacity-50 hover:bg-white/60 transition"
                    >
                      {syncing && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                      {syncing ? "Checking…" : "Already paid? Refresh status"}
                    </button>
                  </div>
                )}
              {order.coupon_code && (
                <div className="text-xs text-primary mt-1">Coupon: {order.coupon_code}</div>
              )}
              {order.order_number && isInvoiceAvailable(order) && (
                <Link
                  to="/account/orders/$orderNumber/invoice"
                  params={{ orderNumber: order.order_number }}
                  className="mt-3 rounded-full glass px-4 py-2 text-xs inline-flex items-center justify-center gap-2 hover:bg-white/60 transition w-full sm:w-auto"
                >
                  <FileText className="h-3.5 w-3.5" /> View invoice
                </Link>
              )}
            </div>
          </div>
        </div>

        <aside className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 h-fit space-y-1.5 text-sm min-w-0">
          <div className="font-display text-lg sm:text-xl mb-3">Summary</div>
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

      {SHOW_M2_POST_ORDER_UI && (
      <AlertDialog
        open={cancelDialogOpen}
        onOpenChange={(open) => {
          setCancelDialogOpen(open);
          if (!open) setCancelReason("");
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Request cancellation?</AlertDialogTitle>
            <AlertDialogDescription>
              Your request will be reviewed by our team before the order is cancelled
              {order.payment_status === "paid" ? " and any refund is processed" : ""}.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="space-y-2">
            {(order.order_items ?? []).map((item) => (
              <div key={item.id ?? item.product_name} className="flex gap-3 rounded-xl border border-border p-3">
                <OrderProductThumb src={item.product_image} alt={item.product_name ?? ""} />
                <div className="min-w-0">
                  <p className="font-medium text-sm break-words">{item.product_name}</p>
                  <p className="text-xs text-muted-foreground">
                    {[item.size, item.color].filter(Boolean).join(" · ")}
                    {item.qty != null ? ` · Qty ${item.qty}` : ""}
                  </p>
                </div>
              </div>
            ))}
          </div>
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
              {cancelling ? "Submitting…" : "Submit request"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      )}

      {SHOW_M2_POST_ORDER_UI && (
      <Dialog
        open={returnDialogOpen}
        onOpenChange={(open) => {
          setReturnDialogOpen(open);
          if (!open) {
            setReturnReason("");
            setReturnImages([]);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Return items</DialogTitle>
            <DialogDescription>
              Choose how many pieces to send back, tell us why, and attach photos if you can. Refunds cover item
              price after discounts — delivery and COD fees are not refunded.
            </DialogDescription>
          </DialogHeader>
          <ReturnConditionNotice />
          <div className="space-y-2">
            <p className="text-sm font-medium">Items and quantities</p>
            {(order.order_items ?? []).map((item) => {
              const max = Number(item.qty ?? 1);
              return (
                <label
                  key={item.id}
                  className="flex items-center justify-between gap-3 rounded-xl border border-border p-3 text-sm"
                >
                  <span className="flex min-w-0 items-start gap-3">
                    <OrderProductThumb src={item.product_image} alt={item.product_name ?? ""} />
                    <span className="min-w-0">
                    <span className="block font-medium break-words">{item.product_name}</span>
                    <span className="block text-xs text-muted-foreground">
                      {[item.size, item.color].filter(Boolean).join(" · ")}
                      {item.size || item.color ? " · " : ""}
                      Ordered qty {max}
                    </span>
                    </span>
                  </span>
                  <select
                    value={item.id == null ? 0 : returnQuantities[item.id] ?? 0}
                    onChange={(event) =>
                      setReturnQuantities((current) => ({
                        ...current,
                        [item.id ?? ""]: Number(event.target.value),
                      }))
                    }
                    className="rounded-lg border border-border bg-background px-2 py-1"
                    disabled={submittingReturn || item.id == null}
                    aria-label={`Return quantity for ${item.product_name}`}
                  >
                    {Array.from({ length: max + 1 }, (_, quantity) => (
                      <option key={quantity} value={quantity}>
                        {quantity}
                      </option>
                    ))}
                  </select>
                </label>
              );
            })}
          </div>
          <div className="space-y-1.5">
            <label htmlFor="return-reason" className="text-sm font-medium">
              Why are you returning this order?
              <span className="text-destructive" aria-hidden="true"> *</span>
            </label>
            <textarea
              id="return-reason"
              className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
              placeholder="Tell us what went wrong"
              value={returnReason}
              onChange={(e) => setReturnReason(e.target.value)}
              disabled={submittingReturn}
              required
              aria-required="true"
            />
          </div>
          <ReturnImageUpload
            urls={returnImages}
            onChange={setReturnImages}
            disabled={submittingReturn}
            onUploadingChange={setReturnImagesUploading}
          />
          <DialogFooter>
            <button
              type="button"
              onClick={() => setReturnDialogOpen(false)}
              disabled={submittingReturn || returnImagesUploading}
              className="rounded-full border border-border px-4 py-2 text-sm disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={submitReturn}
              disabled={
                submittingReturn || returnImagesUploading || returnReason.trim().length < 10
              }
              className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              {submittingReturn ? "Submitting…" : "Submit return"}
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      )}

      {SHOW_M2_POST_ORDER_UI && (
      <Dialog
        open={!!exchangeItem}
        onOpenChange={(open) => {
          if (!open) {
            setExchangeItem(null);
            setExchangeImages([]);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Exchange item</DialogTitle>
            <DialogDescription>
              Choose a replacement size or colour. Photos help us review your request faster. Any refund or
              collect is on item price after discounts — delivery and COD fees are not refunded.
            </DialogDescription>
          </DialogHeader>
          <ReturnConditionNotice />
          {exchangeItem ? (
            <div className="flex gap-3 rounded-xl border border-border p-3">
              <OrderProductThumb src={exchangeItem.product_image} alt={exchangeItem.product_name ?? ""} />
              <div className="min-w-0">
                <p className="font-medium text-sm break-words">{exchangeItem.product_name}</p>
                <p className="text-xs text-muted-foreground">
                  {[exchangeItem.size, exchangeItem.color].filter(Boolean).join(" · ")}
                </p>
                <p className="mt-1 text-sm">
                  Quantity:{" "}
                  <span className="font-semibold tabular-nums">{exchangeItem.qty ?? 1}</span>
                  <span className="text-xs text-muted-foreground">
                    {" "}
                    · entire quantity will be exchanged
                  </span>
                </p>
              </div>
            </div>
          ) : null}
          <ExchangeReplacementPicker
            product={exchangeProduct}
            originalSize={exchangeItem?.size}
            originalColor={exchangeItem?.color}
            originalUnitPrice={exchangeItem?.price}
            qty={exchangeItem?.qty}
            size={exchangeSize}
            color={exchangeColor}
            onSizeChange={setExchangeSize}
            onColorChange={setExchangeColor}
          />
          <div className="space-y-1.5">
            <label htmlFor="exchange-reason" className="text-sm font-medium">
              Why are you exchanging this item?
              <span className="text-destructive" aria-hidden="true"> *</span>
            </label>
            <textarea
              id="exchange-reason"
              className="w-full rounded-xl border border-border bg-background/60 px-3 py-2 text-sm min-h-[80px]"
              placeholder="Tell us what went wrong"
              value={exchangeReason}
              onChange={(e) => setExchangeReason(e.target.value)}
              disabled={submittingExchange}
              required
              aria-required="true"
            />
          </div>
          <ReturnImageUpload
            urls={exchangeImages}
            onChange={setExchangeImages}
            disabled={submittingExchange}
            onUploadingChange={setExchangeImagesUploading}
          />
          <DialogFooter>
            <button
              type="button"
              onClick={() => setExchangeItem(null)}
              disabled={submittingExchange || exchangeImagesUploading}
              className="rounded-full border border-border px-4 py-2 text-sm disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={submitExchange}
              disabled={
                submittingExchange ||
                exchangeImagesUploading ||
                exchangeReason.trim().length < 10 ||
                !exchangeSize ||
                !exchangeColor
              }
              className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm disabled:opacity-50"
            >
              {submittingExchange ? "Submitting…" : "Submit exchange"}
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      )}
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
      className={`flex justify-between gap-3 ${muted ? "text-muted-foreground" : ""} ${accent ? "text-primary" : ""}`}
    >
      <span>{label}</span>
      <span>{value}</span>
    </div>
  );
}
