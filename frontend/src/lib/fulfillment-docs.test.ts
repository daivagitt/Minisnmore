import { describe, expect, it } from "vitest";
import {
  documentNumber,
  formatAddressLines,
  isPackageReady,
} from "@/lib/fulfillment-docs";

describe("fulfillment document helpers", () => {
  it("pads package and shipment numbers like Zoho", () => {
    expect(documentNumber("PKG-", 18)).toBe("PKG-00018");
    expect(documentNumber("SHP-", 33)).toBe("SHP-00033");
    expect(documentNumber("PKG-")).toBe("PKG-00000");
  });

  it("formats warehouse-style address cards", () => {
    expect(
      formatAddressLines({
        name: "Stolt global private limited",
        line1: "49 50 51, Haridasanagar",
        city: "Bengaluru",
        state: "Karnataka",
        pincode: "560074",
        phone: "7483456582",
      }),
    ).toEqual([
      "Stolt global private limited",
      "49 50 51, Haridasanagar",
      "Bengaluru, Karnataka",
      "India , 560074",
      "7483456582",
    ]);
  });

  it("treats a package as ready only after wrap with dimensions", () => {
    expect(isPackageReady({ packed_at: "2026-09-30T00:00:00Z", package_length_cm: 10, package_breadth_cm: 8, package_height_cm: 4 })).toBe(true);
    expect(isPackageReady({ packed_at: "", package_length_cm: 10, package_breadth_cm: 8, package_height_cm: 4 })).toBe(false);
    expect(isPackageReady({ packed_at: "2026-09-30T00:00:00Z", package_length_cm: 0, package_breadth_cm: 8, package_height_cm: 4 })).toBe(false);
  });
});
