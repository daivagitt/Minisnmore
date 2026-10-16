import { createFileRoute } from "@tanstack/react-router";
import { StaffShell } from "@/components/staff/StaffShell";

export const Route = createFileRoute("/operations")({
  head: () => ({ meta: [{ title: "Operations · Mini's n More" }] }),
  component: () => <StaffShell allowedRole="ROLE_OPERATIONS" />,
});
