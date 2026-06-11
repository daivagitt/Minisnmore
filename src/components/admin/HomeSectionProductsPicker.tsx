import { toast } from "sonner";
import { Field } from "@/components/admin/AdminPage";
import {
  HOME_SECTION_PRODUCT_LIMIT,
  normalizeHomeSectionProductSlugs,
  pruneHomeSectionProductSlugs,
} from "@/lib/home-products";

type Props = {
  products: { slug: string; name: string }[];
  slugs: string[];
  onChange: (slugs: string[]) => void;
};

export function HomeSectionProductsPicker({ products, slugs, onChange }: Props) {
  const knownSlugs = new Set(products.map((p) => p.slug));
  const selected = pruneHomeSectionProductSlugs(slugs, knownSlugs);
  const removed = normalizeHomeSectionProductSlugs(slugs).filter((slug) => !knownSlugs.has(slug));
  const atLimit = selected.length >= HOME_SECTION_PRODUCT_LIMIT;

  const toggle = (slug: string, checked: boolean) => {
    if (checked) {
      if (selected.includes(slug)) return;
      if (atLimit) {
        toast.error(`You can pick at most ${HOME_SECTION_PRODUCT_LIMIT} products for this section.`);
        return;
      }
      onChange(normalizeHomeSectionProductSlugs([...selected, slug]));
      return;
    }
    onChange(selected.filter((s) => s !== slug));
  };

  return (
    <Field
      label={`Products to show (${selected.length}/${HOME_SECTION_PRODUCT_LIMIT})`}
      hint={`Choose up to ${HOME_SECTION_PRODUCT_LIMIT} products for the homepage grid. Leave empty to auto-pick by product flags — customers use Explore to see all.`}
    >
      {removed.length > 0 && (
        <p className="text-xs text-amber-700 mb-2">
          Removed from catalog (not counted toward the limit): {removed.join(", ")}. Save to clear
          these picks.
        </p>
      )}
      <div className="glass rounded-2xl p-3 max-h-56 overflow-y-auto grid sm:grid-cols-2 gap-1.5">
        {products.length === 0 && (
          <div className="text-xs text-muted-foreground col-span-full">
            No products yet — add some under Products.
          </div>
        )}
        {products.map((p) => {
          const checked = selected.includes(p.slug);
          const disabled = !checked && atLimit;
          return (
            <label
              key={p.slug}
              className={`flex items-center gap-2 text-sm ${disabled ? "opacity-50 cursor-not-allowed" : ""}`}
            >
              <input
                type="checkbox"
                checked={checked}
                disabled={disabled}
                onChange={(e) => toggle(p.slug, e.target.checked)}
              />
              <span className="truncate">{p.name}</span>
            </label>
          );
        })}
      </div>
    </Field>
  );
}
