import { useEffect, useMemo, useState } from "react";
import { Package } from "lucide-react";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  completeAdminOrderPackage,
  type AdminOrderRow,
} from "@/lib/orders.functions";
import { documentNumber } from "@/lib/fulfillment-docs";
import {
  convertLengthInput,
  convertWeightInput,
  LENGTH_UNITS,
  parsePackageDimensionCm,
  parsePackageWeightGrams,
  WEIGHT_UNITS,
  type LengthUnit,
  type WeightUnit,
} from "@/lib/package-units";
import {
  packageDimensionFieldsFromOrder,
  packageTypeDimensionHint,
  selectZohoPackageType,
  type ZohoPackageType,
} from "@/lib/package-types";
import { reopenZohoPackageType, resolvedPackageTypeId } from "@/lib/package-boxes";
import { usePackageBoxes } from "@/hooks/usePackageBoxes";
import { PackageTypeSelect } from "@/components/admin/PackageTypeSelect";
import { showApiError } from "@/lib/ui-errors";

function todayInputValue() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function itemLabel(item: NonNullable<AdminOrderRow["order_items"]>[number]) {
  const bits = [item.product_name || "Item"];
  if (item.size) bits.push(item.size);
  if (item.color) bits.push(item.color);
  return bits.join(" — ");
}

