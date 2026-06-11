import { useRef, useState } from "react";
import { toast } from "sonner";
import { Upload, FileDown, Loader2 } from "lucide-react";

export type FieldSpec = {
  key: string;
  required?: boolean;
  example?: string | number | boolean;
  note?: string;
};

export type BulkImportProps = {
  entityLabel: string; // e.g. "Categories"
  templateName: string; // e.g. "categories"
  fields: FieldSpec[];
  /** Parse one row (plain object keyed by header) into an insert payload, or throw with a friendly message. */
  transform: (row: Record<string, any>, index: number) => Record<string, any> | Promise<Record<string, any>>;
  /** Persist a batch of validated payloads. Should upsert. */
  persist: (payloads: Record<string, any>[]) => Promise<{ inserted: number; errors?: string[] }>;
  onDone?: () => void;
};

export function BulkImport({ entityLabel, templateName, fields, transform, persist, onDone }: BulkImportProps) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const downloadTemplate = async () => {
    const XLSX = await import("xlsx");
    const headers = fields.map((f) => f.key);
    const example: Record<string, any> = {};
    fields.forEach((f) => (example[f.key] = f.example ?? ""));
    const sheet = XLSX.utils.json_to_sheet([example], { header: headers });
    const guide = XLSX.utils.json_to_sheet(
      fields.map((f) => ({
        Field: f.key,
        Required: f.required ? "Yes" : "Optional",
        Example: String(f.example ?? ""),
        Notes: f.note ?? "",
      })),
    );
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, sheet, templateName);
    XLSX.utils.book_append_sheet(wb, guide, "Field guide");
    XLSX.writeFile(wb, `${templateName}-template.xlsx`);
  };

  const handleFile = async (file: File) => {
    setBusy(true);
    try {
      const XLSX = await import("xlsx");
      const buf = await file.arrayBuffer();
      const wb = XLSX.read(buf, { type: "array" });
      const ws = wb.Sheets[wb.SheetNames[0]];
      const rows: Record<string, any>[] = XLSX.utils.sheet_to_json(ws, { defval: "" });
      if (!rows.length) {
        toast.error("No rows found in the first sheet");
        return;
      }
      const payloads: Record<string, any>[] = [];
      const errors: string[] = [];
      for (let i = 0; i < rows.length; i++) {
        try {
          const payload = await transform(rows[i], i);
          payloads.push(payload);
        } catch (e: any) {
          errors.push(`Row ${i + 2}: ${e?.message ?? "invalid"}`);
        }
      }
      if (!payloads.length) {
        toast.error(`Nothing imported · ${errors.length} error(s)`, { description: errors.slice(0, 5).join("\n") });
        return;
      }
      const res = await persist(payloads);
      const errs = [...errors, ...(res.errors ?? [])];
      if (errs.length) {
        toast.warning(`${res.inserted} ${entityLabel} imported · ${errs.length} skipped`, {
          description: errs.slice(0, 5).join("\n"),
          duration: 8000,
        });
      } else {
        toast.success(`${res.inserted} ${entityLabel} imported`);
      }
      onDone?.();
    } catch (e: any) {
      toast.error("Import failed", { description: e?.message });
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  return (
    <div className="flex items-center gap-2 flex-wrap">
      <button
        type="button"
        onClick={downloadTemplate}
        className="rounded-full glass px-3.5 py-2 text-xs flex items-center gap-1.5 hover:bg-white/60 transition"
        title={`Download sample ${entityLabel} template`}
      >
        <FileDown className="h-3.5 w-3.5" /> Sample template
      </button>
      <label className={`rounded-full bg-primary/10 text-primary border border-primary/30 px-3.5 py-2 text-xs flex items-center gap-1.5 cursor-pointer hover:bg-primary/15 transition ${busy ? "opacity-60 pointer-events-none" : ""}`}>
        {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Upload className="h-3.5 w-3.5" />}
        {busy ? "Importing…" : "Bulk import (.xlsx)"}
        <input
          ref={fileRef}
          type="file"
          accept=".xlsx,.xls,.csv"
          className="hidden"
          onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0])}
        />
      </label>
    </div>
  );
}
