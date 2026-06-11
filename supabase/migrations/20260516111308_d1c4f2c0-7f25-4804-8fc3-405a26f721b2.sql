-- Site-wide settings (single row keyed by id=1)
create table if not exists public.site_settings (
  id integer primary key default 1,
  brand_name text not null default 'Mini''s n More',
  logo_url text,
  logo_horizontal_url text,
  contact_email text,
  contact_phone text,
  whatsapp_number text,
  instagram_url text,
  facebook_url text,
  footer_tagline text,
  footer_copyright text,
  ecatalogue_url text,
  ecatalogue_label text default 'New Catalogue – Soft Bloom',
  updated_at timestamptz not null default now(),
  constraint site_settings_singleton check (id = 1)
);

alter table public.site_settings enable row level security;

drop policy if exists "site_settings public read" on public.site_settings;
create policy "site_settings public read"
  on public.site_settings for select
  to public using (true);

drop policy if exists "site_settings admin all" on public.site_settings;
create policy "site_settings admin all"
  on public.site_settings for all
  to authenticated
  using (has_role(auth.uid(), 'admin'::app_role))
  with check (has_role(auth.uid(), 'admin'::app_role));

drop trigger if exists trg_site_settings_updated on public.site_settings;
create trigger trg_site_settings_updated
  before update on public.site_settings
  for each row execute function public.tg_set_updated_at();

-- Seed the singleton row
insert into public.site_settings (id) values (1) on conflict (id) do nothing;

-- Seed the standard home sections if missing (idempotent)
insert into public.home_sections (key, title, subtitle, enabled, sort_order, payload) values
  ('hero',         'Hero',           null, true, 10, '{}'::jsonb),
  ('by_age',       'Shop by age',    'Made for every little chapter', true, 20, '{}'::jsonb),
  ('best_sellers', 'Featured',       null, true, 30, '{}'::jsonb),
  ('new_arrivals', 'New arrivals',   'Fresh off the loom', true, 40, '{}'::jsonb),
  ('trending',     'Trending now',   'Loved most this week', true, 50, '{}'::jsonb),
  ('watch_shop',   'Watch & Shop',   null, true, 60, '{}'::jsonb),
  ('fabric_story', 'Fabric story',   null, true, 70, '{}'::jsonb),
  ('brand_story',  'Our story',      null, true, 80, '{}'::jsonb),
  ('reviews',      'Reviews',        null, true, 90, '{}'::jsonb),
  ('journal_strip','Journal',        null, true, 100,'{}'::jsonb),
  ('newsletter',   'Newsletter',     null, true, 110,'{}'::jsonb)
on conflict do nothing;
