import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import {
  approveOrderReturn,
  listAdminOrderReturns,
  markOrderReturnReceived,
  refundOrderReturn,
  rejectOrderReturn,
  type AdminOrderReturnRow,
} from "@/lib/orders.functions";
import { showApiError } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/returns")({ component: AdminReturnsPage });

const STATUS_FILTERS = ["", "requested", "approved", "received", "refunded", "rejected", "failed"] as const;

function AdminReturnsPage() {
  const [status, setStatus] = useState<string>("requested");
  const [rows, setRows] = useState<AdminOrderReturnRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listAdminOrderReturns(status || undefined);
      setRows(res.returns ?? []);
    } catch (error) {
      showApiError(error, "Could not load returns");
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    void load();
  }, [load]);

  const run = async (returnNumber: string, action: () => Promise<unknown>, label: string) => {
    setBusy(returnNumber);
    try {
      await action();
      toast.success(label);
      await load();
    } catch (error) {
      showApiError(error, label + " failed");
    } finally {
      setBusy(null);
    }
  };

  return (
    <AdminPage
      eyebrow="Fulfilment"
      title="Order returns"
      description="Review customer return requests, confirm receipt, and issue Razorpay refunds (Ledgerly credit note when invoice exists)."
    >
      <div className="flex flex-wrap gap-2 mb-4">
        {STATUS_FILTERS.map((s) => (
          <button
            key={s || "all"}
            type="button"
            onClick={() => setStatus(s)}
            className={`text-xs rounded-full px-3 py-1.5 border ${
              status === s ? "bg-primary text-primary-foreground border-primary" : "border-border"
            }`}
          >
            {s ? s.replace(/_/g, " ") : "All"}
          </button>
        ))}
      </div>

      {loading ? (
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No returns in this filter.</p>
      ) : (
        <ul className="space-y-4">
          {rows.map((r) => (
            <li key={r.return_number} className="glass-strong rounded-2xl p-4 space-y-3">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-medium">{r.return_number}</p>
                  <p className="text-xs text-muted-foreground">
                    Order {r.order_number} · {r.customer_login} · ₹
                    {Number(r.order_total ?? 0).toLocaleString("en-IN")}
                  </p>
                  <p className="text-xs mt-1">
                    Status: <span className="uppercase tracking-wide">{r.status}</span>
                  </p>
                </div>
                <p className="text-xs text-muted-foreground">
                  {new Date(r.requested_at).toLocaleString("en-IN")}
                </p>
              </div>
              <p className="text-sm">{r.reason}</p>
              {r.admin_notes ? <p className="text-xs text-muted-foreground whitespace-pre-wrap">{r.admin_notes}</p> : null}
              {r.rejection_reason ? <p className="text-xs text-amber-700">{r.rejection_reason}</p> : null}
              {r.razorpay_refund_id ? (
                <p className="text-xs text-muted-foreground">Razorpay refund: {r.razorpay_refund_id}</p>
              ) : null}
              {r.ledgerly_credit_note_id ? (
                <p className="text-xs text-muted-foreground">Ledgerly credit note: {r.ledgerly_credit_note_id}</p>
              ) : null}
              <div className="flex flex-wrap gap-2">
                {r.status === "requested" ? (
                  <>
                    <button
                      type="button"
                      disabled={busy === r.return_number}
                      className="text-xs rounded-full bg-primary text-primary-foreground px-3 py-1.5"
                      onClick={() =>
                        run(r.return_number, () => approveOrderReturn(r.return_number), "Return approved")
                      }
                    >
                      Approve
                    </button>
                    <button
                      type="button"
                      disabled={busy === r.return_number}
                      className="text-xs rounded-full border border-border px-3 py-1.5"
                      onClick={() => {
                        const reason = window.prompt("Rejection reason (required):");
                        if (!reason?.trim()) return;
                        void run(
                          r.return_number,
                          () => rejectOrderReturn(r.return_number, reason.trim()),
                          "Return rejected",
                        );
                      }}
                    >
                      Reject
                    </button>
                  </>
                ) : null}
                {r.status === "approved" ? (
                  <button
                    type="button"
                    disabled={busy === r.return_number}
                    className="text-xs rounded-full bg-primary text-primary-foreground px-3 py-1.5"
                    onClick={() =>
                      run(r.return_number, () => markOrderReturnReceived(r.return_number), "Marked received")
                    }
                  >
                    Mark received
                  </button>
                ) : null}
                {r.status === "received" || r.status === "failed" ? (
                  <button
                    type="button"
                    disabled={busy === r.return_number}
                    className="text-xs rounded-full bg-primary text-primary-foreground px-3 py-1.5"
                    onClick={() =>
                      run(r.return_number, () => refundOrderReturn(r.return_number), "Refund processed")
                    }
                  >
                    Issue refund
                  </button>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      )}
    </AdminPage>
  );
}
