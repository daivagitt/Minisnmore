const DEFAULT_ORDER_DATE: Intl.DateTimeFormatOptions = {
  day: "numeric",
  month: "short",
  year: "numeric",
};

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
