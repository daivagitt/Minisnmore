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
  AboutFeaturesEditor,
  AboutFoundersEditor,
  AboutParagraphsEditor,
  AboutStatsEditor,
  patchAbout,
} from "@/components/admin/AboutPageEditors";
import {
  DEFAULT_ABOUT_PAGE,
  getAboutPageValidationErrors,
  resolveAboutPage,
  serializeAboutPage,
  type AboutPageContent,
} from "@/lib/about-page";
import {
  getAdminSiteSettings,
  getSiteSettingsValidationErrors,
  saveSiteSettings,
  type SiteSettingsRow,
} from "@/lib/backend-data";
import { invalidateSiteSettings } from "@/hooks/useSiteSettings";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/about")({
  component: AboutAdminPage,
});

const DEFAULT_ROW: SiteSettingsRow = {
  brand_name: "Minis n More",
  contact_email: "care@minisnmore.com",
};

function AboutAdminPage() {
  const [row, setRow] = useState<SiteSettingsRow>(DEFAULT_ROW);
  const [content, setContent] = useState<AboutPageContent>(DEFAULT_ABOUT_PAGE);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const sections = useAdminSections<"hero" | "story" | "why" | "vision" | "founders" | "cta">();

  useEffect(() => {
    (async () => {
      try {
        const data = await getAdminSiteSettings();
        const nextRow = { ...DEFAULT_ROW, ...(data ?? {}) };
        setRow(nextRow);
        setContent(resolveAboutPage(nextRow.about_page_json));
      } catch (error) {
        showApiError(error, "Could not load about page settings");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const save = async () => {
    setSaving(true);
    const payload: SiteSettingsRow = {
      ...row,
      about_page_json: serializeAboutPage(content),
    };
    const errors = [...getSiteSettingsValidationErrors(payload), ...getAboutPageValidationErrors(content)];
    if (errors.length) {
      showValidationErrors(errors);
      sections.expandAll();
      setSaving(false);
      return;
    }
    try {
      await saveSiteSettings(payload);
      invalidateSiteSettings();
      toast.success("About page saved");
    } catch (error) {
      showApiError(error, "Could not save about page");
      sections.expandAll();
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminPage
      eyebrow="Site"
      title="About page"
      description="Edit the public /about page copy, images and founder stories. Leave an image blank to keep the current storefront default."
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
            <div className="grid sm:grid-cols-2 gap-3">
              <Field label="Eyebrow">
                <input
                  className={inputCls()}
                  value={content.hero_eyebrow}
                  onChange={(e) => setContent((c) => patchAbout(c, "hero_eyebrow", e.target.value))}
                />
              </Field>
              <Field label="Word to style in script">
                <input
                  className={inputCls()}
                  value={content.hero_script_word}
                  onChange={(e) => setContent((c) => patchAbout(c, "hero_script_word", e.target.value))}
                />
              </Field>
            </div>
            <Field label="Title">
              <input
                className={inputCls()}
                value={content.hero_title}
                onChange={(e) => setContent((c) => patchAbout(c, "hero_title", e.target.value))}
              />
            </Field>
            <Field label="Intro">
              <textarea
                rows={3}
                className={inputCls()}
                value={content.hero_body}
                onChange={(e) => setContent((c) => patchAbout(c, "hero_body", e.target.value))}
              />
            </Field>
            <ImageUploadField
              label="Hero image"
              folder="about"
              spec="aboutHero"
              value={content.hero_image_url}
              onChange={(hero_image_url) => setContent((c) => patchAbout(c, "hero_image_url", hero_image_url))}
            />
          </AdminPanelSection>

          <AdminPanelSection title="Why we exist" {...sections.panel("story")}>
            <div className="grid sm:grid-cols-2 gap-3">
              <Field label="Eyebrow">
                <input
                  className={inputCls()}
                  value={content.story_eyebrow}
                  onChange={(e) => setContent((c) => patchAbout(c, "story_eyebrow", e.target.value))}
                />
              </Field>
              <Field label="Highlighted number">
                <input
                  className={inputCls()}
                  value={content.story_stat}
                  onChange={(e) => setContent((c) => patchAbout(c, "story_stat", e.target.value))}
                />
              </Field>
            </div>
            <Field label="Headline after the number">
              <input
                className={inputCls()}
                value={content.story_headline}
                onChange={(e) => setContent((c) => patchAbout(c, "story_headline", e.target.value))}
              />
            </Field>
            <Field label="Short supporting line">
              <textarea
                rows={3}
                className={inputCls()}
                value={content.story_sub}
                onChange={(e) => setContent((c) => patchAbout(c, "story_sub", e.target.value))}
              />
            </Field>
            <AboutStatsEditor
              stats={content.story_stats}
              onChange={(story_stats) => setContent((c) => patchAbout(c, "story_stats", story_stats))}
            />
            <Field label="Promise label">
              <input
                className={inputCls()}
                value={content.story_promise}
                onChange={(e) => setContent((c) => patchAbout(c, "story_promise", e.target.value))}
              />
            </Field>
            <AboutParagraphsEditor
              label="Story paragraphs"
              values={content.story_paragraphs}
              onChange={(story_paragraphs) => setContent((c) => patchAbout(c, "story_paragraphs", story_paragraphs))}
            />
            <Field label="Pull quote">
              <input
                className={inputCls()}
                value={content.story_quote}
                onChange={(e) => setContent((c) => patchAbout(c, "story_quote", e.target.value))}
              />
            </Field>
          </AdminPanelSection>

          <AdminPanelSection title="Why choose us" {...sections.panel("why")}>
            <Field label="Eyebrow">
              <input
                className={inputCls()}
                value={content.why_eyebrow}
                onChange={(e) => setContent((c) => patchAbout(c, "why_eyebrow", e.target.value))}
              />
            </Field>
            <Field label="Title">
              <input
                className={inputCls()}
                value={content.why_title}
                onChange={(e) => setContent((c) => patchAbout(c, "why_title", e.target.value))}
              />
            </Field>
            <AboutFeaturesEditor
              features={content.why_features}
              onChange={(why_features) => setContent((c) => patchAbout(c, "why_features", why_features))}
            />
            <Field label="Closing line">
              <textarea
                rows={3}
                className={inputCls()}
                value={content.why_footer}
                onChange={(e) => setContent((c) => patchAbout(c, "why_footer", e.target.value))}
              />
            </Field>
          </AdminPanelSection>

          <AdminPanelSection title="Vision & mission" {...sections.panel("vision")}>
            <Field label="Vision title">
              <input
                className={inputCls()}
                value={content.vision_title}
                onChange={(e) => setContent((c) => patchAbout(c, "vision_title", e.target.value))}
              />
            </Field>
            <Field label="Vision script word">
              <input
                className={inputCls()}
                value={content.vision_script_word}
                onChange={(e) => setContent((c) => patchAbout(c, "vision_script_word", e.target.value))}
              />
            </Field>
            <Field label="Vision body">
              <textarea
                rows={3}
                className={inputCls()}
                value={content.vision_body}
                onChange={(e) => setContent((c) => patchAbout(c, "vision_body", e.target.value))}
              />
            </Field>
            <Field label="Mission title">
              <input
                className={inputCls()}
                value={content.mission_title}
                onChange={(e) => setContent((c) => patchAbout(c, "mission_title", e.target.value))}
              />
            </Field>
            <Field label="Mission script word">
              <input
                className={inputCls()}
                value={content.mission_script_word}
                onChange={(e) => setContent((c) => patchAbout(c, "mission_script_word", e.target.value))}
              />
            </Field>
            <AboutParagraphsEditor
              label="Mission paragraphs"
              values={content.mission_paragraphs}
              onChange={(mission_paragraphs) =>
                setContent((c) => patchAbout(c, "mission_paragraphs", mission_paragraphs))
              }
            />
          </AdminPanelSection>

          <AdminPanelSection title="Founders" {...sections.panel("founders")}>
            <div className="grid sm:grid-cols-2 gap-3">
              <Field label="Eyebrow">
                <input
                  className={inputCls()}
                  value={content.founders_eyebrow}
                  onChange={(e) => setContent((c) => patchAbout(c, "founders_eyebrow", e.target.value))}
                />
              </Field>
              <Field label="Script word in title">
                <input
                  className={inputCls()}
                  value={content.founders_script_word}
                  onChange={(e) => setContent((c) => patchAbout(c, "founders_script_word", e.target.value))}
                />
              </Field>
            </div>
            <Field label="Title">
              <input
                className={inputCls()}
                value={content.founders_title}
                onChange={(e) => setContent((c) => patchAbout(c, "founders_title", e.target.value))}
              />
            </Field>
            <AboutFoundersEditor
              founders={content.founders}
              onChange={(founders) => setContent((c) => patchAbout(c, "founders", founders))}
            />
          </AdminPanelSection>

          <AdminPanelSection title="Bottom call to action" {...sections.panel("cta")}>
            <Field label="Title">
              <input
                className={inputCls()}
                value={content.cta_title}
                onChange={(e) => setContent((c) => patchAbout(c, "cta_title", e.target.value))}
              />
            </Field>
            <Field label="Body">
              <textarea
                rows={2}
                className={inputCls()}
                value={content.cta_body}
                onChange={(e) => setContent((c) => patchAbout(c, "cta_body", e.target.value))}
              />
            </Field>
            <Field label="Button label">
              <input
                className={inputCls()}
                value={content.cta_button}
                onChange={(e) => setContent((c) => patchAbout(c, "cta_button", e.target.value))}
              />
            </Field>
          </AdminPanelSection>
        </div>
      )}
    </AdminPage>
  );
}
