export type FulfillmentAddress = {
  name?: string | null;
  full_name?: string | null;
  line1?: string | null;
  line2?: string | null;
  city?: string | null;
  state?: string | null;
  pincode?: string | null;
  phone?: string | null;
  email?: string | null;
  country?: string | null;
};

export function documentNumber(prefix: "PKG-" | "SHP-", id?: number | null) {
  const n = Number(id);
  if (!Number.isFinite(n) || n <= 0) return `${prefix}00000`;
  return `${prefix}${String(Math.trunc(n)).padStart(5, "0")}`;
}

export function addressDisplayName(address?: FulfillmentAddress | null) {
  return address?.name?.trim() || address?.full_name?.trim() || "";
}

export function formatAddressLines(address?: FulfillmentAddress | null) {
  if (!address) return [];
  const lines: string[] = [];
  const name = addressDisplayName(address);
  if (name) lines.push(name);
  const street = [address.line1, address.line2].map((part) => part?.trim()).filter(Boolean).join(", ");
  if (street) lines.push(street);
  const locality = [address.city, address.state].map((part) => part?.trim()).filter(Boolean).join(", ");
  if (locality) lines.push(locality);
  const country = address.country?.trim() || "India";
  const pin = address.pincode?.trim();
  if (pin) lines.push(`${country} , ${pin}`);
  else lines.push(country);
  if (address.phone?.trim()) lines.push(address.phone.trim());
  return lines;
}

export function isPackageReady(order?: {
  packed_at?: string | null;
  package_length_cm?: number | null;
  package_breadth_cm?: number | null;
  package_height_cm?: number | null;
} | null) {
  if (!order?.packed_at) return false;
  return (
    Number(order.package_length_cm) >= 1 &&
    Number(order.package_breadth_cm) >= 1 &&
    Number(order.package_height_cm) >= 1
  );
}
