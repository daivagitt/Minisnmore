import type { ReactNode, ThHTMLAttributes, TdHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

export function AdminCaseListTable({
  id,
  caption,
  children,
}: {
  id: string;
  caption: string;
  children: ReactNode;
}) {
  return (
    <div id={id} className="overflow-x-auto rounded-2xl border border-border">
      <table className="w-full min-w-[56rem] text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        {children}
      </table>
    </div>
  );
}

export function AdminCaseListHead({ columns }: { columns: readonly string[] }) {
  return (
    <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
      <tr>
        {columns.map((column) => (
          <th key={column} className="px-3 py-2 font-medium whitespace-nowrap">
            {column}
          </th>
        ))}
      </tr>
    </thead>
  );
}

export function AdminCaseTh({
  className,
  ...props
}: ThHTMLAttributes<HTMLTableCellElement>) {
  return <th className={cn("px-3 py-2 font-medium whitespace-nowrap", className)} {...props} />;
}

export function AdminCaseTd({
  className,
  ...props
}: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn("px-3 py-2.5 align-top", className)} {...props} />;
}

export const adminCaseRowClass = "border-t border-border/70";
