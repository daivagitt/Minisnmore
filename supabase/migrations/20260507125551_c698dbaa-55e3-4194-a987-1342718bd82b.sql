
-- Profiles
create table public.profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique,
  full_name text,
  phone text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.profiles enable row level security;

create policy "profiles self read" on public.profiles for select to authenticated using (auth.uid() = user_id);
create policy "profiles self upsert" on public.profiles for insert to authenticated with check (auth.uid() = user_id);
create policy "profiles self update" on public.profiles for update to authenticated using (auth.uid() = user_id);
create policy "profiles admin all" on public.profiles for all to authenticated using (has_role(auth.uid(),'admin')) with check (has_role(auth.uid(),'admin'));

create trigger profiles_updated before update on public.profiles for each row execute function public.tg_set_updated_at();

-- Orders
create table public.orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  order_number text not null unique,
  status text not null default 'pending', -- pending, confirmed, packed, shipped, delivered, cancelled
  payment_status text not null default 'pending', -- pending, paid, failed, refunded
  razorpay_order_id text,
  razorpay_payment_id text,
  razorpay_signature text,
  subtotal numeric not null default 0,
  discount numeric not null default 0,
  shipping numeric not null default 0,
  tax numeric not null default 0,
  donation numeric not null default 0,
  total numeric not null default 0,
  coupon_code text,
  shipping_address jsonb not null default '{}'::jsonb,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.orders enable row level security;

create policy "orders self read" on public.orders for select to authenticated using (auth.uid() = user_id);
create policy "orders self insert" on public.orders for insert to authenticated with check (auth.uid() = user_id);
create policy "orders admin all" on public.orders for all to authenticated using (has_role(auth.uid(),'admin')) with check (has_role(auth.uid(),'admin'));

create trigger orders_updated before update on public.orders for each row execute function public.tg_set_updated_at();
create index orders_user_idx on public.orders(user_id, created_at desc);
create index orders_rzp_idx on public.orders(razorpay_order_id);

-- Order items
create table public.order_items (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders(id) on delete cascade,
  product_id text not null,
  product_slug text,
  product_name text not null,
  product_image text,
  size text,
  color text,
  qty integer not null default 1,
  price numeric not null default 0,
  created_at timestamptz not null default now()
);
alter table public.order_items enable row level security;

create policy "order_items self read" on public.order_items for select to authenticated
  using (exists (select 1 from public.orders o where o.id = order_id and o.user_id = auth.uid()));
create policy "order_items self insert" on public.order_items for insert to authenticated
  with check (exists (select 1 from public.orders o where o.id = order_id and o.user_id = auth.uid()));
create policy "order_items admin all" on public.order_items for all to authenticated
  using (has_role(auth.uid(),'admin')) with check (has_role(auth.uid(),'admin'));

create index order_items_order_idx on public.order_items(order_id);

-- Auto-create profile on signup (extend existing handler)
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare _count int;
begin
  insert into public.profiles (user_id, full_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'full_name', ''))
  on conflict (user_id) do nothing;

  select count(*) into _count from public.user_roles where role = 'admin';
  if _count = 0 then
    insert into public.user_roles (user_id, role) values (new.id, 'admin') on conflict do nothing;
  else
    insert into public.user_roles (user_id, role) values (new.id, 'user') on conflict do nothing;
  end if;
  return new;
end $$;

-- Ensure trigger exists
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
