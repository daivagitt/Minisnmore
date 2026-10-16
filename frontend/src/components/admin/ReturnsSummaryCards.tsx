import { AlertTriangle, ArrowLeftRight, Clock, RotateCcw, Truck, type LucideIcon } from "lucide-react";
import {
  EMPTY_ADMIN_RETURNS_SUMMARY,
  type AdminOrderReturnsSummary,
} from "@/lib/orders.functions";
import { cn } from "@/lib/utils";

type StageId = "all" | "request" | "pickup" | "inspection" | "exchange" | "closed";

type SummaryCard = {
  key: string;
  label: string;
  sublabel: string;
  value: number;
  icon: LucideIcon;
  colorClasses: string;
  badgeClasses: string;
  stage: StageId;
  status?: string;
  requestType?: "" | "return" | "exchange";
};

export function ReturnsSummaryCards({
  summary,
  loading,
  onSelectStage,
}: {
  summary: AdminOrderReturnsSummary | null;
  loading?: boolean;
  onSelectStage?: (stage: StageId, status?: string, requestType?: "" | "return" | "exchange") => void;
}) {
  const counts = summary ?? EMPTY_ADMIN_RETURNS_SUMMARY;

  const cards: SummaryCard[] = [
    {
      key: "returns",
      label: "Returns",
      sublabel: "Refund cases",
      value: counts.returns,
      icon: RotateCcw,
      colorClasses: "border-sky-200/60 bg-sky-50/40",
      badgeClasses: "bg-sky-100 text-sky-800",
      stage: "all",
      requestType: "return",
    },
    {
      key: "exchanges",
      label: "Exchanges",
      sublabel: "Replacement cases",
      value: counts.exchanges,
      icon: ArrowLeftRight,
      colorClasses: "border-indigo-200/60 bg-indigo-50/40",
      badgeClasses: "bg-indigo-100 text-indigo-800",
      stage: "all",
      requestType: "exchange",
    },
    {
      key: "open",
      label: "Open",
      sublabel: "Still in progress",
      value: counts.open,
      icon: Clock,
      colorClasses: "border-amber-200/60 bg-amber-50/40",
      badgeClasses: "bg-amber-100 text-amber-800",
      stage: "all",
      requestType: "",
    },
    {
      key: "overdue",
      label: "Overdue",
      sublabel: "Past SLA",
      value: counts.overdue,
      icon: AlertTriangle,
      colorClasses: "border-rose-200/60 bg-rose-50/40",
      badgeClasses: "bg-rose-100 text-rose-800",
      stage: "all",
      requestType: "",
    },
    {
      key: "xpressbeesReverseReady",
      label: "Book reverse pickup",
      sublabel: "Approved · pickup not booked",
      value: counts.xpressbeesReverseReady,
      icon: Truck,
      colorClasses: "border-emerald-200/60 bg-emerald-50/40",
      badgeClasses: "bg-emerald-100 text-emerald-800",
      stage: "request",
      status: "approved",
      requestType: "",
    },
  ];

  const headline =
    counts.total === 0
      ? "No return or exchange cases yet."
      : `There ${counts.returns === 1 ? "is" : "are"} ${counts.returns} return${counts.returns === 1 ? "" : "s"} and ${counts.exchanges} exchange${counts.exchanges === 1 ? "" : "s"}. ${counts.open} still open${counts.overdue > 0 ? `, ${counts.overdue} overdue` : ""}.`;

  return (
    <section className="space-y-3" aria-label="Returns and exchanges summary">
      <p className="text-sm text-muted-foreground" aria-live="polite">
        {loading && !summary ? "Loading summary…" : headline}
      </p>
      <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5">
        {cards.map((card) => {
          const Icon = card.icon;
          return (
            <button
              key={card.key}
              type="button"
              aria-label={`${card.value} ${card.label.toLowerCase()}`}
              onClick={() => onSelectStage?.(card.stage, card.status, card.requestType)}
              className={cn(
                "relative overflow-hidden rounded-2xl border p-5 text-left shadow-sm transition-all duration-200 glass-strong hover:border-primary/50 hover:translate-y-[-2px]",
                card.colorClasses,
              )}
            >
              <span className={cn("inline-flex rounded-lg p-2 text-xs font-semibold", card.badgeClasses)}>
                <Icon className="h-4 w-4" />
              </span>
              <div className="mt-3">
                <p className="font-display text-3xl font-semibold">{loading && !summary ? "…" : card.value}</p>
                <p className="mt-1 text-xs font-semibold uppercase tracking-wider text-foreground">{card.label}</p>
                <p className="mt-0.5 text-[11px] text-muted-foreground">{card.sublabel}</p>
              </div>
            </button>
          );
        })}
      </div>
    </section>
  );
}
