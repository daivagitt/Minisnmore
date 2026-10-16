import { createFileRoute } from "@tanstack/react-router";
import { OrdersWorkspace } from "@/components/admin/OrdersWorkspace";

export const Route = createFileRoute("/operations/orders")({
  component: () => <OrdersWorkspace mode="operations" invoiceHref={(n) => `/operations/orders/${n}/invoice`} />,
});
