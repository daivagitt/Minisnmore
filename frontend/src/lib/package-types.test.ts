import { describe, expect, it } from "vitest";
import {
  dimensionsForPackageType,
  isZohoPackageType,
  normalizeZohoPackageType,
  packageDimensionFieldsFromOrder,
  packageTypeDimensionHint,
  ZOHO_ENVELOPE_DIMENSIONS_CM,
  ZOHO_PACKAGE_TYPES,
} from "./package-types";

describe("Zoho package types", () => {
  it("exposes the parcel types Zoho Inventory uses on package and shipment forms", () => {
    expect(ZOHO_PACKAGE_TYPES.map((type) => type.value)).toEqual(["custom", "box", "envelope"]);
    expect(ZOHO_PACKAGE_TYPES.map((type) => type.label)).toEqual([
      "Specify custom dimensions",
      "Box",
      "Envelope",
    ]);
  });

  it("defaults unknown values to custom dimensions", () => {
    expect(normalizeZohoPackageType(undefined)).toBe("custom");
    expect(normalizeZohoPackageType("BOX")).toBe("box");
    expect(normalizeZohoPackageType("unknown")).toBe("custom");
    expect(normalizeZohoPackageType("envelope")).toBe("envelope");
  });

  it("recognizes valid Zoho parcel type values", () => {
    expect(isZohoPackageType("box")).toBe(true);
    expect(isZohoPackageType("pallet")).toBe(false);
  });

  it("fills envelope dimensions when reopening a package without saved cm fields", () => {
    expect(
      packageDimensionFieldsFromOrder({ package_type: "envelope" }, "cm"),
    ).toEqual({
      length: "32",
      width: "24",
      height: "2",
    });
    expect(
      packageDimensionFieldsFromOrder({
        package_type: "envelope",
        package_length_cm: 10,
        package_breadth_cm: 8,
        package_height_cm: 4,
      }, "cm"),
    ).toEqual({
      length: "10",
      width: "8",
      height: "4",
    });
  });

  it("tells operators to pick a box type for Box parcels", () => {
    expect(packageTypeDimensionHint("box")).toMatch(/box type/i);
  });

  it("fills a standard envelope size and leaves box/custom for the operator", () => {
    expect(dimensionsForPackageType("custom", "cm")).toBeNull();
    expect(dimensionsForPackageType("box", "cm")).toBeNull();
    expect(dimensionsForPackageType("envelope", "cm")).toEqual({
      length: String(ZOHO_ENVELOPE_DIMENSIONS_CM.lengthCm),
      width: String(ZOHO_ENVELOPE_DIMENSIONS_CM.widthCm),
      height: String(ZOHO_ENVELOPE_DIMENSIONS_CM.heightCm),
    });
    expect(dimensionsForPackageType("envelope", "in")).toEqual({
      length: "12.6",
      width: "9.45",
      height: "0.79",
    });
  });
});
