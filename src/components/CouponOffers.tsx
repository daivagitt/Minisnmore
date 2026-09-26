import { useEffect, useState } from "react";
import { ChevronRight, Loader2, Tag, X } from "lucide-react";
import {
  couponEligibilityHint,
  formatCouponOffer,
  listActiveCoupons,
  type ActiveCoupon,
} from "@/lib/coupons.functions";

function minOrderLabel(c: ActiveCoupon) {
  const type = String(c.discount_type ?? "").toLowerCase();
  if (type === "bogo") {
    return c.min_order > 0 ? `on orders above ₹${c.min_order.toLocaleString("en-IN")}` : "on qualifying cart items";
  }
  return c.min_order > 0 ? `on orders above ₹${c.min_order.toLocaleString("en-IN")}` : "on all orders";
}

export function CouponOffers({
  subtotal,
  itemCount,
  activeCode,
  onApply,
}: {
  subtotal: number;
  itemCount: number;
  activeCode?: string | null;
  onApply: (code: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [coupons, setCoupons] = useState<ActiveCoupon[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    listActiveCoupons()
      .then((res) => {
        if (!cancelled) setCoupons(res.coupons);
      })
      .catch(() => {
        if (!cancelled) setCoupons([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (coupons !== null && coupons.length === 0) return null;

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="mt-2 text-xs text-primary inline-flex items-center gap-1 hover:underline"
      >
        <Tag className="h-3 w-3" />
        {coupons ? `View ${coupons.length} offer${coupons.length === 1 ? "" : "s"}` : "View offers"}
        <ChevronRight className="h-3 w-3" />
      </button>

      {open && (
        <div
          className="fixed inset-0 z-50 grid place-items-end sm:place-items-center bg-black/80 p-0 sm:p-4"
          onClick={() => setOpen(false)}
        >
          <div
            className="bg-background border shadow-lg w-full sm:max-w-md sm:rounded-[2rem] rounded-t-[2rem] max-h-[80vh] overflow-y-auto p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-4">
              <div className="font-display text-xl">Available offers</div>
              <button onClick={() => setOpen(false)} aria-label="Close">
                <X className="h-5 w-5" />
              </button>
            </div>

            {!coupons && (
              <div className="grid place-items-center py-6">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
              </div>
            )}

            <div className="space-y-3">
              {coupons?.map((c) => {
                const hint = couponEligibilityHint(c, subtotal, itemCount);
                const eligible = !hint;
                const applied = activeCode === c.code;
                return (
                  <div key={c.code} className="rounded-2xl border border-border bg-muted/40 p-4 flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <div className="font-medium flex items-center gap-2">
                        <Tag className="h-3.5 w-3.5 text-primary" /> {c.code}
                      </div>
                      <div className="text-xs text-muted-foreground mt-0.5">
                        {formatCouponOffer(c)} {minOrderLabel(c)}
                      </div>
                      {hint && (
                        <div className="text-[11px] text-destructive mt-1">
                          {hint}
                        </div>
                      )}
                    </div>
                    <button
                      type="button"
                      disabled={!eligible}
                      onClick={() => {
                        onApply(c.code);
                        setOpen(false);
                      }}
                      className={`shrink-0 text-xs rounded-full px-3 py-1.5 border transition disabled:opacity-40 disabled:pointer-events-none ${
                        applied
                          ? "bg-primary text-primary-foreground border-primary"
                          : "border-border hover:bg-muted"
                      }`}
                    >
                      {applied ? "Applied" : "Apply"}
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
