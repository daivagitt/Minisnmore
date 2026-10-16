/**
 * M2 post-order workflows: customer cancel request, returns, exchanges,
 * and the matching admin desks.
 *
 * Code stays in the tree; only UI entry points are gated.
 * Set VITE_SHOW_M2_POST_ORDER_UI=false to hide without removing code.
 */
export const SHOW_M2_POST_ORDER_UI = resolveShowM2PostOrderUi();

function resolveShowM2PostOrderUi(): boolean {
  const explicit = import.meta.env.VITE_SHOW_M2_POST_ORDER_UI?.trim().toLowerCase();
  if (explicit === "true" || explicit === "1") return true;
  if (explicit === "false" || explicit === "0") return false;
  return true;
}

export function isM2AdminNavPath(path: string): boolean {
  return path === "/admin/cancellations" || path === "/admin/returns";
}
