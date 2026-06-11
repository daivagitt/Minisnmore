import { Plus, Trash2 } from "lucide-react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { ImageUploadField } from "@/components/admin/ImageUploadField";
import { optionalNumberFromUnknown } from "@/lib/admin-number-field";
import { NumberField } from "@/components/admin/NumberField";
import { rowBtn } from "@/components/admin/utils";

type Badge = { icon: string; text: string };
type Plan = {
  id: string;
  name: string;
  price: number | null;
  tagline: string;
  perks: string[];
  featured: boolean;
  image: string;
  color: string;
};
type Feature = { title: string; description: string };

const BADGE_ICONS = [
  { value: "sparkles", label: "Sparkles" },
  { value: "heart", label: "Heart" },
  { value: "gift", label: "Gift" },
];

const PLAN_COLORS = [
  { value: "from-cream to-sand", label: "Cream / sand" },
  { value: "from-tan to-brick", label: "Tan / brick" },
  { value: "from-sage to-cream", label: "Sage / cream" },
  { value: "from-brick to-tan", label: "Brick / tan" },
];

function asBadges(raw: unknown): Badge[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((b) => {
    const row = (b && typeof b === "object" ? b : {}) as Record<string, unknown>;
    return {
      icon: typeof row.icon === "string" ? row.icon : "sparkles",
      text: typeof row.text === "string" ? row.text : "",
    };
  });
}

function asPlans(raw: unknown): Plan[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((p) => {
    const row = (p && typeof p === "object" ? p : {}) as Record<string, unknown>;
    return {
      id: typeof row.id === "string" ? row.id : "",
      name: typeof row.name === "string" ? row.name : "",
      price: optionalNumberFromUnknown(row.price),
      tagline: typeof row.tagline === "string" ? row.tagline : "",
      perks: Array.isArray(row.perks) ? row.perks.map(String) : [],
      featured: !!row.featured,
      image: typeof row.image === "string" ? row.image : "",
      color: typeof row.color === "string" ? row.color : "from-cream to-sand",
    };
  });
}

function asFeatures(raw: unknown): Feature[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((f) => {
    const row = (f && typeof f === "object" ? f : {}) as Record<string, unknown>;
    return {
      title: typeof row.title === "string" ? row.title : "",
      description: typeof row.description === "string" ? row.description : "",
    };
  });
}

export function SubscribeBadgesEditor({
  badges,
  onChange,
}: {
  badges: unknown;
  onChange: (badges: Badge[]) => void;
}) {
  const rows = asBadges(badges);
  const setRows = onChange;

  return (
    <div className="space-y-3">
      <p className="text-xs text-muted-foreground">
        Small highlights shown under the hero on the subscription page.
      </p>
      {rows.map((b, i) => (
        <div key={i} className="flex flex-wrap gap-2 items-end">
          <Field label="Icon">
            <select
              className={inputCls()}
              value={b.icon}
              onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, icon: e.target.value } : x)))}
            >
              {BADGE_ICONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </Field>
          <div className="flex-1 min-w-[200px]">
            <Field label="Text">
              <input
                className={inputCls()}
                value={b.text}
                onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, text: e.target.value } : x)))}
              />
            </Field>
          </div>
          <button type="button" className={rowBtn(false)} onClick={() => setRows(rows.filter((_, j) => j !== i))}>
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      ))}
      <button type="button" className={rowBtn(true)} onClick={() => setRows([...rows, { icon: "sparkles", text: "" }])}>
        <Plus className="h-3.5 w-3.5" /> Add badge
      </button>
    </div>
  );
}

function PerksEditor({ perks, onChange }: { perks: string[]; onChange: (perks: string[]) => void }) {
  return (
    <div className="space-y-2">
      {perks.map((perk, i) => (
        <div key={i} className="flex gap-2">
          <input
            className={inputCls()}
            placeholder="e.g. Free shipping pan-India"
            value={perk}
            onChange={(e) => onChange(perks.map((p, j) => (j === i ? e.target.value : p)))}
          />
          <button type="button" className={rowBtn(false)} onClick={() => onChange(perks.filter((_, j) => j !== i))}>
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      ))}
      <button type="button" className={rowBtn(true)} onClick={() => onChange([...perks, ""])}>
        <Plus className="h-3.5 w-3.5" /> Add perk
      </button>
    </div>
  );
}

