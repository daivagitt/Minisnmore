import { AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";

export const RETURN_CONDITION_COPY =
  "Please send the product unused, unwashed, and in original packaging with tags attached. Used or damaged items will be rejected.";

export function ReturnConditionNotice({ className }: { className?: string }) {
  return (
    <div
      role="note"
      className={cn(
        "flex gap-2 rounded-xl border border-amber-200/80 bg-amber-50/90 px-3 py-2.5 text-xs leading-relaxed text-amber-950",
        className,
      )}
    >
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-700" aria-hidden />
      <p>
        <span className="font-semibold">Unused items only. </span>
        {RETURN_CONDITION_COPY}
      </p>
    </div>
  );
}
