import { cn } from "@/lib/utils";

type Props = {
  note: string | null | undefined;
  variant?: "overlay" | "inline";
  className?: string;
};

export function SizeFeatureNote({ note, variant = "inline", className }: Props) {
  const text = note?.trim();
  if (!text) return null;

  if (variant === "overlay") {
    return (
      <div
        className={cn("absolute bottom-3 left-3 right-3 z-10 pointer-events-none", className)}
        aria-hidden="true"
      >
        <p className="rounded-2xl bg-white/92 backdrop-blur-sm px-3 py-1.5 text-xs sm:text-sm font-medium text-foreground text-center shadow-sm border border-white/70 leading-snug">
          {text}
        </p>
      </div>
    );
  }

  return (
    <p
      className={cn(
        "mt-1 rounded-lg bg-primary/10 px-3 py-1.5 text-sm font-medium text-primary leading-snug",
        className,
      )}
      role="status"
      aria-live="polite"
    >
      {text}
    </p>
  );
}
