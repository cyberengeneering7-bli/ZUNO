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
as $zuno$
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
$zuno$;

create or replace function public.zuno_analytics_summary(p_days integer default 30)
returns table(total_events bigint, unique_devices bigint, unique_songs bigint, listening_minutes numeric, downloads bigint, wishlist_adds bigint, shares bigint)
language sql
security definer
stable
set search_path = public
as $zuno$
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
$zuno$;

create or replace function public.zuno_analytics_top(p_days integer default 30, p_limit integer default 20)
returns table(video_id text, song_title text, artist text, image_url text, song_link text, plays bigint, listening_minutes numeric, downloads bigint, wishlist_adds bigint, shares bigint, last_played_at timestamptz)
language sql
security definer
stable
set search_path = public
as $zuno$
  with event_stats as (
    select video_id,
           max(song_title) filter (where song_title <> '') as song_title,
           max(artist) as artist, max(image_url) as image_url, max(song_link) as song_link,
           count(*) filter (where event_type = 'play_start')::bigint as event_plays,
           round(coalesce(sum(case when event_type in ('progress','play_stop','play_complete') then greatest(duration_ms, 0) else 0 end), 0) / 60000.0, 1) as listening_minutes,
           count(*) filter (where event_type = 'download_completed')::bigint as downloads,
           count(*) filter (where event_type = 'wishlist_added')::bigint as wishlist_adds,
           count(*) filter (where event_type in ('share_opened','share_completed'))::bigint as shares,
           max(created_at) as event_last_played_at
      from public.analytics_events
     where video_id is not null
       and created_at >= now() - make_interval(days => least(greatest(coalesce(p_days, 30), 1), 365))
     group by video_id
  ), legacy_stats as (
    select video_id, sum(play_count)::bigint as legacy_plays, max(last_played_at) as legacy_last_played_at
      from public.song_play_counts
     group by video_id
  ), catalog as (
    select video_id, title, artist, cover_url
      from public.songs
     where is_active = true and auto_hidden = false
    union all
    select e.video_id, coalesce(e.song_title, e.video_id), coalesce(e.artist, ''), e.image_url
      from event_stats e
     where not exists (select 1 from public.songs s where s.video_id = e.video_id)
  )
  select s.video_id,
         coalesce(e.song_title, s.title) as song_title,
         coalesce(e.artist, s.artist) as artist,
         coalesce(e.image_url, s.cover_url) as image_url,
         coalesce(e.song_link, 'https://www.youtube.com/watch?v=' || s.video_id) as song_link,
         greatest(coalesce(e.event_plays, 0), coalesce(l.legacy_plays, 0))::bigint as plays,
         coalesce(e.listening_minutes, 0)::numeric as listening_minutes,
         coalesce(e.downloads, 0)::bigint as downloads,
         coalesce(e.wishlist_adds, 0)::bigint as wishlist_adds,
         coalesce(e.shares, 0)::bigint as shares,
         greatest(e.event_last_played_at, l.legacy_last_played_at) as last_played_at
    from catalog s
    left join event_stats e on e.video_id = s.video_id
    left join legacy_stats l on l.video_id = s.video_id
   where public.is_admin()
     and (e.video_id is not null or l.video_id is not null)
   order by plays desc, listening_minutes desc, last_played_at desc
   limit least(greatest(coalesce(p_limit, 20), 1), 100);
$zuno$;

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


create or replace function public.zuno_analytics_devices(p_days integer default 30)
returns table(device_id text, device_model text, app_version text, events bigint, songs bigint, listening_minutes numeric, downloads bigint, likes bigint, shares bigint, last_seen timestamptz)
language sql security definer stable set search_path = public
as $zuno$
  select device_id, max(model) as device_model, max(app_version) as app_version, count(*)::bigint as events,
         count(distinct video_id)::bigint as songs,
         round(coalesce(sum(case when event_type in ('progress','play_stop','play_complete') then greatest(duration_ms, 0) else 0 end), 0) / 60000.0, 1) as listening_minutes,
         count(*) filter (where event_type = 'download_completed')::bigint as downloads,
         count(*) filter (where event_type in ('wishlist_added','like_added'))::bigint as likes,
         count(*) filter (where event_type in ('share_opened','share_completed'))::bigint as shares,
         max(created_at) as last_seen
    from public.analytics_events
   where public.is_admin()
     and created_at >= now() - make_interval(days => least(greatest(coalesce(p_days, 30), 1), 365))
   group by device_id
   order by last_seen desc;
$zuno$;

create or replace function public.zuno_clear_analytics(p_scope text default 'today', p_video_id text default null, p_device_id text default null)
returns bigint
language plpgsql security definer set search_path = public
as $zuno$
declare removed bigint := 0;
begin
  if not public.is_admin() then return 0; end if;
  delete from public.analytics_events
   where (p_video_id is null or video_id = p_video_id)
     and (p_device_id is null or device_id = p_device_id)
     and (p_scope = 'all' or (p_scope = 'today' and created_at >= current_date) or (p_scope = 'month' and created_at >= date_trunc('month', now())));
  get diagnostics removed = row_count;
  delete from public.song_play_counts
   where (p_video_id is null or video_id = p_video_id)
     and (p_scope = 'all' or (p_scope = 'today' and play_date = current_date) or (p_scope = 'month' and play_date >= date_trunc('month', current_date)::date));
  return removed;
end;
$zuno$;
revoke all on function public.zuno_analytics_devices(integer) from public;
revoke all on function public.zuno_clear_analytics(text,text,text) from public;
grant execute on function public.zuno_analytics_devices(integer) to authenticated;
grant execute on function public.zuno_clear_analytics(text,text,text) to authenticated;

create or replace function public.zuno_analytics_device_songs(p_device_id text, p_days integer default 30, p_limit integer default 50)
returns table(video_id text, song_title text, artist text, image_url text, song_link text, plays bigint, listening_minutes numeric, downloads bigint, likes bigint, shares bigint, last_played_at timestamptz)
language sql security definer stable set search_path = public
as $zuno$
  select video_id, max(song_title) filter (where song_title <> '') as song_title, max(artist) as artist, max(image_url) as image_url, max(song_link) as song_link,
         count(*) filter (where event_type = 'play_start')::bigint as plays,
         round(coalesce(sum(case when event_type in ('progress','play_stop','play_complete') then greatest(duration_ms, 0) else 0 end), 0) / 60000.0, 1) as listening_minutes,
         count(*) filter (where event_type = 'download_completed')::bigint as downloads,
         count(*) filter (where event_type in ('wishlist_added','like_added'))::bigint as likes,
         count(*) filter (where event_type in ('share_opened','share_completed'))::bigint as shares,
         max(created_at) as last_played_at
    from public.analytics_events
   where public.is_admin() and device_id = p_device_id
     and created_at >= now() - make_interval(days => least(greatest(coalesce(p_days, 30), 1), 365))
   group by video_id order by count(*) filter (where event_type = 'play_start') desc, max(created_at) desc
   limit least(greatest(coalesce(p_limit, 50), 1), 100);
$zuno$;
revoke all on function public.zuno_analytics_device_songs(text,integer,integer) from public;
grant execute on function public.zuno_analytics_device_songs(text,integer,integer) to authenticated;
