import { Ruler } from "lucide-react";
import type { MeasurementLimits } from "@/lib/product-fit";

export type SizeFitState = { size: string } | { error: string } | null;

export function ProductSizeCalculator({
  configured,
  limits,
  lengthCm,
  chestCm,
  onLengthChange,
  onChestChange,
  sizeFit,
}: {
  configured: boolean;
  limits: MeasurementLimits | null;
  lengthCm: string;
  chestCm: string;
  onLengthChange: (value: string) => void;
  onChestChange: (value: string) => void;
  sizeFit: SizeFitState;
}) {
  const frozen = !configured;
  if (!frozen && !limits) return null;

  const showLength = frozen || !!limits?.hasLength;
  const showChest = frozen || !!limits?.hasChest;
  const twoCols = showLength && showChest;

  return (
    <div
      className={`glass rounded-xl p-3 space-y-2 ${frozen ? "opacity-70" : ""}`}
      aria-disabled={frozen || undefined}
    >
      <div className="flex items-center gap-2 text-xs font-medium">
        <Ruler className="h-3.5 w-3.5 text-primary shrink-0" />
        Size calculator
      </div>
      {frozen ? (
        <p className="text-[11px] text-muted-foreground leading-snug">
          Locked until a size chart is added for this piece. You can still pick a size above.
        </p>
      ) : sizeFit && "size" in sizeFit ? (
        <p className="text-xs text-muted-foreground leading-snug">
          Auto-selected <span className="text-primary font-medium">{sizeFit.size}</span> from
          {lengthCm.trim() && chestCm.trim()
            ? " length and chest"
            : lengthCm.trim()
              ? " length"
              : " chest"}
          .
        </p>
      ) : sizeFit && "error" in sizeFit ? (
        <p className="text-destructive text-xs leading-snug line-clamp-2">{sizeFit.error}</p>
      ) : (
        <p className="text-[11px] text-muted-foreground leading-snug">
          Enter length or chest (cm) from the size guide to pick a size automatically.
        </p>
      )}
      <div className={`grid gap-2 ${twoCols ? "grid-cols-1 min-[400px]:grid-cols-2" : "grid-cols-1"}`}>
        {showLength ? (
          <input
            type="number"
            inputMode="decimal"
            min={frozen ? undefined : limits?.lengthMin}
            max={frozen ? undefined : limits?.lengthMax}
            step="0.1"
            value={frozen ? "" : lengthCm}
            disabled={frozen}
            readOnly={frozen}
            onChange={(e) => {
              if (frozen) return;
              onLengthChange(e.target.value);
            }}
            placeholder={
              frozen
                ? "Length (cm)"
                : `Length (${limits!.lengthMin}–${limits!.lengthMax} cm)`
            }
            aria-label="Garment length in centimetres"
            className="h-10 glass rounded-full px-3 sm:px-4 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40 min-w-0 w-full disabled:cursor-not-allowed disabled:opacity-60"
          />
        ) : null}
        {showChest ? (
          <input
            type="number"
            inputMode="decimal"
            min={frozen ? undefined : limits?.chestMin}
            max={frozen ? undefined : limits?.chestMax}
            step="0.1"
            value={frozen ? "" : chestCm}
            disabled={frozen}
            readOnly={frozen}
            onChange={(e) => {
              if (frozen) return;
              onChestChange(e.target.value);
            }}
            placeholder={
              frozen
                ? "Chest (cm)"
                : `Chest (${limits!.chestMin}–${limits!.chestMax} cm)`
            }
            aria-label="Chest width in centimetres"
            className="h-10 glass rounded-full px-3 sm:px-4 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40 min-w-0 w-full disabled:cursor-not-allowed disabled:opacity-60"
          />
        ) : null}
      </div>
    </div>
  );
}
