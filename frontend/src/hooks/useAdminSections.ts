import { useMemo, useState } from "react";

type SectionsState = { all: boolean | null; overrides: Record<string, boolean> };

const INITIAL_STATE: SectionsState = { all: null, overrides: {} };

export type AdminSections<Id extends string = string> = {
  /** Props for one collapsible panel: its own toggle wins, then the last Expand/Collapse all, then `defaultOpen`. */
  panel(id: Id, defaultOpen?: boolean): { open: boolean; onOpenChange: (open: boolean) => void };
  setOpen(id: Id, open: boolean): void;
  expandAll(): void;
  collapseAll(): void;
  reset(): void;
};

/** Open/closed state for a page of collapsible admin panels, including one panel per list row. Actions are stable. */
export function useAdminSections<Id extends string = string>(): AdminSections<Id> {
  const [state, setState] = useState<SectionsState>(INITIAL_STATE);
  const actions = useMemo(
    () => ({
      setOpen: (id: Id, open: boolean) =>
        setState((prev) => ({ ...prev, overrides: { ...prev.overrides, [id]: open } })),
      expandAll: () => setState({ all: true, overrides: {} }),
      collapseAll: () => setState({ all: false, overrides: {} }),
      reset: () => setState(INITIAL_STATE),
    }),
    [],
  );
  return {
    ...actions,
    panel: (id, defaultOpen = false) => ({
      open: state.overrides[id] ?? state.all ?? defaultOpen,
      onOpenChange: (open) => actions.setOpen(id, open),
    }),
  };
}
