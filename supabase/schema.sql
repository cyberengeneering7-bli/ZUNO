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


-- =====================================================================
-- Listening analytics (anonymous device + optional authenticated user)
-- App events are inserted only through the RPC below; admins read them in
-- the separate Listening Analytics panel view.
-- =====================================================================
create table if not exists public.analytics_events (
  id            uuid primary key default gen_random_uuid(),
  device_id     text not null,
  user_id       uuid references auth.users(id) on delete set null,
  event_type    text not null,
  video_id      text,
  song_title    text not null default '',
  artist        text not null default '',
  song_link     text,
  image_url     text,
  source_type   text not null default 'unknown',
  source_name   text not null default '',
  source_id     text,
  category_name text,
  position_ms   bigint not null default 0,
  duration_ms   bigint not null default 0,
  session_id    text,
  app_version   text,
  model         text,
  metadata      jsonb not null default '{}'::jsonb,
  created_at    timestamptz not null default now()
);
create index if not exists analytics_events_created_idx on public.analytics_events(created_at desc);
create index if not exists analytics_events_song_idx on public.analytics_events(video_id, created_at desc);
create index if not exists analytics_events_device_idx on public.analytics_events(device_id, created_at desc);
create index if not exists analytics_events_type_idx on public.analytics_events(event_type, created_at desc);

create or replace function public.record_analytics_event(p_event jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_event is null
     or coalesce(p_event->>'device_id', '') !~ '^[A-Za-z0-9._:-]{8,120}$'
     or coalesce(p_event->>'event_type', '') = '' then
    return;
  end if;
  insert into public.analytics_events(
    device_id, user_id, event_type, video_id, song_title, artist, song_link,
    image_url, source_type, source_name, source_id, category_name, position_ms,
    duration_ms, session_id, app_version, model, metadata
  ) values (
    left(p_event->>'device_id', 120),
    auth.uid(),
    left(p_event->>'event_type', 40),
    nullif(left(p_event->>'video_id', 32), ''),
    left(coalesce(p_event->>'song_title', ''), 180),
    left(coalesce(p_event->>'artist', ''), 120),
    nullif(left(p_event->>'song_link', 500), ''),
    nullif(left(p_event->>'image_url', 500), ''),
    left(coalesce(p_event->>'source_type', 'unknown'), 40),
    left(coalesce(p_event->>'source_name', ''), 180),
    nullif(left(p_event->>'source_id', 120), ''),
    nullif(left(p_event->>'category_name', 120), ''),
    greatest(coalesce((p_event->>'position_ms')::bigint, 0), 0),
    greatest(coalesce((p_event->>'duration_ms')::bigint, 0), 0),
    nullif(left(p_event->>'session_id', 120), ''),
    nullif(left(p_event->>'app_version', 40), ''),
    nullif(left(p_event->>'model', 120), ''),
    coalesce(p_event->'metadata', '{}'::jsonb)
  );
exception when others then
  return;
end;
$$;

create or replace function public.zuno_analytics_summary(p_days integer default 30)
returns table(total_events bigint, unique_devices bigint, unique_songs bigint, listening_minutes numeric, downloads bigint, wishlist_adds bigint, shares bigint)
language sql
security definer
stable
set search_path = public
as $$
  select count(*)::bigint,
         count(distinct device_id)::bigint,
         count(distinct video_id)::bigint,
         round(coalesce(sum(case when event_type in ('progress','play_stop','play_complete') then greatest(duration_ms, 0) else 0 end), 0) / 60000.0, 1),
         count(*) filter (where event_type = 'download_completed')::bigint,
         count(*) filter (where event_type = 'wishlist_added')::bigint,
         count(*) filter (where event_type in ('share_opened','share_completed'))::bigint
    from public.analytics_events
   where public.is_admin()
     and created_at >= now() - make_interval(days => least(greatest(coalesce(p_days, 30), 1), 365));
$$;

create or replace function public.zuno_analytics_top(p_days integer default 30, p_limit integer default 20)
returns table(video_id text, song_title text, artist text, image_url text, song_link text, plays bigint, listening_minutes numeric, downloads bigint, wishlist_adds bigint, shares bigint, last_played_at timestamptz)
language sql
security definer
stable
set search_path = public
as $$
  select video_id,
         max(song_title) filter (where song_title <> ''),
         max(artist), max(image_url), max(song_link),
         count(*) filter (where event_type = 'play_start')::bigint,
         round(coalesce(sum(case when event_type in ('progress','play_stop','play_complete') then greatest(duration_ms, 0) else 0 end), 0) / 60000.0, 1),
         count(*) filter (where event_type = 'download_completed')::bigint,
         count(*) filter (where event_type = 'wishlist_added')::bigint,
         count(*) filter (where event_type in ('share_opened','share_completed'))::bigint,
         max(created_at)
   from public.analytics_events
   where public.is_admin()
     and video_id is not null
     and created_at >= now() - make_interval(days => least(greatest(coalesce(p_days, 30), 1), 365))
   group by video_id
   order by plays desc, listening_minutes desc, last_played_at desc
   limit least(greatest(coalesce(p_limit, 20), 1), 100);
$$;

revoke all on function public.record_analytics_event(jsonb) from public;
revoke all on function public.zuno_analytics_summary(integer) from public;
revoke all on function public.zuno_analytics_top(integer, integer) from public;
grant execute on function public.record_analytics_event(jsonb) to anon, authenticated;
grant execute on function public.zuno_analytics_summary(integer) to authenticated;
grant execute on function public.zuno_analytics_top(integer, integer) to authenticated;

alter table public.analytics_events enable row level security;
drop policy if exists analytics_events_admin_read on public.analytics_events;
create policy analytics_events_admin_read on public.analytics_events
  for select using (public.is_admin());
