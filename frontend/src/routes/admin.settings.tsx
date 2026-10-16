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
import { NumberField } from "@/components/admin/NumberField";
import { toast } from "sonner";
import { Save } from "lucide-react";
import {
  commerceFromRow,
  DEFAULT_DELIVERY_ETA_BUFFER_DAYS,
  MAX_DELIVERY_ETA_BUFFER_DAYS,
  parseDeliveryEtaBufferDays,
} from "@/lib/commerce";
import {
  DEFAULT_REFUND_MAX_BUSINESS_DAYS,
  DEFAULT_REFUND_MIN_BUSINESS_DAYS,
  DEFAULT_RETURN_WINDOW_DAYS,
  MAX_REFUND_BUSINESS_DAYS,
  MAX_RETURN_WINDOW_DAYS,
  parseRefundMaxBusinessDays,
  parseRefundMinBusinessDays,
  parseReturnWindowDays,
} from "@/lib/refund-policy";
import { invalidateSiteSettings, type SiteSettings } from "@/hooks/useSiteSettings";
import { ImageUploadField } from "@/components/admin/ImageUploadField";
import { PdfUploadField } from "@/components/admin/PdfUploadField";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";
import {
  getAdminSiteSettings,
  getSiteSettingsValidationErrors,
  saveSiteSettings,
  type SiteSettingsRow,
} from "@/lib/backend-data";

export const Route = createFileRoute("/admin/settings")({ component: SettingsPage });

const GST_RATE_MAX = 100;
const SHIPPING_INR_MAX = 10_000;
const FREE_SHIPPING_MIN_INR_MAX = 1_000_000;

type SettingsForm = Omit<
  SiteSettings,
  | "gst_rate"
  | "shipping_standard_inr"
  | "shipping_express_inr"
  | "free_shipping_min_inr"
  | "cod_fee_inr"
  | "delivery_eta_buffer_days"
  | "return_window_days"
  | "refund_min_business_days"
  | "refund_max_business_days"
> & {
  gst_rate: number | null;
  shipping_standard_inr: number | null;
  shipping_express_inr: number | null;
  free_shipping_min_inr: number | null;
  cod_fee_inr: number | null;
  delivery_eta_buffer_days: number | null;
  return_window_days: number | null;
  refund_min_business_days: number | null;
  refund_max_business_days: number | null;
};

const empty: SettingsForm = {
  brand_name: "Minis n More",
  legal_entity_name: "Stolt Global Private Limited",
  logo_url: "",
  logo_horizontal_url: "",
  invoice_website: "",
  invoice_gstin: "",
  invoice_sold_by_address: "",
  invoice_dispatch_from_address: "",
  invoice_cin: "",
  invoice_footer_note: "",
  invoice_seller_state: "",
  linkedin_url: "",
  invoice_logo_url: "",
  contact_email: "care@minisnmore.com",
  contact_phone: "+91 76765 25139",
  care_label: "Care",
  whatsapp_number: "",
  instagram_url: "",
  facebook_url: "",
  youtube_url: "",
  twitter_url: "",
  footer_tagline: "",
  footer_copyright: "",
  invoice_registered_office_address: "",
  ecatalogue_url: "",
  ecatalogue_label: "New Catalogue – Soft Bloom",
  gst_rate: 5,
  prices_include_gst: true,
  shipping_standard_inr: 99,
  shipping_express_inr: 150,
  free_shipping_min_inr: 2500,
  cod_fee_inr: 99,
  delivery_eta_buffer_days: DEFAULT_DELIVERY_ETA_BUFFER_DAYS,
  return_window_days: DEFAULT_RETURN_WINDOW_DAYS,
  refund_min_business_days: DEFAULT_REFUND_MIN_BUSINESS_DAYS,
  refund_max_business_days: DEFAULT_REFUND_MAX_BUSINESS_DAYS,
  store_discount_percent: 20,
};

