import { useState } from "react";
import { Upload } from "lucide-react";
import { toast } from "sonner";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { uploadAdminImage } from "@/lib/backend-data";
import { showApiError } from "@/lib/ui-errors";
import {
  type AdminImageFolder,
  IMAGE_ACCEPT,
  assertAllowedImageFile,
  UNSUPPORTED_IMAGE_TYPE_MESSAGE,
} from "@/lib/admin-image";
import {
  ADMIN_IMAGE_SPECS,
  type AdminImageSpecKey,
  type ImageSpec,
} from "@/lib/admin-image-specs";
import { ImageSpecHint } from "@/components/admin/ImageSpecHint";

export function ImageUploadField({
  label,
  value,
  onChange,
  folder,
  placeholder = "https://… or upload",
  previewClassName = "h-20 w-32 rounded-xl object-cover shrink-0",
  hint,
  spec,
}: {
  label: string;
  value: string;
  onChange: (url: string) => void;
  folder: AdminImageFolder;
  placeholder?: string;
  previewClassName?: string;
  hint?: string;
  /** Preset or custom recommended dimensions for this slot */
  spec?: AdminImageSpecKey | ImageSpec;
}) {
  const sizeSpec = typeof spec === "string" ? ADMIN_IMAGE_SPECS[spec] : spec;
  const [uploading, setUploading] = useState(false);

  const upload = async (file: File) => {
    try {
      assertAllowedImageFile(file);
    } catch {
      toast.error(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
      return;
    }
    setUploading(true);
    try {
      const publicUrl = await uploadAdminImage(folder, file);
      onChange(publicUrl);
      toast.success("Image uploaded — remember to save");
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
          <img src={value} alt="" className={previewClassName} />
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
            <Upload className="h-3.5 w-3.5" /> {uploading ? "Uploading…" : "Upload"}
            <input
              type="file"
              accept={IMAGE_ACCEPT}
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
      {sizeSpec ? <ImageSpecHint spec={sizeSpec} className="mt-1.5" /> : null}
      {hint ? <p className="text-xs text-muted-foreground mt-1">{hint}</p> : null}
    </Field>
  );
}
