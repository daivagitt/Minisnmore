import { motion } from "framer-motion";
import { useEffect, useMemo, useState } from "react";

type Variant = "petals" | "stars" | "bubbles" | "leaves";

interface Props { count?: number; variant?: Variant; className?: string; }

export function FloatingDecor({ count = 14, variant = "petals", className = "" }: Props) {
  // Halve the count on small screens, and skip animations entirely if the
  // user has prefers-reduced-motion. Both are big wins for low-end devices.
  const [reduced, setReduced] = useState(false);
  const [isSmall, setIsSmall] = useState(false);
  useEffect(() => {
    const m = window.matchMedia("(prefers-reduced-motion: reduce)");
    const s = window.matchMedia("(max-width: 767px)");
    const sync = () => { setReduced(m.matches); setIsSmall(s.matches); };
    sync();
    m.addEventListener("change", sync); s.addEventListener("change", sync);
    return () => { m.removeEventListener("change", sync); s.removeEventListener("change", sync); };
  }, []);
  const effectiveCount = reduced ? 0 : Math.max(2, Math.round(count * (isSmall ? 0.4 : 1)));
  const items = useMemo(
    () => Array.from({ length: effectiveCount }).map((_, i) => ({
      id: i, left: Math.random() * 100, delay: Math.random() * 8,
      duration: 14 + Math.random() * 14, size: 10 + Math.random() * 22,
      drift: (Math.random() - 0.5) * 120, rotate: Math.random() * 360,
      opacity: 0.25 + Math.random() * 0.45,
    })), [effectiveCount]);
  if (effectiveCount === 0) return null;

  return (
    <div aria-hidden className={`pointer-events-none absolute inset-0 overflow-hidden ${className}`}>
      {items.map((it) => (
        <motion.div key={it.id}
          initial={{ y: "-10%", x: 0, rotate: it.rotate, opacity: 0 }}
          animate={{ y: "115%", x: it.drift, rotate: it.rotate + 360, opacity: [0, it.opacity, it.opacity, 0] }}
          transition={{ duration: it.duration, delay: it.delay, repeat: Infinity, ease: "linear" }}
          style={{ left: `${it.left}%`, width: it.size, height: it.size }}
          className="absolute">
          <Shape variant={variant} />
        </motion.div>
      ))}
    </div>
  );
}

function Shape({ variant }: { variant: Variant }) {
  if (variant === "stars") {
    return (
      <svg viewBox="0 0 24 24" className="h-full w-full text-primary/60">
        <path fill="currentColor" d="M12 2l2.39 6.96H21l-5.6 4.07L17.78 20 12 15.9 6.22 20l2.38-6.97L3 8.96h6.61z" />
      </svg>
    );
  }
  if (variant === "bubbles") {
    return (
      <div className="h-full w-full rounded-full"
        style={{
          background: "radial-gradient(circle at 30% 30%, oklch(1 0 0 / 0.85), oklch(0.78 0.07 50 / 0.35) 60%, transparent 70%)",
          boxShadow: "inset 0 0 6px oklch(1 0 0 / 0.6)",
        }} />
    );
  }
  if (variant === "leaves") {
    return (
      <svg viewBox="0 0 32 32" className="h-full w-full">
        <defs>
          <linearGradient id="leaf-g" x1="0" x2="1" y1="0" y2="1">
            <stop offset="0%" stopColor="oklch(0.74 0.06 60)" />
            <stop offset="100%" stopColor="oklch(0.58 0.115 45)" />
          </linearGradient>
        </defs>
        <path
          d="M4 28 C 4 14, 16 4, 28 4 C 28 16, 18 28, 4 28 Z M4 28 C 10 22, 18 14, 26 8"
          fill="url(#leaf-g)"
          opacity="0.85"
          stroke="oklch(0.42 0.08 40 / 0.5)"
          strokeWidth="0.5"
          strokeLinecap="round"
        />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 32 32" className="h-full w-full">
      <defs>
        <linearGradient id="petal-g" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0%" stopColor="oklch(0.86 0.06 60)" />
          <stop offset="100%" stopColor="oklch(0.62 0.12 45)" />
        </linearGradient>
      </defs>
      <path fill="url(#petal-g)" opacity="0.85"
        d="M16 2 C22 8, 30 12, 28 20 C26 28, 18 30, 16 30 C14 30, 6 28, 4 20 C2 12, 10 8, 16 2 Z" />
    </svg>
  );
}
