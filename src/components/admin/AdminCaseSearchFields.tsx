import { Search } from "lucide-react";

export function AdminCaseSearchFields({
  orderQuery,
  onOrderQueryChange,
  from,
  to,
  onFromChange,
  onToChange,
}: {
  orderQuery: string;
  onOrderQueryChange: (value: string) => void;
  from: string;
  to: string;
  onFromChange: (value: string) => void;
  onToChange: (value: string) => void;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-[minmax(12rem,1.5fr)_repeat(2,minmax(10rem,1fr))]">
      <label className="block min-w-0">
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground">
          Order ID
        </span>
        <span className="relative block">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden
          />
          <input
            type="search"
            value={orderQuery}
            onChange={(event) => onOrderQueryChange(event.target.value)}
            placeholder="Search by order ID"
            aria-label="Search by order ID"
            autoComplete="off"
            className="h-10 w-full rounded-xl border border-border bg-background py-2 pl-9 pr-3 text-sm"
          />
        </span>
      </label>
      <label className="block min-w-0">
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground">
          From date
        </span>
        <input
          type="date"
          value={from}
          onChange={(event) => onFromChange(event.target.value)}
          aria-label="From date"
          className="h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
        />
      </label>
      <label className="block min-w-0">
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground">
          To date
        </span>
        <input
          type="date"
          value={to}
          onChange={(event) => onToChange(event.target.value)}
          aria-label="To date"
          className="h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
        />
      </label>
      {Boolean(orderQuery.trim() || from.trim() || to.trim()) && (
        <p className="text-xs text-muted-foreground sm:col-span-3">
          Searching all cases — status filters pause until you clear.
        </p>
      )}
    </div>
  );
}
