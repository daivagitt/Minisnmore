import { Info } from "lucide-react";
import {
  isOrderRefundSettled,
  refundGatewayStartedMessage,
  refundProcessedMessage,
  type RefundTimeline,
} from "@/lib/refund-policy";
import { useSiteSettings } from "@/hooks/useSiteSettings";

type Props = {
  className?: string;
  orderPaymentStatus?: string | null;
  timeline?: RefundTimeline;
};

export function RefundFailedNotice({ className = "" }: { className?: string }) {
  return (
    <div
      role="status"
      className={`rounded-[1.25rem] border border-destructive/30 bg-destructive/10 p-4 flex items-start gap-3 ${className}`}
    >
      <Info className="h-5 w-5 text-destructive mt-0.5 shrink-0" />
      <div>
        <div className="text-sm font-medium text-destructive">Refund failed</div>
        <p className="text-sm text-muted-foreground mt-1">
          We could not complete this refund to your original payment method. Our team will retry it.
        </p>
      </div>
    </div>
  );
}

export function RefundInitiatedNotice({ className = "", orderPaymentStatus, timeline }: Props) {
  const settings = useSiteSettings();
  const min = timeline?.min ?? settings.refund_min_business_days;
  const max = timeline?.max ?? settings.refund_max_business_days;
  const processed = isOrderRefundSettled(orderPaymentStatus);

  return (
    <div
      role="status"
      className={`rounded-[1.25rem] border border-amber-500/30 bg-amber-500/10 p-4 flex items-start gap-3 ${className}`}
    >
      <Info className="h-5 w-5 text-amber-800 mt-0.5 shrink-0" />
      <div>
        <div className="text-sm font-medium text-amber-950">
          {processed ? "Refund processed" : "Refund initiated"}
        </div>
        <p className="text-sm text-muted-foreground mt-1">
          {processed ? refundProcessedMessage(min, max) : refundGatewayStartedMessage()}
        </p>
      </div>
    </div>
  );
}
