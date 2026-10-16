import { normalizeSizeKey } from "@/lib/product-ages";

export const SIZE_NOTE_MAX_LEN = 120;

export type SizeNotes = Record<string, string>;

export function parseSizeNotes(raw: unknown): SizeNotes {
  if (raw == null) return {};
  let value: unknown = raw;
  if (typeof raw === "string") {
    const trimmed = raw.trim();
    if (!trimmed) return {};
    try {
      value = JSON.parse(trimmed);
    } catch {
      return {};
    }
  }
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  const out: SizeNotes = {};
  for (const [key, note] of Object.entries(value as Record<string, unknown>)) {
    const size = key.trim();
    if (!size || typeof note !== "string") continue;
    const text = note.trim();
    if (!text) continue;
    out[size] = text.slice(0, SIZE_NOTE_MAX_LEN);
  }
  return out;
}

export function serializeSizeNotes(notes: SizeNotes | null | undefined): string | null {
  const parsed = parseSizeNotes(notes);
  return Object.keys(parsed).length ? JSON.stringify(parsed) : null;
}

export function sizeNoteForSelection(
  notes: SizeNotes | null | undefined,
  size: string | null | undefined,
): string | null {
  if (!notes || !size) return null;
  const direct = notes[size]?.trim();
  if (direct) return direct;
  const want = normalizeSizeKey(size);
  if (!want) return null;
  for (const [key, value] of Object.entries(notes)) {
    if (normalizeSizeKey(key) !== want) continue;
    const text = value.trim();
    if (text) return text;
  }
  return null;
}

/** Draft text for an admin size row, including untrimmed in-progress typing. */
export function sizeNoteDraftForSize(notes: SizeNotes | null | undefined, size: string): string {
  if (!notes) return "";
  if (Object.prototype.hasOwnProperty.call(notes, size) && typeof notes[size] === "string") {
    return notes[size]!;
  }
  return sizeNoteForSelection(notes, size) ?? "";
}

/** Write a note under the current size label and drop alias keys (e.g. `0-3m` vs `0-3 M`). */
export function setSizeNote(notes: SizeNotes | null | undefined, size: string, text: string): SizeNotes {
  const want = normalizeSizeKey(size);
  const next: SizeNotes = {};
  for (const [key, value] of Object.entries(notes ?? {})) {
    if (want && normalizeSizeKey(key) === want) continue;
    next[key] = value;
  }
  next[size] = text;
  return next;
}

export function sizeNoteValidationErrors(notes: SizeNotes | null | undefined): string[] {
  if (!notes) return [];
  const errors: string[] = [];
  for (const [key, value] of Object.entries(notes)) {
    if (!key.trim()) {
      errors.push("Size callout is missing a size label.");
      continue;
    }
    if (typeof value !== "string") {
      errors.push(`Size callout for ${key.trim()} must be text.`);
      continue;
    }
    if (value.trim().length > SIZE_NOTE_MAX_LEN) {
      errors.push(`Size callout for ${key.trim()} must be ${SIZE_NOTE_MAX_LEN} characters or fewer.`);
    }
  }
  return errors;
}
