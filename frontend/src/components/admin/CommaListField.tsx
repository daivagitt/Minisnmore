import { useState } from "react";
import { Field, inputCls } from "@/components/admin/AdminPage";
import { formatCommaList, parseCommaList } from "@/lib/comma-list-field";

/** Comma-separated text list; keeps raw draft while typing so trailing commas work. */
export function CommaListField({
  label,
  value,
  onChange,
  placeholder,
  required,
  readOnly,
  hint,
}: {
  label: string;
  value: string[];
  onChange?: (value: string[]) => void;
  placeholder?: string;
  required?: boolean;
  readOnly?: boolean;
  hint?: string;
}) {
  const [draft, setDraft] = useState<string | null>(null);
  const display = draft ?? formatCommaList(value);

  return (
    <Field label={label} required={required} hint={hint}>
      <input
        className={inputCls() + (readOnly ? " bg-muted/30 cursor-default" : "")}
        value={display}
        placeholder={placeholder}
        required={required}
        readOnly={readOnly}
        onChange={(e) => {
          if (readOnly || !onChange) return;
          const next = e.target.value;
          setDraft(next);
          onChange(parseCommaList(next));
        }}
        onBlur={() => setDraft(null)}
      />
    </Field>
  );
}
