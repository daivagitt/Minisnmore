import type { SizeGuideRow } from "@/lib/product-fit";
import type { ProductSizeChart } from "@/lib/product-size-charts";

function joinMeasurementNames(names: string[]): string {
  if (names.length === 0) return "length or chest";
  if (names.length === 1) return names[0]!.toLowerCase();
  return `${names.slice(0, -1).join(", ").toLowerCase()} or ${names[names.length - 1]!.toLowerCase()}`;
}

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
  const lengthLabel = rows.find((row) => row.lengthLabel)?.lengthLabel || "Length";
  const chestLabel = rows.find((row) => row.chestLabel)?.chestLabel || "Chest";
  const showLength = rows.some((row) => row.length !== "—");
  const showChest = rows.some((row) => row.chest !== "—");
  const showRanges = rows.length > 0 && (showLength || showChest);
  const calculatorNames = [
    ...(showLength ? [lengthLabel] : []),
    ...(showChest ? [chestLabel] : []),
  ];

  return (
    <div className={className}>
      {title ? (
        <div className="text-[11px] text-muted-foreground mb-3 truncate">{title}</div>
      ) : null}
      {chart ? (
        <div className="space-y-3">
          <p className="text-xs text-muted-foreground">
            Measurements in cm unless marked otherwise. {chart.tolerance}
          </p>
          {chart.sections.map((section, index) => (
            <div key={`${section.title}-${index}`} className="overflow-x-auto -mx-1 px-1">
              <table className="w-full text-sm min-w-[20rem]">
                <caption className="text-left font-medium py-2">{section.title}</caption>
                <thead>
                  <tr className="text-xs uppercase tracking-wider text-muted-foreground">
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
        </div>
      ) : null}
      {showRanges ? (
        <div className={chart ? "mt-5" : undefined}>
          {chart ? (
            <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-2">
              Size calculator ranges
            </div>
          ) : null}
          <div className="overflow-x-auto -mx-1 px-1">
            <table className="w-full text-sm min-w-[16rem]">
              <thead>
                <tr className="text-xs uppercase tracking-wider text-muted-foreground">
                  <th className="text-left py-2">Size</th>
                  {showLength ? <th>{lengthLabel}</th> : null}
                  {showChest ? <th>{chestLabel}</th> : null}
                </tr>
              </thead>
              <tbody className="text-foreground/80">
                {rows.map((row) => (
                  <tr key={row.size} className="border-t border-border/40">
                    <td className="py-2 font-medium">{row.size}</td>
                    {showLength ? (
                      <td className="text-center tabular-nums">{row.length}</td>
                    ) : null}
                    {showChest ? (
                      <td className="text-center tabular-nums">{row.chest}</td>
                    ) : null}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
      <p className="text-xs text-muted-foreground mt-4">
        Simply enter your baby’s {joinMeasurementNames(calculatorNames)} measurement into the size
        calculator below to find the right size.
      </p>
    </div>
  );
}
