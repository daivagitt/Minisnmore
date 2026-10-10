import { describe, expect, it } from "vitest";
import { certifiedFabricLabel, PRIMARY_STOREFRONT_FABRIC } from "./products";

describe("storefront fabric copy", () => {
  it("builds the certified fabric label from the primary storefront fabric", () => {
    expect(certifiedFabricLabel()).toBe(`100% Certified ${PRIMARY_STOREFRONT_FABRIC}`);
    expect(certifiedFabricLabel()).not.toContain("1499");
  });
});