export function SubscribePlansEditor({
  plans,
  onChange,
}: {
  plans: unknown;
  onChange: (plans: Plan[]) => void;
}) {
  const rows = asPlans(plans);
  const setRows = onChange;

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted-foreground">
        Each plan needs a unique ID, name, and price (INR). Plans without all three are hidden on the
        storefront.
      </p>
      {rows.map((plan, i) => (
        <div key={i} className="glass rounded-2xl p-4 space-y-3">
          <div className="flex justify-between items-center flex-wrap gap-2">
            <span className="text-sm font-medium">Plan {i + 1}</span>
            <div className="flex items-center gap-3">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={plan.featured}
                  onChange={(e) =>
                    setRows(rows.map((p, j) => (j === i ? { ...p, featured: e.target.checked } : p)))
                  }
                />
                Featured
              </label>
              <button type="button" className={rowBtn(false)} onClick={() => setRows(rows.filter((_, j) => j !== i))}>
                <Trash2 className="h-3.5 w-3.5" /> Remove
              </button>
            </div>
          </div>
          <div className="grid sm:grid-cols-2 gap-3">
            <Field label="Plan ID (unique)">
              <input
                className={inputCls()}
                placeholder="essentials"
                value={plan.id}
                onChange={(e) => setRows(rows.map((p, j) => (j === i ? { ...p, id: e.target.value } : p)))}
              />
            </Field>
            <Field label="Name">
              <input
                className={inputCls()}
                value={plan.name}
                onChange={(e) => setRows(rows.map((p, j) => (j === i ? { ...p, name: e.target.value } : p)))}
              />
            </Field>
            <NumberField
              label="Price (INR)"
              value={plan.price}
              onChange={(price) => setRows(rows.map((p, j) => (j === i ? { ...p, price } : p)))}
              min={0}
              required
            />
            <Field label="Tagline">
              <input
                className={inputCls()}
                value={plan.tagline}
                onChange={(e) => setRows(rows.map((p, j) => (j === i ? { ...p, tagline: e.target.value } : p)))}
              />
            </Field>
            <Field label="Card color">
              <select
                className={inputCls()}
                value={plan.color}
                onChange={(e) => setRows(rows.map((p, j) => (j === i ? { ...p, color: e.target.value } : p)))}
              >
                {PLAN_COLORS.map((c) => (
                  <option key={c.value} value={c.value}>
                    {c.label}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <ImageUploadField
            label="Plan image (optional)"
            folder="subscriptions"
            spec="subscribePlan"
            value={plan.image}
            onChange={(image) => setRows(rows.map((p, j) => (j === i ? { ...p, image } : p)))}
          />
          <Field label="What's included">
            <PerksEditor
              perks={plan.perks}
              onChange={(perks) => setRows(rows.map((p, j) => (j === i ? { ...p, perks } : p)))}
            />
          </Field>
        </div>
      ))}
      <button
        type="button"
        className={rowBtn(true)}
        onClick={() =>
          setRows([
            ...rows,
            {
              id: "",
              name: "",
              price: null,
              tagline: "",
              perks: [],
              featured: false,
              image: "",
              color: "from-cream to-sand",
            },
          ])
        }
      >
        <Plus className="h-3.5 w-3.5" /> Add plan
      </button>
    </div>
  );
}

export function SubscribeFeaturesEditor({
  features,
  onChange,
}: {
  features: unknown;
  onChange: (features: Feature[]) => void;
}) {
  const rows = asFeatures(features);
  const setRows = onChange;

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted-foreground">Three-column footer on the subscription page.</p>
      {rows.map((f, i) => (
        <div key={i} className="glass rounded-2xl p-4 space-y-3">
          <div className="flex justify-between items-center">
            <span className="text-sm font-medium">Feature {i + 1}</span>
            <button type="button" className={rowBtn(false)} onClick={() => setRows(rows.filter((_, j) => j !== i))}>
              <Trash2 className="h-3.5 w-3.5" /> Remove
            </button>
          </div>
          <Field label="Title">
            <input
              className={inputCls()}
              value={f.title}
              onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)))}
            />
          </Field>
          <Field label="Description">
            <textarea
              rows={2}
              className={inputCls()}
              value={f.description}
              onChange={(e) =>
                setRows(rows.map((x, j) => (j === i ? { ...x, description: e.target.value } : x)))
              }
            />
          </Field>
        </div>
      ))}
      <button type="button" className={rowBtn(true)} onClick={() => setRows([...rows, { title: "", description: "" }])}>
        <Plus className="h-3.5 w-3.5" /> Add feature
      </button>
    </div>
  );
}
