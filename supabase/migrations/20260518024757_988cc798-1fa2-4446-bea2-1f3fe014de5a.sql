ALTER TABLE public.products ADD COLUMN IF NOT EXISTS sku TEXT;
ALTER TABLE public.products ADD COLUMN IF NOT EXISTS fabric_type TEXT DEFAULT '100% Cotton';
UPDATE public.products SET sku = UPPER(REGEXP_REPLACE(slug, '[^a-zA-Z0-9]+', '-', 'g')) WHERE sku IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS products_sku_unique ON public.products(sku) WHERE sku IS NOT NULL;