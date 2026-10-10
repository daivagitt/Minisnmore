import { createFileRoute } from "@tanstack/react-router";
import { OrdersWorkspace } from "@/components/admin/OrdersWorkspace";

export const Route = createFileRoute("/customersupport/orders")({
  component: () => (
    <OrdersWorkspace mode="support" invoiceHref={(n) => `/customersupport/orders/${n}/invoice`} />
  ),
});
