import { ReactNode } from "react";
import type { AdminSections } from "@/hooks/useAdminSections";
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

const COLLAPSIBLE_VARIANTS = {
  nested: {
    root: "rounded-2xl ring-1 ring-border/60 bg-white/30",
    header: "flex flex-wrap items-center hover:bg-white/25",
    trigger: "px-4 py-3",
    actions: "px-4 pb-3 sm:py-3 sm:pl-0",
    title: "text-sm font-medium",
    hint: "text-[11px] text-muted-foreground mt-0.5 leading-snug",
    chevron: "h-4 w-4",
    body: "space-y-4 px-4 pb-4",
  },
  panel: {
    root: "glass-strong rounded-2xl sm:rounded-3xl",
    header: "flex flex-wrap items-center",
    trigger: "p-4 sm:p-5",
    actions: "px-4 pb-4 sm:py-5 sm:pl-0 sm:pr-5",
    title: "font-display text-base sm:text-lg",
    hint: "text-[11px] text-muted-foreground mt-1 max-w-xl leading-snug",
    chevron: "h-5 w-5",
    body: "space-y-3 sm:space-y-4 px-4 pb-4 sm:px-5 sm:pb-5",
  },
} as const;

type AdminCollapsibleSectionProps = {
  title: ReactNode;
  hint?: ReactNode;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Header controls (e.g. a visibility checkbox) rendered outside the toggle so they stay clickable. */
  actions?: ReactNode;
  children: ReactNode;
};

/** Collapsible block: `nested` sits inside a card (product editor); `panel` is a top-level glass card. */
export function AdminCollapsibleSection({
  variant = "nested",
  title,
  hint,
  open,
  onOpenChange,
  actions,
  children,
}: AdminCollapsibleSectionProps & { variant?: keyof typeof COLLAPSIBLE_VARIANTS }) {
  const cls = COLLAPSIBLE_VARIANTS[variant];
  return (
    <Collapsible open={open} onOpenChange={onOpenChange} className={cls.root}>
      <div className={cls.header}>
        <CollapsibleTrigger
          type="button"
          className={`group flex min-w-[12rem] flex-1 items-center justify-between gap-3 text-left ${cls.trigger}`}
        >
          <span className="block min-w-0">
            <span className={`block ${cls.title}`}>{title}</span>
            {hint ? <span className={`block ${cls.hint}`}>{hint}</span> : null}
          </span>
          <ChevronDown
            className={`${cls.chevron} shrink-0 text-muted-foreground transition-transform duration-200 group-data-[state=open]:rotate-180`}
          />
        </CollapsibleTrigger>
        {actions ? <div className={`shrink-0 ${cls.actions}`}>{actions}</div> : null}
      </div>
      {/* Kept mounted while closed so child state (e.g. an upload in progress) survives collapsing. */}
      <CollapsibleContent forceMount hidden={!open}>
        <div className={cls.body}>{children}</div>
      </CollapsibleContent>
    </Collapsible>
  );
}

export function AdminPanelSection(props: AdminCollapsibleSectionProps) {
  return <AdminCollapsibleSection variant="panel" {...props} />;
}

export function AdminSectionToggles({
  sections,
  children,
}: {
  sections: Pick<AdminSections, "expandAll" | "collapseAll">;
  children?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div className="min-w-0">{children}</div>
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={sections.expandAll}
          className="text-xs text-primary story-link"
        >
          Expand all
        </button>
        <button
          type="button"
          onClick={sections.collapseAll}
          className="text-xs text-primary story-link"
        >
          Collapse all
        </button>
      </div>
    </div>
  );
}
