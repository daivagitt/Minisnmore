import { Star } from "lucide-react";

type Props = {
  rating: number;
  className?: string;
  starClassName?: string;
};

export function ReviewStars({ rating, className = "flex gap-0.5", starClassName = "h-3.5 w-3.5" }: Props) {
  const clamped = Number.isFinite(rating) ? Math.min(5, Math.max(0, rating)) : 0;
  const label = Number.isInteger(clamped) ? String(clamped) : clamped.toFixed(1);

  return (
    <div className={className} aria-label={`${label} out of 5 stars`}>
      {Array.from({ length: 5 }).map((_, index) => {
        const fill = Math.min(1, Math.max(0, clamped - index));
        return (
          <span key={index} className={`relative inline-block ${starClassName}`}>
            <Star className={`${starClassName} text-muted-foreground`} aria-hidden />
            {fill > 0 ? (
              <span className="absolute inset-0 overflow-hidden" style={{ width: `${fill * 100}%` }} aria-hidden>
                <Star className={`${starClassName} fill-primary text-primary`} />
              </span>
            ) : null}
          </span>
        );
      })}
    </div>
  );
}
