import { createFileRoute } from "@tanstack/react-router";
import { AdminContactMessagesPage } from "@/routes/admin.contact-messages";

export const Route = createFileRoute("/customersupport/contact-messages")({
  head: () => ({ meta: [{ title: "Contact messages · Customer Support · Mini's n More" }] }),
  component: () => <AdminContactMessagesPage canDelete={false} />,
});
