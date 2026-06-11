ALTER TABLE public.site_settings
  ADD COLUMN IF NOT EXISTS legal_entity_name TEXT,
  ADD COLUMN IF NOT EXISTS care_label TEXT DEFAULT 'Care';

UPDATE public.site_settings
SET
  legal_entity_name = COALESCE(legal_entity_name, 'Stolt Global Private Limited'),
  brand_name        = COALESCE(NULLIF(brand_name, ''), 'Minis n More'),
  contact_phone     = COALESCE(NULLIF(contact_phone, ''), '+91 76765 25139'),
  contact_email     = COALESCE(NULLIF(contact_email, ''), 'care@minisnmore.com'),
  care_label        = COALESCE(care_label, 'Care')
WHERE id = 1;

INSERT INTO public.site_settings (id, brand_name, legal_entity_name, contact_phone, contact_email, care_label)
SELECT 1, 'Minis n More', 'Stolt Global Private Limited', '+91 76765 25139', 'care@minisnmore.com', 'Care'
WHERE NOT EXISTS (SELECT 1 FROM public.site_settings WHERE id = 1);