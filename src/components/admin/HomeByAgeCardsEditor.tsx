import { Plus, Trash2 } from "lucide-react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { ImageUploadField } from "@/components/admin/ImageUploadField";
import { NumberField } from "@/components/admin/NumberField";
import {
  HOME_AGE_FILTER_OPTIONS,
  emptyHomeByAgeCard,
  homeByAgeDraftsFromPayload,
  type HomeByAgeCardDraft,
} from "@/lib/home-by-age";
import { HOME_SECTION_CARD_LIMIT } from "@/lib/home-section-limit";

type Props = {
  payload: Record<string, unknown>;
  onChange: (cards: HomeByAgeCardDraft[]) => void;
};

export function HomeByAgeCardsEditor({ payload, onChange }: Props) {
  const cards = homeByAgeDraftsFromPayload(payload);

  const update = (index: number, patch: Partial<HomeByAgeCardDraft>) => {
    const next = cards.map((row, i) => (i === index ? { ...row, ...patch } : row));
    onChange(next);
  };

  const atLimit = cards.length >= HOME_SECTION_CARD_LIMIT;
  const add = () => {
    if (atLimit) return;
    const nextOrder = cards.length > 0 ? Math.max(...cards.map((c) => c.sort_order)) + 1 : 0;
    onChange([...cards, emptyHomeByAgeCard(nextOrder)]);
  };

  const remove = (index: number) => onChange(cards.filter((_, i) => i !== index));

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <div>
          <div className="text-sm font-medium">
            Age category cards ({cards.length}/{HOME_SECTION_CARD_LIMIT})
          </div>
          <p className="text-xs text-muted-foreground mt-0.5">
            Each card links to the shop filtered by age. Add up to five cards in Admin — the
            homepage shows nothing here until cards are saved.
          </p>
        </div>
        <button
          type="button"
          onClick={add}
          disabled={atLimit}
          className="rounded-full glass px-3 py-1.5 text-xs inline-flex items-center gap-1.5 hover:bg-white/60 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Plus className="h-3.5 w-3.5" /> Add card
        </button>
      </div>

      {cards.length === 0 && (
        <div className="glass rounded-2xl p-4 text-xs text-muted-foreground">
          No age cards yet. Add one above and save to show this section on the homepage.
        </div>
      )}

      {cards.map((card, index) => (
        <div key={index} className="glass rounded-2xl p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="text-xs uppercase tracking-wider text-primary">Card {index + 1}</div>
            <button
              type="button"
              onClick={() => remove(index)}
              className="rounded-full glass p-2 text-destructive hover:bg-destructive/10"
              aria-label={`Remove card ${index + 1}`}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>

          <div className="grid sm:grid-cols-2 gap-3">
            <Field label="Category name (required)" hint='Shown on the card, e.g. "Newborn".'>
              <input
                className={inputCls()}
                value={card.label}
                onChange={(e) => update(index, { label: e.target.value })}
                placeholder="Newborn"
                maxLength={40}
              />
            </Field>
            <Field label="Age range label (required)" hint='Short line under the name, e.g. "0–3M".'>
              <input
                className={inputCls()}
                value={card.note}
                onChange={(e) => update(index, { note: e.target.value })}
                placeholder="0–3M"
                maxLength={24}
              />
            </Field>
          </div>

          <div className="grid sm:grid-cols-2 gap-3">
            <Field
              label="Shop filter (required)"
              hint="Which age group opens when a customer taps this card."
            >
              <select
                className={inputCls()}
                value={card.age_filter}
                onChange={(e) =>
                  update(index, { age_filter: e.target.value as HomeByAgeCardDraft["age_filter"] })
                }
              >
                {HOME_AGE_FILTER_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
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

          <ImageUploadField
            label="Card image (required)"
            value={card.image}
            onChange={(url) => update(index, { image: url })}
            folder="home"
            placeholder="https://… or upload"
            previewClassName="h-20 w-20 rounded-2xl object-cover shrink-0"
            hint="Square image works best. Shown as the card background on the homepage."
            spec="portraitCard"
          />
        </div>
      ))}
    </div>
  );
}
