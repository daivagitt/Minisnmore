import { Plus, Trash2 } from "lucide-react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { ImageUploadField } from "@/components/admin/ImageUploadField";
import { HOME_SECTION_CARD_LIMIT } from "@/lib/home-section-limit";
import {
  emptyHomeReview,
  homeReviewDraftsFromPayload,
  type HomeReviewDraft,
} from "@/lib/home-reviews";

type Props = {
  payload: Record<string, unknown>;
  onChange: (reviews: HomeReviewDraft[]) => void;
};

export function HomeReviewsEditor({ payload, onChange }: Props) {
  const reviews = homeReviewDraftsFromPayload(payload);

  const update = (index: number, patch: Partial<HomeReviewDraft>) => {
    const next = reviews.map((row, i) => (i === index ? { ...row, ...patch } : row));
    onChange(next);
  };

  const atLimit = reviews.length >= HOME_SECTION_CARD_LIMIT;
  const add = () => {
    if (atLimit) return;
    onChange([...reviews, emptyHomeReview()]);
  };

  const remove = (index: number) => onChange(reviews.filter((_, i) => i !== index));

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <div>
          <div className="text-sm font-medium">
            Customer reviews ({reviews.length}/{HOME_SECTION_CARD_LIMIT})
          </div>
          <p className="text-xs text-muted-foreground mt-0.5">
            Add up to five review cards, or leave empty to show built-in sample reviews.
          </p>
        </div>
        <button
          type="button"
          onClick={add}
          disabled={atLimit}
          className="rounded-full glass px-3 py-1.5 text-xs inline-flex items-center gap-1.5 hover:bg-white/60 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Plus className="h-3.5 w-3.5" /> Add review
        </button>
      </div>

      {reviews.length === 0 && (
        <div className="glass rounded-2xl p-4 text-xs text-muted-foreground">
          No custom reviews yet. Add one above, or save with none to keep the default carousel.
        </div>
      )}

      {reviews.map((review, index) => (
        <div key={index} className="glass rounded-2xl p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="text-xs uppercase tracking-wider text-primary">Review {index + 1}</div>
            <button
              type="button"
              onClick={() => remove(index)}
              className="rounded-full glass p-2 text-destructive hover:bg-destructive/10"
              aria-label={`Remove review ${index + 1}`}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>

          <div className="grid sm:grid-cols-2 gap-3">
            <Field label="Headline (required)">
              <input
                className={inputCls()}
                value={review.title}
                onChange={(e) => update(index, { title: e.target.value })}
                placeholder="Softest muslin we own"
                maxLength={120}
              />
            </Field>
            <Field label="Author (required)">
              <input
                className={inputCls()}
                value={review.author}
                onChange={(e) => update(index, { author: e.target.value })}
                placeholder="Priya S., Bengaluru"
                maxLength={80}
              />
            </Field>
          </div>

          <Field label="Review text (required)">
            <textarea
              className={inputCls() + " min-h-[96px]"}
              value={review.body}
              onChange={(e) => update(index, { body: e.target.value })}
              placeholder="What did they love about your product or service?"
              maxLength={600}
            />
          </Field>

          <div className="grid sm:grid-cols-2 gap-3">
            <Field label="Date (optional)">
              <input
                className={inputCls()}
                value={review.date}
                onChange={(e) => update(index, { date: e.target.value })}
                placeholder="Mar 2025"
                maxLength={40}
              />
            </Field>
            <Field label="Rating (required)">
              <select
                className={inputCls()}
                value={review.rating}
                onChange={(e) => update(index, { rating: Number(e.target.value) })}
              >
                {[5, 4, 3, 2, 1].map((stars) => (
                  <option key={stars} value={stars}>
                    {stars} star{stars === 1 ? "" : "s"}
                  </option>
                ))}
              </select>
            </Field>
          </div>

          <ImageUploadField
            label="Photo (optional)"
            value={review.photo}
            onChange={(url) => update(index, { photo: url })}
            folder="home"
            placeholder="https://… or upload"
            previewClassName="h-14 w-14 rounded-full object-cover shrink-0"
            hint="Square portrait works best. Leave empty to use a default avatar."
          />
        </div>
      ))}
    </div>
  );
}
