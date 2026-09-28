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
  savedAdminRows,
  useEditableAdminRows,
  type AdminDraftRow,
  type SettledAdminRow,
} from "@/hooks/useEditableAdminRows";
import { ImageUploadField } from "@/components/admin/ImageUploadField";
import { BulkImport } from "@/components/admin/BulkImport";
import { toast } from "sonner";
import { getUserErrorMessage, showApiError, showValidationErrors } from "@/lib/ui-errors";
import { Plus, Trash2, Save } from "lucide-react";
import {
  ADMIN_NAME_MAX_LEN,
  bulkSave,
  deleteBlogPost,
  getBlogPostValidationErrors,
  listBlogPosts,
  saveBlogPost,
  type BlogPostRow,
} from "@/lib/backend-data";
import { invalidateBlogPosts } from "@/lib/storefront";

export const Route = createFileRoute("/admin/blog")({
  head: () => ({ meta: [{ title: "Blogs · Admin · Mini's n More" }] }),
  component: BlogPage,
});

type Post = BlogPostRow & AdminDraftRow;
const empty: Post = {
  slug: "",
  title: "",
  excerpt: "",
  content: "",
  cover_url: "",
  published: false,
  published_at: null,
};

const slugify = (s: string) =>
  s
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");

function BlogPage() {
  const { rows, reload, beginSave, addDraft, updateRow, removeDraft } =
    useEditableAdminRows<Post>();
  const [loading, setLoading] = useState(true);
  const sections = useAdminSections();

  const load = async (settled?: SettledAdminRow[]) => {
    try {
      await reload(() => listBlogPosts(true), settled);
    } catch (error) {
      showApiError(error, "Could not load blog posts");
    }
    setLoading(false);
  };
  useEffect(() => {
    load();
  }, []);

  const save = async (p: Post) => {
    const title = p.title.trim();
    const slug = (p.slug.trim() || slugify(title)).toLowerCase();
    const payload = {
      ...p,
      title,
      slug,
      content: p.content?.trim() ?? "",
      excerpt: p.excerpt?.trim() || null,
      cover_url: p.cover_url?.trim() || null,
      published_at: p.published && !p.published_at ? new Date().toISOString() : p.published_at,
    };
    const errors = getBlogPostValidationErrors(payload);
    if (errors.length) {
      showValidationErrors(errors);
      return;
    }
    try {
      const settle = beginSave(adminRowKey(p));
      const saved = await saveBlogPost(payload);
      invalidateBlogPosts();
      toast.success("Saved");
      if (saved?.id) sections.setOpen(saved.id, true);
      load([settle(saved?.id)]);
    } catch (error) {
      showApiError(error, "Could not save blog post");
    }
  };
  const remove = async (row: Post) => {
    const id = row.id;
    if (!id) return removeDraft(adminRowKey(row));
    if (!confirm("Delete this post?")) return;
    try {
      await deleteBlogPost(id);
      invalidateBlogPosts();
      toast.success("Deleted");
      load([{ key: id }]);
    } catch (error) {
      showApiError(error, "Could not delete blog post");
    }
  };

  return (
    <AdminPage
      eyebrow="Content"
      title="Blogs"
      description="Stories shown on your storefront Blogs page."
      action={
        <div className="flex items-center gap-2 flex-wrap">
          <BulkImport
            entityLabel="blog posts"
            templateName="blogs"
            fields={[
              {
                key: "slug",
                required: true,
                example: "caring-for-organic-cotton",
                note: "Lowercase, hyphens only. Unique.",
              },
              { key: "title", required: true, example: "Caring for organic cotton" },
              { key: "excerpt", example: "A gentle wash guide" },
              {
                key: "content",
                required: true,
                example: "## Heading\n\nBody text…",
                note: "Markdown supported",
              },
              { key: "cover_url", example: "https://…/cover.jpg" },
              { key: "published", example: "TRUE" },
              { key: "published_at", example: "2026-05-18", note: "ISO date or blank" },
            ]}
            transform={(r) => {
              const slug = String(r.slug ?? "")
                .trim()
                .toLowerCase();
              const title = String(r.title ?? "").trim();
              const content = String(r.content ?? "").trim();
              if (!slug) throw new Error("slug is required");
              if (!title) throw new Error("title is required");
              if (title.length > ADMIN_NAME_MAX_LEN) {
                throw new Error(`title must be ${ADMIN_NAME_MAX_LEN} characters or fewer`);
              }
              if (!content) throw new Error("content is required");
              const published = String(r.published ?? "false").toLowerCase() === "true";
              return {
                slug,
                title,
                excerpt: r.excerpt ? String(r.excerpt) : null,
                content,
                cover_url: r.cover_url ? String(r.cover_url) : null,
                published,
                published_at: r.published_at
                  ? new Date(r.published_at).toISOString()
                  : published
                    ? new Date().toISOString()
                    : null,
              };
            }}
            persist={async (payloads) => {
              try {
                const result = await bulkSave(
                  payloads as Post[],
                  savedAdminRows(rows),
                  "slug",
                  saveBlogPost,
                );
                if (result.inserted > 0) invalidateBlogPosts();
                return result;
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
            onDone={load}
          />
          <button
            onClick={() => {
              sections.setOpen(addDraft({ ...empty }), true);
            }}
            className="rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center gap-2"
          >
            <Plus className="h-4 w-4" /> New post
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
              No posts yet.
            </div>
          )}
          {rows.length > 0 && <AdminSectionToggles sections={sections} />}
          {rows.map((p) => {
            const rowKey = adminRowKey(p);
            return (
              <AdminPanelSection
                key={rowKey}
                title={p.title.trim() || "New post"}
                hint={
                  p.id
                    ? `${p.published ? "Published" : "Draft"}${p.slug ? ` · ${p.slug}` : ""}`
                    : undefined
                }
                {...sections.panel(rowKey, !p.id)}
              >
                <div className="grid sm:grid-cols-2 gap-3">
                  <Field label="Title">
                    <input
                      className={inputCls()}
                      maxLength={ADMIN_NAME_MAX_LEN}
                      value={p.title}
                      onChange={(e) => updateRow(rowKey, { title: e.target.value })}
                    />
                  </Field>
                  <Field label="Slug">
                    <input
                      className={inputCls()}
                      value={p.slug}
                      onChange={(e) => updateRow(rowKey, { slug: e.target.value })}
                      placeholder="auto from title"
                    />
                  </Field>
                </div>
                <ImageUploadField
                  label="Cover image"
                  folder="blog"
                  spec="blogCover"
                  value={p.cover_url ?? ""}
                  onChange={(cover_url) => updateRow(rowKey, { cover_url })}
                />
                <Field label="Excerpt">
                  <textarea
                    rows={2}
                    className={inputCls()}
                    value={p.excerpt ?? ""}
                    onChange={(e) => updateRow(rowKey, { excerpt: e.target.value })}
                  />
                </Field>
                <Field label="Content (markdown)">
                  <textarea
                    rows={8}
                    className={inputCls() + " font-mono text-xs"}
                    value={p.content ?? ""}
                    onChange={(e) => updateRow(rowKey, { content: e.target.value })}
                  />
                </Field>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={p.published}
                    onChange={(e) => updateRow(rowKey, { published: e.target.checked })}
                  />{" "}
                  Published
                </label>
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
    </AdminPage>
  );
}
