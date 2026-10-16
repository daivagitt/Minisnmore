import { Plus, Trash2 } from "lucide-react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { ImageSpecHint } from "@/components/admin/ImageSpecHint";
import { NumberField } from "@/components/admin/NumberField";
import { ADMIN_VIDEO_SPECS } from "@/lib/admin-image-specs";
import {
  HOME_WATCH_SHOP_LIMIT,
  emptyWatchReel,
  watchReelDraftsFromPayload,
  type WatchReelDraft,
} from "@/lib/watch-reels";

type ProductOption = { slug: string; name: string; sku: string };

type Props = {
  products: ProductOption[];
  payload: Record<string, unknown>;
  onChange: (reels: WatchReelDraft[]) => void;
};

export function HomeWatchShopEditor({ products, payload, onChange }: Props) {
  const reels = watchReelDraftsFromPayload(payload);
  const atLimit = reels.length >= HOME_WATCH_SHOP_LIMIT;

  const update = (index: number, patch: Partial<WatchReelDraft>) => {
    onChange(reels.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  const add = () => {
    if (atLimit) return;
    const nextOrder = reels.length > 0 ? Math.max(...reels.map((r) => r.sort_order)) + 1 : 0;
    const unused = products.find(
      (p) => !reels.some((r) => (r.sku && p.sku && r.sku === p.sku) || r.product_slug === p.slug),
    );
    onChange([
      ...reels,
      {
        ...emptyWatchReel(nextOrder),
        sku: unused?.sku ?? "",
        product_slug: unused?.slug ?? "",
      },
    ]);
  };

  const remove = (index: number) => onChange(reels.filter((_, i) => i !== index));

  const pickProduct = (index: number, slug: string) => {
    const product = products.find((p) => p.slug === slug);
    update(index, {
      product_slug: product?.slug ?? "",
      sku: product?.sku ?? "",
    });
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <div>
          <div className="text-sm font-medium">
            Watch & Shop reels ({reels.length}/{HOME_WATCH_SHOP_LIMIT})
          </div>
          <p className="text-xs text-muted-foreground mt-0.5">
            Add a video link for each reel and relate it to a product SKU. The homepage shows the
            video and links through to that product.
          </p>
        </div>
        <button
          type="button"
          onClick={add}
          disabled={atLimit}
          className="rounded-full glass px-3 py-1.5 text-xs inline-flex items-center gap-1.5 hover:bg-white/60 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Plus className="h-3.5 w-3.5" /> Add reel
        </button>
      </div>

      {products.length === 0 && (
        <div className="glass rounded-2xl p-4 text-xs text-muted-foreground">
          No products in the catalog yet — add SKUs under Admin → Products, then relate them here.
        </div>
      )}

      {reels.length === 0 && (
        <div className="glass rounded-2xl p-4 text-xs text-muted-foreground">
          No reels yet. Add one above, paste an MP4 link, pick a SKU, and save. Turn the section on
          to show it on the homepage.
        </div>
      )}

      {reels.map((reel, index) => (
        <div key={`${index}-${reel.product_slug}-${reel.sku}`} className="glass rounded-2xl p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="text-xs uppercase tracking-wider text-primary">Reel {index + 1}</div>
            <button
              type="button"
              onClick={() => remove(index)}
              className="rounded-full glass p-2 text-destructive hover:bg-destructive/10"
              aria-label={`Remove reel ${index + 1}`}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
          <div className="grid sm:grid-cols-2 gap-3">
            <Field
              label="Product SKU"
              required
              hint="Links the reel to a live catalog product."
            >
              <select
                className={inputCls()}
                value={reel.product_slug}
                onChange={(e) => pickProduct(index, e.target.value)}
              >
                <option value="">Select a SKU…</option>
                {products.map((p) => (
                  <option key={p.slug} value={p.slug}>
                    {p.sku ? `${p.sku} — ${p.name}` : p.name}
                  </option>
                ))}
              </select>
            </Field>
            <NumberField
              label="Sort order"
              value={reel.sort_order}
              onChange={(sort_order) => update(index, { sort_order: sort_order ?? 0 })}
              min={0}
              required
            />
          </div>
          <Field
            label="Video link"
            required
            hint="Public MP4 URL (uploaded to R2/S3 or another CDN)."
          >
            <input
              className={inputCls()}
              value={reel.video}
              onChange={(e) => update(index, { video: e.target.value })}
              placeholder="https://…/reel.mp4"
            />
            <ImageSpecHint spec={ADMIN_VIDEO_SPECS.watchShopReel} className="mt-1.5" />
          </Field>
        </div>
      ))}
    </div>
  );
}
