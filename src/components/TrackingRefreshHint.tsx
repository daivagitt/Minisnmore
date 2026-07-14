import { Loader2 } from "lucide-react";
import { shouldPollOrderTracking } from "@/lib/order-tracking";

type Props = {
  lastUpdated: Date | null;
  polling: boolean;
};

export function TrackingRefreshHint({ lastUpdated, polling }: Props) {
  if (!polling && !lastUpdated) return null;

  const time =
    lastUpdated?.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" }) ?? null;

  return (
    <p className="text-[11px] text-muted-foreground mt-3 inline-flex items-center gap-1.5">
      {polling && <Loader2 className="h-3 w-3 animate-spin opacity-60" aria-hidden />}
      <span>
        {polling ? "Auto-refreshing every minute" : "Tracking is up to date"}
        {time ? ` · Last checked ${time}` : ""}
      </span>
    </p>
  );
}

export function isTrackingPollActive(order: Parameters<typeof shouldPollOrderTracking>[0]) {
  return shouldPollOrderTracking(order);
}
