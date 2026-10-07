import { createFileRoute } from "@tanstack/react-router";
import { AdminInvoicesPage } from "@/routes/admin.invoices";

export const Route = createFileRoute("/operations/invoices")({
  head: () => ({ meta: [{ title: "Invoices · Operations · Mini's n More" }] }),
  component: () => <AdminInvoicesPage readOnly eyebrow="Operations" />,
});
