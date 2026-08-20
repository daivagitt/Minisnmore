import { Link } from "@tanstack/react-router";
import { ShoppingBag } from "lucide-react";
import { cn } from "@/lib/utils";

export const EMPTY_CART_COPY = {
  headline: "Your basket is looking a bit lonely.",
  subtext: "Explore our latest arrivals and find something special to fill it up.",
  cta: "Discover new arrivals",
} as const;

type EmptyCartStateProps = {
  /** Smaller layout for the slide-out basket drawer. */
  compact?: boolean;
  className?: string;
  onCtaClick?: () => void;
};

export function EmptyCartState({ compact = false, className, onCtaClick }: EmptyCartStateProps) {
  return (
    <div className={cn("text-center", compact ? "py-12 px-2 space-y-3" : "p-8 md:p-12 space-y-4", className)}>
      <div
        className={cn(
          "mx-auto rounded-full glass grid place-items-center",
          compact ? "h-16 w-16" : "h-20 w-20",
        )}
      >
        <ShoppingBag className={cn("text-muted-foreground", compact ? "h-6 w-6" : "h-7 w-7")} />
      </div>
      <div className="space-y-2">
        <h1 className={cn("font-display text-balance", compact ? "text-xl" : "text-3xl md:text-4xl")}>
          {EMPTY_CART_COPY.headline}
        </h1>
        <p className={cn("text-muted-foreground text-balance max-w-md mx-auto", compact ? "text-sm" : "text-base")}>
          {EMPTY_CART_COPY.subtext}
        </p>
      </div>
      <Link
        to="/new-arrivals"
        onClick={onCtaClick}
        className={cn(
          "inline-block rounded-full bg-primary text-primary-foreground font-medium hover:shadow-glow transition",
          compact ? "px-5 py-2.5 text-sm mt-1" : "px-6 py-3 mt-2",
        )}
      >
        {EMPTY_CART_COPY.cta}
      </Link>
    </div>
  );
}