export function CreatePackageDialog({
  order,
  open,
  onOpenChange,
  onSaved,
  completePackage = completeAdminOrderPackage,
  description = "Reduce packed quantity if a unit is damaged or cannot be shipped. Unshipped units are refunded automatically. Package date is display only.",
}: {
  order: AdminOrderRow | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved: () => Promise<void> | void;
  completePackage?: typeof completeAdminOrderPackage;
  description?: string;
}) {
  const [packageDate, setPackageDate] = useState(todayInputValue);
  const [packageType, setPackageType] = useState<ZohoPackageType>("custom");
  const [packageTypeId, setPackageTypeId] = useState("");
  const { boxes, loaded: boxesLoaded, error: boxesLoadError } = usePackageBoxes(open);
  const [grossWeight, setGrossWeight] = useState("");
  const [weightUnit, setWeightUnit] = useState<WeightUnit>("kg");
  const [length, setLength] = useState("");
  const [width, setWidth] = useState("");
  const [height, setHeight] = useState("");
  const [lengthUnit, setLengthUnit] = useState<LengthUnit>("cm");
  const [saving, setSaving] = useState(false);
  const [packedQtyByKey, setPackedQtyByKey] = useState<Record<string, number>>({});

  const items = order?.order_items ?? [];
  const slipNumber = order?.package_slip_number || documentNumber("PKG-", order?.id);

  function itemKey(item: NonNullable<AdminOrderRow["order_items"]>[number], index: number) {
    return String(item.id ?? index);
  }

  function orderedQty(item: NonNullable<AdminOrderRow["order_items"]>[number]) {
    return Math.max(1, Number(item.qty) || 1);
  }

  function packedQty(item: NonNullable<AdminOrderRow["order_items"]>[number], index: number) {
    const key = itemKey(item, index);
    if (packedQtyByKey[key] != null) return packedQtyByKey[key];
    const saved = Number(item.packed_qty);
    if (Number.isFinite(saved) && saved >= 0) return saved;
    return orderedQty(item);
  }

  const totalItems = items.reduce((sum, item, index) => sum + packedQty(item, index), 0);
  const shortUnits = items.reduce((sum, item, index) => sum + Math.max(0, orderedQty(item) - packedQty(item, index)), 0);

  useEffect(() => {
    if (!open || !order) return;
    const grams = order.package_weight_grams || order.default_package_weight_grams || 500;
    const parsed = parsePackageWeightGrams(String(grams), "g");
    setPackageDate(todayInputValue());
    const type = selectZohoPackageType(order.package_type || "custom", "cm").type;
    setPackageType(type);
    setPackageTypeId(order.package_box_id == null ? "" : String(order.package_box_id));
    setGrossWeight(parsed.ok ? convertWeightInput(String(grams), "g", "kg") : "");
    setWeightUnit("kg");
    setLengthUnit("cm");
    const dims = packageDimensionFieldsFromOrder(order, "cm");
    setLength(dims.length);
    setWidth(dims.width);
    setHeight(dims.height);
    const nextPacked: Record<string, number> = {};
    (order.order_items ?? []).forEach((item, index) => {
      const ordered = Math.max(1, Number(item.qty) || 1);
      const saved = Number(item.packed_qty);
      nextPacked[String(item.id ?? index)] = Number.isFinite(saved) && saved >= 0 ? saved : ordered;
    });
    setPackedQtyByKey(nextPacked);
  }, [open, order]);

  useEffect(() => {
    if (!open || !order || !boxesLoaded || boxesLoadError) return;
    const matched = resolvedPackageTypeId(boxes, order);
    setPackageTypeId(matched);
    setPackageType(reopenZohoPackageType(order.package_type, matched));
  }, [open, order, boxesLoaded, boxes, boxesLoadError]);

  useEffect(() => {
    if (!boxesLoadError) return;
    showApiError(boxesLoadError, "Could not load package box types");
  }, [boxesLoadError]);

  const packedAlready = Boolean(order?.packed_at);

  const parsedDims = useMemo(() => {
    const weight = parsePackageWeightGrams(grossWeight, weightUnit);
    const lengthCm = parsePackageDimensionCm(length, lengthUnit);
    const widthCm = parsePackageDimensionCm(width, lengthUnit);
    const heightCm = parsePackageDimensionCm(height, lengthUnit);
    return { weight, lengthCm, widthCm, heightCm };
  }, [grossWeight, weightUnit, length, width, height, lengthUnit]);

  const save = async () => {
    if (!order) return;
    if (!parsedDims.weight.ok) {
      toast.error(parsedDims.weight.message);
      return;
    }
    if (!parsedDims.lengthCm.ok || !parsedDims.widthCm.ok || !parsedDims.heightCm.ok) {
      toast.error("Enter length, width and height greater than 0");
      return;
    }
    if (totalItems < 1) {
      toast.error("Pack at least one unit, or cancel / convert to a refund if nothing can be shipped");
      return;
    }
    setSaving(true);
    try {
      await completePackage(order.order_number, {
        weight_grams: parsedDims.weight.grams,
        length_cm: parsedDims.lengthCm.cm,
        breadth_cm: parsedDims.widthCm.cm,
        height_cm: parsedDims.heightCm.cm,
        package_type: packageType,
        package_box_id: packageTypeId ? Number(packageTypeId) : null,
        items: items.map((item, index) => ({
          order_item_id: item.id,
          packed_qty: packedQty(item, index),
        })),
      });
      toast.success(`${slipNumber} saved. You can now book a shipment.`);
      await onSaved();
      onOpenChange(false);
    } catch (error) {
      showApiError(error, "Could not save package");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[min(92dvh,900px)] max-w-4xl gap-5 p-0 sm:p-0" aria-describedby={undefined}>
        <DialogHeader className="border-b px-6 py-4 text-left">
          <div className="flex items-start justify-between gap-3 pr-8">
            <DialogTitle className="flex items-center gap-2 text-xl font-semibold">
              <Package className="h-5 w-5" />
              {packedAlready ? "Update Package" : "New Package"}
            </DialogTitle>
          </div>
        </DialogHeader>

        <div className="space-y-5 px-6">
          <div className="grid gap-x-8 gap-y-4 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="mb-1.5 block font-medium">
                Package Slip# <span className="text-destructive">*</span>
              </span>
              <span className="flex h-10 items-center rounded-xl border border-border bg-muted px-3">
                <input readOnly value={slipNumber} className="min-w-0 flex-1 bg-transparent text-sm outline-none" />
              </span>
            </label>
            <label className="block text-sm">
              <span className="mb-1.5 block font-medium">
                Date <span className="text-destructive">*</span>
              </span>
              <input
                type="date"
                readOnly
                value={packageDate}
                className="h-10 w-full rounded-xl border border-border bg-muted px-3 text-sm"
              />
            </label>
            <PackageTypeSelect
              zohoType={packageType}
              packageTypeId={packageTypeId}
              boxes={boxes}
              loaded={boxesLoaded}
              lengthUnit={lengthUnit}
              weightUnit={weightUnit}
              onChange={(next) => {
                setPackageType(next.zohoType);
                setPackageTypeId(next.packageTypeId);
                if (next.dims) {
                  setLength(next.dims.length);
                  setWidth(next.dims.width);
                  setHeight(next.dims.height);
                } else {
                  const preset = selectZohoPackageType(next.zohoType, lengthUnit).preset;
                  if (preset) {
                    setLength(preset.length);
                    setWidth(preset.width);
                    setHeight(preset.height);
                  }
                }
                if (next.weight) setGrossWeight(next.weight);
              }}
            />
            <label className="block text-sm">
              <span className="mb-1.5 block font-medium">
                Gross Weight <span className="text-destructive">*</span>
              </span>
              <span className="flex">
                <input
                  aria-label="Gross weight"
                  type="number"
                  min={0}
                  step="any"
                  value={grossWeight}
                  onChange={(event) => setGrossWeight(event.target.value)}
                  className="h-10 min-w-0 flex-1 rounded-l-xl border border-border bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-primary/40"
                />
                <select
                  aria-label="Weight unit"
                  value={weightUnit}
                  onChange={(event) => {
                    const next = event.target.value as WeightUnit;
                    setGrossWeight(convertWeightInput(grossWeight, weightUnit, next));
                    setWeightUnit(next);
                  }}
                  className="h-10 w-[4.5rem] rounded-r-xl border border-border bg-muted px-1 text-sm"
                >
                  {WEIGHT_UNITS.map((unit) => (
                    <option key={unit.value} value={unit.value}>
                      {unit.label}
                    </option>
                  ))}
                </select>
              </span>
            </label>
            <label className="block text-sm sm:col-span-2 sm:max-w-xl">
              <span className="mb-1.5 block font-medium">
                Dimensions
                <span className="mt-0.5 block text-[11px] font-normal text-muted-foreground">
                  {packageTypeDimensionHint(packageType)}
                </span>
              </span>
              <span className="flex">
                <input
                  aria-label="Length"
                  type="number"
                  min={0}
                  step="any"
                  placeholder="L"
                  value={length}
                  onChange={(event) => setLength(event.target.value)}
                  className="h-10 min-w-0 flex-1 rounded-l-xl border border-border bg-background px-2 text-center text-sm outline-none focus:ring-2 focus:ring-primary/40"
                />
                <span className="inline-flex h-10 items-center border-y border-border px-1 text-xs text-muted-foreground">
                  x
                </span>
                <input
                  aria-label="Width"
                  type="number"
                  min={0}
                  step="any"
                  placeholder="W"
                  value={width}
                  onChange={(event) => setWidth(event.target.value)}
                  className="h-10 min-w-0 flex-1 border-y border-border bg-background px-2 text-center text-sm outline-none focus:ring-2 focus:ring-primary/40"
                />
                <span className="inline-flex h-10 items-center border-y border-border px-1 text-xs text-muted-foreground">
                  x
                </span>
                <input
                  aria-label="Height"
                  type="number"
                  min={0}
                  step="any"
                  placeholder="H"
                  value={height}
                  onChange={(event) => setHeight(event.target.value)}
                  className="h-10 min-w-0 flex-1 border-y border-border bg-background px-2 text-center text-sm outline-none focus:ring-2 focus:ring-primary/40"
                />
                <select
                  aria-label="Dimension unit"
                  value={lengthUnit}
                  onChange={(event) => {
                    const next = event.target.value as LengthUnit;
                    setLength(convertLengthInput(length, lengthUnit, next));
                    setWidth(convertLengthInput(width, lengthUnit, next));
                    setHeight(convertLengthInput(height, lengthUnit, next));
                    setLengthUnit(next);
                  }}
                  className="h-10 w-[4.5rem] rounded-r-xl border border-border bg-muted px-1 text-sm"
                >
                  {LENGTH_UNITS.map((unit) => (
                    <option key={unit.value} value={unit.value}>
                      {unit.label}
                    </option>
                  ))}
                </select>
              </span>
            </label>
          </div>

          <p className="text-sm text-muted-foreground">
            {description}
          </p>

          <div className="overflow-x-auto rounded-xl border border-border">
            <table className="w-full min-w-[40rem] text-left text-sm">
              <thead className="border-b bg-muted/40 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Items & Description</th>
                  <th className="px-4 py-3 text-right">Ordered</th>
                  <th className="px-4 py-3 text-right">Packed</th>
                </tr>
              </thead>
              <tbody>
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={3} className="px-4 py-6 text-center text-muted-foreground">
                      No items on this sales order.
                    </td>
                  </tr>
                ) : (
                  items.map((item, index) => {
                    const key = itemKey(item, index);
                    const ordered = orderedQty(item);
                    const packed = packedQty(item, index);
                    return (
                      <tr key={key} className="border-b last:border-0">
                        <td className="px-4 py-3">
                          <p className="font-medium">{itemLabel(item)}</p>
                          {item.sku ? <p className="text-xs text-muted-foreground">SKU: {item.sku}</p> : null}
                          {packed < ordered ? (
                            <p className="text-xs text-amber-700">
                              {ordered - packed} unit{ordered - packed === 1 ? "" : "s"} will be refunded
                            </p>
                          ) : null}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">{ordered}</td>
                        <td className="px-4 py-3 text-right">
                          <input
                            aria-label={`Packed quantity for ${itemLabel(item)}`}
                            type="number"
                            min={0}
                            max={ordered}
                            step={1}
                            disabled={packedAlready}
                            value={packed}
                            onChange={(event) => {
                              const next = Number(event.target.value);
                              if (!Number.isFinite(next)) return;
                              setPackedQtyByKey((current) => ({
                                ...current,
                                [key]: Math.min(ordered, Math.max(0, Math.trunc(next))),
                              }));
                            }}
                            className="ml-auto h-9 w-20 rounded-lg border border-border bg-background px-2 text-right text-sm tabular-nums outline-none focus:ring-2 focus:ring-primary/40 disabled:bg-muted"
                          />
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        <DialogFooter className="flex-row items-center justify-between gap-3 border-t px-6 py-4 sm:justify-between">
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => void save()}
              disabled={saving}
              className="rounded-full bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
            >
              {saving ? "Saving…" : "Save"}
            </button>
            <button
              type="button"
              onClick={() => onOpenChange(false)}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-medium"
            >
              Cancel
            </button>
          </div>
          <p className="text-sm text-muted-foreground">
            Total Items : {totalItems}
            {shortUnits > 0 ? ` · ${shortUnits} to refund` : ""}
          </p>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
