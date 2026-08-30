import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { Loader2, Package, ChevronRight, FileText } from "lucide-react";
import { useAccountData } from "@/lib/account";
import { LazyImage } from "@/components/LazyImage";
import { CopyButton } from "@/components/CopyButton";
import { ListPagination } from "@/components/ListPagination";
import { useListPagination } from "@/hooks/useListPagination";
import { LIST_PAGE_SIZE } from "@/lib/pagination";
import { formatOrderDate } from "@/lib/dates";
import {
  formatOrderStatusSummary,
  needsOnlinePayment,
  PAYMENT_STATUS_TONE_CLASS,
  paymentStatusTone,
} from "@/lib/order-status";
import { shouldPollOrderTracking } from "@/lib/order-tracking";
import { isInvoiceAvailable } from "@/lib/invoice-availability";

export const Route = createFileRoute("/account/orders/")({
  head: () => ({ meta: [{ title: "Your orders · Mini's n More" }] }),
  component: OrdersPage,
});

const ORDER_FILTERS = [
  { key: "all", label: "All" },
  { key: "pending", label: "Pending" },
  { key: "completed", label: "Completed" },
] as const;

function OrdersPage() {
  const { orders, loadingOrders, refreshOrders } = useAccountData();
  const [filter, setFilter] = useState<(typeof ORDER_FILTERS)[number]["key"]>("all");

  useEffect(() => {
    // Account data is cached across nested account routes. Revalidate whenever the
    // customer returns here so a payment completed on an order page is visible now.
    void refreshOrders();
  }, [refreshOrders]);

  const filteredOrders = useMemo(() => {
    if (!orders) return orders;
    if (filter === "pending") return orders.filter((o) => o.payment_status === "pending");
    if (filter === "completed") return orders.filter((o) => o.payment_status === "paid");
    return orders;
  }, [orders, filter]);

  const paging = useListPagination(filteredOrders ?? [], {
    pageSize: LIST_PAGE_SIZE,
    resetKey: filter,
  });

  return (
    <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 md:p-8">
      <h1 className="font-display text-2xl sm:text-3xl mb-1 flex items-center gap-2">
        <Package className="h-6 w-6 text-primary shrink-0" /> Your orders
      </h1>
      <p className="text-sm text-muted-foreground mb-6">
        All your little adventures, in one place.
      </p>

      {orders && orders.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-5">
          {ORDER_FILTERS.map((f) => (
            <button
              key={f.key}
              type="button"
              onClick={() => setFilter(f.key)}
              className={`text-xs rounded-full px-3 py-1.5 border transition ${filter === f.key ? "bg-primary text-primary-foreground border-primary" : "border-border hover:bg-muted/60"}`}
            >
              {f.label}
            </button>
          ))}
        </div>
      )}

      {loadingOrders && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
      {orders && orders.length === 0 && (
        <div className="text-sm text-muted-foreground glass rounded-2xl p-6 text-center">
          No orders yet.{" "}
          <Link to="/shop" className="text-primary story-link">
            Begin discovering →
          </Link>
        </div>
      )}
      {orders && orders.length > 0 && filteredOrders?.length === 0 && (
        <div className="text-sm text-muted-foreground glass rounded-2xl p-6 text-center">
          No {filter} orders.
        </div>
      )}

      <div id="account-orders-list" className="space-y-3">
        {paging.pageItems.map((o) => (
          <div
            key={o.id}
            className="glass rounded-2xl p-3 sm:p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-white/60 transition group min-w-0"
          >
            <Link
              to="/account/orders/$orderNumber"
              params={{ orderNumber: o.order_number }}
              search={needsOnlinePayment(o) ? { pay: "1" } : {}}
              className="flex items-center gap-3 min-w-0 flex-1"
            >
              <div className="flex -space-x-2 shrink-0">
                {(o.order_items ?? [])
                  .slice(0, 3)
                  .map((it: any, i: number) =>
                    it.product_image ? (
                      <LazyImage
                        key={i}
                        src={it.product_image}
                        alt=""
                        className="h-10 w-10 rounded-xl object-cover ring-2 ring-background"
                        priority="auto"
                      />
                    ) : (
                      <div
                        key={i}
                        className="h-10 w-10 rounded-xl bg-muted ring-2 ring-background"
                      />
                    ),
                  )}
              </div>
              <div className="min-w-0">
                <div className="font-medium flex items-center gap-1.5 min-w-0">
                  <span className="truncate">{o.order_number}</span>
                  <CopyButton
                    value={o.order_number}
                    label="Copy order ID"
                    title="Copy"
                    successMessage="Order ID copied"
                    className="h-7 w-7 shrink-0"
                    iconClassName="h-3 w-3"
                  />
                </div>
                <div className="text-xs text-muted-foreground">
                  {formatOrderDate(o.created_at)}
                  {" · "}
                  {o.order_items?.length || 0} item(s)
                  {needsOnlinePayment(o) && (
                    <span className="text-primary"> · Tap to complete payment</span>
                  )}
                </div>
              </div>
            </Link>
            <div className="flex items-center justify-between sm:justify-end gap-3 min-w-0 pl-0 sm:pl-0">
              <div className="min-w-0 sm:text-right">
                <div className="font-medium">₹{Number(o.total).toLocaleString("en-IN")}</div>
                <StatusPill order={o} />
                <div className="mt-1 flex flex-wrap sm:flex-col sm:items-end gap-x-3 gap-y-1">
                  {shouldPollOrderTracking(o) && (
                    <Link
                      to="/track"
                      search={{ order: o.order_number }}
                      className="text-[10px] text-primary hover:underline"
                    >
                      Track order
                    </Link>
                  )}
                  {o.order_number && isInvoiceAvailable(o) && (
                    <Link
                      to="/account/orders/$orderNumber/invoice"
                      params={{ orderNumber: o.order_number }}
                      className="text-[10px] text-primary hover:underline inline-flex items-center gap-1"
                    >
                      <FileText className="h-3 w-3" /> Invoice
                    </Link>
                  )}
                </div>
              </div>
              <Link
                to="/account/orders/$orderNumber"
                params={{ orderNumber: o.order_number }}
                search={needsOnlinePayment(o) ? { pay: "1" } : {}}
                className="text-muted-foreground group-hover:translate-x-0.5 transition shrink-0"
                aria-label={`View order ${o.order_number}`}
              >
                <ChevronRight className="h-4 w-4" />
              </Link>
            </div>
          </div>
        ))}
      </div>
      <ListPagination
        page={paging.page}
        totalPages={paging.totalPages}
        totalItems={paging.totalItems}
        pageSize={paging.pageSize}
        scrollToId="account-orders-list"
        onPageChange={paging.setPage}
      />
    </div>
  );
}

function StatusPill({
  order,
}: {
  order: { payment_status?: string; payment_method?: string; status?: string };
}) {
  const tone = paymentStatusTone(order.payment_status);
  return (
    <span
      className={`inline-flex text-[10px] tracking-wide rounded-full px-2 py-0.5 ${PAYMENT_STATUS_TONE_CLASS[tone]}`}
    >
      {formatOrderStatusSummary(order)}
    </span>
  );
}
