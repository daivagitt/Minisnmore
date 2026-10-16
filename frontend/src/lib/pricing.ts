export const DEFAULT_STORE_DISCOUNT_PERCENT = 20;

export function sellingMultiplier(discountPercent: number): number {
  const discount = Number.isFinite(discountPercent) ? discountPercent : DEFAULT_STORE_DISCOUNT_PERCENT;
  return 1 - discount / 100;
}

export function sellingFromMrp(mrp: number, discountPercent = DEFAULT_STORE_DISCOUNT_PERCENT): number {
  if (!Number.isFinite(mrp) || mrp <= 0) return 0;
  return Math.round(mrp * sellingMultiplier(discountPercent) * 100) / 100;
}

export function mrpFromSelling(selling: number, discountPercent = DEFAULT_STORE_DISCOUNT_PERCENT): number {
  if (!Number.isFinite(selling) || selling <= 0) return 0;
  const mult = sellingMultiplier(discountPercent);
  if (mult <= 0) return selling;
  return Math.round((selling / mult) * 100) / 100;
}
