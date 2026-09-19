import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { listAdminOrders } from "@/lib/orders.functions";
import { showApiError } from "@/lib/ui-errors";
import { Link } from "@tanstack/react-router";

export const Route = createFileRoute("/operations/dashboard")({
  component: OperationsDashboard,
});

function OperationsDashboard() {
  const [counts, setCounts] = useState({ pending: 0, confirmed: 0, handWrapped: 0, fulfilled: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void (async () => {
      try {
        const [pending, confirmed, handWrapped, fulfilled] = await Promise.all([
          listAdminOrders(undefined, undefined, { status: "pending", page: 1, size: 1 }),
          listAdminOrders(undefined, undefined, { status: "confirmed", page: 1, size: 1 }),
          listAdminOrders(undefined, undefined, { status: "hand_wrapped", page: 1, size: 1 }),
          listAdminOrders(undefined, undefined, { status: "fulfilled", page: 1, size: 1 }),
        ]);
        setCounts({
          pending: pending.totalItems,
          confirmed: confirmed.totalItems,
          handWrapped: handWrapped.totalItems,
          fulfilled: fulfilled.totalItems,
        });
      } catch (error) {
        showApiError(error, "Could not load operations dashboard");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const cards = [
    { label: "Pending", value: counts.pending },
    { label: "Confirmed", value: counts.confirmed },
    { label: "Hand-wrapped", value: counts.handWrapped },
    { label: "Delivered", value: counts.fulfilled },
  ];

  return (
    <AdminPage eyebrow="Operations" title="Dashboard" description="Fulfilment snapshot for today&rsquo;s orders.">
      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {cards.map((card) => (
            <div key={card.label} className="glass-strong rounded-2xl p-5">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">{card.label}</p>
              <p className="mt-2 font-display text-3xl">{card.value}</p>
            </div>
          ))}
        </div>
      )}
      <Link to="/operations/orders" className="inline-flex mt-2 text-sm text-primary story-link">
        Open orders →
      </Link>
    </AdminPage>
  );
}
