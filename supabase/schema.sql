-- =====================================================================
--  ZUNO panel  -  Supabase schema
--  Supabase dashboard -> SQL Editor -> New query -> paste -> Run
--  Ek hi baar chalana hai. Dobara chalane par bhi error nahi aayega.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------
-- Admins  (jo log panel mein login karke edit kar sakte hain)
-- ---------------------------------------------------------------------
create table if not exists public.admins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  email   text,
  created_at timestamptz not null default now()
);

create or replace function public.is_admin()
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists (select 1 from public.admins where user_id = auth.uid());
$$;

-- ---------------------------------------------------------------------
-- Categories  (Home pe sections: "Sad 2026", "Rap", ...)
-- ---------------------------------------------------------------------
create table if not exists public.categories (
  id           uuid primary key default gen_random_uuid(),
  name         text not null,
  sort_order   int  not null default 0,
  is_active    boolean not null default true,
  show_on_home boolean not null default true,
  created_at   timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- Songs  (YouTube video ID se; audio khud host nahi hota)
-- ---------------------------------------------------------------------
create table if not exists public.songs (
  id          uuid primary key default gen_random_uuid(),
  video_id    text not null unique,
  title       text not null,
  artist      text not null default '',
  cover_url   text,
  category_id uuid references public.categories(id) on delete set null,
  tags        text[] not null default '{}',
  sort_order  int  not null default 0,
  is_active   boolean not null default true,
  auto_hidden boolean not null default false,
  broken_count int not null default 0,
  last_error_at timestamptz,
  created_at  timestamptz not null default now()
);
alter table public.songs
  add column if not exists is_pinned_banner boolean not null default false;
create index if not exists songs_category_idx on public.songs(category_id);
create index if not exists songs_active_idx   on public.songs(is_active);

-- ---------------------------------------------------------------------
-- Global song popularity (counts capped per device/song/day)
-- ---------------------------------------------------------------------
create table if not exists public.song_play_counts (
  video_id       text not null references public.songs(video_id) on delete cascade,
  device_key     text not null,
  play_date      date not null default current_date,
  play_count     int not null default 0 check (play_count between 0 and 20),
  last_played_at timestamptz not null default now(),
  primary key (video_id, device_key, play_date)
);
create index if not exists song_play_counts_recent_idx
  on public.song_play_counts(video_id, play_date desc);

-- ---------------------------------------------------------------------
-- Banners  (unlimited, pin, schedule)
-- action_type: playlist | song | link | none
-- ---------------------------------------------------------------------
create table if not exists public.banners (
  id           uuid primary key default gen_random_uuid(),
  title        text not null,
  subtitle     text not null default '',
  image_url    text,
  action_type  text not null default 'playlist'
               check (action_type in ('playlist','song','link','none')),
  action_value text not null default '',
  is_pinned    boolean not null default false,
  is_active    boolean not null default true,
  sort_order   int not null default 0,
  starts_at    timestamptz,
  ends_at      timestamptz,
  created_at   timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- Announcements  (popup message)
-- ---------------------------------------------------------------------
create table if not exists public.announcements (
  id         uuid primary key default gen_random_uuid(),
  title      text not null,
  body       text not null default '',
  link_url   text,
  is_active  boolean not null default true,
  show_once  boolean not null default true,
  expires_at timestamptz,
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- Together servers  (app yahan se server list leta hai)
-- ---------------------------------------------------------------------
create table if not exists public.together_servers (
  id        uuid primary key default gen_random_uuid(),
  name      text not null,
  url       text not null,
  priority  int  not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- App config (key/value)
--   maintenance        -> {"enabled": false, "message": ""}
--   auto_hide_threshold-> {"value": 3}
-- ---------------------------------------------------------------------
create table if not exists public.app_config (
  key   text primary key,
  value jsonb not null default '{}'::jsonb
);
insert into public.app_config(key, value) values
  ('maintenance',         '{"enabled": false, "message": ""}'),
  ('auto_hide_threshold', '{"value": 3}')
on conflict (key) do nothing;

-- ---------------------------------------------------------------------
-- Devices  (kaun active hai, kaun blocked)
-- Sirf random device ID + model + app version. Koi token / login nahi.
-- ---------------------------------------------------------------------
create table if not exists public.devices (
  device_id   text primary key,
  app_version text,
  model       text,
  first_seen  timestamptz not null default now(),
  last_seen   timestamptz not null default now(),
  is_blocked  boolean not null default false,
  block_reason text,
  note        text
);

-- ---------------------------------------------------------------------
-- Song error reports
-- ---------------------------------------------------------------------
create table if not exists public.song_reports (
  id         bigserial primary key,
  video_id   text not null,
  device_id  text not null,
  error      text,
  created_at timestamptz not null default now()
);
create index if not exists song_reports_video_idx on public.song_reports(video_id, created_at);

-- =====================================================================
--  Functions jo app (anon) bulata hai
-- =====================================================================

create or replace function public.register_device(
  p_device_id text, p_app_version text default null, p_model text default null)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare d public.devices;
begin
  if p_device_id is null or length(p_device_id) < 8 or length(p_device_id) > 80 then
    return json_build_object('blocked', false);
  end if;
  insert into public.devices(device_id, app_version, model)
  values (p_device_id, left(p_app_version, 40), left(p_model, 80))
  on conflict (device_id) do update
     set last_seen = now(),
         app_version = coalesce(left(excluded.app_version, 40), public.devices.app_version),
         model = coalesce(left(excluded.model, 80), public.devices.model)
  returning * into d;
  return json_build_object('blocked', d.is_blocked, 'reason', coalesce(d.block_reason, ''));
end;
$$;

create or replace function public.report_broken(
  p_video_id text, p_device_id text, p_error text default null)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  threshold int;
  uniq int;
begin
  if not exists (select 1 from public.songs where video_id = p_video_id) then
    return;
  end if;
  insert into public.song_reports(video_id, device_id, error)
  values (p_video_id, left(p_device_id, 80), left(p_error, 200));

  select coalesce((value->>'value')::int, 3) into threshold
    from public.app_config where key = 'auto_hide_threshold';
  if threshold is null then threshold := 3; end if;

  select count(distinct device_id) into uniq
    from public.song_reports
   where video_id = p_video_id and created_at > now() - interval '3 days';

  update public.songs
     set broken_count = uniq,
         last_error_at = now(),
         is_active   = case when uniq >= threshold then false else is_active end,
         auto_hidden = case when uniq >= threshold then true  else auto_hidden end
   where video_id = p_video_id;
end;
$$;

create or replace function public.record_song_play(p_video_id text, p_device_id text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_device_key text;
begin
  if p_video_id is null or p_video_id !~ '^[A-Za-z0-9_-]{11}$'
     or p_device_id is null or length(p_device_id) < 8 or length(p_device_id) > 80 then
    return;
  end if;
  if not exists (select 1 from public.songs where video_id = p_video_id and is_active = true) then
    return;
  end if;

  v_device_key := md5('zuno-play:' || p_device_id || current_date::text);
  insert into public.song_play_counts(video_id, device_key, play_date, play_count, last_played_at)
  values (p_video_id, v_device_key, current_date, 1, now())
  on conflict (video_id, device_key, play_date) do update
    set play_count = least(public.song_play_counts.play_count + 1, 20),
        last_played_at = now();
end;
$$;

create or replace function public.zuno_top_songs(p_limit integer default 20)
returns table (
  video_id text,
  title text,
  artist text,
  cover_url text,
  tags text[],
  sort_order integer,
  is_pinned_banner boolean,
  play_count bigint
)
language sql
stable
security definer
set search_path = public
as $$
  select s.video_id, s.title, s.artist, s.cover_url, s.tags, s.sort_order,
         s.is_pinned_banner,
         coalesce(sum(c.play_count), 0)::bigint as play_count
    from public.songs s
    left join public.song_play_counts c
      on c.video_id = s.video_id
     and c.play_date >= current_date - 29
   where s.is_active = true and s.auto_hidden = false
   group by s.id
   order by s.is_pinned_banner desc,
            case when s.is_pinned_banner then s.sort_order end asc nulls last,
            coalesce(sum(c.play_count), 0) desc,
            max(c.last_played_at) desc nulls last,
            s.created_at desc,
            s.sort_order asc
   limit least(greatest(coalesce(p_limit, 20), 1), 20);
$$;

revoke all on function public.record_song_play(text, text) from public;
revoke all on function public.zuno_top_songs(integer) from public;
grant execute on function public.record_song_play(text, text) to anon, authenticated;
grant execute on function public.zuno_top_songs(integer) to anon, authenticated;

grant execute on function public.register_device(text, text, text) to anon, authenticated;
grant execute on function public.report_broken(text, text, text)    to anon, authenticated;
grant execute on function public.is_admin() to anon, authenticated;

-- =====================================================================
--  Row Level Security  (bahar wala sirf padh sakta hai, likh nahi sakta)
-- =====================================================================
alter table public.admins           enable row level security;
alter table public.categories       enable row level security;
alter table public.songs            enable row level security;
alter table public.banners          enable row level security;
alter table public.announcements    enable row level security;
alter table public.together_servers enable row level security;
alter table public.app_config       enable row level security;
alter table public.devices          enable row level security;
alter table public.song_reports     enable row level security;
alter table public.song_play_counts enable row level security;

-- admins: sirf admin dekh sakta hai
drop policy if exists admins_admin_all on public.admins;
create policy admins_admin_all on public.admins
  for all using (public.is_admin()) with check (public.is_admin());

-- public read (app), admin full access
do $$
declare t text;
begin
  foreach t in array array['categories','songs','banners','announcements','together_servers']
  loop
    execute format('drop policy if exists %I_public_read on public.%I', t, t);
    execute format('create policy %I_public_read on public.%I for select using (is_active = true)', t, t);
    execute format('drop policy if exists %I_admin_all on public.%I', t, t);
    execute format('create policy %I_admin_all on public.%I for all using (public.is_admin()) with check (public.is_admin())', t, t);
  end loop;
end $$;

drop policy if exists app_config_public_read on public.app_config;
create policy app_config_public_read on public.app_config for select using (true);
drop policy if exists app_config_admin_all on public.app_config;
create policy app_config_admin_all on public.app_config
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists devices_admin_all on public.devices;
create policy devices_admin_all on public.devices
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists song_reports_admin_all on public.song_reports;
create policy song_reports_admin_all on public.song_reports
  for all using (public.is_admin()) with check (public.is_admin());

-- =====================================================================
--  Image storage (banner / cover upload)
-- =====================================================================
insert into storage.buckets (id, name, public)
values ('zuno-images', 'zuno-images', true)
on conflict (id) do nothing;

drop policy if exists zuno_images_read on storage.objects;
create policy zuno_images_read on storage.objects
  for select using (bucket_id = 'zuno-images');

drop policy if exists zuno_images_admin_write on storage.objects;
create policy zuno_images_admin_write on storage.objects
  for all using (bucket_id = 'zuno-images' and public.is_admin())
  with check (bucket_id = 'zuno-images' and public.is_admin());

-- =====================================================================
--  PEHLA ADMIN BANANA (SQL chalane ke BAAD):
--   1) Supabase -> Authentication -> Users -> Add user (email + password)
--   2) Neeche apni email daal ke ye chalao:
--
--   insert into public.admins(user_id, email)
--   select id, email from auth.users where email = 'TERI_EMAIL@gmail.com';
-- =====================================================================

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
