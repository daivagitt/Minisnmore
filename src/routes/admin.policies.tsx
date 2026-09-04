import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { toast } from "sonner";
import { Plus, Trash2, Save } from "lucide-react";
import { ADMIN_NAME_MAX_LEN, deletePolicy, getPolicyValidationErrors, listPolicies, savePolicy, type PolicyRow } from "@/lib/backend-data";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/policies")({ component: PoliciesPage });

type Policy = PolicyRow;
const empty: Policy = { slug: "", title: "", body: "" };

/** Slugs wired on the storefront — must match exactly. */
const STOREFRONT_POLICY_SLUGS = [
  { slug: "privacy", path: "/privacy", label: "Privacy policy" },
  { slug: "terms", path: "/terms", label: "Terms & conditions" },
  { slug: "shipping", path: "/shipping", label: "Shipping" },
] as const;

function PoliciesPage() {
  const [rows, setRows] = useState<Policy[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      setRows(await listPolicies());
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
      await savePolicy(p);
      toast.success("Saved");
      load();
    } catch (error) {
      showApiError(error, "Could not save policy");
    }
  };
  const remove = async (id?: string) => {
    if (!id) return setRows((r) => r.filter((x) => x.id));
    if (!confirm("Delete this policy?")) return;
    try {
      const removed = rows.find((x) => x.id === id);
      await deletePolicy(id, removed?.slug);
      toast.success("Deleted");
      load();
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
          onClick={() => setRows((r) => [{ ...empty }, ...r])}
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
          {rows.map((p, i) => (
            <div key={p.id ?? `new-${i}`} className="glass-strong rounded-3xl p-5 space-y-3">
              <div className="grid sm:grid-cols-2 gap-3">
                <Field label="Slug (lowercase, e.g. privacy, terms, shipping)">
                  <input
                    className={inputCls()}
                    value={p.slug}
                    list="policy-slug-suggestions"
                    onChange={(e) =>
                      setRows((r) =>
                        r.map((x, j) => (j === i ? { ...x, slug: e.target.value } : x)),
                      )
                    }
                  />
                  {STOREFRONT_POLICY_SLUGS.some((s) => s.slug === p.slug.trim().toLowerCase()) && (
                    <p className="mt-1 text-xs text-primary">
                      Shown on{" "}
                      {STOREFRONT_POLICY_SLUGS.find((s) => s.slug === p.slug.trim().toLowerCase())!
                        .path}
                    </p>
                  )}
                </Field>
                <Field label="Title">
                  <input
                    className={inputCls()}
                    maxLength={ADMIN_NAME_MAX_LEN}
                    value={p.title}
                    onChange={(e) =>
                      setRows((r) =>
                        r.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)),
                      )
                    }
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
                  onChange={(e) =>
                    setRows((r) => r.map((x, j) => (j === i ? { ...x, body: e.target.value } : x)))
                  }
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
                  onClick={() => remove(p.id)}
                  className="rounded-full glass px-4 py-2 text-sm flex items-center gap-2 text-destructive"
                >
                  <Trash2 className="h-4 w-4" /> Delete
                </button>
              </div>
            </div>
          ))}
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
