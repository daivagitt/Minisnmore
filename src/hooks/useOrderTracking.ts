import { useCallback, useEffect, useRef, useState } from "react";
import { getOrder, trackOrderPublic } from "@/lib/orders.functions";
import { type OrderTrackingFields } from "@/lib/order-tracking";

export type TrackingOrder = OrderTrackingFields & {
  order_number?: string;
  payment_status?: string;
  payment_method?: string;
  id?: number;
  created_at?: string;
  coupon_code?: string;
  cancellation_reason?: string;
  razorpay_payment_id?: string;
  invoice_pdf_url?: string;
  subtotal?: number;
  discount?: number;
  shipping?: number;
  cod_fee?: number;
  tax?: number;
  donation?: number;
  total?: number;
  order_items?: Array<{
    id?: number;
    product_id?: string;
    product_slug?: string;
    sku?: string;
    product_image?: string;
    product_name?: string;
    size?: string;
    color?: string;
    qty?: number;
    price?: number;
  }>;
  shipping_address?: Record<string, string>;
};

type Mode = "public" | "authenticated";

type Options = {
  enabled?: boolean;
};

export function useOrderTracking(
  orderNumber: string | null | undefined,
  mode: Mode,
  options?: Options,
) {
  const enabled = options?.enabled ?? true;
  const normalizedNumber = orderNumber?.trim() || null;

  const [order, setOrder] = useState<TrackingOrder | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const orderRef = useRef<TrackingOrder | null>(null);
  const inFlightRef = useRef(false);
  const activeNumberRef = useRef("");

  const fetchTracking = useCallback(
    async (targetNumber: string, opts?: { silent?: boolean }) => {
      if (!enabled || !targetNumber) return;
      if (inFlightRef.current) return;

      inFlightRef.current = true;
      if (!opts?.silent) setLoading(true);

      try {
        const data =
          mode === "public"
            ? (await trackOrderPublic({ data: { orderNumber: targetNumber } })).order
            : (await getOrder({ data: { orderNumber: targetNumber } })).order;

        if (activeNumberRef.current !== targetNumber) return;

        if (data) {
          setOrder(data);
          orderRef.current = data;
          setError(null);
        } else {
          setOrder(null);
          orderRef.current = null;
          setError("No tracking details found for that order.");
        }
      } catch (e) {
        if (activeNumberRef.current !== targetNumber) return;
        const message = e instanceof Error ? e.message : "Could not load tracking details.";
        if (!opts?.silent || !orderRef.current) {
          setError(message);
          setOrder(null);
          orderRef.current = null;
        }
      } finally {
        inFlightRef.current = false;
        if (!opts?.silent && activeNumberRef.current === targetNumber) {
          setLoading(false);
        }
      }
    },
    [enabled, mode],
  );

  const refresh = useCallback(async (opts?: { silent?: boolean }) => {
    const num = activeNumberRef.current;
    if (num) await fetchTracking(num, opts);
  }, [fetchTracking]);

  useEffect(() => {
    if (!enabled || !normalizedNumber) {
      activeNumberRef.current = "";
      setOrder(null);
      orderRef.current = null;
      setError(null);
      setLoading(false);
      return;
    }

    activeNumberRef.current = normalizedNumber;
    setOrder(null);
    orderRef.current = null;
    setError(null);
    void fetchTracking(normalizedNumber);
  }, [enabled, normalizedNumber, fetchTracking]);

  return { order, loading, error, refresh };
}
