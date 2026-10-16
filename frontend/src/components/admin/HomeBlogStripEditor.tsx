import { Plus, Trash2 } from "lucide-react";
import { Field } from "@/components/admin/AdminPage";
import { NumberField } from "@/components/admin/NumberField";
import {
  HOME_BLOG_STRIP_LIMIT,
  emptyHomeBlogCard,
  homeBlogCardsFromPayload,
  type HomeBlogCardDraft,
} from "@/lib/home-blog-strip";

type BlogOption = { slug: string; title: string };

type Props = {
  posts: BlogOption[];
  payload: Record<string, unknown>;
  onChange: (cards: HomeBlogCardDraft[]) => void;
};

export function HomeBlogStripEditor({ posts, payload, onChange }: Props) {
  const cards = homeBlogCardsFromPayload(payload);
  const atLimit = cards.length >= HOME_BLOG_STRIP_LIMIT;

  const update = (index: number, patch: Partial<HomeBlogCardDraft>) => {
    const next = cards.map((row, i) => (i === index ? { ...row, ...patch } : row));
    onChange(next);
  };

  const add = () => {
    if (atLimit) return;
    const nextOrder = cards.length > 0 ? Math.max(...cards.map((c) => c.sort_order)) + 1 : 0;
    const unused = posts.find((p) => !cards.some((c) => c.slug === p.slug));
    onChange([...cards, { ...emptyHomeBlogCard(nextOrder), slug: unused?.slug ?? "" }]);
  };

  const remove = (index: number) => onChange(cards.filter((_, i) => i !== index));

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <div>
          <div className="text-sm font-medium">Blog cards ({cards.length}/{HOME_BLOG_STRIP_LIMIT})</div>
          <p className="text-xs text-muted-foreground mt-0.5">
            Choose which posts appear on the homepage. Add up to five cards, or leave empty to show
            the five latest posts automatically.
          </p>
        </div>
        <button
          type="button"
          onClick={add}
          disabled={atLimit}
          className="rounded-full glass px-3 py-1.5 text-xs inline-flex items-center gap-1.5 hover:bg-white/60 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Plus className="h-3.5 w-3.5" /> Add blog card
        </button>
      </div>

      {posts.length === 0 && (
        <div className="glass rounded-2xl p-4 text-xs text-muted-foreground">
          No blog posts in the catalog yet — create posts under Admin → Blog, then pick them here.
          You can still add card slots now and choose posts after publishing.
        </div>
      )}

      {cards.length === 0 && (
        <div className="glass rounded-2xl p-4 text-xs text-muted-foreground">
          No cards picked yet. Add a blog card above, or save with none to auto-show the latest posts.
        </div>
      )}

      {cards.map((card, index) => (
        <div key={`${index}-${card.slug}`} className="glass rounded-2xl p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="text-xs uppercase tracking-wider text-primary">Blog card {index + 1}</div>
            <button
              type="button"
              onClick={() => remove(index)}
              className="rounded-full glass p-2 text-destructive hover:bg-destructive/10"
              aria-label={`Remove blog card ${index + 1}`}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
          <div className="grid sm:grid-cols-2 gap-3">
            <Field label="Blog post">
              <select
                className="w-full rounded-2xl glass px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-primary/40"
                value={card.slug}
                onChange={(e) => update(index, { slug: e.target.value.trim().toLowerCase() })}
              >
                <option value="">Select a post…</option>
                {posts.map((p) => (
                  <option key={p.slug} value={p.slug}>
                    {p.title}
                  </option>
                ))}
              </select>
            </Field>
            <NumberField
              label="Sort order"
              value={card.sort_order}
              onChange={(sort_order) => update(index, { sort_order: sort_order ?? 0 })}
              min={0}
              required
            />
          </div>
        </div>
      ))}
    </div>
  );
}
