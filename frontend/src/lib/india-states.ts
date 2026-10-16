export const DEFAULT_SELLER_STATE = "Karnataka";

const INDIA_STATE_CODES: Record<string, string> = {
  jammuandkashmir: "01",
  himachalpradesh: "02",
  punjab: "03",
  chandigarh: "04",
  uttarakhand: "05",
  haryana: "06",
  delhi: "07",
  rajasthan: "08",
  uttarpradesh: "09",
  bihar: "10",
  sikkim: "11",
  arunachalpradesh: "12",
  nagaland: "13",
  manipur: "14",
  mizoram: "15",
  tripura: "16",
  meghalaya: "17",
  assam: "18",
  westbengal: "19",
  jharkhand: "20",
  odisha: "21",
  chhattisgarh: "22",
  madhyapradesh: "23",
  gujarat: "24",
  dadraandnagarhavelianddamananddiu: "26",
  maharashtra: "27",
  andhrapradesh: "28",
  karnataka: "29",
  goa: "30",
  lakshadweep: "31",
  kerala: "32",
  tamilnadu: "33",
  puducherry: "34",
  andamanandnicobarislands: "35",
  telangana: "36",
  andhrapradeshnew: "37",
  ladakh: "38",
};

function normalizeStateName(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]/g, "");
}

export function formatPlaceOfSupply(stateValue: string | null | undefined): string {
  const state = String(stateValue ?? "").trim();
  if (!state) return "—";
  const normalized = normalizeStateName(state);
  const code = /^\d{2}$/.test(normalized) ? normalized : INDIA_STATE_CODES[normalized];
  return code ? `${code}-${state}` : state;
}
