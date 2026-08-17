import { z } from "zod";
import { api } from "@/lib/api";
import { FullNameSchema } from "@/lib/orders.functions";
import { normalizePincode, validatePincode } from "@/lib/pincode";

export type SavedAddress = {
  id: number;
  label: string;
  full_name: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  pincode: string;
  is_default?: boolean;
};

const PHONE_PATTERN = /^(\+91[\s-]?)?[6-9]\d{4}[\s-]?\d{5}$/;
const PLACE_NAME_PATTERN = /^[\p{L}][\p{L} .'-]*$/u;

/** Keep digits only; drop a leading 91 country code when a 12-digit paste is used; cap at 10. */
export function normalizeAddressPhone(value: string): string {
  let digits = value.replace(/\D/g, "");
  if (digits.length >= 12 && digits.startsWith("91")) digits = digits.slice(2);
  return digits.slice(0, 10);
}

export const AddressFormInput = z.object({
  label: z.string().trim().max(40, "Label is too long").optional().default("Home"),
  full_name: FullNameSchema,
  phone: z
    .string()
    .trim()
    .min(1, "Phone number is required")
    .max(20, "Phone number is too long")
    .regex(PHONE_PATTERN, "Enter a valid 10-digit Indian mobile number"),
  line1: z
    .string()
    .trim()
    .min(5, "Enter a complete street address")
    .max(200, "Address line 1 is too long"),
  line2: z.string().trim().max(200, "Address line 2 is too long").optional().default(""),
  city: z
    .string()
    .trim()
    .min(2, "City is required")
    .max(80, "City name is too long")
    .regex(PLACE_NAME_PATTERN, "City may only contain letters, spaces, apostrophes, periods and hyphens"),
  state: z
    .string()
    .trim()
    .min(2, "State is required")
    .max(80, "State name is too long")
    .regex(PLACE_NAME_PATTERN, "State may only contain letters, spaces, apostrophes, periods and hyphens"),
  pincode: z.string().superRefine((value, ctx) => {
    const result = validatePincode(value);
    if (!result.ok) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: result.error });
    }
  }),
  is_default: z.boolean().optional(),
});

type AddressInput = z.infer<typeof AddressFormInput>;

export type AddressFieldName = keyof Omit<AddressInput, "is_default">;
export type AddressFieldErrors = Partial<Record<AddressFieldName, string>>;

export type AddressDraftInput = {
  label?: string;
  full_name: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  pincode: string;
  is_default?: boolean;
};

export function normalizeAddressDraft(draft: AddressDraftInput): AddressInput {
  return {
    label: draft.label?.trim() || "Home",
    full_name: draft.full_name.trim(),
    phone: normalizeAddressPhone(draft.phone),
    line1: draft.line1.trim(),
    line2: draft.line2?.trim() ?? "",
    city: draft.city.trim(),
    state: draft.state.trim(),
    pincode: normalizePincode(draft.pincode),
    is_default: draft.is_default,
  };
}

export function validateAddressForm(draft: AddressDraftInput): AddressFieldErrors {
  const result = AddressFormInput.safeParse(normalizeAddressDraft(draft));
  if (result.success) return {};
  const errors: AddressFieldErrors = {};
  for (const issue of result.error.issues) {
    const key = issue.path[0];
    if (typeof key === "string" && !errors[key as AddressFieldName]) {
      errors[key as AddressFieldName] = issue.message;
    }
  }
  return errors;
}

export const CheckoutAddressFields = AddressFormInput.pick({
  line1: true,
  line2: true,
  city: true,
  state: true,
  pincode: true,
});

export type CheckoutAddressFieldName = keyof z.infer<typeof CheckoutAddressFields>;
export type CheckoutAddressFieldErrors = Partial<Record<CheckoutAddressFieldName, string>>;

export function normalizeCheckoutAddress(
  draft: Pick<AddressDraftInput, "line1" | "line2" | "city" | "state" | "pincode">,
) {
  return {
    line1: draft.line1.trim(),
    line2: draft.line2?.trim() ?? "",
    city: draft.city.trim(),
    state: draft.state.trim(),
    pincode: normalizePincode(draft.pincode),
  };
}

export function validateCheckoutAddress(
  draft: Pick<AddressDraftInput, "line1" | "line2" | "city" | "state" | "pincode">,
): CheckoutAddressFieldErrors {
  const result = CheckoutAddressFields.safeParse(normalizeCheckoutAddress(draft));
  if (result.success) return {};
  const errors: CheckoutAddressFieldErrors = {};
  for (const issue of result.error.issues) {
    const key = issue.path[0];
    if (typeof key === "string" && !errors[key as CheckoutAddressFieldName]) {
      errors[key as CheckoutAddressFieldName] = issue.message;
    }
  }
  return errors;
}

function mapAddress(raw: unknown): SavedAddress | null {
  if (!raw || typeof raw !== "object") return null;
  const row = raw as Record<string, unknown>;
  const id = Number(row.id);
  if (!Number.isFinite(id)) return null;
  const fullName = row.full_name ?? row.fullName;
  const line1 = row.line1;
  const city = row.city;
  const state = row.state;
  const pincode = row.pincode;
  const phone = row.phone;
  return {
    id,
    label: String(row.label ?? "Home"),
    full_name: String(fullName ?? ""),
    phone: String(phone ?? ""),
    line1: String(line1 ?? ""),
    line2: row.line2 ? String(row.line2) : "",
    city: String(city ?? ""),
    state: String(state ?? ""),
    pincode: String(pincode ?? ""),
    is_default: Boolean(row.is_default ?? row.isDefault),
  };
}

