import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { Save } from "lucide-react";
import { toast } from "sonner";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { ImageUploadField } from "@/components/admin/ImageUploadField";
import {
  PortraitInvoiceDocument,
  type InvoiceBrandSettings,
} from "@/components/PortraitInvoiceDocument";
import { getAdminSiteSettings, saveSiteSettings, type SiteSettingsRow } from "@/lib/backend-data";
import { invalidateSiteSettings } from "@/hooks/useSiteSettings";
import { showApiError } from "@/lib/ui-errors";
import type { InvoiceOrder } from "@/lib/invoice-breakdown";

export const Route = createFileRoute("/admin/invoices")({ component: AdminInvoicesPage });

const DEFAULTS: SiteSettingsRow = {
  brand_name: "Minis n More",
  legal_entity_name: "Stolt Global Private Limited",
  invoice_logo_url: "",
  invoice_website: "",
  invoice_gstin: "",
  invoice_sold_by_address: "",
  invoice_dispatch_from_address: "",
  invoice_registered_office_address: "",
  invoice_cin: "",
  invoice_footer_note: "",
  invoice_seller_state: "Karnataka",
  logo_url: "",
  logo_horizontal_url: "",
  contact_email: "care@minisnmore.com",
  contact_phone: "+91 76765 25139",
  gst_rate: 5,
  prices_include_gst: true,
  shipping_standard_inr: 99,
  shipping_express_inr: 150,
  free_shipping_min_inr: 2500,
};

const PREVIEW_ORDER: InvoiceOrder & { shipping_address: Record<string, unknown> } = {
  order_number: "MNM-PREVIEW-001",
  created_at: new Date().toISOString(),
  subtotal: 1685.6,
  discount: 168.56,
  shipping: 0,
  cod_fee: 99,
  tax: 76.95,
  donation: 0,
  total: 1616.04,
  coupon_code: "WELCOME10",
  payment_method: "cod",
  payment_status: "paid",
  shipping_address: {
    full_name: "Invoice Preview Customer",
    line1: "12 Sample Road",
    city: "Bengaluru",
    state: "Karnataka",
    pincode: "560001",
    phone: "+91 90000 00000",
  },
  order_items: [
    {
      id: 1,
      product_name: "Body suit (Half sleeve)",
      size: "0-3 Months",
      sku: "MnM_BHS1_0-3",
      hsn: "61112000",
      qty: 1,
      mrp: 349,
      price: 279.2,
      tax_pct: 5,
    },
    {
      id: 2,
      product_name: "Reversible padded jacket with hood",
      size: "3-6 Months",
      sku: "MnM_RJH_FS1_3-6",
      hsn: "61112000",
      qty: 1,
      mrp: 959,
      price: 767.2,
      tax_pct: 5,
    },
    {
      id: 3,
      product_name: "Gift set 8 piece",
      size: "0-3 Months",
      sku: "MnM_GS1_8",
      hsn: "61112000",
      qty: 1,
      mrp: 799,
      price: 639.2,
      tax_pct: 5,
    },
  ],
};

