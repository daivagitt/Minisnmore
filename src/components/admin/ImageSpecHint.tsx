import type { ImageSpec } from "@/lib/admin-image-specs";
import { formatImageSpec, imageSpecLines } from "@/lib/admin-image-specs";

/** Compact size guide shown under admin image/video fields. */
export function ImageSpecHint({ spec, className = "" }: { spec: ImageSpec; className?: string }) {
  const { ratio, size, notes } = imageSpecLines(spec);
  return (
    <p className={`text-xs text-muted-foreground leading-relaxed ${className}`}>
      <span className="font-medium text-foreground/85">Recommended size:</span> {size}
      <span className="mx-1.5 text-border">·</span>
      <span className="font-medium text-foreground/85">Aspect ratio:</span> {ratio}
      {notes ? (
        <>
          <span className="mx-1.5 text-border">·</span>
          {notes}
        </>
      ) : null}
    </p>
  );
}

/** Single-line variant for tight layouts */
export function ImageSpecHintCompact({ spec }: { spec: ImageSpec }) {
  return <p className="text-xs text-muted-foreground mt-1">{formatImageSpec(spec)}</p>;
}
