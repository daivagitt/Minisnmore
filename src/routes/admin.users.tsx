import { createFileRoute, redirect } from "@tanstack/react-router";

/** Legacy path — Customers is the single admin list for shopper accounts. */
export const Route = createFileRoute("/admin/users")({
  beforeLoad: () => {
    throw redirect({ to: "/admin/customers" });
  },
});
