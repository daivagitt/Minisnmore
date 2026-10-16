import { ImageIcon } from "lucide-react";
import { useState } from "react";
import { AdminProductImage } from "@/components/admin/AdminProductImage";
import { Field } from "@/components/admin/AdminPage";
import { ImageSpecHint } from "@/components/admin/ImageSpecHint";
import { ADMIN_IMAGE_SPECS } from "@/lib/admin-image-specs";
import { getDisplayImageDragUrl, isDisplayImageDrag } from "@/lib/admin-display-image";
import { normalizeProductImageUrl } from "@/lib/backend-data";

export function DisplayImageField({
  imageUrl,
  autoFromPrints,
  onSetDisplayImage,
  onClear,
}: {
  imageUrl: string | null;
  /** True when shop cards still follow the first print photo (no explicit listing image). */
  autoFromPrints?: boolean;
  onSetDisplayImage: (url: string) => void;
  onClear?: () => void;
}) {
  const [over, setOver] = useState(false);
  const preview = imageUrl ? (normalizeProductImageUrl(imageUrl) ?? imageUrl) : "";

  return (
    <Field
      label="Display image"
      hint="Shown on shop cards, trending, and search. Drag a photo from colour galleries or variant photos."
    >
      <ImageSpecHint spec={ADMIN_IMAGE_SPECS.productGallery} className="mb-2" />
      <div
        onDragOver={(event) => {
          if (!isDisplayImageDrag(event.dataTransfer)) return;
          event.preventDefault();
          event.dataTransfer.dropEffect = "copy";
          if (!over) setOver(true);
        }}
        onDragLeave={(event) => {
          const next = event.relatedTarget as Node | null;
          if (next && event.currentTarget.contains(next)) return;
          setOver(false);
        }}
        onDrop={(event) => {
          event.preventDefault();
          setOver(false);
          const url = getDisplayImageDragUrl(event.dataTransfer);
          if (url) onSetDisplayImage(url);
        }}
        className={`relative flex min-h-[8.5rem] items-stretch gap-3 rounded-2xl border-2 border-dashed p-3 transition ${
          over ? "border-primary bg-primary/5" : "border-border bg-white/30"
        }`}
        role="group"
        aria-label="Storefront display image"
      >
        <div className="relative h-28 w-28 shrink-0 overflow-hidden rounded-xl ring-1 ring-border bg-muted">
          {preview ? (
            <AdminProductImage src={preview} alt="" className="h-full w-full object-cover" />
          ) : (
            <div className="grid h-full w-full place-items-center text-muted-foreground">
              <ImageIcon className="h-7 w-7" aria-hidden />
            </div>
          )}
          {preview ? (
            <span className="absolute bottom-1 left-1 rounded bg-foreground/70 px-1.5 py-0.5 text-[9px] font-medium uppercase tracking-wide text-background">
              Shop
            </span>
          ) : null}
        </div>
        <div className="flex min-w-0 flex-1 flex-col justify-center gap-1">
          <p className="text-sm font-medium">
            {over ? "Drop to use this photo" : preview ? "Current storefront photo" : "No display image yet"}
          </p>
          <p className="text-xs text-muted-foreground leading-snug">
            {autoFromPrints
              ? "Using the first print gallery photo until you drop a specific image here."
              : "Drag any variant or print photo onto this tile, then click Save."}
          </p>
          {preview && onClear ? (
            <button
              type="button"
              onClick={onClear}
              className="mt-1 self-start text-xs font-medium text-muted-foreground underline-offset-2 hover:underline"
            >
              Clear and use first print photo
            </button>
          ) : null}
        </div>
      </div>
    </Field>
  );
}
