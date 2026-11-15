-- ZUNO in-app update system. Safe to re-run.
create table if not exists public.app_updates (
  id uuid primary key default gen_random_uuid(),
  version_name text not null,
  version_code integer not null check (version_code > 0),
  apk_url text not null check (apk_url ~ '^https://'),
  sha256 text,
  notes text not null default '',
  force_update boolean not null default false,
  min_version_code integer not null default 0 check (min_version_code >= 0),
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);
create index if not exists app_updates_latest_idx on public.app_updates(is_active, version_code desc);
alter table public.devices add column if not exists app_version_code integer;

alter table public.app_updates enable row level security;
drop policy if exists app_updates_public_read on public.app_updates;
create policy app_updates_public_read on public.app_updates
  for select using (is_active = true);
drop policy if exists app_updates_admin_all on public.app_updates;
create policy app_updates_admin_all on public.app_updates
  for all using (public.is_admin()) with check (public.is_admin());

drop function if exists public.register_device(text, text, text, integer);
create or replace function public.register_device(
  p_device_id text,
  p_app_version text default null,
  p_model text default null,
  p_app_version_code integer default null
) returns json language plpgsql security definer set search_path = public as $$
declare d public.devices;
begin
  if p_device_id is null or length(p_device_id) < 8 or length(p_device_id) > 80 then
    return json_build_object('blocked', false);
  end if;
  insert into public.devices(device_id, app_version, app_version_code, model)
  values (p_device_id, left(p_app_version, 40), p_app_version_code, left(p_model, 80))
  on conflict (device_id) do update set
    last_seen = now(),
    app_version = coalesce(left(excluded.app_version, 40), public.devices.app_version),
    app_version_code = coalesce(excluded.app_version_code, public.devices.app_version_code),
    model = coalesce(left(excluded.model, 80), public.devices.model)
  returning * into d;
  return json_build_object('blocked', d.is_blocked, 'reason', coalesce(d.block_reason, ''));
end;
$$;
-- Keep the legacy three-argument RPC available for older APKs.
grant execute on function public.register_device(text, text, text) to anon, authenticated;
grant execute on function public.register_device(text, text, text, integer) to anon, authenticated;