function AdminInvoicesPage() {
  const [row, setRow] = useState<SiteSettingsRow>(DEFAULTS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void (async () => {
      try {
        const saved = await getAdminSiteSettings();
        if (saved) setRow({ ...DEFAULTS, ...saved });
      } catch (error) {
        showApiError(error, "Could not load invoice settings");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const settings = useMemo(() => row as InvoiceBrandSettings, [row]);

  const save = async () => {
    setSaving(true);
    try {
      await saveSiteSettings(row);
      invalidateSiteSettings();
      toast.success("Invoice settings saved");
    } catch (error) {
      showApiError(error, "Could not save invoice settings");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminPage
      eyebrow="Sales"
      title="Invoices"
      description="Configure the identity and legal details printed on custom invoices. A unique QR code linking to the matching order is generated automatically."
      action={
        <button
          type="button"
          onClick={() => void save()}
          disabled={loading || saving}
          className="inline-flex items-center gap-2 rounded-full bg-primary px-4 py-2.5 text-sm text-primary-foreground disabled:opacity-60"
        >
          <Save className="h-4 w-4" /> {saving ? "Saving…" : "Save invoice settings"}
        </button>
      }
    >
      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <div className="space-y-6">
          <section className="glass-strong rounded-3xl p-5">
            <div className="space-y-5">
              <ImageUploadField
                label="Invoice logo"
                folder="branding"
                spec="invoiceLogo"
                value={String(row.invoice_logo_url ?? "")}
                onChange={(invoice_logo_url) =>
                  setRow((current) => ({ ...current, invoice_logo_url }))
                }
                previewClassName="h-20 w-56 rounded-xl bg-white object-contain p-2 shrink-0"
                hint="If left blank, invoices use the horizontal brand logo and then the main logo."
              />

              <div className="grid gap-4 md:grid-cols-2">
                <InvoiceInput
                  label="Invoice website"
                  placeholder="https://www.example.com"
                  value={row.invoice_website}
                  onChange={(invoice_website) =>
                    setRow((current) => ({ ...current, invoice_website }))
                  }
                />
                <InvoiceInput
                  label="GSTIN"
                  placeholder="Enter company GSTIN"
                  value={row.invoice_gstin}
                  onChange={(invoice_gstin) => setRow((current) => ({ ...current, invoice_gstin }))}
                />
                <InvoiceInput
                  label="CIN"
                  placeholder="Enter company CIN"
                  value={row.invoice_cin}
                  onChange={(invoice_cin) => setRow((current) => ({ ...current, invoice_cin }))}
                />
                <InvoiceInput
                  label="Seller state"
                  placeholder="Karnataka"
                  value={row.invoice_seller_state}
                  onChange={(invoice_seller_state) =>
                    setRow((current) => ({ ...current, invoice_seller_state }))
                  }
                />
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <InvoiceTextarea
                  label="Sold by address"
                  placeholder="Enter the legal seller name and full address"
                  value={row.invoice_sold_by_address}
                  onChange={(invoice_sold_by_address) =>
                    setRow((current) => ({ ...current, invoice_sold_by_address }))
                  }
                />
                <InvoiceTextarea
                  label="Dispatch from address"
                  placeholder="Enter the warehouse or dispatch address"
                  value={row.invoice_dispatch_from_address}
                  onChange={(invoice_dispatch_from_address) =>
                    setRow((current) => ({ ...current, invoice_dispatch_from_address }))
                  }
                />
                <InvoiceTextarea
                  label="Registered office address"
                  placeholder="Enter the registered-office address"
                  value={row.invoice_registered_office_address}
                  onChange={(invoice_registered_office_address) =>
                    setRow((current) => ({ ...current, invoice_registered_office_address }))
                  }
                />
                <InvoiceTextarea
                  label="Invoice footer note"
                  placeholder="Optional tax, membership or customer note"
                  value={row.invoice_footer_note}
                  onChange={(invoice_footer_note) =>
                    setRow((current) => ({ ...current, invoice_footer_note }))
                  }
                />
              </div>
            </div>
          </section>

          <section>
            <div className="mb-3">
              <h2 className="font-display text-xl">Invoice preview</h2>
              <p className="text-sm text-muted-foreground">
                Includes the workbook's multi-item COD −₹0.01 round-off example.
              </p>
            </div>
            <div className="max-h-[780px] overflow-auto rounded-3xl border border-border bg-slate-200 p-3">
              <PortraitInvoiceDocument order={PREVIEW_ORDER} settings={settings} compact />
            </div>
          </section>
        </div>
      )}
    </AdminPage>
  );
}

function InvoiceInput({
  label,
  placeholder,
  value,
  onChange,
}: {
  label: string;
  placeholder: string;
  value: SiteSettingsRow[string];
  onChange: (value: string) => void;
}) {
  return (
    <Field label={label}>
      <input
        className={inputCls()}
        placeholder={placeholder}
        value={String(value ?? "")}
        onChange={(event) => onChange(event.target.value)}
      />
    </Field>
  );
}

function InvoiceTextarea({
  label,
  placeholder,
  value,
  onChange,
}: {
  label: string;
  placeholder: string;
  value: SiteSettingsRow[string];
  onChange: (value: string) => void;
}) {
  return (
    <Field label={label}>
      <textarea
        className={`${inputCls()} min-h-24 resize-y`}
        placeholder={placeholder}
        value={String(value ?? "")}
        onChange={(event) => onChange(event.target.value)}
      />
    </Field>
  );
}
