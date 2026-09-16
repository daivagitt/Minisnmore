import { describe, expect, it } from "vitest";
import {
  parseSizeNotes,
  serializeSizeNotes,
  setSizeNote,
  SIZE_NOTE_MAX_LEN,
  sizeNoteDraftForSize,
  sizeNoteForSelection,
  sizeNoteValidationErrors,
} from "./product-size-notes";

describe("parseSizeNotes", () => {
  it("keeps non-empty size → text pairs and ignores junk", () => {
    expect(parseSizeNotes(null)).toEqual({});
    expect(parseSizeNotes("not-json")).toEqual({});
    expect(
      parseSizeNotes({
        "0-3m": "Without grippers",
        "3-6m": "  ",
        "6-12m": "This size has grippers",
        "": "skip",
        extra: 12,
      }),
    ).toEqual({
      "0-3m": "Without grippers",
      "6-12m": "This size has grippers",
    });
  });
});

describe("serializeSizeNotes", () => {
  it("returns null when every note is blank", () => {
    expect(serializeSizeNotes({})).toBeNull();
    expect(serializeSizeNotes({ "0-3m": "  " })).toBeNull();
    expect(serializeSizeNotes({ "6-12m": "This size has grippers" })).toBe(
      JSON.stringify({ "6-12m": "This size has grippers" }),
    );
  });
});

describe("sizeNoteForSelection", () => {
  const notes = {
    "0-3m": "Without grippers",
    "6-12 M": "This size has grippers",
  };

  it("matches the selected size even when spacing or M suffix differs", () => {
    expect(sizeNoteForSelection(notes, "0-3m")).toBe("Without grippers");
    expect(sizeNoteForSelection(notes, "0-3 M")).toBe("Without grippers");
    expect(sizeNoteForSelection(notes, "6-12m")).toBe("This size has grippers");
    expect(sizeNoteForSelection(notes, "3-6m")).toBeNull();
    expect(sizeNoteForSelection(notes, null)).toBeNull();
  });
});

describe("sizeNoteDraftForSize / setSizeNote", () => {
  it("shows a stored note even when the size label spacing differs", () => {
    expect(sizeNoteDraftForSize({ "0-3 M": "Without grippers" }, "0-3m")).toBe("Without grippers");
  });

  it("stores the current size label and drops alias keys", () => {
    expect(setSizeNote({ "0-3 M": "Without grippers", "6-12m": "This size has grippers" }, "0-3m", "No grippers")).toEqual({
      "0-3m": "No grippers",
      "6-12m": "This size has grippers",
    });
  });
});

describe("sizeNoteValidationErrors", () => {
  it("flags notes that are too long", () => {
    expect(sizeNoteValidationErrors({ "6-12m": "ok" })).toEqual([]);
    expect(sizeNoteValidationErrors({ "6-12m": "x".repeat(SIZE_NOTE_MAX_LEN + 1) })).toEqual([
      `Size callout for 6-12m must be ${SIZE_NOTE_MAX_LEN} characters or fewer.`,
    ]);
  });
});
