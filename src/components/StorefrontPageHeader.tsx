import { motion } from "framer-motion";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export const storefrontPageHeaderShellClass =
  "glass-strong rounded-[1.75rem] p-5 md:p-8 mb-5 md:mb-6";

export const storefrontHeaderEyebrowClass =
  "text-xs uppercase tracking-[0.28em] text-primary";

export const storefrontHeaderTitleClass = "font-display text-3xl md:text-5xl mt-1.5";

export const storefrontHeaderSubtitleClass =
  "mt-2 text-sm md:text-base text-muted-foreground max-w-xl";

type Props = {
  eyebrow: string;
  heading: ReactNode;
  subtitle?: string;
  children?: ReactNode;
  className?: string;
  animate?: boolean;
  align?: "left" | "center";
  bare?: boolean;
};

export function StorefrontPageHeader({
  eyebrow,
  heading,
  subtitle,
  children,
  className,
  animate = true,
  align = "left",
  bare = false,
}: Props) {
  const centered = align === "center";

  const content = (
    <>
      <div className={storefrontHeaderEyebrowClass}>{eyebrow}</div>
      <h1 className={cn(storefrontHeaderTitleClass, centered && "text-balance")}>{heading}</h1>
      {subtitle ? (
        <p className={cn(storefrontHeaderSubtitleClass, centered && "mx-auto")}>{subtitle}</p>
      ) : null}
      {children}
    </>
  );

  if (bare) {
    return (
      <header className={cn(centered && "text-center max-w-2xl mx-auto", className)}>
        {content}
      </header>
    );
  }

  const shell = (
    <div className={cn(storefrontPageHeaderShellClass, className)}>{content}</div>
  );

  if (!animate) return shell;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6 }}
    >
      {shell}
    </motion.div>
  );
}
