export function formatCaseAge(row: {
  case_age_hours?: number;
  sla_due_at?: string | null;
  sla_overdue?: boolean;
}): string {
  const age = row.case_age_hours ?? 0;
  const ageLabel = age < 24 ? `${age}h old` : `${Math.floor(age / 24)}d old`;
  if (!row.sla_due_at) {
    return ageLabel;
  }
  const due = new Date(row.sla_due_at).toLocaleString("en-IN", { dateStyle: "short", timeStyle: "short" });
  return row.sla_overdue ? `${ageLabel} · overdue (due ${due})` : `${ageLabel} · due ${due}`;
}
