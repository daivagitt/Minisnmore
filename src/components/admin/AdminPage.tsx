import { ReactNode } from "react";

export function AdminPage({
  eyebrow, title, description, action, children,
}: {
  eyebrow?: string; title: string; description?: string; action?: ReactNode; children: ReactNode;
}) {
  return (
    <div className="space-y-6">
      <div className="glass-strong rounded-[1.5rem] md:rounded-[2rem] p-6 md:p-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          {eyebrow && <div className="text-xs uppercase tracking-[0.28em] text-primary">{eyebrow}</div>}
          <h1 className="font-display text-2xl md:text-4xl mt-1.5">{title}</h1>
          {description && <p className="text-sm text-muted-foreground mt-2 max-w-xl">{description}</p>}
        </div>
        {action}
      </div>
      {children}
    </div>
  );
}

export function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="block">
      <div className="text-xs uppercase tracking-wider text-muted-foreground">{label}</div>
      {hint ? <p className="text-[11px] text-muted-foreground mt-1 leading-snug">{hint}</p> : null}
      <div className="mt-1.5">{children}</div>
    </div>
  );
}

export function inputCls() {
  return "w-full rounded-2xl glass px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-primary/40";
}