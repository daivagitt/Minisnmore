import { createFileRoute } from "@tanstack/react-router";
import { AdminContactMessagesPage } from "@/routes/admin.contact-messages";

export const Route = createFileRoute("/operations/contact-messages")({
  head: () => ({ meta: [{ title: "Contact messages · Operations · Mini's n More" }] }),
  component: () => <AdminContactMessagesPage canDelete={false} />,
});
