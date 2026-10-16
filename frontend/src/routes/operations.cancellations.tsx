import { createFileRoute } from "@tanstack/react-router";
import { AdminCancellationsPage } from "@/routes/admin.cancellations";

export const Route = createFileRoute("/operations/cancellations")({
  head: () => ({ meta: [{ title: "Cancellations · Operations · Mini's n More" }] }),
  component: () => <AdminCancellationsPage mode="operations" />,
});
