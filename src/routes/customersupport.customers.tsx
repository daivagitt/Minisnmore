import { createFileRoute } from "@tanstack/react-router";
import { AdminCustomersPage } from "@/routes/admin.customers";

export const Route = createFileRoute("/customersupport/customers")({
  head: () => ({ meta: [{ title: "Customers · Customer Support · Mini's n More" }] }),
  component: () => <AdminCustomersPage showRbacHint={false} eyebrow="Customer Support" />,
});
