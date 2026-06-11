import { motion } from "framer-motion";
import { Sparkles } from "lucide-react";

/**
 * Section divider styled to match the offers marquee directly above it
 * (rounded-full pill, h-12) while retaining the brand colour palette.
 */
export function SectionDivider({
  label = "Mini's n More",
  compact: _compact = false,
}: { label?: string; flip?: boolean; compact?: boolean }) {
  return (
    <section className="px-3 md:px-6 select-none my-2 md:my-3" aria-hidden>
      <div className="relative h-12 overflow-hidden rounded-full bg-primary text-primary-foreground shadow-soft">
        <motion.div
          className="absolute inset-y-0 left-0 flex items-center gap-10 whitespace-nowrap pl-6 text-xs uppercase tracking-[0.22em]"
          initial={{ x: 0 }}
          animate={{ x: "-50%" }}
          transition={{ duration: 40, repeat: Infinity, ease: "linear" }}
        >
          {Array.from({ length: 16 }).map((_, i) => (
            <span key={i} className="inline-flex items-center gap-2">
              <Sparkles className="h-3.5 w-3.5 text-primary-foreground" /> {label}
            </span>
          ))}
        </motion.div>
      </div>
    </section>
  );
}
