import { useEffect, useMemo, useState } from "react";
import { CheckCircle2, ChevronLeft, Printer } from "lucide-react";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  bookAdminOrderShipment,
  previewAdminOrderPackageRates,
  type AdminOrderRow,
  type PackageCourierOption,
  type PackageRatesResult,
} from "@/lib/orders.functions";
import {
  documentNumber,
  formatAddressLines,
  isPackageReady,
  type FulfillmentAddress,
} from "@/lib/fulfillment-docs";
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
  selectZohoPackageType,
  ZOHO_PACKAGE_TYPES,
  type ZohoPackageType,
} from "@/lib/package-types";
import { openPrintableShippingLabel } from "@/lib/print-shipping-label";
import { showApiError } from "@/lib/ui-errors";

type WizardStep = "address" | "shipment" | "label";
type CarrierMode = "xpressbees" | "manual";

const DELIVERY_STATUSES = [
  { value: "", label: "Select status" },
  { value: "booked", label: "Booked" },
  { value: "in_transit", label: "In Transit" },
  { value: "out_for_delivery", label: "Out For Delivery" },
  { value: "delivered", label: "Delivered" },
  { value: "rto", label: "RTO" },
];

const CONTROL_CLS =
  "h-10 w-full rounded-xl border border-border bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-primary/40";
const PRIMARY_BTN =
  "rounded-full bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50";
const SECONDARY_BTN = "rounded-full border border-border bg-background px-4 py-2 text-sm font-medium";

function todayInputValue() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function courierRateLabel(option: PackageCourierOption) {
  const amount = Number(option.rate);
  if (Number.isFinite(amount) && amount > 0) {
    return `₹${amount.toLocaleString("en-IN")}`;
  }
  return "Rate on booking";
}

function packageFormValues(target: AdminOrderRow) {
  const grams = Number(target.package_weight_grams || target.default_package_weight_grams || 500);
  const dims = packageDimensionFieldsFromOrder(target, "cm");
  return {
    weight: convertWeightInput(String(grams), "g", "kg"),
    weightUnit: "kg" as WeightUnit,
    length: dims.length,
    width: dims.width,
    height: dims.height,
    lengthUnit: "cm" as LengthUnit,
    courierId: target.package_courier_id || "",
  };
}

function AddressCard({
  title,
  address,
}: {
  title: string;
  address?: FulfillmentAddress | null;
}) {
  const lines = formatAddressLines(address);
  return (
    <div>
      <p className="mb-1.5 text-sm font-medium">
        {title} <span className="text-destructive">*</span>
      </p>
      <div className="min-h-[9.5rem] rounded-xl border border-dashed border-border px-4 py-3 text-sm leading-6">
        {lines.length ? (
          lines.map((line) => (
            <p key={line} className={line === lines[0] ? "font-medium" : ""}>
              {line}
            </p>
          ))
        ) : (
          <p className="text-muted-foreground">Address not available</p>
        )}
      </div>
    </div>
  );
}

