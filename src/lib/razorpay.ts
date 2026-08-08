import { verifyPayment, syncPaymentStatus } from "@/lib/orders.functions";

declare global {
  interface Window {
    Razorpay: any;
  }
}

export function loadRazorpayScript(): Promise<boolean> {
  return new Promise((resolve) => {
    if (typeof window === "undefined") return resolve(false);
    if (window.Razorpay) return resolve(true);
    const s = document.createElement("script");
    s.src = "https://checkout.razorpay.com/v1/checkout.js";
    s.onload = () => resolve(true);
    s.onerror = () => resolve(false);
    document.body.appendChild(s);
  });
}

export type RazorpayCheckoutInit = {
  orderId: number;
  orderNumber: string;
  razorpayOrderId: string;
  keyId: string;
  amount: number;
  currency: string;
};

/** Opens the Razorpay widget, verifies the result against our backend, and polls as a fallback for async methods (UPI/QR). */
export async function openRazorpayCheckout(
  init: RazorpayCheckoutInit,
  opts: {
    prefill: { name: string; email: string; contact: string };
    description?: string;
    onSuccess: (orderNumber: string) => void | Promise<void>;
    onCancelled?: () => void;
    onFailed?: (reason: string) => void;
  },
): Promise<void> {
  const ok = await loadRazorpayScript();
  if (!ok) throw new Error("Could not load Razorpay");

  let paymentHandled = false;
  let pollTimer: ReturnType<typeof setInterval> | null = null;

  const finish = async (orderNumber: string) => {
    if (paymentHandled) return;
    paymentHandled = true;
    if (pollTimer) clearInterval(pollTimer);
    await opts.onSuccess(orderNumber);
  };

  const handleSuccess = async (resp: {
    razorpay_order_id: string;
    razorpay_payment_id: string;
    razorpay_signature: string;
  }) => {
    if (paymentHandled) return;
    try {
      const v: any = await verifyPayment({
        data: {
          orderId: init.orderId,
          razorpay_order_id: resp.razorpay_order_id,
          razorpay_payment_id: resp.razorpay_payment_id,
          razorpay_signature: resp.razorpay_signature,
        },
      });
      await finish(v.orderNumber ?? init.orderNumber);
    } catch (e: any) {
      opts.onFailed?.(e?.message || "Payment verification failed. Please contact support if amount was debited.");
    }
  };

  const pollStatus = async () => {
    if (paymentHandled) return;
    try {
      const res: any = await syncPaymentStatus({ data: { orderId: init.orderId } });
      if (res.paid) await finish(res.orderNumber ?? init.orderNumber);
    } catch {
      // Razorpay may not have confirmed yet — keep polling until modal closes or payment succeeds.
    }
  };

  const rzp = new window.Razorpay({
    key: init.keyId,
    amount: init.amount,
    currency: init.currency,
    name: "Mini's n More",
    description: opts.description ?? `Order ${init.orderNumber}`,
    order_id: init.razorpayOrderId,
    prefill: opts.prefill,
    theme: { color: "#7c5cff" },
    handler: async (resp: any) => {
      await handleSuccess(resp);
    },
    modal: {
      ondismiss: () => {
        if (pollTimer) clearInterval(pollTimer);
        if (!paymentHandled) opts.onCancelled?.();
      },
    },
  });
  rzp.on("payment.success", (resp: any) => {
    void handleSuccess(resp);
  });
  rzp.on("payment.failed", (r: any) => {
    const reason = r?.error?.description || r?.error?.reason || "Payment failed";
    opts.onFailed?.(reason);
  });
  rzp.open();
  void pollStatus();
  pollTimer = setInterval(() => {
    void pollStatus();
  }, 3000);
}
