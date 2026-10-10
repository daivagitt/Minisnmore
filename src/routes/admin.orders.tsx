import { createFileRoute } from "@tanstack/react-router";
import { OrdersWorkspace } from "@/components/admin/OrdersWorkspace";

export const Route = createFileRoute("/admin/orders")({ component: AdminOrdersPage });

function AdminOrdersPage() {
  return <OrdersWorkspace mode="admin" invoiceHref={(n) => `/admin/orders/${n}/invoice`} />;
}
