export type ExportColumn<T> = {
  header: string;
  value: (row: T) => string | number | boolean | null | undefined;
};

/** Neutralize spreadsheet formula injection for attacker-controlled export cells. */
export function sanitizeExportValue(value: string | number | boolean | null | undefined): string | number {
  if (value == null) return "";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "number") return Number.isFinite(value) ? value : "";
  const text = String(value);
  return /^[=+\-@\t\r\n]/.test(text) ? `'${text}` : text;
}

export function rowsForExport<T>(
  rows: readonly T[],
  columns: readonly ExportColumn<T>[],
): Record<string, string | number>[] {
  return rows.map((row) => {
    const out: Record<string, string | number> = {};
    for (const column of columns) {
      out[column.header] = sanitizeExportValue(column.value(row));
    }
    return out;
  });
}

export function exportFilename(prefix: string, now = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${prefix}-${year}${month}${day}.xlsx`;
}

export async function downloadTableAsXlsx(
  data: Record<string, string | number>[],
  filename: string,
  sheetName = "Export",
): Promise<void> {
  if (!data.length) {
    throw new Error("Nothing to export");
  }
  const XLSX = await import("xlsx");
  const wb = XLSX.utils.book_new();
  const sheet = XLSX.utils.json_to_sheet(data);
  XLSX.utils.book_append_sheet(wb, sheet, sheetName.slice(0, 31) || "Export");
  XLSX.writeFile(wb, filename);
}
