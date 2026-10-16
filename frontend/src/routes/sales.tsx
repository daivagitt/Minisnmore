import { createFileRoute } from "@tanstack/react-router";
import { StaffShell } from "@/components/staff/StaffShell";

export const Route = createFileRoute("/sales")({
  head: () => ({ meta: [{ title: "Sales · Mini's n More" }] }),
  component: () => <StaffShell allowedRole="ROLE_SALES" />,
});
