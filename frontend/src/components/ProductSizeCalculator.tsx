import { Ruler } from "lucide-react";
import type { MeasurementLimits } from "@/lib/product-fit";

export type SizeFitState = { size: string } | { error: string } | null;

function joinMeasurementNames(names: string[]): string {
  if (names.length === 0) return "length or chest";
  if (names.length === 1) return names[0]!.toLowerCase();
  return `${names.slice(0, -1).join(", ").toLowerCase()} or ${names[names.length - 1]!.toLowerCase()}`;
}

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

  const lengthLabel = frozen ? "Length" : limits?.lengthLabel || "Length";
  const chestLabel = frozen ? "Chest" : limits?.chestLabel || "Chest";
  const showLength = frozen || !!limits?.hasLength;
  const showChest = frozen || !!limits?.hasChest;
  const twoCols = showLength && showChest;
  const enteredNames = [
    ...(lengthCm.trim() ? [lengthLabel] : []),
    ...(chestCm.trim() ? [chestLabel] : []),
  ];

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
          Auto-selected <span className="text-primary font-medium">{sizeFit.size}</span> from{" "}
          {joinMeasurementNames(enteredNames)}.
        </p>
      ) : sizeFit && "error" in sizeFit ? (
        <p className="text-destructive text-xs leading-snug line-clamp-2">{sizeFit.error}</p>
      ) : (
        <p className="text-[11px] text-muted-foreground leading-snug">
          Enter {joinMeasurementNames([
            ...(showLength ? [lengthLabel] : []),
            ...(showChest ? [chestLabel] : []),
          ])}{" "}
          (cm) from the size guide to pick a size automatically.
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
                ? `${lengthLabel} (cm)`
                : `${lengthLabel} (${limits!.lengthMin}–${limits!.lengthMax} cm)`
            }
            aria-label={`${lengthLabel} in centimetres`}
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
                ? `${chestLabel} (cm)`
                : `${chestLabel} (${limits!.chestMin}–${limits!.chestMax} cm)`
            }
            aria-label={`${chestLabel} in centimetres`}
            className="h-10 glass rounded-full px-3 sm:px-4 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40 min-w-0 w-full disabled:cursor-not-allowed disabled:opacity-60"
          />
        ) : null}
      </div>
    </div>
  );
}
