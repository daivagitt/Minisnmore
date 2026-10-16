import { createFileRoute } from "@tanstack/react-router";
import { AdminCustomersPage } from "@/routes/admin.customers";

export const Route = createFileRoute("/operations/customers")({
  head: () => ({ meta: [{ title: "Customers · Operations · Mini's n More" }] }),
  component: () => <AdminCustomersPage showRbacHint={false} eyebrow="Operations" />,
});
