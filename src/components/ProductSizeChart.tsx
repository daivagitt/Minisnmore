import type { SizeGuideRow } from "@/lib/product-fit";

export function ProductSizeChartTables({
  rows,
  className = "",
  title,
}: {
  rows: SizeGuideRow[];
  className?: string;
  title?: string | null;
}) {
  return (
    <div className={className}>
      {title ? (
        <div className="text-[11px] text-muted-foreground mb-3 truncate">{title}</div>
      ) : null}
      <div className="overflow-x-auto -mx-1 px-1">
        <table className="w-full text-sm min-w-[16rem]">
          <thead>
            <tr className="text-xs uppercase tracking-wider text-muted-foreground">
              <th className="text-left py-2">Size</th>
              <th>Length</th>
              <th>Chest</th>
            </tr>
          </thead>
          <tbody className="text-foreground/80">
            {rows.map((row) => (
              <tr key={row.size} className="border-t border-border/40">
                <td className="py-2 font-medium">{row.size}</td>
                <td className="text-center tabular-nums">{row.length}</td>
                <td className="text-center tabular-nums">{row.chest}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-muted-foreground mt-4">
        Simply enter your baby’s length or chest measurement into the size calculator below to
        find the right size.
      </p>
    </div>
  );
}
