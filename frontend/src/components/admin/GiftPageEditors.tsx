import { Plus, Trash2 } from "lucide-react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { rowBtn } from "@/components/admin/utils";
import {
  GIFT_FEATURE_ICONS,
  type GiftAgeOption,
  type GiftBudget,
  type GiftFeature,
  type GiftPageContent,
} from "@/lib/gift-page";

function StringListEditor({
  label,
  values,
  onChange,
  rows = 1,
  placeholder,
}: {
  label: string;
  values: string[];
  onChange: (next: string[]) => void;
  rows?: number;
  placeholder?: string;
}) {
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <div className="text-xs uppercase tracking-wider text-muted-foreground">{label}</div>
        <button type="button" className={rowBtn(true)} onClick={() => onChange([...values, ""])}>
          <Plus className="h-3 w-3" /> Add
        </button>
      </div>
      {values.map((value, i) => (
        <div key={`${label}-${i}`} className="flex gap-2 items-start">
          {rows > 1 ? (
            <textarea
              rows={rows}
              className={inputCls()}
              value={value}
              placeholder={placeholder}
              onChange={(e) => onChange(values.map((x, j) => (j === i ? e.target.value : x)))}
            />
          ) : (
            <input
              className={inputCls()}
              value={value}
              placeholder={placeholder}
              onChange={(e) => onChange(values.map((x, j) => (j === i ? e.target.value : x)))}
            />
          )}
          <button
            type="button"
            className={rowBtn(false)}
            onClick={() => onChange(values.filter((_, j) => j !== i))}
            aria-label={`Remove ${label} ${i + 1}`}
          >
            <Trash2 className="h-3 w-3" />
          </button>
        </div>
      ))}
    </div>
  );
}

export function GiftFeaturesEditor({
  features,
  onChange,
}: {
  features: GiftFeature[];
  onChange: (next: GiftFeature[]) => void;
}) {
  return (
    <div className="space-y-3">
      {features.map((feature, i) => (
        <div key={i} className="glass rounded-2xl p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="font-medium text-sm">Feature {i + 1}</div>
            <button
              type="button"
              className={rowBtn(false)}
              onClick={() => onChange(features.filter((_, j) => j !== i))}
            >
              <Trash2 className="h-3 w-3" /> Remove
            </button>
          </div>
          <div className="grid sm:grid-cols-[140px_1fr] gap-3">
            <Field label="Icon">
              <select
                className={inputCls()}
                value={feature.icon}
                onChange={(e) =>
                  onChange(
                    features.map((x, j) =>
                      j === i ? { ...x, icon: e.target.value as GiftFeature["icon"] } : x,
                    ),
                  )
                }
              >
                {GIFT_FEATURE_ICONS.map((icon) => (
                  <option key={icon} value={icon}>
                    {icon}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Title">
              <input
                className={inputCls()}
                value={feature.title}
                onChange={(e) =>
                  onChange(features.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)))
                }
              />
            </Field>
          </div>
          <Field label="Body">
            <textarea
              rows={2}
              className={inputCls()}
              value={feature.body}
              onChange={(e) =>
                onChange(features.map((x, j) => (j === i ? { ...x, body: e.target.value } : x)))
              }
            />
          </Field>
        </div>
      ))}
      <button
        type="button"
        className={rowBtn(true)}
        onClick={() => onChange([...features, { icon: "gift", title: "", body: "" }])}
      >
        <Plus className="h-3 w-3" /> Add feature
      </button>
    </div>
  );
}

export function GiftBudgetsEditor({
  budgets,
  onChange,
}: {
  budgets: GiftBudget[];
  onChange: (next: GiftBudget[]) => void;
}) {
  return (
    <div className="space-y-3">
      {budgets.map((budget, i) => (
        <div key={i} className="grid sm:grid-cols-[1fr_2fr_1fr_auto] gap-2 items-end">
          <Field label="Id">
            <input
              className={inputCls()}
              value={budget.id}
              placeholder="u2k"
              onChange={(e) =>
                onChange(budgets.map((x, j) => (j === i ? { ...x, id: e.target.value } : x)))
              }
            />
          </Field>
          <Field label="Label">
            <input
              className={inputCls()}
              value={budget.label}
              placeholder="Under ₹2,000"
              onChange={(e) =>
                onChange(budgets.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))
              }
            />
          </Field>
          <Field label="Max (₹)">
            <input
              type="number"
              min={1}
              className={inputCls()}
              value={budget.max || ""}
              onChange={(e) =>
                onChange(
                  budgets.map((x, j) =>
                    j === i ? { ...x, max: Number(e.target.value) || 0 } : x,
                  ),
                )
              }
            />
          </Field>
          <button
            type="button"
            className={rowBtn(false)}
            onClick={() => onChange(budgets.filter((_, j) => j !== i))}
            aria-label={`Remove budget ${i + 1}`}
          >
            <Trash2 className="h-3 w-3" />
          </button>
        </div>
      ))}
      <button
        type="button"
        className={rowBtn(true)}
        onClick={() => onChange([...budgets, { id: "", label: "", max: 0 }])}
      >
        <Plus className="h-3 w-3" /> Add budget
      </button>
    </div>
  );
}

export function GiftAgesEditor({
  ages,
  onChange,
}: {
  ages: GiftAgeOption[];
  onChange: (next: GiftAgeOption[]) => void;
}) {
  return (
    <div className="space-y-2">
      <div className="text-xs text-muted-foreground">
        Age values are fixed to product age ranges. Only labels are editable.
      </div>
      {ages.map((age, i) => (
        <div key={age.value} className="grid sm:grid-cols-[120px_1fr] gap-2 items-end">
          <Field label="Value">
            <input className={inputCls()} value={age.value} disabled readOnly />
          </Field>
          <Field label="Label">
            <input
              className={inputCls()}
              value={age.label}
              onChange={(e) =>
                onChange(ages.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))
              }
            />
          </Field>
        </div>
      ))}
    </div>
  );
}

export function GiftStringListEditor({
  label,
  values,
  onChange,
  rows,
  placeholder,
}: {
  label: string;
  values: string[];
  onChange: (next: string[]) => void;
  rows?: number;
  placeholder?: string;
}) {
  return (
    <StringListEditor
      label={label}
      values={values}
      onChange={onChange}
      rows={rows}
      placeholder={placeholder}
    />
  );
}

export function patchGift<K extends keyof GiftPageContent>(
  content: GiftPageContent,
  key: K,
  value: GiftPageContent[K],
): GiftPageContent {
  return { ...content, [key]: value };
}
