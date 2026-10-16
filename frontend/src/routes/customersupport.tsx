import { createFileRoute } from "@tanstack/react-router";
import { StaffShell } from "@/components/staff/StaffShell";

export const Route = createFileRoute("/customersupport")({
  head: () => ({ meta: [{ title: "Customer Support · Mini's n More" }] }),
  component: () => <StaffShell allowedRole="ROLE_CUSTOMER_SUPPORT" />,
});
