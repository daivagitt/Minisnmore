import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { NumberField } from "@/components/admin/NumberField";
import { ImageUploadField } from "@/components/admin/ImageUploadField";
import { toast } from "sonner";
import { Plus, Trash2, Save } from "lucide-react";
import { ADMIN_NAME_MAX_LEN, deleteBanner, getBannerValidationErrors, listBanners, saveBanner, type BannerRow } from "@/lib/backend-data";
import { invalidateBanners } from "@/hooks/useBanners";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { BANNER_LABEL_MAX, BANNER_TRUST_LABEL_MAX, DEFAULT_HERO_DYES_LABEL } from "@/lib/banner-trust";
import { DEFAULT_BANNER_EYEBROW_FIRST } from "@/lib/banner-eyebrow";
import { homeStorefrontTrustCopy } from "@/lib/home-storefront-copy";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/banners")({ component: BannersPage });

type Banner = BannerRow;

const empty: Banner = {
  title: "",
  subtitle: "",
  image_url: "",
  link_url: "",
  cta_label: "Shop",
  eyebrow: null,
  trust_fabric_label: null,
  trust_shipping_label: null,
  trust_dyes_label: null,
  sort_order: null,
  active: true,
};

function BannersPage() {
  const [rows, setRows] = useState<Banner[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      setRows(await listBanners(true));
    } catch (error) {
      showApiError(error, "Could not load banners");
    }
    setLoading(false);
  };
  useEffect(() => {
    load();
  }, []);

  const save = async (b: Banner) => {
    const payload = { ...b };
    const errors = getBannerValidationErrors(payload);
    if (errors.length) {
      showValidationErrors(errors);
      return;
    }
    try {
      await saveBanner(payload);
      invalidateBanners();
      toast.success("Saved");
      load();
    } catch (error) {
      showApiError(error, "Could not save banner");
    }
  };
  const remove = async (id?: string) => {
    if (!id) return setRows((r) => r.filter((x) => x.id));
    if (!confirm("Delete this banner?")) return;
    try {
      await deleteBanner(id);
      invalidateBanners();
      toast.success("Deleted");
      load();
    } catch (error) {
      showApiError(error, "Could not delete banner");
    }
  };

  return (
    <AdminPage
      eyebrow="Content"
      title="Homepage banners"
      description="Homepage hero carousel. Use wide images (about 1920 × 900 px) — see the size guide on each banner. Each slide has its own title, button, and three trust lines under the card. Active banners appear in sort order."
      action={
        <button
          onClick={() => setRows((r) => [{ ...empty }, ...r])}
          className="rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center gap-2"
        >
          <Plus className="h-4 w-4" /> Add banner
        </button>
      }
    >
      {loading ? (
        <div className="text-sm text-muted-foreground">Loading…</div>
      ) : (
        <div className="space-y-4">
          {rows.length === 0 && (
            <div className="glass rounded-2xl p-6 text-sm text-muted-foreground text-center">
              No banners yet.
            </div>
          )}
          {rows.map((b, i) => (
            <BannerCard
              key={b.id ?? `new-${i}`}
              value={b}
              onChange={(v) => setRows((r) => r.map((x, j) => (j === i ? v : x)))}
              onSave={save}
              onDelete={() => remove(b.id)}
            />
          ))}
        </div>
      )}
    </AdminPage>
  );
}

