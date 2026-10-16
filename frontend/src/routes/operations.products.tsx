import { createFileRoute } from "@tanstack/react-router";
import { SalesProductsPage } from "@/routes/sales.products";

export const Route = createFileRoute("/operations/products")({
  head: () => ({ meta: [{ title: "Products · Operations · Mini's n More" }] }),
  component: () => <SalesProductsPage eyebrow="Operations" />,
});
