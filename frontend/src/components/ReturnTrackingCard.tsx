import type { ReactNode } from "react";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { RefundInitiatedNotice } from "@/components/RefundInitiatedNotice";
import { CopyButton } from "@/components/CopyButton";
import { OrderProductThumb } from "@/components/OrderProductThumb";
import { ReturnImageGallery } from "@/components/ReturnImageGallery";
import {
  closedReturnMessage,
  hasReturnShipmentDetails,
  isClosedReturn,
  isExchangeRequest,
  resolvePickupTrackingUrl,
  resolveReturnStage,
  returnStageDetail,
  returnStageLabel,
  returnStatusLabel,
  returnTrackingStages,
  type ReturnTrackingFields,
} from "@/lib/return-tracking";
import { resolveReturnProductLines } from "@/lib/return-product-lines";
import { formatReturnProductSubtitle } from "@/lib/return-variant";
import { formatOrderDate } from "@/lib/dates";

type Props = {
  item: ReturnTrackingFields & {
    return_number?: string;
    order_number?: string;
    product_name?: string;
    product_image?: string | null;
    original_size?: string | null;
    original_color?: string | null;
    sku?: string | null;
    replacement_size?: string | null;
    replacement_color?: string | null;
    variant_sku?: string | null;
    requested_at?: string;
    image_urls?: string[] | null;
    items?: Array<{ product_name?: string; product_image?: string | null }>;
    order_payment_status?: string | null;
  };
  actions?: ReactNode;
  showHeader?: boolean;
  /** When the parent already shows {@link closedReturnMessage}, skip the duplicate block. */
  omitClosedMessage?: boolean;
};

