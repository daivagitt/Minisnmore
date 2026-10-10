import { createFileRoute } from "@tanstack/react-router";
import { AdminReturnsPage } from "@/routes/admin.returns";

export const Route = createFileRoute("/operations/returns")({
  head: () => ({ meta: [{ title: "Returns · Operations · Mini's n More" }] }),
  component: () => <AdminReturnsPage mode="operations" />,
});
