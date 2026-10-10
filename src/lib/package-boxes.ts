import { api } from "@/lib/api";
import {
  formatPackageUnitValue,
  fromCentimetres,
  fromGrams,
  type LengthUnit,
  type WeightUnit,
} from "@/lib/package-units";
import { normalizeZohoPackageType, type PackageDimensionFields, type ZohoPackageType } from "@/lib/package-types";

export type PackageBoxRow = {
  id?: string;
  name: string;
  length_cm: number;
  breadth_cm: number;
  height_cm: number;
  weight_grams: number;
};

type PackageBoxDto = {
  id?: number;
  name?: string | null;
  lengthCm?: number | null;
  breadthCm?: number | null;
  heightCm?: number | null;
  weightGrams?: number | null;
  length_cm?: number | null;
  breadth_cm?: number | null;
  height_cm?: number | null;
  weight_grams?: number | null;
};

export const emptyPackageBox = (): PackageBoxRow => ({
  name: "",
  length_cm: 0,
  breadth_cm: 0,
  height_cm: 0,
  weight_grams: 0,
});

function positiveCm(value: number | null | undefined) {
  const n = Number(value);
  return Number.isFinite(n) && n >= 1 ? Math.round(n) : 0;
}

function optionalGrams(value: number | null | undefined) {
  const n = Number(value);
  return Number.isFinite(n) && n >= 1 ? Math.round(n) : 0;
}

export function fromPackageBoxDto(dto: PackageBoxDto): PackageBoxRow {
  return {
    id: dto.id == null ? undefined : String(dto.id),
    name: String(dto.name ?? "").trim(),
    length_cm: positiveCm(dto.lengthCm ?? dto.length_cm),
    breadth_cm: positiveCm(dto.breadthCm ?? dto.breadth_cm),
    height_cm: positiveCm(dto.heightCm ?? dto.height_cm),
    weight_grams: optionalGrams(dto.weightGrams ?? dto.weight_grams),
  };
}

export function toPackageBoxDto(row: PackageBoxRow): PackageBoxDto {
  return {
    id: row.id ? Number(row.id) : undefined,
    name: row.name.trim(),
    lengthCm: positiveCm(row.length_cm),
    breadthCm: positiveCm(row.breadth_cm),
    heightCm: positiveCm(row.height_cm),
    weightGrams: optionalGrams(row.weight_grams) || undefined,
  };
}

export function getPackageBoxValidationErrors(row: PackageBoxRow): string[] {
  const errors: string[] = [];
  if (!row.name.trim()) errors.push("Name is required");
  if (positiveCm(row.length_cm) < 1) errors.push("Length must be at least 1 cm");
  if (positiveCm(row.breadth_cm) < 1) errors.push("Width must be at least 1 cm");
  if (positiveCm(row.height_cm) < 1) errors.push("Height must be at least 1 cm");
  if (row.weight_grams && optionalGrams(row.weight_grams) < 1) {
    errors.push("Gross weight must be at least 1 g");
  }
  return errors;
}

export function fieldsFromBoxCm(box: PackageBoxRow, lengthUnit: LengthUnit): PackageDimensionFields {
  return {
    length: formatPackageUnitValue(fromCentimetres(box.length_cm, lengthUnit), lengthUnit),
    width: formatPackageUnitValue(fromCentimetres(box.breadth_cm, lengthUnit), lengthUnit),
    height: formatPackageUnitValue(fromCentimetres(box.height_cm, lengthUnit), lengthUnit),
  };
}

export function weightFromPackageType(box: PackageBoxRow, weightUnit: WeightUnit): string | null {
  if (!box.weight_grams) return null;
  return formatPackageUnitValue(fromGrams(box.weight_grams, weightUnit), weightUnit);
}

export function matchingPackageBoxId(
  boxes: PackageBoxRow[],
  lengthCm: number | null | undefined,
  breadthCm: number | null | undefined,
  heightCm: number | null | undefined,
) {
  const length = positiveCm(lengthCm);
  const breadth = positiveCm(breadthCm);
  const height = positiveCm(heightCm);
  if (!length || !breadth || !height) return "";
  return (
    boxes.find(
      (box) => box.length_cm === length && box.breadth_cm === breadth && box.height_cm === height,
    )?.id ?? ""
  );
}

export function resolvedPackageTypeId(
  boxes: PackageBoxRow[],
  order: {
    package_box_id?: string | number | null;
    package_type?: string | null;
    package_length_cm?: number | null;
    package_breadth_cm?: number | null;
    package_height_cm?: number | null;
  },
) {
  const saved = order.package_box_id == null || order.package_box_id === "" ? "" : String(order.package_box_id);
  if (saved) return saved;
  const zohoType = normalizeZohoPackageType(order.package_type);
  if (zohoType === "envelope" || zohoType === "custom") return "";
  return matchingPackageBoxId(boxes, order.package_length_cm, order.package_breadth_cm, order.package_height_cm);
}

/** After catalog list loads, align Zoho type with the resolved catalog id (legacy `box` without id → custom). */
export function reopenZohoPackageType(
  orderPackageType: string | null | undefined,
  matchedCatalogId: string,
): ZohoPackageType {
  if (matchedCatalogId) return "box";
  const zoho = normalizeZohoPackageType(orderPackageType);
  return zoho === "box" ? "custom" : zoho;
}

export function parsePackageTypeChoice(raw: string, boxes: PackageBoxRow[]) {
  const box = boxes.find((row) => row.id === raw) ?? null;
  if (box?.id) {
    return { zohoType: "box" as const, packageTypeId: box.id, box };
  }
  const type = normalizeZohoPackageType(raw);
  return {
    zohoType: (type === "box" ? "custom" : type) as ZohoPackageType,
    packageTypeId: "",
    box: null,
  };
}

export function packageTypeChoiceValue(zohoType: ZohoPackageType, packageTypeId: string) {
  if (packageTypeId) return packageTypeId;
  return zohoType === "envelope" ? "envelope" : "custom";
}

export const listPackageBoxes = async () => {
  const rows = await api.get<PackageBoxDto[]>("/api/v1/admin/package-boxes", { auth: true });
  return (Array.isArray(rows) ? rows : []).map(fromPackageBoxDto);
};

export const savePackageBox = async (row: PackageBoxRow) => {
  const errors = getPackageBoxValidationErrors(row);
  if (errors.length) throw new Error(errors.join("\n"));
  const payload = toPackageBoxDto(row);
  const saved = row.id
    ? await api.put<PackageBoxDto>(`/api/v1/admin/package-boxes/${row.id}`, payload, { auth: true })
    : await api.post<PackageBoxDto>("/api/v1/admin/package-boxes", payload, { auth: true });
  return fromPackageBoxDto(saved);
};

export const deletePackageBox = (id: string) =>
  api.delete<{ deleted: boolean }>(`/api/v1/admin/package-boxes/${id}`, { auth: true });