export function ReturnTrackingCard({ item, actions, showHeader = true, omitClosedMessage = false }: Props) {
  const settings = useSiteSettings();
  const refundTimeline = {
    min: settings.refund_min_business_days,
    max: settings.refund_max_business_days,
  };
  const exchange = isExchangeRequest(item);
  const closed = isClosedReturn(item);
  const stages = returnTrackingStages(item);
  const idx = resolveReturnStage(item);
  const currentStage = idx >= 0 ? stages[idx] : undefined;
  const subtitle = formatReturnProductSubtitle(item);
  const productThumbs = resolveReturnProductLines(item);
  const refunded = (item.status ?? "").toLowerCase() === "refunded";
  const pickupTrackingUrl = resolvePickupTrackingUrl(item);

  return (
    <div className="space-y-4">
      {refunded ? (
        <RefundInitiatedNotice
          orderPaymentStatus={item.order_payment_status}
          timeline={refundTimeline}
        />
      ) : null}
      {showHeader && (
        <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
          {productThumbs.length > 0 ? (
            <div className="flex flex-wrap gap-2 shrink-0">
              {productThumbs.slice(0, 3).map((line, index) => (
                <OrderProductThumb
                  key={`${line.product_name ?? "item"}-${index}`}
                  src={line.product_image}
                  alt={line.product_name ?? ""}
                  className="h-12 w-12"
                />
              ))}
            </div>
          ) : null}
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted-foreground flex-wrap">
              <span>
                {exchange ? "Exchange" : "Return"} {item.return_number}
              </span>
              {item.return_number ? (
                <CopyButton
                  value={item.return_number}
                  label="Copy request ID"
                  successMessage="Request ID copied"
                  className="h-7 w-7"
                  iconClassName="h-3 w-3"
                />
              ) : null}
            </div>
            <div className="font-display text-2xl mt-1">{returnStatusLabel(item)}</div>
            {item.order_number || item.requested_at ? (
              <p className="text-xs text-muted-foreground mt-1">
                {item.order_number ? `Order ${item.order_number}` : null}
                {item.order_number && item.requested_at ? " · " : null}
                {item.requested_at ? `Requested ${formatOrderDate(item.requested_at)}` : null}
              </p>
            ) : null}
            {subtitle ? <p className="text-sm text-muted-foreground mt-1">{subtitle}</p> : null}
            {closed ? <p className="text-sm mt-2 text-amber-800">{closedReturnMessage(item, refundTimeline)}</p> : null}
            {!closed && currentStage ? (
              <p className="text-sm text-muted-foreground mt-1 max-w-xl">
                {returnStageDetail(item, currentStage.key, "current", refundTimeline)}
              </p>
            ) : null}
            {!closed && currentStage ? (
              <p className="text-xs text-muted-foreground mt-2">
                Step {idx + 1} of {stages.length}
              </p>
            ) : null}
          </div>
          {actions}
        </div>
      )}

      {!showHeader && closed && !omitClosedMessage ? (
        <p className="text-sm text-amber-800">{closedReturnMessage(item, refundTimeline)}</p>
      ) : null}

      {!showHeader && productThumbs.length > 0 ? (
        <div className="flex flex-wrap gap-3">
          {productThumbs.map((line, index) => (
            <div key={`${line.product_name ?? "item"}-${index}`} className="flex items-center gap-2 min-w-0">
              <OrderProductThumb src={line.product_image} alt={line.product_name ?? ""} />
              {line.product_name ? (
                <span className="text-sm font-medium break-words">{line.product_name}</span>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}

      {item.image_urls && item.image_urls.length > 0 ? (
        <div className="space-y-1.5">
          <p className="text-[11px] uppercase tracking-wider text-muted-foreground">Photos</p>
          <ReturnImageGallery
            urls={item.image_urls}
            alt={exchange ? "Customer exchange photo" : "Customer return photo"}
          />
        </div>
      ) : null}

      {hasReturnShipmentDetails(item) && (
        <div className="glass rounded-2xl p-4 text-sm space-y-2">
          {(item.carrier_name || item.reverse_awb || pickupTrackingUrl || item.pickup_window) && (
            <div className="space-y-1">
              <div className="text-[11px] uppercase tracking-wider text-muted-foreground">
                Pickup
              </div>
              {item.carrier_name ? (
                <div>
                  <span className="text-muted-foreground">Carrier:</span> {item.carrier_name}
                </div>
              ) : null}
              {item.reverse_awb ? (
                <div className="flex items-center gap-2 flex-wrap">
                  <span>
                    <span className="text-muted-foreground">AWB:</span> {item.reverse_awb}
                  </span>
                  <CopyButton
                    value={item.reverse_awb}
                    label="Copy pickup AWB"
                    successMessage="Pickup AWB copied"
                    variant="text"
                    className="text-[11px]"
                  />
                </div>
              ) : null}
              {item.pickup_window ? (
                <div>
                  <span className="text-muted-foreground">Pickup window:</span> {item.pickup_window}
                </div>
              ) : null}
              {pickupTrackingUrl ? (
                <div>
                  <a href={pickupTrackingUrl} target="_blank" rel="noreferrer" className="underline">
                    Track pickup
                  </a>
                </div>
              ) : null}
            </div>
          )}
          {(item.outbound_carrier_name || item.outbound_awb || item.outbound_tracking_url) && (
            <div className="space-y-1">
              <div className="text-[11px] uppercase tracking-wider text-muted-foreground">
                Replacement shipment
              </div>
              {item.outbound_carrier_name ? (
                <div>
                  <span className="text-muted-foreground">Carrier:</span> {item.outbound_carrier_name}
                </div>
              ) : null}
              {item.outbound_awb ? (
                <div className="flex items-center gap-2 flex-wrap">
                  <span>
                    <span className="text-muted-foreground">AWB:</span> {item.outbound_awb}
                  </span>
                  <CopyButton
                    value={item.outbound_awb}
                    label="Copy replacement AWB"
                    successMessage="Replacement AWB copied"
                    variant="text"
                    className="text-[11px]"
                  />
                </div>
              ) : null}
              {item.outbound_tracking_url ? (
                <div>
                  <a
                    href={item.outbound_tracking_url}
                    target="_blank"
                    rel="noreferrer"
                    className="underline"
                  >
                    Track replacement
                  </a>
                </div>
              ) : null}
            </div>
          )}
        </div>
      )}

      {!closed && currentStage ? (
        <div className="relative">
          <div className="absolute left-5 top-0 bottom-0 w-px bg-border" />
          <div
            className="space-y-5"
            role="list"
            aria-label={exchange ? "Exchange timeline" : "Return timeline"}
          >
            <p className="sr-only">
              Step {idx + 1} of {stages.length}: {returnStageLabel(item, currentStage.key)}
            </p>
            {stages.map((stage, i) => {
              const Icon = stage.icon;
              const done = i <= idx;
              const current = i === idx;
              const state = current ? "current" : done ? "done" : "upcoming";
              const label = returnStageLabel(item, stage.key);
              const detail = current ? returnStageDetail(item, stage.key, state, refundTimeline) : "";
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
      ) : null}
    </div>
  );
}
