import { useState } from "react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { formatAgeRangeField, parseAgeRangeField } from "@/lib/comma-list-field";

/** Age range as "min, max" months — comma-separated in one field. */
export function AgeRangeField({
  min,
  max,
  onChange,
  required,
}: {
  min: number | null | undefined;
  max: number | null | undefined;
  onChange: (range: { age_min_months: number | null; age_max_months: number | null }) => void;
  required?: boolean;
}) {
  const [draft, setDraft] = useState<string | null>(null);
  const display = draft ?? formatAgeRangeField(min, max);

  return (
    <Field label="Age (months, comma-separated)" required={required}>
      <input
        className={inputCls()}
        value={display}
        placeholder="0, 24"
        required={required}
        inputMode="numeric"
        onChange={(e) => {
          const next = e.target.value;
          setDraft(next);
          const parsed = parseAgeRangeField(next);
          onChange({ age_min_months: parsed.min, age_max_months: parsed.max });
        }}
        onBlur={() => setDraft(null)}
      />
    </Field>
  );
}
