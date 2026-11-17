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
