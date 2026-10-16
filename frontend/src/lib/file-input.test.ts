/** @vitest-environment jsdom */
import { describe, expect, it } from "vitest";
import { snapshotInputFiles } from "./file-input";

describe("snapshotInputFiles", () => {
  it("returns a copy of selected files and clears the input", () => {
    const input = document.createElement("input");
    const file = new File(["photo"], "damage.jpg", { type: "image/jpeg" });
    Object.defineProperty(input, "files", {
      configurable: true,
      value: [file],
    });

    const files = snapshotInputFiles(input);

    expect(files).toEqual([file]);
    expect(input.value).toBe("");
  });

  it("still returns files when clearing the input empties the live FileList", () => {
    const input = document.createElement("input");
    const file = new File(["photo"], "damage.jpg", { type: "image/jpeg" });
    let length = 1;
    const liveList = {
      0: file,
      get length() {
        return length;
      },
      item: (index: number) => (index === 0 && length > 0 ? file : null),
      *[Symbol.iterator]() {
        if (length > 0) yield file;
      },
    };
    Object.defineProperty(input, "files", { configurable: true, get: () => liveList });
    Object.defineProperty(input, "value", {
      configurable: true,
      get: () => "",
      set: (next: string) => {
        if (next === "") length = 0;
      },
    });

    const files = snapshotInputFiles(input);

    expect(files).toEqual([file]);
  });
});