function SettingsPage() {
  const [s, setS] = useState<SettingsForm>(empty);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const sections = useAdminSections<"brand" | "contact" | "footer" | "pricing" | "returns" | "ecatalogue">();

  useEffect(() => {
    (async () => {
      try {
        const data = await getAdminSiteSettings();
        if (data) {
          const c = commerceFromRow(data as Record<string, unknown>);
          setS({
            ...empty,
            ...data,
            gst_rate: c.gstRate,
            prices_include_gst: c.pricesIncludeGst,
            shipping_standard_inr: c.shippingStandardInr,
            shipping_express_inr: c.shippingExpressInr,
            free_shipping_min_inr: c.freeShippingMinInr,
            cod_fee_inr: c.codFeeInr,
            delivery_eta_buffer_days: parseDeliveryEtaBufferDays(
              data.delivery_eta_buffer_days,
              DEFAULT_DELIVERY_ETA_BUFFER_DAYS
            ),
            return_window_days: parseReturnWindowDays(
              data.return_window_days,
              DEFAULT_RETURN_WINDOW_DAYS
            ),
            refund_min_business_days: parseRefundMinBusinessDays(
              data.refund_min_business_days,
              DEFAULT_REFUND_MIN_BUSINESS_DAYS
            ),
            refund_max_business_days: parseRefundMaxBusinessDays(
              data.refund_max_business_days,
              parseRefundMinBusinessDays(data.refund_min_business_days, DEFAULT_REFUND_MIN_BUSINESS_DAYS),
              DEFAULT_REFUND_MAX_BUSINESS_DAYS
            ),
          } as SettingsForm);
        }
      } catch (error) {
        showApiError(error, "Could not load site settings");
      }
      setLoading(false);
    })();
  }, []);

  const up = <K extends keyof SettingsForm>(k: K, v: SettingsForm[K]) =>
    setS((p) => ({ ...p, [k]: v }));

  const save = async () => {
    setSaving(true);
    const payload = s as unknown as SiteSettingsRow;
    const errors = getSiteSettingsValidationErrors(payload);
    if (errors.length) {
      showValidationErrors(errors);
      sections.expandAll();
      setSaving(false);
      return;
    }
    try {
      await saveSiteSettings(payload);
      toast.success("Settings saved");
      invalidateSiteSettings();
    } catch (error) {
      showApiError(error, "Could not save settings");
      sections.expandAll();
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminPage
      eyebrow="Site"
      title="Site settings"
      description="Logo, contact details, office address, social links, footer text and e-catalogue. Changes apply to the storefront immediately."
      action={
        <button
          onClick={save}
          disabled={saving || loading}
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
          <AdminPanelSection title="Brand" {...sections.panel("brand", true)}>
            <div className="grid sm:grid-cols-2 gap-3">
              <Field label="Brand name">
                <input
                  className={inputCls()}
                  value={s.brand_name}
                  onChange={(e) => up("brand_name", e.target.value)}
                />
              </Field>
              <Field label="Registered legal entity">
                <input
                  className={inputCls()}
                  value={s.legal_entity_name ?? ""}
                  onChange={(e) => up("legal_entity_name", e.target.value)}
                  placeholder="Stolt Global Private Limited"
                />
              </Field>
              <ImageUploadField
                label="Logo (square / stacked — footer)"
                folder="branding"
                spec="logoSquare"
                value={s.logo_url ?? ""}
                onChange={(logo_url) => up("logo_url", logo_url)}
              />
              <ImageUploadField
                label="Horizontal logo (navbar)"
                folder="branding"
                spec="logoHorizontal"
                value={s.logo_horizontal_url ?? ""}
                onChange={(logo_horizontal_url) => up("logo_horizontal_url", logo_horizontal_url)}
              />
            </div>
            <div className="flex gap-4 items-end">
              {s.logo_horizontal_url && (
                <div>
                  <div className="text-[11px] uppercase tracking-wider text-muted-foreground mb-1">
                    Navbar preview
                  </div>
                  <img
                    src={s.logo_horizontal_url}
                    alt=""
                    className="h-12 object-contain glass rounded-xl p-2"
                  />
                </div>
              )}
              {s.logo_url && (
                <div>
                  <div className="text-[11px] uppercase tracking-wider text-muted-foreground mb-1">
                    Footer preview
                  </div>
                  <img
                    src={s.logo_url}
                    alt=""
                    className="h-16 object-contain glass rounded-xl p-2"
                  />
                </div>
              )}
            </div>
          </AdminPanelSection>

          <AdminPanelSection title="Contact & social" {...sections.panel("contact")}>
            <div className="grid sm:grid-cols-2 gap-3">
              <Field label="Contact email">
                <input
                  className={inputCls()}
                  value={s.contact_email ?? ""}
                  onChange={(e) => up("contact_email", e.target.value)}
                />
              </Field>
              <Field label="Care number (phone)">
                <input
                  className={inputCls()}
                  value={s.contact_phone ?? ""}
                  onChange={(e) => up("contact_phone", e.target.value)}
                  placeholder="+91 76765 25139"
                />
              </Field>
              <Field label="Care label (shown before phone/email)">
                <input
                  className={inputCls()}
                  value={s.care_label ?? ""}
                  onChange={(e) => up("care_label", e.target.value)}
                  placeholder="Care"
                />
              </Field>
              <Field
                label="Office address"
                hint="Registered office on invoices, and Our office address on Contact Us."
              >
                <textarea
                  className={`${inputCls()} min-h-[5.5rem]`}
                  value={s.invoice_registered_office_address ?? ""}
                  onChange={(e) => up("invoice_registered_office_address", e.target.value)}
                  placeholder="Street, area, city, state, PIN"
                  rows={3}
                />
              </Field>
              <Field label="WhatsApp number (digits only, with country code)">
                <input
                  className={inputCls()}
                  value={s.whatsapp_number ?? ""}
                  onChange={(e) => up("whatsapp_number", e.target.value)}
                  placeholder="919876543210"
                />
              </Field>
              <Field label="Instagram URL">
                <input
                  className={inputCls()}
                  value={s.instagram_url ?? ""}
                  onChange={(e) => up("instagram_url", e.target.value)}
                />
              </Field>
              <Field label="Facebook URL">
                <input
                  className={inputCls()}
                  value={s.facebook_url ?? ""}
                  onChange={(e) => up("facebook_url", e.target.value)}
                />
              </Field>
              <Field label="YouTube URL">
                <input
                  className={inputCls()}
                  value={s.youtube_url ?? ""}
                  onChange={(e) => up("youtube_url", e.target.value)}
                  placeholder="https://youtube.com/@yourchannel"
                />
              </Field>
              <Field label="X (Twitter) URL">
                <input
                  className={inputCls()}
                  value={s.twitter_url ?? ""}
                  onChange={(e) => up("twitter_url", e.target.value)}
                  placeholder="https://x.com/yourhandle"
                />
              </Field>
            </div>
          </AdminPanelSection>

          <AdminPanelSection title="Footer" {...sections.panel("footer")}>
            <Field label="Tagline">
              <textarea
                rows={3}
                className={inputCls()}
                value={s.footer_tagline ?? ""}
                onChange={(e) => up("footer_tagline", e.target.value)}
              />
            </Field>
            <Field label="Copyright line (leave blank for default)">
              <input
                className={inputCls()}
                value={s.footer_copyright ?? ""}
                onChange={(e) => up("footer_copyright", e.target.value)}
                placeholder="© 2026 Mini's n More · Made with love in India"
              />
            </Field>
          </AdminPanelSection>

          <AdminPanelSection title="Pricing & delivery" {...sections.panel("pricing")}>
            <p className="text-sm text-muted-foreground">
              Set one total GST rate; CGST/SGST or IGST is derived later from the delivery state and does not need
              separate admin fields. Product prices should match whether GST is included or added at checkout.
            </p>
            <div className="grid sm:grid-cols-2 gap-3">
              <NumberField
                label="GST rate (%)"
                value={s.gst_rate}
                onChange={(gst_rate) => up("gst_rate", gst_rate)}
                min={0}
                max={GST_RATE_MAX}
                step={0.01}
                required
              />
              <Field label="Product prices include GST?">
                <label className="flex items-center gap-2 text-sm mt-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={s.prices_include_gst}
                    onChange={(e) => up("prices_include_gst", e.target.checked)}
                    className="accent-primary"
                  />
                  Yes — GST shown for information only (not added again)
                </label>
              </Field>
              <NumberField
                label="Standard shipping (₹)"
                value={s.shipping_standard_inr}
                onChange={(shipping_standard_inr) => up("shipping_standard_inr", shipping_standard_inr)}
                min={0}
                max={SHIPPING_INR_MAX}
                required
              />
              <NumberField
                label="Express shipping (₹)"
                value={s.shipping_express_inr}
                onChange={(shipping_express_inr) => up("shipping_express_inr", shipping_express_inr)}
                min={0}
                max={SHIPPING_INR_MAX}
                required
              />
              <NumberField
                label="Free standard shipping above (₹ subtotal after discount)"
                value={s.free_shipping_min_inr}
                onChange={(free_shipping_min_inr) => up("free_shipping_min_inr", free_shipping_min_inr)}
                min={0}
                max={FREE_SHIPPING_MIN_INR_MAX}
                required
              />
              <NumberField
                label="Cash on Delivery handling fee (₹)"
                value={s.cod_fee_inr}
                onChange={(cod_fee_inr) => up("cod_fee_inr", cod_fee_inr)}
                min={0}
                max={SHIPPING_INR_MAX}
                required
              />
              <NumberField
                label="Extra days added to delivery ETA"
                value={s.delivery_eta_buffer_days}
                onChange={(delivery_eta_buffer_days) => up("delivery_eta_buffer_days", delivery_eta_buffer_days)}
                min={0}
                max={MAX_DELIVERY_ETA_BUFFER_DAYS}
                required
              />
            </div>
            <p className="text-xs text-muted-foreground">
              Pincode lookup ETAs (e.g. 2–3 days) get this many extra days before showing on product and checkout.
              Set 0 for no buffer.
            </p>
          </AdminPanelSection>

          <AdminPanelSection title="Returns & refunds" {...sections.panel("returns")}>
            <div className="grid sm:grid-cols-3 gap-3">
              <NumberField
                label="Return window (days after delivery)"
                value={s.return_window_days}
                onChange={(return_window_days) => up("return_window_days", return_window_days)}
                min={1}
                max={MAX_RETURN_WINDOW_DAYS}
                required
              />
              <NumberField
                label="Refund min business days"
                value={s.refund_min_business_days}
                onChange={(refund_min_business_days) => up("refund_min_business_days", refund_min_business_days)}
                min={1}
                max={MAX_REFUND_BUSINESS_DAYS}
                required
              />
              <NumberField
                label="Refund max business days"
                value={s.refund_max_business_days}
                onChange={(refund_max_business_days) => up("refund_max_business_days", refund_max_business_days)}
                min={1}
                max={MAX_REFUND_BUSINESS_DAYS}
                required
              />
            </div>
            <p className="text-xs text-muted-foreground">
              Return window is counted from delivery. Refund min–max is shown to customers when a refund is
              initiated (original payment source).
            </p>
          </AdminPanelSection>

          <AdminPanelSection title="E-Catalogue" {...sections.panel("ecatalogue")}>
            <div className="grid sm:grid-cols-2 gap-3">
              <PdfUploadField
                label="Catalogue PDF"
                value={s.ecatalogue_url ?? ""}
                onChange={(ecatalogue_url) => up("ecatalogue_url", ecatalogue_url)}
              />
              <Field label="Button / link label">
                <input
                  className={inputCls()}
                  value={s.ecatalogue_label ?? ""}
                  onChange={(e) => up("ecatalogue_label", e.target.value)}
                />
              </Field>
            </div>
          </AdminPanelSection>
        </div>
      )}
    </AdminPage>
  );
}
