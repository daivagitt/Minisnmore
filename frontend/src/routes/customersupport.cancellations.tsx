import { createFileRoute } from "@tanstack/react-router";
import { AdminCancellationsPage } from "@/routes/admin.cancellations";

export const Route = createFileRoute("/customersupport/cancellations")({
  head: () => ({ meta: [{ title: "Cancellations · Customer Support · Mini's n More" }] }),
  component: () => <AdminCancellationsPage mode="support" />,
});
