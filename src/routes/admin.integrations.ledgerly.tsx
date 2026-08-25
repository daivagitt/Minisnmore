import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/admin/integrations/ledgerly")({
  beforeLoad: () => {
    throw redirect({ to: "/admin" });
  },
});
