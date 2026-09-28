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
import { ImageUploadField } from "@/components/admin/ImageUploadField";
import {
  GiftAgesEditor,
  GiftBudgetsEditor,
  GiftFeaturesEditor,
  GiftStringListEditor,
  patchGift,
} from "@/components/admin/GiftPageEditors";
import {
  DEFAULT_GIFT_PAGE,
  getGiftPageValidationErrors,
  resolveGiftPage,
  serializeGiftPage,
  type GiftPageContent,
} from "@/lib/gift-page";
import {
  getAdminSiteSettings,
  getSiteSettingsValidationErrors,
  saveSiteSettings,
  type SiteSettingsRow,
} from "@/lib/backend-data";
import { invalidateSiteSettings } from "@/hooks/useSiteSettings";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/gift")({
  component: GiftAdminPage,
});

const DEFAULT_ROW: SiteSettingsRow = {
  brand_name: "Minis n More",
  contact_email: "care@minisnmore.com",
};

function GiftAdminPage() {
  const [row, setRow] = useState<SiteSettingsRow>(DEFAULT_ROW);
  const [content, setContent] = useState<GiftPageContent>(DEFAULT_GIFT_PAGE);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const sections = useAdminSections<"hero" | "features" | "wizard" | "inside">();

  const load = async (opts?: { quiet?: boolean }) => {
    if (!opts?.quiet) setLoading(true);
    try {
      const data = await getAdminSiteSettings();
      const nextRow = { ...DEFAULT_ROW, ...(data ?? {}) };
      setRow(nextRow);
      setContent(resolveGiftPage(nextRow.gift_page_json));
    } catch (error) {
      showApiError(error, "Could not load gift page settings");
    } finally {
      if (!opts?.quiet) setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const save = async () => {
    setSaving(true);
    const payload: SiteSettingsRow = {
      ...row,
      gift_page_json: serializeGiftPage(content),
    };
    const errors = [
      ...getSiteSettingsValidationErrors(payload),
      ...getGiftPageValidationErrors(content),
    ];
    if (errors.length) {
      showValidationErrors(errors);
      sections.expandAll();
      setSaving(false);
      return;
    }
    try {
      await saveSiteSettings(payload);
      invalidateSiteSettings();
      toast.success("Gift page saved");
      await load({ quiet: true });
    } catch (error) {
      showApiError(error, "Could not save gift page");
      sections.expandAll();
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminPage
      eyebrow="Site"
      title="Gift page"
      description="Edit the public /gift page hero, feature cards, wizard options, and what's-inside copy. Leave the hero image blank to keep the storefront default photo."
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
            <Field label="Eyebrow">
              <input
                className={inputCls()}
                value={content.hero_eyebrow}
                onChange={(e) => setContent((c) => patchGift(c, "hero_eyebrow", e.target.value))}
              />
            </Field>
            <Field label="Title">
              <input
                className={inputCls()}
                value={content.hero_title}
                onChange={(e) => setContent((c) => patchGift(c, "hero_title", e.target.value))}
              />
            </Field>
            <Field label="Subtitle">
              <textarea
                rows={2}
                className={inputCls()}
                value={content.hero_subtitle}
                onChange={(e) => setContent((c) => patchGift(c, "hero_subtitle", e.target.value))}
              />
            </Field>
            <Field label="Image alt text">
              <input
                className={inputCls()}
                value={content.hero_image_alt}
                onChange={(e) => setContent((c) => patchGift(c, "hero_image_alt", e.target.value))}
              />
            </Field>
            <ImageUploadField
              label="Hero image"
              folder="home"
              spec="giftHero"
              value={content.hero_image_url}
              onChange={(hero_image_url) => setContent((c) => patchGift(c, "hero_image_url", hero_image_url))}
            />
          </AdminPanelSection>

          <AdminPanelSection title="Feature cards" {...sections.panel("features")}>
            <GiftFeaturesEditor
              features={content.features}
              onChange={(features) => setContent((c) => patchGift(c, "features", features))}
            />
          </AdminPanelSection>

          <AdminPanelSection title="Wizard options" {...sections.panel("wizard")}>
            <GiftStringListEditor
              label="Occasions"
              values={content.occasions}
              onChange={(occasions) => setContent((c) => patchGift(c, "occasions", occasions))}
              placeholder="Naming Ceremony"
            />
            <GiftBudgetsEditor
              budgets={content.budgets}
              onChange={(budgets) => setContent((c) => patchGift(c, "budgets", budgets))}
            />
            <GiftStringListEditor
              label="Palettes"
              values={content.palettes}
              onChange={(palettes) => setContent((c) => patchGift(c, "palettes", palettes))}
              placeholder="Cream & Sage"
            />
            <GiftAgesEditor
              ages={content.ages}
              onChange={(ages) => setContent((c) => patchGift(c, "ages", ages))}
            />
            <div className="space-y-2">
              <div className="text-xs uppercase tracking-wider text-muted-foreground">Step labels</div>
              <div className="text-xs text-muted-foreground">
                Exactly five wizard steps — rename labels only (order is fixed).
              </div>
              {content.steps.map((stepLabel, i) => (
                <Field key={`step-${i}`} label={`Step ${i + 1}`}>
                  <input
                    className={inputCls()}
                    value={stepLabel}
                    onChange={(e) =>
                      setContent((c) =>
                        patchGift(
                          c,
                          "steps",
                          c.steps.map((s, j) => (j === i ? e.target.value : s)),
                        ),
                      )
                    }
                  />
                </Field>
              ))}
            </div>
          </AdminPanelSection>

          <AdminPanelSection title="What’s inside" {...sections.panel("inside")}>
            <Field label="Section title">
              <input
                className={inputCls()}
                value={content.inside_title}
                onChange={(e) => setContent((c) => patchGift(c, "inside_title", e.target.value))}
              />
            </Field>
            <GiftStringListEditor
              label="Bullets"
              values={content.inside_bullets}
              onChange={(inside_bullets) => setContent((c) => patchGift(c, "inside_bullets", inside_bullets))}
              rows={2}
            />
            <Field label="Highlight line">
              <input
                className={inputCls()}
                value={content.highlight_line}
                onChange={(e) => setContent((c) => patchGift(c, "highlight_line", e.target.value))}
              />
            </Field>
            <Field label="Shop link label">
              <input
                className={inputCls()}
                value={content.shop_link_label}
                onChange={(e) => setContent((c) => patchGift(c, "shop_link_label", e.target.value))}
              />
            </Field>
          </AdminPanelSection>
        </div>
      )}
    </AdminPage>
  );
}
