import { ChevronLeft, ChevronRight, GripVertical, Loader2, Upload, X } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { AdminProductImage } from "@/components/admin/AdminProductImage";
import { Field } from "@/components/admin/AdminPage";
import { ImageSpecHint } from "@/components/admin/ImageSpecHint";
import { ADMIN_IMAGE_SPECS } from "@/lib/admin-image-specs";
import { IMAGE_ACCEPT, validateAdminImageFile } from "@/lib/admin-image";
import { prepareProductImageForUpload } from "@/lib/product-image-normalize";
import { uploadAdminImage } from "@/lib/backend-data";
import { getGalleryReorderKey, setDisplayImageDragData } from "@/lib/admin-display-image";
import {
  MAX_COLOR_IMAGES,
  colorImageUrls,
  colorNames,
  ensureColorEntries,
  hexForColorName,
  moveItem,
  type ProductColorEntry,
} from "@/lib/product-colors";
import { getUserErrorMessage, showApiError } from "@/lib/ui-errors";
import { sanitizePublicUrl, toDirectDownloadUrl } from "@/lib/utils";

function parseGalleryDrag(raw: string): { color: number; image: number } | null {
  const [colorRaw, imageRaw] = raw.split(":");
  const color = Number(colorRaw);
  const image = Number(imageRaw);
  if (!Number.isInteger(color) || !Number.isInteger(image)) return null;
  return { color, image };
}

function previewUrl(url: string): string {
  const direct = toDirectDownloadUrl(url.trim());
  return sanitizePublicUrl(direct) ?? url;
}

const GALLERY_PREVIEW_LIMIT = 2;

function UploadTile({
  busy,
  multiple,
  ariaLabel,
  onFiles,
}: {
  busy?: boolean;
  multiple?: boolean;
  ariaLabel: string;
  onFiles: (files: FileList) => void;
}) {
  return (
    <div
      className={`relative h-20 w-20 shrink-0 rounded-xl border-2 border-dashed border-border text-muted-foreground overflow-hidden ${
        busy ? "opacity-50" : "hover:bg-white/30"
      }`}
    >
      <input
        type="file"
        accept={IMAGE_ACCEPT}
        multiple={multiple}
        disabled={busy}
        aria-label={ariaLabel}
        className="absolute inset-0 z-10 h-full w-full cursor-pointer opacity-0 disabled:cursor-not-allowed"
        onChange={(e) => {
          const files = e.target.files;
          if (files?.length) onFiles(files);
          e.target.value = "";
        }}
      />
      <div className="pointer-events-none absolute inset-0 grid place-items-center">
        {busy ? (
          <Loader2 className="h-4 w-4 animate-spin" />
        ) : (
          <div className="text-center px-1">
            <Upload className="h-4 w-4 mx-auto" />
            <div className="text-[9px] mt-0.5 leading-tight">Upload</div>
          </div>
        )}
      </div>
    </div>
  );
}