function pickAddress(res: unknown): SavedAddress | null {
  if (!res || typeof res !== "object") return null;
  const row = res as Record<string, unknown>;
  if ("address" in row) return mapAddress(row.address);
  const nested = row.data;
  if (nested && typeof nested === "object" && "address" in nested) {
    return mapAddress((nested as Record<string, unknown>).address);
  }
  return mapAddress(row);
}

export async function listMyAddresses(): Promise<{ addresses: SavedAddress[] }> {
  const res = await api.get<{ addresses?: unknown[] }>("/api/v1/account/addresses", { auth: true });
  const addresses = (res.addresses ?? []).map(mapAddress).filter((a): a is SavedAddress => a != null);
  return { addresses };
}

export async function createMyAddress(input: AddressInput): Promise<{ address: SavedAddress }> {
  const parsed = AddressInput.parse(input);
  const res = await api.post<unknown>("/api/v1/account/addresses", parsed, { auth: true });
  const address = pickAddress(res);
  if (!address) throw new Error("Could not save address");
  return { address };
}

export async function updateMyAddress(id: number, input: AddressInput): Promise<{ address: SavedAddress }> {
  const parsed = AddressInput.parse(input);
  const res = await api.put<unknown>(`/api/v1/account/addresses/${id}`, parsed, { auth: true });
  const address = pickAddress(res);
  if (!address) throw new Error("Could not update address");
  return { address };
}

export async function deleteMyAddress(id: number): Promise<{ ok: boolean }> {
  return api.delete(`/api/v1/account/addresses/${id}`, { auth: true });
}

export async function setMyDefaultAddress(id: number): Promise<{ address: SavedAddress }> {
  const res = await api.put<unknown>(`/api/v1/account/addresses/${id}/default`, {}, { auth: true });
  const address = pickAddress(res);
  if (!address) throw new Error("Could not set default address");
  return { address };
}

export async function syncMyAddresses(addresses: AddressInput[]): Promise<{ addresses: SavedAddress[] }> {
  const parsed = addresses.map((row) => AddressInput.parse(row));
  const res = await api.post<{ addresses?: unknown[] }>(
    "/api/v1/account/addresses/sync",
    { addresses: parsed },
    { auth: true },
  );
  return { addresses: (res.addresses ?? []).map(mapAddress).filter((a): a is SavedAddress => a != null) };
}

export async function listMyWishlist(): Promise<{ product_ids: string[] }> {
  const res = await api.get<{ product_ids?: string[] }>("/api/v1/account/wishlist", { auth: true });
  return { product_ids: (res.product_ids ?? []).map(String) };
}

export async function addMyWishlistItem(productId: string): Promise<{ ok: boolean }> {
  const id = z.string().min(1).max(120).parse(productId);
  return api.post("/api/v1/account/wishlist/items", { product_id: id }, { auth: true });
}

export async function removeMyWishlistItem(productId: string): Promise<{ ok: boolean }> {
  const id = encodeURIComponent(z.string().min(1).max(120).parse(productId));
  return api.delete(`/api/v1/account/wishlist/items/${id}`, { auth: true });
}

export async function syncMyWishlist(productIds: string[]): Promise<{ product_ids: string[] }> {
  const parsed = z.array(z.string().min(1).max(120)).max(500).parse(productIds);
  const res = await api.post<{ product_ids?: string[] }>(
    "/api/v1/account/wishlist/sync",
    { product_ids: parsed },
    { auth: true },
  );
  return { product_ids: (res.product_ids ?? []).map(String) };
}

export async function replaceMyWishlist(productIds: string[]): Promise<{ product_ids: string[] }> {
  const parsed = z.array(z.string().min(1).max(120)).max(500).parse(productIds);
  const res = await api.put<{ product_ids?: string[] }>(
    "/api/v1/account/wishlist",
    { product_ids: parsed },
    { auth: true },
  );
  return { product_ids: (res.product_ids ?? parsed).map(String) };
}

export type PersistedCoupon = { code: string; discount: number } | null;

export async function listMyCart(): Promise<{ items: unknown[]; coupon: PersistedCoupon }> {
  const res = await api.get<{ items?: unknown[]; coupon?: PersistedCoupon }>("/api/v1/account/cart", { auth: true });
  return {
    items: Array.isArray(res.items) ? res.items : [],
    coupon: res.coupon && typeof res.coupon === "object" ? res.coupon : null,
  };
}

export async function replaceMyCart(
  items: unknown[],
  coupon: PersistedCoupon,
): Promise<{ items: unknown[]; coupon: PersistedCoupon }> {
  const res = await api.put<{ items?: unknown[]; coupon?: PersistedCoupon }>(
    "/api/v1/account/cart",
    { items, coupon },
    { auth: true },
  );
  return {
    items: Array.isArray(res.items) ? res.items : items,
    coupon: res.coupon && typeof res.coupon === "object" ? res.coupon : coupon,
  };
}

/** Legacy localStorage address shape (string id) — used only for one-time migration. */
export type LegacyLocalAddress = {
  id?: string;
  label?: string;
  full_name: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  pincode: string;
  is_default?: boolean;
};

export function readLegacyLocalAddresses(storageKey: string): LegacyLocalAddress[] {
  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as LegacyLocalAddress[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function clearLegacyLocalAddresses(storageKey: string) {
  try {
    localStorage.removeItem(storageKey);
  } catch {
    /* ignore */
  }
}
