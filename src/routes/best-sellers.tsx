import { createFileRoute } from "@tanstack/react-router";
import { CollectionView } from "@/components/CollectionView";

export const Route = createFileRoute("/best-sellers")({
  head: () => ({ meta: [{ title: "Best Sellers · Mini's n More" }] }),
  component: () => (
    <CollectionView
      eyebrow="Forever favourites"
      title="Best"
      script="sellers"
      subtitle="Pieces our little ones reach for again and again — soft, sturdy, certified organic."
      filter={(p) => p.isBestseller}
    />
  ),
});