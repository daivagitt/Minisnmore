import type { SizeGuideRow } from "@/lib/product-fit";
import type { ProductSizeChart } from "@/lib/product-size-charts";

export function ProductSizeChartTables({
  rows,
  className = "",
  title,
  chart,
}: {
  rows: SizeGuideRow[];
  className?: string;
  title?: string | null;
  chart?: ProductSizeChart | null;
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
      {chart ? (
        <details className="mt-4">
          <summary className="cursor-pointer text-sm font-medium">All garment measurements</summary>
          <p className="text-xs text-muted-foreground mt-2">
            Measurements in cm unless marked otherwise. {chart.tolerance}
          </p>
          {chart.sections.map((section, index) => (
            <div key={`${section.title}-${index}`} className="overflow-x-auto mt-3">
              <table className="w-full text-sm min-w-[20rem]">
                <caption className="text-left font-medium py-2">{section.title}</caption>
                <thead>
                  <tr>
                    <th scope="col" className="text-left py-2">
                      Measurement
                    </th>
                    {section.bands.map((band) => (
                      <th scope="col" key={band} className="px-2 whitespace-nowrap">
                        {band}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {section.rows.map((row, rowIndex) => (
                    <tr key={`${row.label}-${rowIndex}`} className="border-t border-border/40">
                      <th scope="row" className="text-left py-2 font-normal">
                        {row.label}
                      </th>
                      {section.bands.map((band) => (
                        <td key={band} className="px-2 text-center tabular-nums">
                          {row.values[band] === "" || row.values[band] == null
                            ? "—"
                            : row.values[band]}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
        </details>
      ) : null}
      <p className="text-xs text-muted-foreground mt-4">
        Simply enter your baby’s length or chest measurement into the size calculator below to find
        the right size.
      </p>
    </div>
  );
}