function BannerCard({
  value,
  onChange,
  onSave,
  onDelete,
}: {
  value: Banner;
  onChange: (v: Banner) => void;
  onSave: (v: Banner) => void;
  onDelete: () => void;
}) {
  const settings = useSiteSettings();
  const storefrontTrust = homeStorefrontTrustCopy(settings.free_shipping_min_inr);
  const fabricPlaceholder = storefrontTrust.fabricLabel;
  const shippingPlaceholder = storefrontTrust.heroShippingLabel;
  return (
    <div className="glass-strong rounded-3xl p-5 grid md:grid-cols-[160px_1fr] gap-4">
      <div className="space-y-1.5">
        <div className="aspect-[2/1] rounded-2xl overflow-hidden bg-secondary/40 grid place-items-center">
          {value.image_url ? (
            <img src={value.image_url} alt="" className="h-full w-full object-cover" />
          ) : (
            <span className="text-xs text-muted-foreground">No image</span>
          )}
        </div>
        <p className="text-[10px] text-muted-foreground text-center">Preview (~2 : 1)</p>
      </div>
      <div className="grid sm:grid-cols-2 gap-3">
        <Field
          label="Eyebrow label"
          hint='Small uppercase line above the title — e.g. "Featured", "Just in". Leave blank to use Featured on the first slide and Just in on the rest.'
        >
          <input
            className={inputCls()}
            maxLength={BANNER_LABEL_MAX}
            value={value.eyebrow ?? ""}
            onChange={(e) => onChange({ ...value, eyebrow: e.target.value })}
            placeholder={DEFAULT_BANNER_EYEBROW_FIRST}
          />
        </Field>
        <Field label="Title">
          <input
            className={inputCls()}
            maxLength={ADMIN_NAME_MAX_LEN}
            value={value.title}
            onChange={(e) => onChange({ ...value, title: e.target.value })}
            placeholder="Main heading shown on carousel slide"
          />
        </Field>
        <div className="sm:col-span-2">
          <Field label="Subtitle">
            <input
              className={inputCls()}
              value={value.subtitle ?? ""}
              onChange={(e) => onChange({ ...value, subtitle: e.target.value })}
              placeholder="Small supporting text shown under title"
            />
          </Field>
        </div>
        <div className="sm:col-span-2">
          <ImageUploadField
            label="Image for carousel"
            folder="banners"
            spec="homepageBanner"
            value={value.image_url ?? ""}
            onChange={(image_url) => onChange({ ...value, image_url })}
            hint="This image appears in the homepage hero carousel."
          />
        </div>
        <Field label="On-site link (path only)">
          <input
            className={inputCls()}
            value={value.link_url ?? ""}
            onChange={(e) => onChange({ ...value, link_url: e.target.value })}
            placeholder="/shop or /gift — no http:// or external sites"
          />
        </Field>
        <Field label="Button text on slide">
          <input
            className={inputCls()}
            value={value.cta_label ?? ""}
            onChange={(e) => onChange({ ...value, cta_label: e.target.value })}
            placeholder='Example: "Shop now"'
          />
        </Field>
        <div className="sm:col-span-2 grid sm:grid-cols-1 gap-3 rounded-2xl ring-1 ring-border/60 bg-white/30 p-3">
          <div className="text-xs uppercase tracking-wider text-muted-foreground">
            Trust lines under this slide
          </div>
          <p className="text-[11px] text-muted-foreground leading-snug -mt-1">
            Shown at the bottom of this carousel card. Leave blank to keep the storefront default
            for this slide only.
          </p>
          <Field label="Organic / fabric line" hint={`Default is “${fabricPlaceholder}”.`}>
            <input
              className={inputCls()}
              maxLength={BANNER_TRUST_LABEL_MAX}
              value={value.trust_fabric_label ?? ""}
              onChange={(e) => onChange({ ...value, trust_fabric_label: e.target.value })}
              placeholder={fabricPlaceholder}
            />
          </Field>
          <Field
            label="Shipping line"
            hint={`Leave blank to follow Site settings (currently “${shippingPlaceholder}”).`}
          >
            <input
              className={inputCls()}
              maxLength={BANNER_TRUST_LABEL_MAX}
              value={value.trust_shipping_label ?? ""}
              onChange={(e) => onChange({ ...value, trust_shipping_label: e.target.value })}
              placeholder={shippingPlaceholder}
            />
          </Field>
          <Field
            label="Dyes line"
            hint={`Default is “${DEFAULT_HERO_DYES_LABEL}”. Hidden on very small screens.`}
          >
            <input
              className={inputCls()}
              maxLength={BANNER_TRUST_LABEL_MAX}
              value={value.trust_dyes_label ?? ""}
              onChange={(e) => onChange({ ...value, trust_dyes_label: e.target.value })}
              placeholder={DEFAULT_HERO_DYES_LABEL}
            />
          </Field>
        </div>
        <NumberField
          label="Carousel order"
          value={value.sort_order}
          onChange={(sort_order) => onChange({ ...value, sort_order })}
          min={0}
          required
        />
        <label className="flex items-center gap-2 text-sm sm:col-span-2">
          <input
            type="checkbox"
            checked={value.active}
            onChange={(e) => onChange({ ...value, active: e.target.checked })}
          />{" "}
          Show this slide on homepage carousel
        </label>
        <div className="sm:col-span-2 flex gap-2">
          <button
            onClick={() => onSave(value)}
            className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm flex items-center gap-2"
          >
            <Save className="h-4 w-4" /> Save
          </button>
          <button
            onClick={onDelete}
            className="rounded-full glass px-4 py-2 text-sm flex items-center gap-2 text-destructive"
          >
            <Trash2 className="h-4 w-4" /> Delete
          </button>
        </div>
      </div>
    </div>
  );
}
