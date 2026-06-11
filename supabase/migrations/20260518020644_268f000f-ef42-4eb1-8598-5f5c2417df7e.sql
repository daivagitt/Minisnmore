
DELETE FROM public.products WHERE slug IN (
  'rose-blossom-dress-sample','cloud-soft-romper-sample','sunshine-day-set-sample',
  'heritage-lehenga-sample','cozy-knit-cardigan-sample','infant-girl-bloom-frock-sample'
);
DELETE FROM public.categories WHERE slug IN (
  'newborn-essentials','dresses-frocks','rompers-onesies','ethnic-wear'
);
DELETE FROM public.banners WHERE title IN ('Soft Bloom Edit','Newborn Nest','Festive Heirloom');
DELETE FROM public.home_sections WHERE key IN ('hero','featured_products','categories','watch_and_shop','journal');
DELETE FROM public.coupons WHERE code IN ('WELCOME10','BLOOM500','FESTIVE20');
DELETE FROM public.blog_posts WHERE slug IN ('caring-for-organic-cotton','first-festive-outfit','building-a-newborn-wardrobe');
DELETE FROM public.site_settings WHERE id = 1;
