import { createFileRoute } from "@tanstack/react-router";
import { AdminUsersPage } from "./admin.users";

export const Route = createFileRoute("/admin/customers")({
  head: () => ({ meta: [{ title: "Customers · Mini's n More" }] }),
  component: AdminUsersPage,
});
