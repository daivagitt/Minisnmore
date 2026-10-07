import { formatPackageUnitValue, fromCentimetres, type LengthUnit } from "./package-units";

/** Zoho Inventory parcel types shown on package / shipment forms. */
export const ZOHO_PACKAGE_TYPES = [
  { value: "custom", label: "Specify custom dimensions" },
  { value: "box", label: "Box" },
  { value: "envelope", label: "Envelope" },
] as const;

export type ZohoPackageType = (typeof ZOHO_PACKAGE_TYPES)[number]["value"];

/** Standard envelope size used when Zoho's Envelope parcel type is selected (cm). */
export const ZOHO_ENVELOPE_DIMENSIONS_CM = {
  lengthCm: 32,
  widthCm: 24,
  heightCm: 2,
} as const;

export function isZohoPackageType(value: string | null | undefined): value is ZohoPackageType {
  return ZOHO_PACKAGE_TYPES.some((type) => type.value === value);
}

// Unknown read values normalize to "custom" for display. Writes must use ZohoPackageType;
// the catalog API rejects unknown package_type on complete-package.
export function normalizeZohoPackageType(value: string | null | undefined): ZohoPackageType {
  const normalized = String(value || "").trim().toLowerCase();
  return isZohoPackageType(normalized) ? normalized : "custom";
}

export type PackageDimensionFields = {
  length: string;
  width: string;
  height: string;
};

export function packageDimensionFieldsFromOrder(
  order: {
    package_type?: string | null;
    package_length_cm?: number | null;
    package_breadth_cm?: number | null;
    package_height_cm?: number | null;
  },
  lengthUnit: LengthUnit = "cm",
): PackageDimensionFields {
  const type = normalizeZohoPackageType(order.package_type);
  const hasSaved =
    Number(order.package_length_cm) >= 1 &&
    Number(order.package_breadth_cm) >= 1 &&
    Number(order.package_height_cm) >= 1;
  if (hasSaved) {
    return {
      length: String(order.package_length_cm),
      width: String(order.package_breadth_cm),
      height: String(order.package_height_cm),
    };
  }
  const preset = dimensionsForPackageType(type, lengthUnit);
  return preset ?? { length: "", width: "", height: "" };
}

export function packageTypeDimensionHint(type: ZohoPackageType) {
  if (type === "envelope") {
    return "Standard envelope size is filled in. Adjust if the parcel is different.";
  }
  return "(Length X Width X Height)";
}

export function selectZohoPackageType(raw: string, lengthUnit: LengthUnit) {
  const type = normalizeZohoPackageType(raw);
  return { type, preset: dimensionsForPackageType(type, lengthUnit) };
}

export function dimensionsForPackageType(type: ZohoPackageType, lengthUnit: LengthUnit) {
  if (type !== "envelope") return null;
  return {
    length: formatPackageUnitValue(fromCentimetres(ZOHO_ENVELOPE_DIMENSIONS_CM.lengthCm, lengthUnit), lengthUnit),
    width: formatPackageUnitValue(fromCentimetres(ZOHO_ENVELOPE_DIMENSIONS_CM.widthCm, lengthUnit), lengthUnit),
    height: formatPackageUnitValue(fromCentimetres(ZOHO_ENVELOPE_DIMENSIONS_CM.heightCm, lengthUnit), lengthUnit),
  };
}
