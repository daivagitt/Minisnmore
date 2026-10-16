/** Query values arrive as strings through our router's plain-string parser. */
export function parseOrderPaymentSearch(search: Record<string, unknown>): { pay?: boolean } {
  return { pay: search.pay === "1" || search.pay === "true" || search.pay === 1 || search.pay === true };
}
