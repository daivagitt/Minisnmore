import { Field, inputCls } from "@/components/admin/AdminPage";
import { normalizeSizeKey } from "@/lib/product-ages";
import { setSizeNote, sizeNoteDraftForSize, SIZE_NOTE_MAX_LEN, type SizeNotes } from "@/lib/product-size-notes";

type Props = {
  sizes: string[];
  value: SizeNotes | null | undefined;
  onChange: (next: SizeNotes) => void;
};

export function SizeNotesEditor({ sizes, value, onChange }: Props) {
  const notes = value ?? {};
  const uniqueSizes: string[] = [];
  const seen = new Set<string>();
  for (const size of sizes) {
    const label = size.trim();
    const key = normalizeSizeKey(label);
    if (!label || !key || seen.has(key)) continue;
    seen.add(key);
    uniqueSizes.push(label);
  }

  return (
    <Field
      label="Size callouts"
      hint="Shown on the product photo when a shopper picks that size. Use this for differences such as socks: 0–3m and 3–6m without grippers, 6–12m with grippers. Leave a size blank to hide the note."
    >
      {uniqueSizes.length === 0 ? (
        <p className="text-xs text-muted-foreground">Add sizes first, then write a short note per size.</p>
      ) : (
        <div className="space-y-2">
          {uniqueSizes.map((size) => (
            <label key={normalizeSizeKey(size)} className="grid sm:grid-cols-[7.5rem_1fr] gap-2 items-center">
              <span className="text-sm font-medium truncate">{size}</span>
              <input
                className={inputCls()}
                maxLength={SIZE_NOTE_MAX_LEN}
                aria-label={`Callout for size ${size}`}
                placeholder="e.g. This size has grippers"
                value={sizeNoteDraftForSize(notes, size)}
                onChange={(e) => onChange(setSizeNote(notes, size, e.target.value))}
              />
            </label>
          ))}
        </div>
      )}
    </Field>
  );
}
