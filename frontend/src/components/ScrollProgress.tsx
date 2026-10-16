import { motion, useScroll, useSpring } from "framer-motion";
import type { RefObject } from "react";

export function ScrollProgress({
  containerRef,
}: {
  containerRef?: RefObject<HTMLElement | null>;
}) {
  const { scrollYProgress } = useScroll(containerRef ? { container: containerRef } : undefined);
  const scaleX = useSpring(scrollYProgress, { stiffness: 120, damping: 24, mass: 0.3 });
  return (
    <motion.div style={{ scaleX, transformOrigin: "0% 50%" }}
      className="fixed top-0 left-0 right-0 h-[3px] z-[60] bg-gradient-to-r from-primary via-accent to-primary/70"
      aria-hidden />
  );
}
