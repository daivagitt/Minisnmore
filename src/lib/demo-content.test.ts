import { describe, expect, it } from "vitest";
import { isDemoStorefrontCopy } from "./demo-content";

describe("isDemoStorefrontCopy", () => {
  it("flags scaffold hero copy", () => {
    expect(isDemoStorefrontCopy("Softness made for first cuddles.")).toBe(true);
    expect(isDemoStorefrontCopy("My brand headline")).toBe(false);
  });
});
