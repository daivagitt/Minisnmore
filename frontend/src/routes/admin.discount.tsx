import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { Save, Percent } from "lucide-react";
import { toast } from "sonner";
import { AdminPage } from "@/components/admin/AdminPage";
import { NumberField } from "@/components/admin/NumberField";
import { invalidateSiteSettings } from "@/hooks/useSiteSettings";
import { invalidateProducts } from "@/lib/storefront";
import { DEFAULT_STORE_DISCOUNT_PERCENT } from "@/lib/pricing";
import { showApiError, showValidationErrors } from "@/lib/ui-errors";
import {
  getAdminSiteSettings,
  getSiteSettingsValidationErrors,
  saveSiteSettings,
  type SiteSettingsRow,
} from "@/lib/backend-data";

export const Route = createFileRoute("/admin/discount")({ component: DiscountPage });

const DISCOUNT_MAX = 100;

function DiscountPage() {
  const [settings, setSettings] = useState<SiteSettingsRow | null>(null);
  const [discountPercent, setDiscountPercent] = useState<number | null>(DEFAULT_STORE_DISCOUNT_PERCENT);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const data = await getAdminSiteSettings();
        if (data) {
          setSettings(data);
          const raw = data.store_discount_percent;
          setDiscountPercent(
            raw != null ? Number(raw) : DEFAULT_STORE_DISCOUNT_PERCENT,
          );
        }
      } catch (error) {
        showApiError(error, "Could not load discount settings");
      }
      setLoading(false);
    })();
  }, []);

  const save = async () => {
    if (!settings) {
      toast.error("Settings not loaded yet");
      return;
    }
    setSaving(true);
    const payload: SiteSettingsRow = {
      ...settings,
      store_discount_percent: discountPercent,
    };
    const errors = getSiteSettingsValidationErrors(payload);
    if (errors.length) {
      showValidationErrors(errors);
      setSaving(false);
      return;
    }
    try {
      const saved = await saveSiteSettings(payload);
      if (saved) setSettings(saved);
      toast.success("Discount saved — storefront prices updated");
      invalidateSiteSettings();
      invalidateProducts();
    } catch (error) {
      showApiError(error, "Could not save discount");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminPage
      eyebrow="Pricing"
      title="Store discount"
      description="Set the global discount applied to MRP across the storefront. Selling price = MRP minus this percentage. Changes apply immediately."
      action={
        <button
          onClick={save}
          disabled={saving || loading || !settings}
          className="rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center gap-2 disabled:opacity-60"
        >
          <Save className="h-4 w-4" />
          Save
        </button>
      }
    >
      <div className="glass rounded-[1.75rem] p-5 md:p-6 max-w-lg">
        <div className="flex items-center gap-3 mb-4">
          <div className="rounded-2xl bg-primary/10 p-2.5 text-primary">
            <Percent className="h-5 w-5" />
          </div>
          <div>
            <h2 className="font-medium">MRP discount</h2>
            <p className="text-sm text-muted-foreground">
              Applies store-wide on catalog, product pages, and checkout.
            </p>
          </div>
        </div>
        <NumberField
          label="Discount off MRP (%)"
          hint="0 = no discount. Selling price = MRP minus this percentage."
          value={discountPercent}
          onChange={setDiscountPercent}
          min={0}
          max={DISCOUNT_MAX}
          readOnly={loading}
        />
        {discountPercent != null && discountPercent > 0 && (
          <p className="mt-3 text-sm text-muted-foreground">
            Example: MRP ₹1,000 → selling ₹
            {Math.round(1000 * (1 - discountPercent / 100))}
          </p>
        )}
      </div>
    </AdminPage>
  );
}
