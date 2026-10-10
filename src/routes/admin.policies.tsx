import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
  AdminPage,
  AdminPanelSection,
  AdminSectionToggles,
  Field,
  inputCls,
} from "@/components/admin/AdminPage";
import { useAdminSections } from "@/hooks/useAdminSections";
import {
  adminRowKey,
  useEditableAdminRows,
  type AdminDraftRow,
  type SettledAdminRow,
} from "@/hooks/useEditableAdminRows";
import { toast } from "sonner";
import { Plus, Trash2, Save } from "lucide-react";
import {
  ADMIN_NAME_MAX_LEN,
  deletePolicy,
  getPolicyValidationErrors,
  listPolicies,
  savePolicy,
  type PolicyRow,
} from "@/lib/backend-data";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/policies")({ component: PoliciesPage });

type Policy = PolicyRow & AdminDraftRow;
const empty: Policy = { slug: "", title: "", body: "" };

/** Slugs wired on the storefront — must match exactly. */
const STOREFRONT_POLICY_SLUGS = [
  { slug: "privacy", path: "/privacy", label: "Privacy policy" },
  { slug: "terms", path: "/terms", label: "Terms & conditions" },
  { slug: "shipping", path: "/shipping", label: "Shipping" },
] as const;

function PoliciesPage() {
  const { rows, reload, beginSave, addDraft, updateRow, removeDraft } =
    useEditableAdminRows<Policy>();
  const [loading, setLoading] = useState(true);
  const sections = useAdminSections();

  const load = async (settled?: SettledAdminRow[]) => {
    try {
      await reload(() => listPolicies(), settled);
    } catch (error) {
      showApiError(error, "Could not load policies");
    }
    setLoading(false);
  };
  useEffect(() => {
    load();
  }, []);

  const save = async (p: Policy) => {
    const errors = getPolicyValidationErrors(p);
    if (errors.length) {
      showValidationErrors(errors);
      return;
    }
    try {
      const settle = beginSave(adminRowKey(p));
      const saved = await savePolicy(p);
      toast.success("Saved");
      if (saved?.id) sections.setOpen(saved.id, true);
      load([settle(saved?.id)]);
    } catch (error) {
      showApiError(error, "Could not save policy");
    }
  };
  const remove = async (row: Policy) => {
    const id = row.id;
    if (!id) return removeDraft(adminRowKey(row));
    if (!confirm("Delete this policy?")) return;
    try {
      await deletePolicy(id, row.slug);
      toast.success("Deleted");
      load([{ key: id }]);
    } catch (error) {
      showApiError(error, "Could not delete policy");
    }
  };

  return (
    <AdminPage
      eyebrow="Legal"
      title="Policies"
      description="Use slugs privacy, terms, or shipping to replace the matching public pages (/privacy, /terms, /shipping)."
      action={
        <button
          onClick={() => {
            sections.setOpen(addDraft({ ...empty }), true);
          }}
          className="rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center gap-2"
        >
          <Plus className="h-4 w-4" /> New policy
        </button>
      }
    >
      {loading ? (
        <div className="text-sm text-muted-foreground">Loading…</div>
      ) : (
        <div className="space-y-4">
          {rows.length === 0 && (
            <div className="glass rounded-2xl p-6 text-sm text-muted-foreground text-center">
              No policies yet.
            </div>
          )}
          {rows.length > 0 && <AdminSectionToggles sections={sections} />}
          {rows.map((p) => {
            const rowKey = adminRowKey(p);
            return (
              <AdminPanelSection
                key={rowKey}
                title={p.title.trim() || p.slug.trim() || "New policy"}
                hint={p.slug.trim() ? `Slug: ${p.slug.trim()}` : undefined}
                {...sections.panel(rowKey, !p.id)}
              >
                <div className="grid sm:grid-cols-2 gap-3">
                  <Field label="Slug (lowercase, e.g. privacy, terms, shipping)">
                    <input
                      className={inputCls()}
                      value={p.slug}
                      list="policy-slug-suggestions"
                      onChange={(e) => updateRow(rowKey, { slug: e.target.value })}
                    />
                    {STOREFRONT_POLICY_SLUGS.some(
                      (s) => s.slug === p.slug.trim().toLowerCase(),
                    ) && (
                      <p className="mt-1 text-xs text-primary">
                        Shown on{" "}
                        {
                          STOREFRONT_POLICY_SLUGS.find(
                            (s) => s.slug === p.slug.trim().toLowerCase(),
                          )!.path
                        }
                      </p>
                    )}
                  </Field>
                  <Field label="Title">
                    <input
                      className={inputCls()}
                      maxLength={ADMIN_NAME_MAX_LEN}
                      value={p.title}
                      onChange={(e) => updateRow(rowKey, { title: e.target.value })}
                    />
                  </Field>
                </div>
                <Field label="Body">
                  <textarea
                    rows={12}
                    placeholder={
                      "Write naturally — we auto-format on publish.\n\n1. Eligibility\nYou must be 18+ to use the service.\n\nKey terms:\n- Free shipping above ₹999\n- Dispatch in 1–3 business days\n\nContact: hello@brand.com"
                    }
                    className={inputCls() + " font-mono text-[13px] leading-relaxed"}
                    value={p.body ?? ""}
                    onChange={(e) => updateRow(rowKey, { body: e.target.value })}
                  />
                  <p className="mt-2 text-xs text-muted-foreground">
                    Plain text, Markdown (<code>## Heading</code>, <code>**bold**</code>,{" "}
                    <code>- bullet</code>), or HTML — all auto-formatted into a clean, responsive
                    layout when published.
                  </p>
                </Field>
                <div className="flex gap-2">
                  <button
                    onClick={() => save(p)}
                    className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm flex items-center gap-2"
                  >
                    <Save className="h-4 w-4" /> Save
                  </button>
                  <button
                    onClick={() => remove(p)}
                    className="rounded-full glass px-4 py-2 text-sm flex items-center gap-2 text-destructive"
                  >
                    <Trash2 className="h-4 w-4" /> Delete
                  </button>
                </div>
              </AdminPanelSection>
            );
          })}
        </div>
      )}
      <datalist id="policy-slug-suggestions">
        {STOREFRONT_POLICY_SLUGS.map((s) => (
          <option key={s.slug} value={s.slug}>
            {s.label}
          </option>
        ))}
      </datalist>
    </AdminPage>
  );
}
