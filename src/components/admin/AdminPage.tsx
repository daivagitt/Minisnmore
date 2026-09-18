import { ReactNode } from "react";
import { ChevronDown } from "lucide-react";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";

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
  required,
  children,
}: {
  label: string;
  hint?: string;
  required?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="block">
      <div className="text-xs uppercase tracking-wider text-muted-foreground">
        {label}
        {required ? <span className="text-destructive" aria-hidden="true"> *</span> : null}
      </div>
      {hint ? <p className="text-[11px] text-muted-foreground mt-1 leading-snug">{hint}</p> : null}
      <div className="mt-1.5">{children}</div>
    </div>
  );
}

export function inputCls() {
  return "w-full rounded-2xl glass px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-primary/40";
}

export function AdminCollapsibleSection({
  title,
  hint,
  open,
  onOpenChange,
  children,
}: {
  title: string;
  hint?: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: ReactNode;
}) {
  return (
    <Collapsible open={open} onOpenChange={onOpenChange} className="rounded-2xl ring-1 ring-border/60 bg-white/30">
      <CollapsibleTrigger
        type="button"
        className="group flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-white/25"
      >
        <div className="min-w-0">
          <div className="text-sm font-medium">{title}</div>
          {hint ? <p className="text-[11px] text-muted-foreground mt-0.5 leading-snug">{hint}</p> : null}
        </div>
        <ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground transition-transform duration-200 group-data-[state=open]:rotate-180" />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="space-y-4 px-4 pb-4">{children}</div>
      </CollapsibleContent>
    </Collapsible>
  );
}
