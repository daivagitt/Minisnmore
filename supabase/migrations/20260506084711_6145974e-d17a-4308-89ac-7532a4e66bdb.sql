-- Roles enum + table
create type public.app_role as enum ('admin', 'editor', 'user');

create table public.user_roles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete cascade not null,
  role app_role not null default 'user',
  created_at timestamptz not null default now(),
  unique (user_id, role)
);

alter table public.user_roles enable row level security;

create or replace function public.has_role(_user_id uuid, _role app_role)
returns boolean language sql stable security definer set search_path = public
as $$ select exists (select 1 from public.user_roles where user_id = _user_id and role = _role) $$;

create policy "users see own roles" on public.user_roles for select to authenticated using (auth.uid() = user_id);
create policy "admins manage roles" on public.user_roles for all to authenticated
  using (public.has_role(auth.uid(), 'admin')) with check (public.has_role(auth.uid(), 'admin'));

-- Generic updated_at trigger
create or replace function public.tg_set_updated_at() returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;

-- Banners
create table public.banners (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  subtitle text,
  image_url text,
  link_url text,
  cta_label text,
  sort_order int not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.banners enable row level security;
create policy "banners public read" on public.banners for select using (active = true);
create policy "banners admin all" on public.banners for all to authenticated
  using (public.has_role(auth.uid(), 'admin')) with check (public.has_role(auth.uid(), 'admin'));
create trigger banners_updated before update on public.banners for each row execute function public.tg_set_updated_at();

-- Coupons
create table public.coupons (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  discount_type text not null default 'percent', -- percent|flat
  discount_value numeric not null default 0,
  min_order numeric not null default 0,
  max_discount numeric,
  expires_at timestamptz,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.coupons enable row level security;
create policy "coupons admin all" on public.coupons for all to authenticated
  using (public.has_role(auth.uid(), 'admin')) with check (public.has_role(auth.uid(), 'admin'));
create trigger coupons_updated before update on public.coupons for each row execute function public.tg_set_updated_at();

-- Blog posts
create table public.blog_posts (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  excerpt text,
  content text,
  cover_url text,
  published boolean not null default false,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.blog_posts enable row level security;
create policy "blog public read published" on public.blog_posts for select using (published = true);
create policy "blog admin all" on public.blog_posts for all to authenticated
  using (public.has_role(auth.uid(), 'admin')) with check (public.has_role(auth.uid(), 'admin'));
create trigger blog_updated before update on public.blog_posts for each row execute function public.tg_set_updated_at();

-- Policies (shipping, returns, privacy, etc.)
create table public.policies (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  body text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.policies enable row level security;
create policy "policies public read" on public.policies for select using (true);
create policy "policies admin all" on public.policies for all to authenticated
  using (public.has_role(auth.uid(), 'admin')) with check (public.has_role(auth.uid(), 'admin'));
create trigger policies_updated before update on public.policies for each row execute function public.tg_set_updated_at();

-- Product overrides (extra info on top of Zoho)
create table public.product_overrides (
  id uuid primary key default gen_random_uuid(),
  zoho_item_id text,
  slug text not null unique,
  badge text,
  story text,
  gallery jsonb not null default '[]'::jsonb,
  featured boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.product_overrides enable row level security;
create policy "overrides public read" on public.product_overrides for select using (true);
create policy "overrides admin all" on public.product_overrides for all to authenticated
  using (public.has_role(auth.uid(), 'admin')) with check (public.has_role(auth.uid(), 'admin'));
create trigger overrides_updated before update on public.product_overrides for each row execute function public.tg_set_updated_at();

-- Zoho settings (single row)
create table public.zoho_settings (
  id int primary key default 1,
  org_id text,
  client_id text,
  refresh_token text,
  data_center text default 'in',
  connected boolean not null default false,
  updated_at timestamptz not null default now(),
  constraint zoho_singleton check (id = 1)
);
alter table public.zoho_settings enable row level security;
create policy "zoho admin all" on public.zoho_settings for all to authenticated
  using (public.has_role(auth.uid(), 'admin')) with check (public.has_role(auth.uid(), 'admin'));
create trigger zoho_updated before update on public.zoho_settings for each row execute function public.tg_set_updated_at();

insert into public.zoho_settings (id) values (1) on conflict (id) do nothing;

-- Auto-grant first signup as admin (bootstrap), everyone else = user
create or replace function public.handle_new_user() returns trigger language plpgsql security definer set search_path = public as $$
declare _count int;
begin
  select count(*) into _count from public.user_roles where role = 'admin';
  if _count = 0 then
    insert into public.user_roles (user_id, role) values (new.id, 'admin');
  else
    insert into public.user_roles (user_id, role) values (new.id, 'user');
  end if;
  return new;
end $$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- Seed sample banners
insert into public.banners (title, subtitle, cta_label, link_url, sort_order, active) values
  ('Tiny luxuries, softly made', 'Heirloom · Hand-finished organic baby clothing', 'Shop the edit', '/shop', 1, true),
  ('May Launch · New Catalogue', 'Whisper-soft pieces for the first 24 months', 'Explore', '/shop', 2, true);

-- Seed default policies
insert into public.policies (slug, title, body) values
  ('shipping', 'Shipping', 'Pan-India shipping in 3–5 business days.'),
  ('returns', 'Returns', '7-day returns on unworn pieces with original tags.'),
  ('privacy', 'Privacy', 'We respect your privacy. Read our full policy here.');