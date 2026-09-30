import { useState } from "react";
import { ImagePlus, Loader2, X } from "lucide-react";
import { toast } from "sonner";
import {
  IMAGE_ACCEPT,
  validateAdminImageFile,
} from "@/lib/admin-image";
import { MAX_RETURN_IMAGES, uploadReturnImage } from "@/lib/orders.functions";
import { showApiError } from "@/lib/ui-errors";

export function ReturnImageUpload({
  urls,
  onChange,
  disabled,
  onUploadingChange,
}: {
  urls: string[];
  onChange: (urls: string[]) => void;
  disabled?: boolean;
  onUploadingChange?: (uploading: boolean) => void;
}) {
  const [uploading, setUploading] = useState(false);
  const setUploadingState = (next: boolean) => {
    setUploading(next);
    onUploadingChange?.(next);
  };
  const remaining = Math.max(0, MAX_RETURN_IMAGES - urls.length);

  const upload = async (files: FileList | File[]) => {
    const selected = Array.from(files).slice(0, remaining);
    if (selected.length === 0) {
      toast.error(`You can attach up to ${MAX_RETURN_IMAGES} images`);
      return;
    }
    setUploadingState(true);
    try {
      const next = [...urls];
      for (const file of selected) {
        const validation = validateAdminImageFile(file);
        if (!validation.ok) {
          toast.error(validation.error);
          continue;
        }
        next.push(await uploadReturnImage(file));
      }
      onChange(next);
    } catch (error) {
      showApiError(error, "Could not upload image");
    } finally {
      setUploadingState(false);
    }
  };

  return (
    <div className="space-y-1.5">
      <p className="text-sm font-medium">Photos (optional)</p>
      <p className="text-xs text-muted-foreground">
        Add up to {MAX_RETURN_IMAGES} photos of the item. JPG, PNG, WebP, or GIF, max 8 MB each.
      </p>
      {urls.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {urls.map((url) => (
            <div key={url} className="relative h-20 w-20 overflow-hidden rounded-xl border border-border">
              <img src={url} alt="Return photo" className="h-full w-full object-cover" />
              <button
                type="button"
                disabled={disabled || uploading}
                onClick={() => onChange(urls.filter((item) => item !== url))}
                className="absolute right-1 top-1 grid h-6 w-6 place-items-center rounded-full bg-black/60 text-white"
                aria-label="Remove photo"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          ))}
        </div>
      ) : null}
      {remaining > 0 ? (
        <label
          className={`inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1.5 text-xs ${
            disabled || uploading ? "opacity-50 pointer-events-none" : "cursor-pointer hover:bg-muted/60"
          }`}
        >
          {uploading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <ImagePlus className="h-3.5 w-3.5" />}
          {uploading ? "Uploading…" : "Add photos"}
          <input
            type="file"
            accept={IMAGE_ACCEPT}
            multiple
            className="hidden"
            disabled={disabled || uploading}
            onChange={(event) => {
              const files = event.target.files;
              event.target.value = "";
              if (files?.length) void upload(files);
            }}
          />
        </label>
      ) : null}
    </div>
  );
}
