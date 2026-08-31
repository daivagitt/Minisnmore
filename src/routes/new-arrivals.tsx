import { createFileRoute } from "@tanstack/react-router";
import { CollectionView } from "@/components/CollectionView";
import { compareNewArrivalsFirst } from "@/lib/product-sort";

export const Route = createFileRoute("/new-arrivals")({
  head: () => ({ meta: [{ title: "New Arrivals · Mini's n More" }] }),
  component: () => (
    <CollectionView
      eyebrow="Fresh off the loom"
      title="New"
      script="arrivals"
      subtitle="The latest organic essentials, hand-finished by our atelier of artisan mothers."
      sort={compareNewArrivalsFirst}
    />
  ),
});
