import { Link } from "@tanstack/react-router";
import {
  fieldsFromBoxCm,
  parsePackageTypeChoice,
  packageTypeChoiceValue,
  weightFromPackageType,
  type PackageBoxRow,
} from "@/lib/package-boxes";
import type { LengthUnit, WeightUnit } from "@/lib/package-units";
import type { PackageDimensionFields, ZohoPackageType } from "@/lib/package-types";

const FALLBACK_TYPES: { value: "custom" | "envelope"; label: string }[] = [
  { value: "custom", label: "Specify custom dimensions" },
  { value: "envelope", label: "Envelope" },
];

export function PackageTypeSelect({
  zohoType,
  packageTypeId,
  boxes,
  loaded,
  lengthUnit,
  weightUnit,
  onChange,
  className,
  ariaLabel = "Package type",
  label = "Package Type",
}: {
  zohoType: ZohoPackageType;
  packageTypeId: string;
  boxes: PackageBoxRow[];
  loaded: boolean;
  lengthUnit: LengthUnit;
  weightUnit: WeightUnit;
  onChange: (
    next: {
      zohoType: ZohoPackageType;
      packageTypeId: string;
      dims: PackageDimensionFields | null;
      weight: string | null;
    },
  ) => void;
  className?: string;
  ariaLabel?: string;
  label?: string;
}) {
  const value = packageTypeChoiceValue(zohoType, packageTypeId);

  return (
    <label className={`block text-sm ${className ?? ""}`}>
      <span className="mb-1.5 block font-medium">
        {label} <span className="text-destructive">*</span>
      </span>
      <select
        aria-label={ariaLabel}
        value={value}
        disabled={!loaded}
        onChange={(event) => {
          const choice = parsePackageTypeChoice(event.target.value, boxes);
          onChange({
            zohoType: choice.zohoType,
            packageTypeId: choice.packageTypeId,
            dims: choice.box ? fieldsFromBoxCm(choice.box, lengthUnit) : null,
            weight: choice.box ? weightFromPackageType(choice.box, weightUnit) : null,
          });
        }}
        className="h-10 w-full rounded-xl border border-border bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-primary/40 disabled:opacity-70"
      >
        {FALLBACK_TYPES.map((type) => (
          <option key={type.value} value={type.value}>
            {type.label}
          </option>
        ))}
        {packageTypeId && !boxes.some((box) => box.id === packageTypeId) ? (
          <option value={packageTypeId}>Saved package type (#{packageTypeId})</option>
        ) : null}
        {boxes.map((box) =>
          box.id ? (
            <option key={box.id} value={box.id}>
              {box.name}
              {box.weight_grams
                ? ` (${box.length_cm}×${box.breadth_cm}×${box.height_cm} cm, ${box.weight_grams} g)`
                : ` (${box.length_cm}×${box.breadth_cm}×${box.height_cm} cm)`}
            </option>
          ) : null,
        )}
      </select>
      {loaded && boxes.length === 0 ? (
        <span className="mt-1.5 block text-[11px] text-muted-foreground">
          No package types saved yet.{" "}
          <Link to="/admin/package-types" className="font-medium text-primary hover:underline">
            Add a package type
          </Link>
        </span>
      ) : null}
    </label>
  );
}
