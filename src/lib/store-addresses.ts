import { api } from "@/lib/api";
import {
  normalizeAddressDraft,
  normalizeAddressPhone,
  validateAddressForm,
  type AddressDraftInput,
} from "@/lib/account.functions";
import { normalizePincode } from "@/lib/pincode";

export const STORE_ADDRESS_USAGES = ["shipment", "normal", "invoice"] as const;
export type StoreAddressUsage = (typeof STORE_ADDRESS_USAGES)[number];

export const STORE_ADDRESS_USAGE_LABELS: Record<StoreAddressUsage, string> = {
  shipment: "Shipment",
  normal: "Normal address",
  invoice: "Invoice",
};

export type StoreAddressRow = {
  id?: string;
  label: string;
  full_name: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  state: string;
  pincode: string;
  country: string;
  usage: StoreAddressUsage;
  is_default: boolean;
};

type StoreAddressDto = {
  id?: number;
  label?: string | null;
  fullName: string;
  phone: string;
  line1: string;
  line2?: string | null;
  city: string;
  state: string;
  pincode: string;
  country?: string | null;
  usage: "SHIPMENT" | "NORMAL" | "INVOICE";
  isDefault?: boolean | null;
  is_default?: boolean | null;
};

export const emptyStoreAddress = (): StoreAddressRow => ({
  label: "",
  full_name: "",
  phone: "",
  line1: "",
  line2: "",
  city: "",
  state: "",
  pincode: "",
  country: "India",
  usage: "normal",
  is_default: false,
});

export function isStoreAddressUsage(value: string): value is StoreAddressUsage {
  return (STORE_ADDRESS_USAGES as readonly string[]).includes(value);
}

export function fromStoreAddressDto(dto: StoreAddressDto): StoreAddressRow {
  const usage = String(dto.usage ?? "NORMAL").toLowerCase();
  return {
    id: dto.id == null ? undefined : String(dto.id),
    label: dto.label ?? "",
    full_name: dto.fullName ?? "",
    phone: dto.phone ?? "",
    line1: dto.line1 ?? "",
    line2: dto.line2 ?? "",
    city: dto.city ?? "",
    state: dto.state ?? "",
    pincode: dto.pincode ?? "",
    country: dto.country?.trim() || "India",
    usage: isStoreAddressUsage(usage) ? usage : "normal",
    is_default: !!(dto.isDefault ?? dto.is_default),
  };
}

export function toStoreAddressDto(row: StoreAddressRow): StoreAddressDto {
  const usage = row.usage === "shipment" ? "SHIPMENT" : row.usage === "invoice" ? "INVOICE" : "NORMAL";
  return {
    id: row.id ? Number(row.id) : undefined,
    label: row.label.trim() || null,
    fullName: row.full_name.trim(),
    phone: normalizeAddressPhone(row.phone),
    line1: row.line1.trim(),
    line2: row.line2.trim() || null,
    city: row.city.trim(),
    state: row.state.trim(),
    pincode: normalizePincode(row.pincode),
    country: row.country.trim() || "India",
    usage,
    isDefault: !!row.is_default,
  };
}

export function getStoreAddressValidationErrors(row: StoreAddressRow): string[] {
  const errors: string[] = [];
  if (!isStoreAddressUsage(row.usage)) {
    errors.push("Choose whether this is shown as shipment, normal address, or invoice.");
  }
  const fieldErrors = validateAddressForm({
    label: row.label || "Address",
    full_name: row.full_name,
    phone: row.phone,
    line1: row.line1,
    line2: row.line2,
    city: row.city,
    state: row.state,
    pincode: row.pincode,
  } satisfies AddressDraftInput);
  errors.push(...Object.values(fieldErrors).filter((message): message is string => Boolean(message)));
  return errors;
}

export const listStoreAddresses = async () => {
  const rows = await api.get<StoreAddressDto[]>("/api/v1/admin/store-addresses", { auth: true });
  return (Array.isArray(rows) ? rows : []).map(fromStoreAddressDto);
};

export const saveStoreAddress = async (row: StoreAddressRow) => {
  const normalized: StoreAddressRow = {
    ...row,
    ...normalizeAddressDraft({
      label: row.label || "Address",
      full_name: row.full_name,
      phone: row.phone,
      line1: row.line1,
      line2: row.line2,
      city: row.city,
      state: row.state,
      pincode: row.pincode,
    }),
    label: row.label.trim(),
    usage: row.usage,
    is_default: row.is_default,
  };
  const errors = getStoreAddressValidationErrors(normalized);
  if (errors.length) throw new Error(errors.join("\n"));
  const payload = toStoreAddressDto(normalized);
  const saved = normalized.id
    ? await api.put<StoreAddressDto>(`/api/v1/admin/store-addresses/${normalized.id}`, payload, { auth: true })
    : await api.post<StoreAddressDto>("/api/v1/admin/store-addresses", payload, { auth: true });
  return fromStoreAddressDto(saved);
};

export const deleteStoreAddress = (id: string) =>
  api.delete<{ deleted: boolean }>(`/api/v1/admin/store-addresses/${id}`, { auth: true });
