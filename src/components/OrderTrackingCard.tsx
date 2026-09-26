import { CopyButton } from "@/components/CopyButton";
import {
  TRACKING_STAGES,
  type OrderTrackingFields,
  resolveTrackingStage,
  trackingStageDetail,
  trackingStageLabel,
  trackingStatusLabel,
} from "@/lib/order-tracking";

type Props = {
  order: OrderTrackingFields & { order_number?: string };
  showHeader?: boolean;
};

export function OrderTrackingCard({ order, showHeader = true }: Props) {
  const idx = resolveTrackingStage(order);

  if (idx < 0) {
    return (
      <div className="glass rounded-2xl p-4 text-sm text-muted-foreground">
        Order {order.order_number ? `${order.order_number} ` : ""}was cancelled.
      </div>
    );
  }

  const currentStage = TRACKING_STAGES[idx];

  return (
    <div>
      <p className="sr-only">
        Step {idx + 1} of {TRACKING_STAGES.length}: {trackingStageLabel(order, currentStage.key)}
      </p>
      {showHeader && (
        <>
          {order.order_number && (
            <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted-foreground flex-wrap">
              <span>Order {order.order_number}</span>
              <CopyButton
                value={order.order_number}
                label="Copy order ID"
                successMessage="Order ID copied"
                className="h-7 w-7"
                iconClassName="h-3 w-3"
              />
            </div>
          )}
          <div className="font-display text-2xl mt-1">{trackingStatusLabel(order)}</div>
          <p className="text-sm text-muted-foreground mt-1 max-w-xl">
            {trackingStageDetail(order, currentStage.key, "current")}
          </p>
        </>
      )}

      {(order.tracking_number || order.tracking_carrier || order.shipment_date || order.tracking_url) && (
        <div className={`glass rounded-2xl p-4 text-sm space-y-1 ${showHeader ? "mt-4" : ""}`}>
          {order.tracking_carrier && (
            <div>
              <span className="text-muted-foreground">Carrier:</span> {order.tracking_carrier}
            </div>
          )}
          {order.tracking_number && (
            <div className="flex items-center gap-2 flex-wrap">
              <span>
                <span className="text-muted-foreground">Tracking number:</span> {order.tracking_number}
              </span>
              <CopyButton
                value={order.tracking_number}
                label="Copy tracking number"
                successMessage="Tracking number copied"
                variant="text"
                className="text-[11px]"
              />
            </div>
          )}
          {order.shipment_date &&
            ["shipped", "out_for_delivery", "delivery_failed", "delivered", "fulfilled"].includes((order.tracking_status ?? "").toLowerCase()) && (
            <div>
              <span className="text-muted-foreground">Shipped on:</span> {order.shipment_date}
            </div>
          )}
          {order.tracking_url && (
            <div>
              <a href={order.tracking_url} target="_blank" rel="noreferrer" className="underline">
                Track shipment
              </a>
            </div>
          )}
        </div>
      )}

      <div className={`relative ${showHeader ? "mt-8" : "mt-4"}`}>
        <div className="absolute left-5 top-0 bottom-0 w-px bg-border" />
        <div className="space-y-5" role="list" aria-label="Order delivery timeline">
          {TRACKING_STAGES.map((stage, i) => {
            const Icon = stage.icon;
            const done = i <= idx;
            const current = i === idx;
            const state = current ? "current" : done ? "done" : "upcoming";
            const label = trackingStageLabel(order, stage.key);
            const detail = trackingStageDetail(order, stage.key, state);
            const ariaState = current ? "Current" : done ? "Completed" : "Upcoming";
            return (
              <div
                key={stage.key}
                role="listitem"
                aria-label={`${label}: ${ariaState}. ${detail}`}
                aria-current={current ? "step" : undefined}
                className="flex gap-4 items-start relative"
              >
                <div
                  className={`h-10 w-10 rounded-full grid place-items-center shrink-0 transition ${
                    done ? "bg-primary text-primary-foreground" : "glass text-muted-foreground"
                  } ${current ? "shadow-glow" : ""}`}
                >
                  <Icon className="h-4 w-4" />
                </div>
                <div className={`glass rounded-2xl p-4 flex-1 ${current ? "ring-2 ring-primary/40" : ""}`}>
                  <div className="font-medium text-sm">{label}</div>
                  <div className="text-xs text-muted-foreground mt-0.5">{detail}</div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