function Stepper({ step }: { step: WizardStep }) {
  const items: { id: WizardStep; label: string }[] = [
    { id: "address", label: "Validate Address" },
    { id: "shipment", label: "Create Shipment" },
    { id: "label", label: "Generate Label" },
  ];
  const currentIndex = items.findIndex((item) => item.id === step);
  return (
    <div className="border-b border-border px-6 pt-1">
      <ol className="flex flex-wrap items-center gap-2 text-sm">
        {items.map((item, index) => {
          const done = index < currentIndex;
          const current = item.id === step;
          return (
            <li key={item.id} className="flex items-center gap-2">
              {index > 0 ? <span className="text-muted-foreground">›</span> : null}
              <span
                className={`inline-flex items-center gap-1.5 pb-3 ${
                  current ? "border-b-2 border-primary font-semibold text-primary" : "text-muted-foreground"
                }`}
              >
                {done ? <CheckCircle2 className="h-4 w-4 text-emerald-600" /> : null}
                {item.label}
              </span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

export function BookShipmentDialog({
  order,
  open,
  requiresOverride,
  onOpenChange,
  onSaved,
  title: titleOverride,
  previewRates = previewAdminOrderPackageRates,
  bookShipment = bookAdminOrderShipment,
}: {
  order: AdminOrderRow | null;
  open: boolean;
  requiresOverride: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved: () => Promise<void> | void;
  title?: string;
  previewRates?: typeof previewAdminOrderPackageRates;
  bookShipment?: typeof bookAdminOrderShipment;
}) {
  const [step, setStep] = useState<WizardStep>("address");
  const [carrier, setCarrier] = useState<CarrierMode>("xpressbees");
  const [shipDate, setShipDate] = useState(todayInputValue);
  const [parcelType, setParcelType] = useState<ZohoPackageType>("custom");
  const [deliveryStatus, setDeliveryStatus] = useState("");
  const [weight, setWeight] = useState("");
  const [weightUnit, setWeightUnit] = useState<WeightUnit>("kg");
  const [length, setLength] = useState("");
  const [width, setWidth] = useState("");
  const [height, setHeight] = useState("");
  const [lengthUnit, setLengthUnit] = useState<LengthUnit>("cm");
  const [autoPickup, setAutoPickup] = useState(false);
  const [codEnabled, setCodEnabled] = useState(false);
  const [codAmount, setCodAmount] = useState("");
  const [selectedCourierId, setSelectedCourierId] = useState("");
  const [rates, setRates] = useState<PackageRatesResult | null>(null);
  const [bookAwb, setBookAwb] = useState("");
  const [bookCarrier, setBookCarrier] = useState("");
  const [bookTrackingUrl, setBookTrackingUrl] = useState("");
  const [bookLabelUrl, setBookLabelUrl] = useState("");
  const [overrideReason, setOverrideReason] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [booked, setBooked] = useState<AdminOrderRow | null>(null);

  const packageSlip = order?.package_slip_number || documentNumber("PKG-", order?.id);
  const shipmentNumber = order?.shipment_order_number || documentNumber("SHP-", order?.id);
  const isCod = order?.payment_method?.toLowerCase() === "cod";

  const fillFromPackage = (target: AdminOrderRow) => {
    const next = packageFormValues(target);
    setWeight(next.weight);
    setWeightUnit(next.weightUnit);
    setLength(next.length);
    setWidth(next.width);
    setHeight(next.height);
    setLengthUnit(next.lengthUnit);
    setSelectedCourierId(next.courierId);
    setRates(null);
    return next;
  };

  useEffect(() => {
    if (!open || !order) return;
    setStep("address");
    setCarrier(
      order.can_book_expressbees_shipment ||
        order.can_book_expressbees_shipment_with_override ||
        order.can_rebook_carrier_cancelled_shipment
        ? "xpressbees"
        : "manual",
    );
    setShipDate(todayInputValue());
    setParcelType(selectZohoPackageType(order.package_type || "custom", "cm").type);
    setDeliveryStatus("");
    fillFromPackage(order);
    setAutoPickup(false);
    setCodEnabled(isCod);
    setCodAmount(isCod ? String(order.total ?? "") : "");
    setRates(null);
    setBookAwb("");
    setBookCarrier("");
    setBookTrackingUrl("");
    setBookLabelUrl("");
    setOverrideReason("");
    setBusy(null);
    setBooked(null);
  }, [open, order?.order_number]);

  const parsedDims = () => {
    const parsedWeight = parsePackageWeightGrams(weight, weightUnit);
    if (!parsedWeight.ok) return { ok: false as const, message: parsedWeight.message };
    const parsedLength = parsePackageDimensionCm(length, lengthUnit);
    const parsedWidth = parsePackageDimensionCm(width, lengthUnit);
    const parsedHeight = parsePackageDimensionCm(height, lengthUnit);
    if (!parsedLength.ok || !parsedWidth.ok || !parsedHeight.ok) {
      return { ok: false as const, message: "Enter length, width and height greater than 0" };
    }
    return {
      ok: true as const,
      weight_grams: parsedWeight.grams,
      length_cm: parsedLength.cm,
      breadth_cm: parsedWidth.cm,
      height_cm: parsedHeight.cm,
    };
  };

  const savedPackageDims = (fromPackage: AdminOrderRow) => {
    const grams = Number(fromPackage.package_weight_grams || fromPackage.default_package_weight_grams || 500);
    const lengthCm = Number(fromPackage.package_length_cm);
    const widthCm = Number(fromPackage.package_breadth_cm);
    const heightCm = Number(fromPackage.package_height_cm);
    if (!Number.isFinite(grams) || grams <= 0 || lengthCm < 1 || widthCm < 1 || heightCm < 1) {
      return { ok: false as const, message: "Saved package is missing weight or dimensions" };
    }
    return {
      ok: true as const,
      weight_grams: Math.round(grams),
      length_cm: Math.round(lengthCm),
      breadth_cm: Math.round(widthCm),
      height_cm: Math.round(heightCm),
    };
  };

  const loadRates = async (fromPackage?: AdminOrderRow) => {
    if (!order) return;
    const dims = fromPackage ? savedPackageDims(fromPackage) : parsedDims();
    if (!dims.ok) {
      toast.error(dims.message);
      return;
    }
    setBusy("rates");
    try {
      const result = await previewRates(order.order_number, dims);
      setRates(result);
      if (!result.couriers?.length && result.rates_warning) {
        toast.error(result.rates_warning);
      }
      const preferred =
        result.couriers?.find((option) => option.preferred)?.id ||
        result.preferred_courier_id ||
        result.couriers?.[0]?.id ||
        "";
      setSelectedCourierId(preferred);
    } catch (error) {
      showApiError(error, "Could not calculate shipping rates");
    } finally {
      setBusy(null);
    }
  };

  const continueFromAddress = () => {
    if (!order) return;
    if (!isPackageReady(order)) {
      toast.error("Create a package before booking a shipment");
      return;
    }
    setStep("shipment");
    if (carrier === "xpressbees") {
      void loadRates(order);
    }
  };

  const createShipment = async () => {
    if (!order) return;
    if (!isPackageReady(order)) {
      toast.error("Create a package before booking a shipment");
      return;
    }
    if (requiresOverride && overrideReason.trim().length < 5) {
      toast.error("Override reason must be at least 5 characters");
      return;
    }
    const dims = isPackageReady(order) ? savedPackageDims(order) : parsedDims();
    if (!dims.ok) {
      toast.error(dims.message);
      return;
    }
    const selected = rates?.couriers?.find((option) => option.id === selectedCourierId);
    if (carrier === "xpressbees" && !selected && !rates?.preferred_courier_id) {
      toast.error("Choose a shipping method");
      return;
    }
    if (carrier === "manual" && bookAwb.trim().length < 3) {
      toast.error("AWB or tracking reference is required");
      return;
    }
    setBusy("book");
    try {
      const nextCourierId = selected?.id || rates?.preferred_courier_id || "";
      const result = await bookShipment(order.order_number, {
        mode: carrier,
        carrier_name: carrier === "manual" ? bookCarrier.trim() || undefined : undefined,
        awb: bookAwb.trim() || undefined,
        tracking_url: bookTrackingUrl.trim() || undefined,
        label_url: bookLabelUrl.trim() || undefined,
        courier_id: carrier === "xpressbees" ? nextCourierId || undefined : undefined,
        courier_name:
          carrier === "xpressbees"
            ? selected?.name || rates?.preferred_courier_name || undefined
            : undefined,
        override_fulfillment_hold: requiresOverride || undefined,
        override_reason: requiresOverride ? overrideReason.trim() : undefined,
        package_type: parcelType,
      });
      const bookedOrder = (result as { order?: AdminOrderRow }).order ?? {
        ...order,
        tracking_number:
          (result as { outbound_awb?: string }).outbound_awb || bookAwb || "",
        shipment_label_url:
          (result as { outbound_label_url?: string }).outbound_label_url || bookLabelUrl || "",
      };
      setBooked(bookedOrder);
      setStep("label");
      toast.success(
        carrier === "xpressbees" ? `${order.order_number} booked with XpressBees` : `${order.order_number} shipment recorded`,
      );
      await onSaved();
    } catch (error) {
      showApiError(error, "Could not create shipment");
    } finally {
      setBusy(null);
    }
  };

  const selectedCourier: PackageCourierOption | undefined = rates?.couriers?.find(
    (option) => option.id === selectedCourierId,
  );
  const labelUrl = booked?.shipment_label_url || "";
  const trackingNumber = booked?.tracking_number || "";

  const destination = (order?.shipping_address || {
    full_name: order?.full_name,
    phone: order?.phone,
    city: order?.city,
  }) as FulfillmentAddress;

  const title = useMemo(
    () => titleOverride || (requiresOverride ? "Override hold & book" : "New Shipment"),
    [requiresOverride, titleOverride],
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[min(92dvh,900px)] max-w-4xl gap-0 p-0 sm:p-0" aria-describedby={undefined}>
        <DialogHeader className="px-6 pb-2 pt-5 text-left">
          <DialogTitle className="text-xl font-semibold">{title}</DialogTitle>
        </DialogHeader>
        <Stepper step={step} />

        {step === "address" ? (
          <div className="space-y-5 px-6 py-5">
            <label className="block max-w-sm text-sm">
              <span className="mb-1.5 block font-medium">
                Carrier <span className="text-destructive">*</span>
              </span>
              <select
                value={carrier}
                onChange={(event) => setCarrier(event.target.value as CarrierMode)}
                className={CONTROL_CLS}
              >
                <option value="xpressbees">XpressBees</option>
                <option value="manual">Ship manually</option>
              </select>
            </label>
            <div className="grid gap-8 sm:grid-cols-2">
              <AddressCard title="Dispatch Address" address={order?.dispatch_address} />
              <AddressCard title="Destination Address" address={destination} />
            </div>
          </div>
        ) : null}

        {step === "shipment" ? (
          <div className="space-y-5 px-6 py-5">
            {requiresOverride ? (
              <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">
                This order has a pending cancellation request. Provide a reason — it is logged server-side.
              </div>
            ) : null}
            <label className="block max-w-xs text-sm">
              <span className="mb-1.5 block font-medium">Ship Date</span>
              <input
                type="date"
                value={shipDate}
                onChange={(event) => setShipDate(event.target.value)}
                className={CONTROL_CLS}
              />
            </label>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm">
                <span className="mb-1.5 block font-medium">
                Package <span className="text-destructive">*</span>
              </span>
                <select
                  value={packageSlip}
                  disabled
                  aria-readonly="true"
                  className={`${CONTROL_CLS} disabled:opacity-100`}
                >
                  <option value={packageSlip}>{packageSlip}</option>
                </select>
              </label>
              <label className="block text-sm">
                <span className="mb-1.5 block font-medium">
                  Shipment Order# <span className="text-destructive">*</span>
                </span>
                <span className="flex h-10 items-center rounded-xl border border-border bg-muted px-3">
                  <input readOnly value={shipmentNumber} className="min-w-0 flex-1 bg-transparent text-sm outline-none" />
                </span>
              </label>
            </div>
            <label className="block max-w-sm text-sm">
              <span className="mb-1.5 block font-medium">
                Parcel Type <span className="text-destructive">*</span>
              </span>
              <select
                aria-label="Parcel type"
                value={parcelType}
                onChange={(event) => {
                  const { type, preset } = selectZohoPackageType(event.target.value, lengthUnit);
                  setParcelType(type);
                  if (preset) {
                    setLength(preset.length);
                    setWidth(preset.width);
                    setHeight(preset.height);
                    setRates(null);
                  }
                }}
                className={CONTROL_CLS}
              >
                {ZOHO_PACKAGE_TYPES.map((type) => (
                  <option key={type.value} value={type.value}>
                    {type.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="block max-w-sm text-sm">
              <span className="mb-1.5 block font-medium">MiniMS Delivery Status</span>
              <select
                value={deliveryStatus}
                onChange={(event) => setDeliveryStatus(event.target.value)}
                className={CONTROL_CLS}
              >
                {DELIVERY_STATUSES.map((status) => (
                  <option key={status.value || "empty"} value={status.value}>
                    {status.label}
                  </option>
                ))}
              </select>
            </label>

            <div className="rounded-xl border border-border bg-muted/20 p-4">
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                  Package Dimensions
                </p>
                <button
                  type="button"
                  onClick={() => {
                    if (!order) return;
                    fillFromPackage(order);
                    if (carrier === "xpressbees") void loadRates(order);
                  }}
                  className="text-sm font-medium text-primary hover:underline"
                >
                  Auto-fill from Package
                </button>
              </div>
              <div className="grid gap-4 sm:grid-cols-4">
                <label className="block text-sm">
                  <span className="mb-1.5 block font-medium">
                    Weight <span className="text-destructive">*</span>
                  </span>
                  <input
                    aria-label="Weight"
                    type="number"
                    min={0}
                    step="any"
                    value={weight}
                    onChange={(event) => {
                      setWeight(event.target.value);
                      setRates(null);
                    }}
                    className={CONTROL_CLS}
                  />
                  <span className="mt-1 block text-[11px] text-muted-foreground">{weightUnit}</span>
                </label>
                <label className="block text-sm">
                  <span className="mb-1.5 block font-medium">
                    Weight Units <span className="text-destructive">*</span>
                  </span>
                  <select
                    aria-label="Weight unit"
                    value={weightUnit}
                    onChange={(event) => {
                      const next = event.target.value as WeightUnit;
                      setWeight(convertWeightInput(weight, weightUnit, next));
                      setWeightUnit(next);
                      setRates(null);
                    }}
                    className={CONTROL_CLS}
                  >
                    {WEIGHT_UNITS.map((unit) => (
                      <option key={unit.value} value={unit.value}>
                        {unit.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="block text-sm">
                  <span className="mb-1.5 block font-medium">
                    Length <span className="text-destructive">*</span>
                  </span>
                  <input
                    aria-label="Length"
                    type="number"
                    min={0}
                    step="any"
                    value={length}
                    onChange={(event) => {
                      setLength(event.target.value);
                      setRates(null);
                    }}
                    className={CONTROL_CLS}
                  />
                  <span className="mt-1 block text-[11px] text-muted-foreground">{lengthUnit}</span>
                </label>
                <label className="block text-sm">
                  <span className="mb-1.5 block font-medium">
                    Width <span className="text-destructive">*</span>
                  </span>
                  <input
                    aria-label="Width"
                    type="number"
                    min={0}
                    step="any"
                    value={width}
                    onChange={(event) => {
                      setWidth(event.target.value);
                      setRates(null);
                    }}
                    className={CONTROL_CLS}
                  />
                  <span className="mt-1 block text-[11px] text-muted-foreground">{lengthUnit}</span>
                </label>
                <label className="block text-sm">
                  <span className="mb-1.5 block font-medium">
                    Height <span className="text-destructive">*</span>
                  </span>
                  <input
                    aria-label="Height"
                    type="number"
                    min={0}
                    step="any"
                    value={height}
                    onChange={(event) => {
                      setHeight(event.target.value);
                      setRates(null);
                    }}
                    className={CONTROL_CLS}
                  />
                  <span className="mt-1 block text-[11px] text-muted-foreground">{lengthUnit}</span>
                </label>
                <label className="block text-sm">
                  <span className="mb-1.5 block font-medium">
                    Dimension Units <span className="text-destructive">*</span>
                  </span>
                  <select
                    aria-label="Dimension unit"
                    value={lengthUnit}
                    onChange={(event) => {
                      const next = event.target.value as LengthUnit;
                      setLength(convertLengthInput(length, lengthUnit, next));
                      setWidth(convertLengthInput(width, lengthUnit, next));
                      setHeight(convertLengthInput(height, lengthUnit, next));
                      setLengthUnit(next);
                      setRates(null);
                    }}
                    className={CONTROL_CLS}
                  >
                    {LENGTH_UNITS.map((unit) => (
                      <option key={unit.value} value={unit.value}>
                        {unit.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>

            <div className="rounded-xl border border-border bg-muted/20 p-4">
              <p className="mb-3 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                Service Tracking Preferences
              </p>
              <div className="flex flex-wrap gap-6 text-sm">
                <label className="inline-flex items-center gap-2">
                  <input type="checkbox" checked={autoPickup} onChange={(event) => setAutoPickup(event.target.checked)} />
                  Request Auto Pickup
                </label>
                <label className="inline-flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={codEnabled}
                    onChange={(event) => setCodEnabled(event.target.checked)}
                    disabled={isCod}
                  />
                  COD
                </label>
              </div>
              {codEnabled ? (
                <label className="mt-3 block max-w-xs text-sm">
                  <span className="mb-1.5 block font-medium">COD Amount</span>
                  <input
                    type="number"
                    min={0}
                    step="any"
                    value={codAmount}
                    onChange={(event) => setCodAmount(event.target.value)}
                    className={CONTROL_CLS}
                  />
                </label>
              ) : null}
            </div>

            {carrier === "xpressbees" ? (
              <div className="rounded-xl border border-border bg-muted/20 p-4">
                <p className="mb-3 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                  Choose shipping method
                </p>
                {rates?.couriers?.length ? (
                  <div className="divide-y divide-border rounded-xl border border-border bg-background">
                    {rates.couriers.map((option) => (
                      <label
                        key={option.id}
                        className="flex cursor-pointer items-center justify-between gap-3 px-4 py-3 text-sm has-[:checked]:bg-primary/10"
                      >
                        <span className="inline-flex items-center gap-3">
                          <input
                            type="radio"
                            name="shipping-method"
                            checked={selectedCourierId === option.id}
                            onChange={() => setSelectedCourierId(option.id)}
                          />
                          <span className="font-medium">{option.name}</span>
                        </span>
                        <span className="text-sm font-medium text-muted-foreground">{courierRateLabel(option)}</span>
                      </label>
                    ))}
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => void loadRates()}
                    disabled={busy === "rates"}
                    className={`${SECONDARY_BTN} disabled:opacity-50`}
                  >
                    {busy === "rates" ? "Calculating…" : "Get shipping rates"}
                  </button>
                )}
              </div>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block text-sm">
                  Carrier name
                  <input
                    value={bookCarrier}
                    onChange={(event) => setBookCarrier(event.target.value)}
                    className={`mt-1 ${CONTROL_CLS}`}
                    placeholder="e.g. Delhivery"
                  />
                </label>
                <label className="block text-sm">
                  AWB / reference *
                  <input
                    value={bookAwb}
                    onChange={(event) => setBookAwb(event.target.value)}
                    className={`mt-1 ${CONTROL_CLS}`}
                  />
                </label>
                <label className="block text-sm">
                  Tracking URL
                  <input
                    value={bookTrackingUrl}
                    onChange={(event) => setBookTrackingUrl(event.target.value)}
                    className={`mt-1 ${CONTROL_CLS}`}
                  />
                </label>
                <label className="block text-sm">
                  Label URL
                  <input
                    value={bookLabelUrl}
                    onChange={(event) => setBookLabelUrl(event.target.value)}
                    className={`mt-1 ${CONTROL_CLS}`}
                  />
                </label>
              </div>
            )}

            {requiresOverride ? (
              <label className="block text-sm font-medium">
                Override reason *
                <textarea
                  className="mt-1 min-h-20 w-full rounded-xl border border-border bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-primary/40"
                  value={overrideReason}
                  onChange={(event) => setOverrideReason(event.target.value)}
                  placeholder="Why is this order shipping while cancellation is pending?"
                />
              </label>
            ) : null}
          </div>
        ) : null}

        {step === "label" ? (
          <div className="space-y-4 px-6 py-8 text-center">
            <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-600" />
            <p className="text-lg font-semibold">Shipment created</p>
            {trackingNumber ? (
              <p className="text-sm text-muted-foreground">
                AWB {trackingNumber}
                {selectedCourier?.name ? ` · ${selectedCourier.name}` : ""}
              </p>
            ) : null}
            {labelUrl ? (
              <div className="flex justify-center gap-2">
                <a
                  href={labelUrl}
                  target="_blank"
                  rel="noreferrer"
                  className={`inline-flex items-center gap-2 ${PRIMARY_BTN}`}
                >
                  <Printer className="h-4 w-4" /> Download Label
                </a>
                <button
                  type="button"
                  onClick={() => void openPrintableShippingLabel(labelUrl)}
                  className={`inline-flex items-center gap-2 ${SECONDARY_BTN}`}
                >
                  <Printer className="h-4 w-4" /> Print Label
                </button>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No printable label was returned by the carrier.</p>
            )}
          </div>
        ) : null}

        <DialogFooter className="flex-row items-center justify-start gap-2 border-t px-6 py-4 sm:justify-start">
          {step === "address" ? (
            <>
              <button
                type="button"
                onClick={continueFromAddress}
                className={PRIMARY_BTN}
              >
                Save & Continue
              </button>
              <button
                type="button"
                onClick={() => onOpenChange(false)}
                className={SECONDARY_BTN}
              >
                Cancel
              </button>
            </>
          ) : null}
          {step === "shipment" ? (
            <>
              <button
                type="button"
                onClick={() => setStep("address")}
                className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-border"
                aria-label="Back"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={() => void createShipment()}
                disabled={busy === "book"}
                className={PRIMARY_BTN}
              >
                {busy === "book" ? "Creating…" : "Create Shipment"}
              </button>
              <button
                type="button"
                onClick={() => onOpenChange(false)}
                className={SECONDARY_BTN}
              >
                Cancel
              </button>
            </>
          ) : null}
          {step === "label" ? (
            <button
              type="button"
              onClick={() => onOpenChange(false)}
              className={PRIMARY_BTN}
            >
              Done
            </button>
          ) : null}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
