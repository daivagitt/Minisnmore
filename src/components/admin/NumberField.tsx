import { Field, inputCls } from "@/components/admin/AdminPage";
import { numberFieldDisplay, parseNumberField } from "@/lib/admin-number-field";

export function NumberField({
  label,
  hint,
  value,
  onChange,
  min,
  max,
  /** Defaults to "any" so price/MRP decimals (e.g. 2.67) are valid HTML5 input. Pass 1 for integers. */
  step = "any",
  placeholder,
  required,
  readOnly,
}: {
  label: string;
  hint?: string;
  value: number | null | undefined;
  onChange: (value: number | null) => void;
  min?: number;
  max?: number;
  step?: number | string;
  placeholder?: string;
  required?: boolean;
  readOnly?: boolean;
}) {
  return (
    <Field label={label} hint={hint}>
      <input
        type="number"
        className={`${inputCls()}${readOnly ? " opacity-70 cursor-default" : ""}`}
        value={numberFieldDisplay(value)}
        min={min}
        max={max}
        step={step}
        placeholder={placeholder}
        required={required}
        readOnly={readOnly}
        onChange={(e) => {
          if (readOnly) return;
          onChange(parseNumberField(e.target.value));
        }}
      />
    </Field>
  );
}
