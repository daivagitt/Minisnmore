import { Plus, Trash2 } from "lucide-react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { ImageUploadField } from "@/components/admin/ImageUploadField";
import { rowBtn } from "@/components/admin/utils";
import {
  ABOUT_FEATURE_ICONS,
  type AboutFeature,
  type AboutFounder,
  type AboutPageContent,
  type AboutStat,
} from "@/lib/about-page";

function StringListEditor({
  label,
  values,
  onChange,
  rows = 3,
}: {
  label: string;
  values: string[];
  onChange: (next: string[]) => void;
  rows?: number;
}) {
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <div className="text-xs uppercase tracking-wider text-muted-foreground">{label}</div>
        <button
          type="button"
          className={rowBtn(true)}
          onClick={() => onChange([...values, ""])}
        >
          <Plus className="h-3 w-3" /> Add
        </button>
      </div>
      {values.map((value, i) => (
        <div key={`${label}-${i}`} className="flex gap-2 items-start">
          <textarea
            rows={rows}
            className={inputCls()}
            value={value}
            onChange={(e) => onChange(values.map((x, j) => (j === i ? e.target.value : x)))}
          />
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

export function AboutStatsEditor({
  stats,
  onChange,
}: {
  stats: AboutStat[];
  onChange: (next: AboutStat[]) => void;
}) {
  return (
    <div className="space-y-3">
      {stats.map((stat, i) => (
        <div key={i} className="grid sm:grid-cols-[1fr_2fr_auto] gap-2 items-end">
          <Field label="Value">
            <input
              className={inputCls()}
              value={stat.value}
              onChange={(e) => onChange(stats.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))}
            />
          </Field>
          <Field label="Label">
            <input
              className={inputCls()}
              value={stat.label}
              onChange={(e) => onChange(stats.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))}
            />
          </Field>
          <button type="button" className={rowBtn(false)} onClick={() => onChange(stats.filter((_, j) => j !== i))}>
            <Trash2 className="h-3 w-3" />
          </button>
        </div>
      ))}
      <button
        type="button"
        className={rowBtn(true)}
        onClick={() => onChange([...stats, { value: "", label: "" }])}
      >
        <Plus className="h-3 w-3" /> Add stat
      </button>
    </div>
  );
}

export function AboutFeaturesEditor({
  features,
  onChange,
}: {
  features: AboutFeature[];
  onChange: (next: AboutFeature[]) => void;
}) {
  return (
    <div className="space-y-3">
      {features.map((feature, i) => (
        <div key={i} className="rounded-2xl border border-border/60 p-3 space-y-2">
          <div className="grid sm:grid-cols-[160px_1fr_auto] gap-2 items-end">
            <Field label="Icon">
              <select
                className={inputCls()}
                value={feature.icon}
                onChange={(e) =>
                  onChange(
                    features.map((x, j) =>
                      j === i ? { ...x, icon: e.target.value as AboutFeature["icon"] } : x,
                    ),
                  )
                }
              >
                {ABOUT_FEATURE_ICONS.map((icon) => (
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
                onChange={(e) => onChange(features.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)))}
              />
            </Field>
            <button
              type="button"
              className={rowBtn(false)}
              onClick={() => onChange(features.filter((_, j) => j !== i))}
            >
              <Trash2 className="h-3 w-3" />
            </button>
          </div>
          <Field label="Description">
            <textarea
              rows={2}
              className={inputCls()}
              value={feature.description}
              onChange={(e) =>
                onChange(features.map((x, j) => (j === i ? { ...x, description: e.target.value } : x)))
              }
            />
          </Field>
        </div>
      ))}
      <button
        type="button"
        className={rowBtn(true)}
        onClick={() => onChange([...features, { icon: "leaf", title: "", description: "" }])}
      >
        <Plus className="h-3 w-3" /> Add highlight
      </button>
    </div>
  );
}

export function AboutFoundersEditor({
  founders,
  onChange,
}: {
  founders: AboutFounder[];
  onChange: (next: AboutFounder[]) => void;
}) {
  return (
    <div className="space-y-4">
      {founders.map((founder, i) => (
        <div key={i} className="rounded-2xl border border-border/60 p-4 space-y-3">
          <div className="flex justify-between items-center gap-2">
            <div className="font-medium">Founder {i + 1}</div>
            <button type="button" className={rowBtn(false)} onClick={() => onChange(founders.filter((_, j) => j !== i))}>
              <Trash2 className="h-3 w-3" /> Remove
            </button>
          </div>
          <div className="grid sm:grid-cols-2 gap-3">
            <Field label="Name">
              <input
                className={inputCls()}
                value={founder.name}
                onChange={(e) => onChange(founders.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))}
              />
            </Field>
            <Field label="Role">
              <input
                className={inputCls()}
                value={founder.role}
                onChange={(e) => onChange(founders.map((x, j) => (j === i ? { ...x, role: e.target.value } : x)))}
              />
            </Field>
          </div>
          <ImageUploadField
            label="Photo"
            folder="about"
            spec="aboutFounder"
            value={founder.image_url}
            onChange={(image_url) => onChange(founders.map((x, j) => (j === i ? { ...x, image_url } : x)))}
          />
          <StringListEditor
            label="Biography paragraphs"
            values={founder.paragraphs}
            onChange={(paragraphs) => onChange(founders.map((x, j) => (j === i ? { ...x, paragraphs } : x)))}
          />
          <Field label="Quote">
            <textarea
              rows={3}
              className={inputCls()}
              value={founder.quote}
              onChange={(e) => onChange(founders.map((x, j) => (j === i ? { ...x, quote: e.target.value } : x)))}
            />
          </Field>
        </div>
      ))}
      <button
        type="button"
        className={rowBtn(true)}
        onClick={() =>
          onChange([...founders, { name: "", role: "", image_url: "", paragraphs: [""], quote: "" }])
        }
      >
        <Plus className="h-3 w-3" /> Add founder
      </button>
    </div>
  );
}

export function AboutParagraphsEditor({
  label,
  values,
  onChange,
}: {
  label: string;
  values: string[];
  onChange: (next: string[]) => void;
}) {
  return <StringListEditor label={label} values={values} onChange={onChange} rows={4} />;
}

export function patchAbout<K extends keyof AboutPageContent>(
  content: AboutPageContent,
  key: K,
  value: AboutPageContent[K],
): AboutPageContent {
  return { ...content, [key]: value };
}
