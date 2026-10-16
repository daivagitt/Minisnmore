import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { Save } from "lucide-react";
import { toast } from "sonner";
import {
  AdminPage,
  AdminPanelSection,
  AdminSectionToggles,
  Field,
  inputCls,
} from "@/components/admin/AdminPage";
import { useAdminSections } from "@/hooks/useAdminSections";
import {
  getAdminSiteSettings,
  getSiteSettingsValidationErrors,
  saveSiteSettings,
  type SiteSettingsRow,
} from "@/lib/backend-data";
import { getSubscribePageValidationErrors } from "@/lib/admin-subscribe-validation";
import { invalidateSiteSettings } from "@/hooks/useSiteSettings";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";
import {
  SubscribeBadgesEditor,
  SubscribeFeaturesEditor,
  SubscribePlansEditor,
} from "@/components/admin/SubscribePayloadEditors";

export const Route = createFileRoute("/admin/subscribe")({
  component: SubscribeAdminPage,
});

const DEFAULT_ROW: SiteSettingsRow = {
  brand_name: "Minis n More",
  contact_email: "care@minisnmore.com",
};

function parseJsonArray(raw: string): unknown[] {
  if (!raw.trim()) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function toJson(value: unknown): string {
  return JSON.stringify(value ?? [], null, 2);
}

function SubscribeAdminPage() {
  const [row, setRow] = useState<SiteSettingsRow>(DEFAULT_ROW);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const sections = useAdminSections<"hero" | "badges" | "plans" | "features">();

  useEffect(() => {
    (async () => {
      try {
        const data = await getAdminSiteSettings();
        setRow({ ...DEFAULT_ROW, ...(data ?? {}) });
      } catch (error) {
        showApiError(error, "Could not load subscription settings");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const up = (key: string, value: string | null | undefined) =>
    setRow((prev) => ({ ...prev, [key]: value }));

  const save = async () => {
    setSaving(true);
    const errors = [
      ...getSiteSettingsValidationErrors(row),
      ...getSubscribePageValidationErrors(row),
    ];
    if (errors.length) {
      showValidationErrors(errors);
      sections.expandAll();
      setSaving(false);
      return;
    }
    try {
      await saveSiteSettings(row);
      invalidateSiteSettings();
      toast.success("Subscription page settings saved");
    } catch (error) {
      showApiError(error, "Could not save subscription settings");
      sections.expandAll();
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminPage
      eyebrow="Subscriptions"
      title="Subscription page"
      description="Control copy and plan content for the storefront subscription page."
      action={
        <button
          onClick={save}
          disabled={loading || saving}
          className="rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center gap-2 disabled:opacity-60"
        >
          <Save className="h-4 w-4" /> {saving ? "Saving…" : "Save changes"}
        </button>
      }
    >
      {loading ? (
        <div className="text-sm text-muted-foreground">Loading…</div>
      ) : (
        <div className="space-y-4">
          <AdminSectionToggles sections={sections} />
          <AdminPanelSection title="Hero" {...sections.panel("hero", true)}>
            <Field label="Hero title">
              <input
                className={inputCls()}
                value={String(row.subscribe_hero_title ?? "")}
                onChange={(e) => up("subscribe_hero_title", e.target.value)}
              />
            </Field>
            <Field label="Hero subtitle">
              <textarea
                rows={3}
                className={inputCls()}
                value={String(row.subscribe_hero_subtitle ?? "")}
                onChange={(e) => up("subscribe_hero_subtitle", e.target.value)}
              />
            </Field>
            <Field label="Testimonial line">
              <input
                className={inputCls()}
                value={String(row.subscribe_testimonial ?? "")}
                onChange={(e) => up("subscribe_testimonial", e.target.value)}
              />
            </Field>
          </AdminPanelSection>

          <AdminPanelSection title="Badges" {...sections.panel("badges")}>
            <SubscribeBadgesEditor
              badges={parseJsonArray(String(row.subscribe_badges_json ?? "[]"))}
              onChange={(badges) => up("subscribe_badges_json", toJson(badges))}
            />
          </AdminPanelSection>

          <AdminPanelSection title="Plans" {...sections.panel("plans")}>
            <SubscribePlansEditor
              plans={parseJsonArray(String(row.subscribe_plans_json ?? "[]"))}
              onChange={(plans) => up("subscribe_plans_json", toJson(plans))}
            />
          </AdminPanelSection>

          <AdminPanelSection title="Bottom features" {...sections.panel("features")}>
            <SubscribeFeaturesEditor
              features={parseJsonArray(String(row.subscribe_features_json ?? "[]"))}
              onChange={(features) => up("subscribe_features_json", toJson(features))}
            />
          </AdminPanelSection>
        </div>
      )}
    </AdminPage>
  );
}
