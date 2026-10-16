import { describe, expect, it } from "vitest";
import {
  DISPLAY_IMAGE_DRAG_TYPE,
  clearDisplayImage,
  getDisplayImageDragUrl,
  looksLikeImageUrl,
  promoteDisplayImage,
  setDisplayImageDragData,
} from "@/lib/admin-display-image";

function mockDataTransfer(initial: Record<string, string> = {}) {
  const store = { ...initial };
  return {
    types: Object.keys(store),
    setData: (key: string, value: string) => {
      store[key] = value;
    },
    getData: (key: string) => store[key] ?? "",
  } as unknown as DataTransfer;
}

describe("promoteDisplayImage", () => {
  it("puts the chosen URL first and removes duplicates", () => {
    expect(
      promoteDisplayImage(
        ["https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"],
        "https://cdn.example.com/b.jpg",
      ),
    ).toEqual(["https://cdn.example.com/b.jpg", "https://cdn.example.com/a.jpg"]);
  });

  it("adds a variant photo that was not already in the listing gallery", () => {
    expect(promoteDisplayImage(["https://cdn.example.com/a.jpg"], "https://cdn.example.com/variant.jpg")).toEqual([
      "https://cdn.example.com/variant.jpg",
      "https://cdn.example.com/a.jpg",
    ]);
  });
});

describe("clearDisplayImage", () => {
  it("restores the first print photo instead of promoting the next listing URL", () => {
    expect(
      clearDisplayImage(
        ["https://cdn.example.com/variant.jpg", "https://cdn.example.com/print-a.jpg", "https://cdn.example.com/print-b.jpg"],
        "https://cdn.example.com/print-a.jpg",
      ),
    ).toEqual(["https://cdn.example.com/print-a.jpg", "https://cdn.example.com/print-b.jpg"]);
  });

  it("is a no-op when the listing hero is already the first print photo", () => {
    expect(
      clearDisplayImage(
        ["https://cdn.example.com/print-a.jpg", "https://cdn.example.com/print-b.jpg"],
        "https://cdn.example.com/print-a.jpg",
      ),
    ).toEqual(["https://cdn.example.com/print-a.jpg", "https://cdn.example.com/print-b.jpg"]);
  });
});

describe("display image drag payload", () => {
  it("reads the custom MIME type first", () => {
    const dt = mockDataTransfer();
    setDisplayImageDragData(dt, "https://cdn.example.com/hero.jpg");
    expect(getDisplayImageDragUrl(dt)).toBe("https://cdn.example.com/hero.jpg");
    expect(dt.getData(DISPLAY_IMAGE_DRAG_TYPE)).toBe("https://cdn.example.com/hero.jpg");
  });

  it("reads a JSON text/plain payload used by Safari", () => {
    const dt = mockDataTransfer({
      "text/plain": JSON.stringify({ url: "https://cdn.example.com/hero.jpg", reorder: "1:0" }),
    });
    expect(getDisplayImageDragUrl(dt)).toBe("https://cdn.example.com/hero.jpg");
  });

  it("ignores gallery reorder indexes in text/plain", () => {
    const dt = mockDataTransfer({ "text/plain": "0:1" });
    expect(getDisplayImageDragUrl(dt)).toBeNull();
    expect(looksLikeImageUrl("0:1")).toBe(false);
  });
});
