-- ZUNO: sirf About links ka update. Agar schema.sql pehle chala chuke ho toh ye chalao.
-- Supabase -> SQL Editor -> New query -> paste -> Run

-- =====================================================================
--  About page links (panel se badalte hain)
-- =====================================================================
create table if not exists public.about_links (
  id         uuid primary key default gen_random_uuid(),
  label      text not null,
  url        text not null,
  icon       text not null default 'link'
             check (icon in ('github','instagram','telegram','discord','website','link','info')),
  sort_order int  not null default 0,
  is_active  boolean not null default true,
  created_at timestamptz not null default now()
);
alter table public.about_links enable row level security;

drop policy if exists about_links_public_read on public.about_links;
create policy about_links_public_read on public.about_links for select using (is_active = true);
drop policy if exists about_links_admin_all on public.about_links;
create policy about_links_admin_all on public.about_links
  for all using (public.is_admin()) with check (public.is_admin());

-- developer naam / photo (optional). Khali ho toh app ka default dikhta hai.
insert into public.app_config(key, value) values ('about', '{"name": "", "photo_url": ""}')
on conflict (key) do nothing;