export function ColorImagesEditor({
  colors,
  onPatchColor,
  onGalleryUploaded,
  onSwatchUploaded,
  freezeSwatches = false,
}: {
  colors: ProductColorEntry[];
  onPatchColor: (index: number, patch: Partial<ProductColorEntry>) => void;
  /** Persist gallery URLs immediately so uploads are not lost if Save is skipped. */
  onGalleryUploaded?: (patch: { index: number; imageUrls: string[] }) => void | Promise<void>;
  /** Persist swatch URL immediately after upload. */
  onSwatchUploaded?: (patch: { index: number; swatchUrl: string }) => void | Promise<void>;
  /** Print swatches come from Zoho pull — show read-only previews. */
  freezeSwatches?: boolean;
}) {
  const normalizedColors = ensureColorEntries(colors);
  const [uploadingKey, setUploadingKey] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [dragging, setDragging] = useState<{ color: number; image: number } | null>(null);
  const [dropTarget, setDropTarget] = useState<{ color: number; image: number } | null>(null);
  const [expandedGalleries, setExpandedGalleries] = useState<Set<number>>(() => new Set());

  const names = colorNames(normalizedColors);
  if (!names.length) {
    return (
      <Field label="Colour images">
        <p className="text-xs text-muted-foreground rounded-xl glass px-3 py-2">
          Add colours above (comma-separated or via the variant matrix), then upload print swatches and/or per-colour gallery photos.
        </p>
      </Field>
    );
  }

  const validateFiles = (files: FileList): File[] => {
    const accepted: File[] = [];
    for (const file of Array.from(files)) {
      const result = validateAdminImageFile(file);
      if (result.ok) {
        accepted.push(file);
      } else {
        setStatus(result.error);
        toast.error(result.error);
      }
    }
    return accepted;
  };

  const uploadOne = async (key: string, file: File, role: "gallery" | "swatch") => {
    setUploadingKey(key);
    setStatus("Uploading…");
    try {
      const prepared = await prepareProductImageForUpload(file, role);
      const url = await uploadAdminImage("products", prepared);
      setStatus(null);
      return url;
    } catch (error) {
      const message = getUserErrorMessage(error, "Upload failed");
      setStatus(message);
      showApiError(error, "Upload failed");
      throw error;
    } finally {
      setUploadingKey(null);
    }
  };

  const handleSwatchUpload = async (index: number, files: FileList) => {
    const file = validateFiles(files)[0];
    if (!file) return;
    const key = `swatch-${index}`;
    try {
      const url = await uploadOne(key, file, "swatch");
      onPatchColor(index, { swatchUrl: url });
      toast.success(`Swatch uploaded for ${normalizedColors[index]?.name ?? "colour"}`);
      try {
        await onSwatchUploaded?.({ index, swatchUrl: url });
      } catch {
        toast.error("Swatch uploaded but could not auto-save — click Save on the product.");
      }
    } catch {
      /* toast + inline status already set */
    }
  };

  const handleGalleryUpload = async (index: number, files: FileList) => {
    const entry = normalizedColors[index];
    if (!entry) return;
    const current = colorImageUrls(entry);
    const room = MAX_COLOR_IMAGES - current.length;
    if (room <= 0) {
      const message = `Maximum ${MAX_COLOR_IMAGES} gallery images per colour`;
      setStatus(message);
      toast.error(message);
      return;
    }
    const batch = validateFiles(files).slice(0, room);
    if (!batch.length) return;

    const urls: string[] = [];
    for (const [offset, file] of batch.entries()) {
      const key = `gallery-${index}-${offset}`;
      try {
        urls.push(await uploadOne(key, file, "gallery"));
      } catch {
        break;
      }
    }
    if (!urls.length) return;
    const imageUrls = [...current, ...urls].slice(0, MAX_COLOR_IMAGES);
    toast.success(`${urls.length} gallery image(s) uploaded for ${entry.name}.`);
    await persistGallery(index, imageUrls);
  };

  const persistGallery = async (index: number, imageUrls: string[]) => {
    onPatchColor(index, { imageUrls });
    try {
      await onGalleryUploaded?.({ index, imageUrls });
    } catch {
      toast.error("Gallery updated but could not auto-save — click Save on the product.");
    }
  };

  const removeGalleryImage = (index: number, url: string) => {
    const entry = normalizedColors[index];
    if (!entry) return;
    void persistGallery(index, colorImageUrls(entry).filter((item) => item !== url));
  };

  const moveGalleryImage = (index: number, from: number, to: number) => {
    const entry = normalizedColors[index];
    if (!entry) return;
    const current = colorImageUrls(entry);
    const imageUrls = moveItem(current, from, to);
    if (imageUrls.every((url, i) => url === current[i])) return;
    onPatchColor(index, { imageUrls });
  };

  return (
    <div className="space-y-4">
      {status ? (
        <p className="text-xs rounded-lg px-3 py-2 bg-destructive/10 text-destructive leading-snug" role="status">
          {status}
        </p>
      ) : null}
      {uploadingKey ? (
        <p className="text-xs text-muted-foreground flex items-center gap-2" role="status">
          <Loader2 className="h-3.5 w-3.5 animate-spin shrink-0" />
          Uploading image…
        </p>
      ) : null}

      <Field
        label="Print swatches"
        hint={
          freezeSwatches
            ? "Swatches are frozen after Zoho inventory sync."
            : undefined
        }
      >
        <ImageSpecHint spec={ADMIN_IMAGE_SPECS.printSwatch} className="mb-2" />
        <div className="grid gap-3 sm:grid-cols-2">
          {normalizedColors.map((entry, index) => {
            const swatch = entry.swatchUrl?.trim() || null;
            const busy = uploadingKey === `swatch-${index}`;
            return (
              <div key={`swatch-${entry.name}-${index}`} className="rounded-xl border border-border/60 bg-white/30 p-3 space-y-2">
                <div className="flex items-center justify-between gap-2 min-w-0">
                  <div className="flex items-center gap-2 min-w-0">
                    {swatch ? (
                      <span className="h-6 w-6 shrink-0 rounded-full border border-white/80 ring-1 ring-border/40 overflow-hidden">
                        <AdminProductImage src={previewUrl(swatch)} alt="" className="h-full w-full object-cover" />
                      </span>
                    ) : (
                      <span
                        className="h-6 w-6 shrink-0 rounded-full border border-white/80 ring-1 ring-border/40"
                        style={{ background: entry.hex?.trim() || hexForColorName(entry.name) }}
                        aria-hidden
                      />
                    )}
                    <span className="text-sm font-medium truncate" title={entry.name}>
                      {entry.name}
                    </span>
                  </div>
                  <span className="text-[10px] text-muted-foreground shrink-0">{swatch ? "1/1" : "0/1"}</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {swatch ? (
                    <div className="relative h-20 w-20 rounded-xl overflow-hidden ring-1 ring-border shrink-0">
                      <AdminProductImage src={previewUrl(swatch)} alt="" className="h-full w-full object-cover" />
                    </div>
                  ) : freezeSwatches ? (
                    <div className="h-20 w-20 shrink-0 rounded-xl border border-dashed border-border/60 bg-muted/20 grid place-items-center px-1">
                      <p className="text-[9px] text-center text-muted-foreground leading-tight">No swatch</p>
                    </div>
                  ) : (
                    <UploadTile
                      busy={busy}
                      ariaLabel={`Upload swatch for ${entry.name}`}
                      onFiles={(files) => void handleSwatchUpload(index, files)}
                    />
                  )}
                </div>
                <p className="text-[10px] text-muted-foreground leading-snug">
                  Small preview in the colour picker for {entry.name}.
                </p>
              </div>
            );
          })}
        </div>
      </Field>

      <Field label="Colour gallery (main product photos)">
        <ImageSpecHint spec={ADMIN_IMAGE_SPECS.productGallery} className="mb-2" />
        <p className="text-xs text-muted-foreground mb-3">
          First photo is the main product image for this colour on the product page. Drag a photo
          onto Display image below to choose the shop-card photo. Drag within a colour to reorder,
          or use the arrows. Click Save on the product to keep the new order. Up to {MAX_COLOR_IMAGES}{" "}
          images per colour.
        </p>
        <div className="grid gap-3 sm:grid-cols-2">
          {normalizedColors.map((entry, index) => {
            const gallery = colorImageUrls(entry);
            const canAdd = gallery.length < MAX_COLOR_IMAGES;
            const busy = uploadingKey?.startsWith(`gallery-${index}-`) ?? false;
            const galleryExpanded = expandedGalleries.has(index) || gallery.length <= GALLERY_PREVIEW_LIMIT;
            const visibleGallery = galleryExpanded
              ? gallery.map((url, imageIndex) => ({ url, imageIndex }))
              : gallery.slice(0, GALLERY_PREVIEW_LIMIT).map((url, imageIndex) => ({ url, imageIndex }));
            return (
              <div key={`gallery-${entry.name}-${index}`} className="rounded-xl border border-border/60 bg-white/30 p-3 space-y-2">
                <div className="flex items-center justify-between gap-2 min-w-0">
                  <span className="text-sm font-medium truncate" title={entry.name}>
                    {entry.name}
                  </span>
                  <span className="text-[10px] text-muted-foreground shrink-0">
                    {gallery.length}/{MAX_COLOR_IMAGES}
                  </span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {visibleGallery.map(({ url, imageIndex }) => {
                    const isDragging =
                      dragging?.color === index && dragging.image === imageIndex;
                    const isDropTarget =
                      dropTarget?.color === index && dropTarget.image === imageIndex && !isDragging;
                    const canReorder = !uploadingKey && gallery.length > 1;
                    const canDrag = !uploadingKey;
                    return (
                    <div
                      key={url}
                      draggable={canDrag}
                      onDragStart={(event) => {
                        if (!canDrag) {
                          event.preventDefault();
                          return;
                        }
                        event.dataTransfer.effectAllowed = canReorder ? "copyMove" : "copy";
                        setDisplayImageDragData(
                          event.dataTransfer,
                          url,
                          canReorder ? `${index}:${imageIndex}` : undefined,
                        );
                        setDragging({ color: index, image: imageIndex });
                      }}
                      onDragOver={(event) => {
                        event.preventDefault();
                        event.dataTransfer.dropEffect = canReorder ? "move" : "copy";
                        if (!isDropTarget) setDropTarget({ color: index, image: imageIndex });
                      }}
                      onDrop={(event) => {
                        event.preventDefault();
                        const payload = parseGalleryDrag(getGalleryReorderKey(event.dataTransfer));
                        setDragging(null);
                        setDropTarget(null);
                        if (!payload || payload.color !== index) return;
                        moveGalleryImage(index, payload.image, imageIndex);
                      }}
                      onDragEnd={() => {
                        setDragging(null);
                        setDropTarget(null);
                      }}
                      aria-grabbed={isDragging || undefined}
                      aria-label={
                        canReorder
                          ? `Reorder ${entry.name} image ${imageIndex + 1}`
                          : `Drag ${entry.name} image ${imageIndex + 1} as display image`
                      }
                      title="Drag to Display image, or reorder within this colour"
                      className={`relative h-20 w-20 rounded-xl overflow-hidden ring-1 ring-border group shrink-0 ${
                        canDrag ? "cursor-grab active:cursor-grabbing" : ""
                      } ${isDragging ? "opacity-40" : ""} ${
                        isDropTarget ? "ring-2 ring-primary" : ""
                      }`}
                    >
                      <AdminProductImage
                        src={previewUrl(url)}
                        alt=""
                        draggable={false}
                        className="h-full w-full object-cover pointer-events-none"
                      />
                      {canDrag ? (
                        <span className="absolute top-1 left-1 h-5 w-5 grid place-items-center rounded-full bg-foreground/70 text-background">
                          <GripVertical className="h-3 w-3" aria-hidden />
                        </span>
                      ) : null}
                      {imageIndex === 0 ? (
                        <span className="absolute bottom-1 left-1 rounded bg-foreground/70 px-1 text-[8px] leading-tight text-background">
                          Main
                        </span>
                      ) : null}
                      <div className="absolute bottom-1 right-1 flex gap-0.5">
                        <button
                          type="button"
                          title="Move left"
                          aria-label={`Move ${entry.name} image ${imageIndex + 1} left`}
                          disabled={!!uploadingKey || imageIndex === 0}
                          onPointerDown={(event) => event.stopPropagation()}
                          onClick={() => moveGalleryImage(index, imageIndex, imageIndex - 1)}
                          className="h-5 w-5 grid place-items-center rounded-full bg-foreground/70 text-background disabled:opacity-30"
                        >
                          <ChevronLeft className="h-3 w-3" />
                        </button>
                        <button
                          type="button"
                          title="Move right"
                          aria-label={`Move ${entry.name} image ${imageIndex + 1} right`}
                          disabled={!!uploadingKey || imageIndex === gallery.length - 1}
                          onPointerDown={(event) => event.stopPropagation()}
                          onClick={() => moveGalleryImage(index, imageIndex, imageIndex + 1)}
                          className="h-5 w-5 grid place-items-center rounded-full bg-foreground/70 text-background disabled:opacity-30"
                        >
                          <ChevronRight className="h-3 w-3" />
                        </button>
                      </div>
                      <button
                        type="button"
                        title="Remove gallery image"
                        disabled={!!uploadingKey}
                        onPointerDown={(event) => event.stopPropagation()}
                        onClick={() => removeGalleryImage(index, url)}
                        className="absolute top-1 right-1 h-5 w-5 grid place-items-center rounded-full bg-foreground/70 text-background opacity-0 group-hover:opacity-100 transition"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </div>
                    );
                  })}
                  {gallery.length > GALLERY_PREVIEW_LIMIT ? (
                    <button
                      type="button"
                      onClick={() =>
                        setExpandedGalleries((current) => {
                          const next = new Set(current);
                          if (next.has(index)) next.delete(index);
                          else next.add(index);
                          return next;
                        })
                      }
                      className="h-20 min-w-20 shrink-0 rounded-xl border border-dashed border-border px-2 text-[10px] leading-tight text-muted-foreground hover:bg-white/30"
                    >
                      {galleryExpanded ? "Show fewer" : `Show all ${gallery.length}`}
                    </button>
                  ) : null}
                  {canAdd ? (
                    <UploadTile
                      busy={busy}
                      multiple
                      ariaLabel={`Upload gallery images for ${entry.name}`}
                      onFiles={(files) => void handleGalleryUpload(index, files)}
                    />
                  ) : null}
                </div>
              </div>
            );
          })}
        </div>
      </Field>
    </div>
  );
}
