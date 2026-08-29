import { useEffect, useState } from "react";
import { getSiteSettings } from "@/lib/backend-data";

export type SiteSettings = {
  brand_name: string;
  legal_entity_name: string | null;
  logo_url: string | null;
  logo_horizontal_url: string | null;
  invoice_logo_url: string | null;
  invoice_website: string | null;
  invoice_gstin: string | null;
  invoice_sold_by_address: string | null;
  invoice_dispatch_from_address: string | null;
  invoice_registered_office_address: string | null;
  invoice_cin: string | null;
  invoice_footer_note: string | null;
  invoice_seller_state: string | null;
  contact_email: string | null;
  contact_phone: string | null;
  care_label: string | null;
  whatsapp_number: string | null;
  instagram_url: string | null;
  facebook_url: string | null;
  youtube_url: string | null;
  linkedin_url: string | null;
  twitter_url: string | null;
  footer_tagline: string | null;
  footer_copyright: string | null;
  ecatalogue_url: string | null;
  ecatalogue_label: string | null;
  gst_rate: number;
  prices_include_gst: boolean;
  shipping_standard_inr: number;
  shipping_express_inr: number;
  free_shipping_min_inr: number;
};

const DEFAULT_SOCIAL = {
  instagram_url: "https://www.instagram.com/minis_n_more_official/",
  facebook_url: "https://www.facebook.com/profile.php?id=61590495838027",
  youtube_url: "https://www.youtube.com/@Minis_n_More_official",
  linkedin_url: "https://www.linkedin.com/in/minis-n-more/",
} as const;

function coalesceUrl(value: string | null | undefined, fallback: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : fallback;
}

function mergeSiteSettings(data: Partial<SiteSettings> | null | undefined): SiteSettings {
  const merged = { ...DEFAULTS, ...(data ?? {}) } as SiteSettings;
  merged.instagram_url = coalesceUrl(merged.instagram_url, DEFAULT_SOCIAL.instagram_url);
  merged.facebook_url = coalesceUrl(merged.facebook_url, DEFAULT_SOCIAL.facebook_url);
  merged.youtube_url = coalesceUrl(merged.youtube_url, DEFAULT_SOCIAL.youtube_url);
  merged.linkedin_url = coalesceUrl(merged.linkedin_url, DEFAULT_SOCIAL.linkedin_url);
  return merged;
}

const DEFAULTS: SiteSettings = {
  brand_name: "Minis n More",
  legal_entity_name: "Stolt Global Private Limited",
  logo_url: null,
  logo_horizontal_url: null,
  invoice_logo_url: null,
  invoice_website: null,
  invoice_gstin: null,
  invoice_sold_by_address: null,
  invoice_dispatch_from_address: null,
  invoice_registered_office_address: null,
  invoice_cin: null,
  invoice_footer_note: null,
  invoice_seller_state: null,
  contact_email: "care@minisnmore.com",
  contact_phone: "+91 76765 25139",
  care_label: "Care",
  whatsapp_number: null,
  instagram_url: DEFAULT_SOCIAL.instagram_url,
  facebook_url: DEFAULT_SOCIAL.facebook_url,
  youtube_url: DEFAULT_SOCIAL.youtube_url,
  linkedin_url: DEFAULT_SOCIAL.linkedin_url,
  twitter_url: null,
  footer_tagline:
    "Minis today, memories forever. Heirloom-quality clothing for the first 24 months — softly made in India, gently loved everywhere.",
  footer_copyright: null,
  ecatalogue_url: "/e-catalogue.pdf",
  ecatalogue_label: "New Catalogue – Soft Bloom",
  gst_rate: 5,
  prices_include_gst: true,
  shipping_standard_inr: 99,
  shipping_express_inr: 150,
  free_shipping_min_inr: 2500,
};

let cache: SiteSettings | null = null;
let inflight: Promise<SiteSettings> | null = null;
const subs = new Set<(s: SiteSettings) => void>();

async function load(): Promise<SiteSettings> {
  if (cache) return cache;
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const data = await getSiteSettings();
      const merged = mergeSiteSettings(data ?? undefined);
      cache = merged;
      subs.forEach((fn) => fn(merged));
      return merged;
    } catch (err) {
      if (import.meta.env.DEV) {
        console.error("useSiteSettings load failed", err);
      }
      cache = DEFAULTS;
      subs.forEach((fn) => fn(DEFAULTS));
      return DEFAULTS;
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}

export function invalidateSiteSettings() {
  cache = null;
  void load();
}

export function useSiteSettings(): SiteSettings {
  const [s, setS] = useState<SiteSettings>(cache ?? DEFAULTS);
  useEffect(() => {
    let alive = true;
    if (!cache) load().then((v) => alive && setS(v));
    const sub = (v: SiteSettings) => alive && setS(v);
    subs.add(sub);
    return () => {
      alive = false;
      subs.delete(sub);
    };
  }, []);
  return s;
}
