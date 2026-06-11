import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { NumberField } from "@/components/admin/NumberField";
import { ImageUploadField } from "@/components/admin/ImageUploadField";
import { BulkImport } from "@/components/admin/BulkImport";
import { toast } from "sonner";
import { coerceNonNegativeIntFromUnknown } from "@/lib/admin-number-field";
import { getUserErrorMessage, showApiError, showValidationErrors } from "@/lib/ui-errors";
import { Plus, Trash2, Save } from "lucide-react";
import {
  ADMIN_NAME_MAX_LEN,
  bulkSave,
  deleteCategory,
  getCategoryValidationErrors,
  listCategories,
  saveCategory,
  type CategoryRow,
} from "@/lib/backend-data";
import { invalidateCategories } from "@/hooks/useCategories";
import { invalidateProducts } from "@/lib/storefront";

const SLUG_RE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export const Route = createFileRoute("/admin/categories")({ component: CategoriesPage });

type Cat = CategoryRow;
const empty: Cat = {
  slug: "",
  name: "",
  description: "",
  image_url: "",
  sort_order: null,
  active: true,
};
const slugify = (s: string) =>
  s
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");

function CategoriesPage() {
  const [rows, setRows] = useState<Cat[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      setRows(await listCategories(true));
    } catch (error) {
      showApiError(error, "Could not load categories");
    }
    setLoading(false);
  };
  useEffect(() => {
    load();
  }, []);

  const save = async (c: Cat) => {
    const name = c.name.trim();
    const slug = (c.slug.trim() || slugify(name)).toLowerCase();
    const payload = { ...c, name, slug };
    const errors = getCategoryValidationErrors(payload);
    if (errors.length) {
      showValidationErrors(errors);
      return;
    }
    try {
      await saveCategory(payload);
      invalidateCategories();
      invalidateProducts();
      toast.success("Saved");
      load();
    } catch (error) {
      showApiError(error, "Could not save category");
    }
  };
  const remove = async (id?: string) => {
    if (!id) return setRows((r) => r.filter((x) => x.id));
    if (!confirm("Delete this category?")) return;
    try {
      await deleteCategory(id);
      invalidateCategories();
      invalidateProducts();
      toast.success("Deleted");
      load();
    } catch (error) {
      showApiError(error, "Could not delete category");
    }
  };

  return (
    <AdminPage
      eyebrow="Catalog"
      title="Categories"
      description="Group your products. Customers browse the storefront by these categories."
      action={
        <div className="flex items-center gap-2 flex-wrap">
          <BulkImport
            entityLabel="categories"
            templateName="categories"
            fields={[
              {
                key: "slug",
                required: true,
                example: "newborn-essentials",
                note: "Lowercase, hyphens only. Unique.",
              },
              { key: "name", required: true, example: "Newborn Essentials" },
              { key: "description", example: "Soft first layers for 0–3m" },
              { key: "image_url", example: "https://…/cover.jpg", note: "Optional" },
              { key: "sort_order", example: 10 },
              { key: "active", example: "TRUE", note: "TRUE or FALSE" },
            ]}
            transform={(r) => {
              const slug = String(r.slug ?? "")
                .trim()
                .toLowerCase();
              const name = String(r.name ?? "").trim();
              if (!slug) throw new Error("slug is required");
              if (!name) throw new Error("name is required");
              if (!SLUG_RE.test(slug)) throw new Error("slug must be lowercase with hyphens only");
              if (!/[a-z]/.test(slug)) throw new Error("slug must include at least one letter");
              if (name.length < 2) throw new Error("name must be at least 2 characters");
              if (name.length > ADMIN_NAME_MAX_LEN) {
                throw new Error(`name must be ${ADMIN_NAME_MAX_LEN} characters or fewer`);
              }
              if (!/\p{L}/u.test(name)) throw new Error("name must contain at least one letter");
              return {
                slug,
                name,
                description: r.description ? String(r.description) : null,
                image_url: r.image_url ? String(r.image_url) : null,
                sort_order: coerceNonNegativeIntFromUnknown(r.sort_order),
                active: String(r.active ?? "true").toLowerCase() !== "false",
              };
            }}
            persist={async (payloads) => {
              try {
                return await bulkSave(payloads as Cat[], rows, "slug", saveCategory);
              } catch (error) {
                return {
                  inserted: 0,
                  errors: [
                    getUserErrorMessage(
                      error,
                      "Import failed. Please verify your sheet data and try again.",
                    ),
                  ],
                };
              }
            }}
            onDone={() => {
              invalidateCategories();
              invalidateProducts();
              load();
            }}
          />
          <button
            onClick={() => setRows((r) => [{ ...empty }, ...r])}
            className="rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center gap-2"
          >
            <Plus className="h-4 w-4" /> New category
          </button>
        </div>
      }
    >
      {loading ? (
        <div className="text-sm text-muted-foreground">Loading…</div>
      ) : (
        <div className="space-y-4">
          {rows.length === 0 && (
            <div className="glass rounded-2xl p-6 text-sm text-muted-foreground text-center">
              No categories yet.
            </div>
          )}
          {rows.map((c, i) => (
            <div key={c.id ?? `new-${i}`} className="glass-strong rounded-3xl p-5 space-y-3">
              <div className="grid sm:grid-cols-3 gap-3">
                <Field label="Name">
                  <input
                    className={inputCls()}
                    maxLength={ADMIN_NAME_MAX_LEN}
                    value={c.name}
                    onChange={(e) =>
                      setRows((r) =>
                        r.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)),
                      )
                    }
                  />
                </Field>
                <Field label="Slug">
                  <input
                    className={inputCls()}
                    value={c.slug}
                    placeholder="auto from name"
                    onChange={(e) =>
                      setRows((r) =>
                        r.map((x, j) => (j === i ? { ...x, slug: e.target.value } : x)),
                      )
                    }
                  />
                </Field>
                <NumberField
                  label="Sort order"
                  value={c.sort_order}
                  onChange={(sort_order) =>
                    setRows((r) => r.map((x, j) => (j === i ? { ...x, sort_order } : x)))
                  }
                  min={0}
                  required
                />
              </div>
              <Field label="Description">
                <textarea
                  rows={2}
                  className={inputCls()}
                  value={c.description ?? ""}
                  onChange={(e) =>
                    setRows((r) =>
                      r.map((x, j) => (j === i ? { ...x, description: e.target.value } : x)),
                    )
                  }
                />
              </Field>
              <ImageUploadField
                label="Category image (optional)"
                folder="categories"
                spec="categoryMenu"
                value={c.image_url ?? ""}
                onChange={(image_url) =>
                  setRows((r) => r.map((x, j) => (j === i ? { ...x, image_url } : x)))
                }
              />
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={c.active}
                  onChange={(e) =>
                    setRows((r) =>
                      r.map((x, j) => (j === i ? { ...x, active: e.target.checked } : x)),
                    )
                  }
                />{" "}
                Active
              </label>
              <div className="flex gap-2">
                <button
                  onClick={() => save(c)}
                  className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm flex items-center gap-2"
                >
                  <Save className="h-4 w-4" /> Save
                </button>
                <button
                  onClick={() => remove(c.id)}
                  className="rounded-full glass px-4 py-2 text-sm flex items-center gap-2 text-destructive"
                >
                  <Trash2 className="h-4 w-4" /> Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </AdminPage>
  );
}
