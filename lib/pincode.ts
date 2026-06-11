const METRO_DISTRICTS = [
  "Mumbai",
  "Delhi",
  "Bengaluru",
  "Bangalore",
  "Chennai",
  "Kolkata",
  "Hyderabad",
  "Pune",
] as const;

export type PincodeValidation =
  | { ok: true; pin: string }
  | { ok: false; error: string };

export type PincodeLookup = {
  city: string;
  state: string;
  eta: string;
};

/** Strip non-digits and cap at 6 characters. */
export function normalizePincode(value: string): string {
  return value.replace(/\D/g, "").slice(0, 6);
}

export function validatePincode(value: string): PincodeValidation {
  const pin = normalizePincode(value);
  if (pin.length === 0) return { ok: false, error: "Enter a 6-digit pincode" };
  if (pin.length < 6) return { ok: false, error: "Pincode must be 6 digits" };
  if (!/^[1-9]\d{5}$/.test(pin)) return { ok: false, error: "Enter a valid Indian pincode" };
  return { ok: true, pin };
}

export function deliveryEtaForDistrict(district: string): string {
  const isMetro = METRO_DISTRICTS.some((m) => district.includes(m));
  return isMetro ? "2–3 days" : "4–6 days";
}

/** Parse India Post API payload from api.postalpincode.in (legacy; browser calls blocked when cert expires). */
export function parsePostalPincodeResponse(data: unknown): PincodeLookup | null {
  if (!Array.isArray(data) || data.length === 0) return null;
  const row = data[0] as { PostOffice?: Array<{ District?: string; State?: string }> };
  const po = row?.PostOffice?.[0];
  if (!po?.District || !po?.State) return null;
  const city = po.District.trim();
  const state = po.State.trim();
  if (!city || !state) return null;
  return { city, state, eta: deliveryEtaForDistrict(city) };
}

/** Lookup pincode via our API (avoids expired third-party TLS certs in the browser). */
export async function lookupPincode(
  value: string,
  fetcher: (path: string) => Promise<PincodeLookup> = defaultPincodeFetcher,
): Promise<{ ok: true; lookup: PincodeLookup } | { ok: false; error: string }> {
  const validated = validatePincode(value);
  if (!validated.ok) return validated;
  try {
    const lookup = await fetcher(`/api/v1/pincode/${validated.pin}`);
    if (!lookup?.city?.trim() || !lookup?.state?.trim()) {
      return { ok: false, error: "We don't deliver here yet" };
    }
    return { ok: true, lookup };
  } catch {
    return { ok: false, error: "Couldn't check just now" };
  }
}

async function defaultPincodeFetcher(path: string): Promise<PincodeLookup> {
  const { api } = await import("@/lib/api");
  return api.get<PincodeLookup>(path);
}
