import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useCallback } from "react";
import { InvoiceViewer } from "@/components/InvoiceViewer";
import { getAdminOrder } from "@/lib/orders.functions";
import type { InvoiceOrder } from "@/lib/invoice-breakdown";

export const Route = createFileRoute("/admin/orders_/$orderNumber/invoice")({
  head: ({ params }) => ({ meta: [{ title: `Invoice ${params.orderNumber} · Mini's n More` }] }),
  component: AdminOrderInvoicePage,
});

function AdminOrderInvoicePage() {
  const { orderNumber } = Route.useParams();
  const navigate = useNavigate();
  const loadOrder = useCallback(
    async (number: string) => (await getAdminOrder(number)).order as InvoiceOrder,
    [],
  );

  return (
    <InvoiceViewer
      orderNumber={orderNumber}
      loadOrder={loadOrder}
      onBack={() => void navigate({ to: "/admin/orders" })}
      backLabel="Back to orders"
    />
  );
}
