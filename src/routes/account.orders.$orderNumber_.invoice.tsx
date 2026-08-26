import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useCallback } from "react";
import { InvoiceViewer } from "@/components/InvoiceViewer";
import { getOrder } from "@/lib/orders.functions";
import type { InvoiceOrder } from "@/lib/invoice-breakdown";

export const Route = createFileRoute("/account/orders/$orderNumber_/invoice")({
  head: ({ params }) => ({ meta: [{ title: `Invoice ${params.orderNumber} · Mini's n More` }] }),
  component: CustomerInvoicePage,
});

function CustomerInvoicePage() {
  const { orderNumber } = Route.useParams();
  const navigate = useNavigate();
  const loadOrder = useCallback(
    async (number: string) => (await getOrder({ data: { orderNumber: number } })).order as InvoiceOrder,
    [],
  );

  return (
    <InvoiceViewer
      orderNumber={orderNumber}
      loadOrder={loadOrder}
      onBack={() => void navigate({ to: "/account/orders/$orderNumber", params: { orderNumber } })}
      backLabel="Back to order"
    />
  );
}
