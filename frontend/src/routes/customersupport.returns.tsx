import { createFileRoute } from "@tanstack/react-router";
import { AdminReturnsPage } from "@/routes/admin.returns";

export const Route = createFileRoute("/customersupport/returns")({
  head: () => ({ meta: [{ title: "Returns · Customer Support · Mini's n More" }] }),
  component: () => <AdminReturnsPage mode="support" />,
});
