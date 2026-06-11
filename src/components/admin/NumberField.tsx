import { Field, inputCls } from "@/components/admin/AdminPage";
import { numberFieldDisplay, parseNumberField } from "@/lib/admin-number-field";

export function NumberField({
  label,
  value,
  onChange,
  min,
  max,
  step,
  placeholder,
  required,
}: {
  label: string;
  value: number | null | undefined;
  onChange: (value: number | null) => void;
  min?: number;
  max?: number;
  step?: number | string;
  placeholder?: string;
  required?: boolean;
}) {
  return (
    <Field label={label} required={required}>
      <input
        type="number"
        className={inputCls()}
        value={numberFieldDisplay(value)}
        min={min}
        max={max}
        step={step}
        placeholder={placeholder}
        required={required}
        onChange={(e) => onChange(parseNumberField(e.target.value))}
      />
    </Field>
  );
}
