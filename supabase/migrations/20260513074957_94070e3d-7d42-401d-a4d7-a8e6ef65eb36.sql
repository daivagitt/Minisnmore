
-- Categories
create table public.categories (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null,
  description text,
  image_url text,
  sort_order int not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.categories enable row level security;
create policy "categories public read" on public.categories for select using (active = true);
create policy "categories admin all" on public.categories for all to authenticated
  using (has_role(auth.uid(), 'admin')) with check (has_role(auth.uid(), 'admin'));
create trigger trg_categories_updated before update on public.categories
  for each row execute function public.tg_set_updated_at();

-- Products
create table public.products (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  category_id uuid references public.categories(id) on delete set null,
  name text not null,
  description text,
  story text,
  price numeric not null default 0,
  compare_at_price numeric,
  currency text not null default 'INR',
  age_min_months int,
  age_max_months int,
  sizes jsonb not null default '[]'::jsonb,
  colors jsonb not null default '[]'::jsonb,
  images jsonb not null default '[]'::jsonb,
  badge text,
  featured boolean not null default false,
  trending boolean not null default false,
  new_arrival boolean not null default false,
  best_seller boolean not null default false,
  active boolean not null default true,
  sort_order int not null default 0,
  stock int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.products enable row level security;
create policy "products public read" on public.products for select using (active = true);
create policy "products admin all" on public.products for all to authenticated
  using (has_role(auth.uid(), 'admin')) with check (has_role(auth.uid(), 'admin'));
create trigger trg_products_updated before update on public.products
  for each row execute function public.tg_set_updated_at();
create index idx_products_category on public.products(category_id);
create index idx_products_flags on public.products(featured, trending, new_arrival, best_seller);

-- Home sections
create table public.home_sections (
  id uuid primary key default gen_random_uuid(),
  key text not null unique,
  title text,
  subtitle text,
  enabled boolean not null default true,
  sort_order int not null default 0,
  payload jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
alter table public.home_sections enable row level security;
create policy "home_sections public read" on public.home_sections for select using (true);
create policy "home_sections admin all" on public.home_sections for all to authenticated
  using (has_role(auth.uid(), 'admin')) with check (has_role(auth.uid(), 'admin'));
create trigger trg_home_sections_updated before update on public.home_sections
  for each row execute function public.tg_set_updated_at();

-- Page visibility
create table public.page_visibility (
  id uuid primary key default gen_random_uuid(),
  page_key text not null unique,
  visible boolean not null default true,
  updated_at timestamptz not null default now()
);
alter table public.page_visibility enable row level security;
create policy "page_visibility public read" on public.page_visibility for select using (true);
create policy "page_visibility admin all" on public.page_visibility for all to authenticated
  using (has_role(auth.uid(), 'admin')) with check (has_role(auth.uid(), 'admin'));
create trigger trg_page_visibility_updated before update on public.page_visibility
  for each row execute function public.tg_set_updated_at();

-- Seed defaults
insert into public.home_sections (key, title, subtitle, enabled, sort_order, payload) values
  ('hero', 'Minis today, memories forever.', 'Heirloom-quality clothing for the first 24 months.', true, 1, '{}'::jsonb),
  ('by_age', 'Shop by age', 'Sized to baby, not to a guess.', true, 2, '{"cards": []}'::jsonb),
  ('best_sellers', 'Best sellers', 'Loved by our families.', true, 3, '{"product_slugs": []}'::jsonb),
  ('trending', 'Trending now', 'What''s catching eyes this week.', true, 4, '{"product_slugs": []}'::jsonb),
  ('new_arrivals', 'New arrivals', 'Fresh in this season.', true, 5, '{"product_slugs": []}'::jsonb),
  ('featured', 'Featured', 'Editor''s picks.', true, 6, '{"product_slugs": []}'::jsonb),
  ('journal_strip', 'From the journal', 'Soft mornings, gentle reads.', true, 7, '{}'::jsonb)
on conflict (key) do nothing;

insert into public.page_visibility (page_key, visible) values
  ('shop', true), ('gift', true), ('subscribe', true), ('track', true),
  ('loyalty', true), ('blog', true), ('buyback', true)
on conflict (page_key) do nothing;

-- Storage bucket for product images
insert into storage.buckets (id, name, public) values ('product-images', 'product-images', true)
on conflict (id) do nothing;

create policy "product-images public read" on storage.objects for select
  using (bucket_id = 'product-images');
create policy "product-images admin write" on storage.objects for insert to authenticated
  with check (bucket_id = 'product-images' and has_role(auth.uid(), 'admin'));
create policy "product-images admin update" on storage.objects for update to authenticated
  using (bucket_id = 'product-images' and has_role(auth.uid(), 'admin'));
create policy "product-images admin delete" on storage.objects for delete to authenticated
  using (bucket_id = 'product-images' and has_role(auth.uid(), 'admin'));
