import { describe, expect, it } from "vitest";
import { GIFT_OCCASIONS, validateGiftStep, validateRecipientName } from "./gift-flow";

describe("gift-flow", () => {
  it("lists milestone occasions without Just Because", () => {
    expect(GIFT_OCCASIONS).not.toContain("Just Because");
    expect(GIFT_OCCASIONS).toContain("Naming Ceremony");
    expect(GIFT_OCCASIONS).toContain("Annaprashan");
    expect(GIFT_OCCASIONS).not.toContain("Baby Shower");
  });

  it("requires a recipient name with at least one letter", () => {
    expect(validateRecipientName("")).toMatch(/required/i);
    expect(validateRecipientName("  ")).toMatch(/required/i);
    expect(validateRecipientName("A")).toMatch(/2 characters/i);
    expect(validateRecipientName("12")).toMatch(/letter/i);
    expect(validateRecipientName("Aanya's mum")).toBeNull();
  });

  it("blocks advancing from baby step without a valid name", () => {
    expect(validateGiftStep(1, { recipient: "", selectedIds: [] })).toMatch(/required/i);
    expect(validateGiftStep(1, { recipient: "Priya", selectedIds: [] })).toBeNull();
  });

  it("blocks advancing from pick-gifts step without a selection", () => {
    expect(validateGiftStep(3, { recipient: "Priya", selectedIds: [] })).toMatch(/at least one gift/i);
    expect(validateGiftStep(3, { recipient: "Priya", selectedIds: ["p1"] })).toBeNull();
  });
});
