import { useState } from "react";
import { FileText, Upload } from "lucide-react";
import { toast } from "sonner";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { uploadAdminCataloguePdf } from "@/lib/backend-data";
import { showApiError } from "@/lib/ui-errors";
import {
  PDF_ACCEPT,
  assertAllowedPdfFile,
  maxAdminPdfSizeLabelMb,
  UNSUPPORTED_PDF_TYPE_MESSAGE,
} from "@/lib/admin-pdf";

export function PdfUploadField({
  label,
  value,
  onChange,
  placeholder = "/e-catalogue.pdf or https://…",
}: {
  label: string;
  value: string;
  onChange: (url: string) => void;
  placeholder?: string;
}) {
  const [uploading, setUploading] = useState(false);

  const upload = async (file: File) => {
    try {
      assertAllowedPdfFile(file);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : UNSUPPORTED_PDF_TYPE_MESSAGE);
      return;
    }
    setUploading(true);
    try {
      const publicUrl = await uploadAdminCataloguePdf(file);
      onChange(publicUrl);
      toast.success("PDF uploaded — remember to save");
    } catch (error) {
      showApiError(error, "Upload failed");
    } finally {
      setUploading(false);
    }
  };

  return (
    <Field label={label}>
      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        {value ? (
          <a
            href={value}
            target="_blank"
            rel="noreferrer"
            className="h-20 w-32 rounded-xl glass flex flex-col items-center justify-center gap-1 shrink-0 text-xs text-muted-foreground hover:bg-white/60"
          >
            <FileText className="h-5 w-5" />
            View PDF
          </a>
        ) : null}
        <div className="flex flex-1 items-center gap-2 min-w-0">
          <input
            className={inputCls()}
            placeholder={placeholder}
            value={value}
            onChange={(e) => onChange(e.target.value)}
          />
          <label
            className={`rounded-full glass px-3 py-2 text-xs flex items-center gap-1.5 whitespace-nowrap shrink-0 transition ${
              uploading ? "opacity-50 pointer-events-none" : "cursor-pointer hover:bg-white/60"
            }`}
          >
            <Upload className="h-3.5 w-3.5" /> {uploading ? "Uploading…" : "Upload PDF"}
            <input
              type="file"
              accept={PDF_ACCEPT}
              className="hidden"
              disabled={uploading}
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.target.value = "";
                if (file) void upload(file);
              }}
            />
          </label>
        </div>
      </div>
      <p className="text-xs text-muted-foreground mt-1">
        Upload a PDF (max {maxAdminPdfSizeLabelMb()} MB), or paste a public URL / site path.
      </p>
    </Field>
  );
}
