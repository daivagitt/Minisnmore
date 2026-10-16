type SubscribeOptInProps = {
  checked: boolean;
  onChange: (next: boolean) => void;
  /** Optional hint under the label. When omitted, only a short uncheck hint is shown while checked. */
  helper?: string;
  /** Hint shown while checked. Defaults to the unsubscribe hint used for existing subscribers. */
  checkedHint?: string;
};

export function SubscribeOptIn({
  checked,
  onChange,
  helper,
  checkedHint = "Uncheck to unsubscribe from emails and offers.",
}: SubscribeOptInProps) {
  const hint = checked
    ? checkedHint
    : helper?.trim() || null;

  return (
    <label className="flex items-start gap-2.5 text-sm cursor-pointer select-none">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="mt-0.5 h-4 w-4 shrink-0 accent-primary"
      />
      <span>
        <span className="font-medium">Subscribe to emails and offers</span>
        {hint ? <span className="block text-xs text-muted-foreground mt-0.5">{hint}</span> : null}
      </span>
    </label>
  );
}
