import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect } from "react";
import { CreditCard, Loader2 } from "lucide-react";
import { useAccountData } from "@/lib/account";
import { formatOrderDate } from "@/lib/dates";
import { ListPagination } from "@/components/ListPagination";
import { useListPagination } from "@/hooks/useListPagination";
import { LIST_PAGE_SIZE } from "@/lib/pagination";

export const Route = createFileRoute("/account/payments")({
  head: () => ({ meta: [{ title: "Payments · Mini's n More" }] }),
  component: PaymentsPage,
});

function PaymentsPage() {
  const { payments: items, loadingPayments, refreshPayments } = useAccountData();
  const paging = useListPagination(items ?? [], { pageSize: LIST_PAGE_SIZE });

  useEffect(() => {
    if (items === null && !loadingPayments) void refreshPayments();
  }, [items, loadingPayments, refreshPayments]);

  return (
    <div className="glass-strong rounded-[1.5rem] sm:rounded-[2rem] p-4 sm:p-6 md:p-8">
      <h1 className="font-display text-2xl sm:text-3xl mb-1 flex items-center gap-2"><CreditCard className="h-6 w-6 text-primary shrink-0" /> Payments</h1>
      <p className="text-sm text-muted-foreground mb-6">A history of every transaction tied to your account.</p>

      {loadingPayments && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
      {items && items.length === 0 && (
        <div className="text-sm text-muted-foreground glass rounded-2xl p-6 text-center">No payments yet.</div>
      )}

      <div id="account-payments-list" className="space-y-3">
        {paging.pageItems.map((p) => (
          <Link key={p.id} to="/account/orders/$orderNumber" params={{ orderNumber: p.order_number }}
            className="glass rounded-2xl p-3 sm:p-4 flex items-center justify-between gap-3 min-w-0 hover:bg-white/60 transition">
            <div className="min-w-0">
              <div className="font-medium truncate">{p.order_number}</div>
              <div className="text-xs text-muted-foreground break-all">
                {p.razorpay_payment_id ? `Txn: ${p.razorpay_payment_id}` : "Awaiting payment"}
              </div>
              <div className="text-xs text-muted-foreground mt-0.5">
                {formatOrderDate(p.created_at, { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })}
              </div>
            </div>
            <div className="text-right shrink-0">
              <div className="font-medium">₹{Number(p.total).toLocaleString("en-IN")}</div>
              <StatusPill status={p.payment_status} />
            </div>
          </Link>
        ))}
      </div>
      <ListPagination
        page={paging.page}
        totalPages={paging.totalPages}
        totalItems={paging.totalItems}
        pageSize={paging.pageSize}
        scrollToId="account-payments-list"
        onPageChange={paging.setPage}
      />
    </div>
  );
}

function StatusPill({ status }: { status: string }) {
  const map: Record<string, string> = {
    paid: "bg-primary/15 text-primary",
    pending: "bg-amber-500/15 text-amber-700",
    failed: "bg-destructive/15 text-destructive",
  };
  return <span className={`text-[10px] uppercase tracking-wider rounded-full px-2 py-0.5 ${map[status] ?? "bg-muted text-muted-foreground"}`}>{status}</span>;
}