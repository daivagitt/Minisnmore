const DEFAULT_ORDER_DATE: Intl.DateTimeFormatOptions = {
  day: "numeric",
  month: "short",
  year: "numeric",
};

function padDatePart(value: number): string {
  return String(value).padStart(2, "0");
}

/** Format an ISO instant for `<input type="datetime-local" />` in the user's timezone. */
export function toDatetimeLocalValue(value: string | null | undefined): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return `${date.getFullYear()}-${padDatePart(date.getMonth() + 1)}-${padDatePart(date.getDate())}T${padDatePart(date.getHours())}:${padDatePart(date.getMinutes())}`;
}

/** Parse a datetime-local input value (local timezone) to an ISO UTC string. */
export function fromDatetimeLocalValue(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString();
}

export function formatOrderDate(
  value: string | null | undefined,
  options: Intl.DateTimeFormatOptions = DEFAULT_ORDER_DATE,
): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  const hasTime = "hour" in options || "minute" in options || "second" in options;
  return hasTime
    ? date.toLocaleString("en-IN", options)
    : date.toLocaleDateString("en-IN", options);
}
