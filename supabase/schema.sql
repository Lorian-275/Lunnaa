-- Ejecutar una vez en el SQL editor de Supabase.

create table songs (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  artist text,
  album text,
  duration_ms int,
  r2_key text not null unique,
  created_at timestamptz default now()
);

create table playlists (
  id uuid primary key default gen_random_uuid(),
  owner uuid not null references auth.users on delete cascade default auth.uid(),
  name text not null,
  created_at timestamptz default now()
);

create table playlist_songs (
  playlist_id uuid references playlists on delete cascade,
  song_id uuid references songs on delete cascade,
  position int not null,
  primary key (playlist_id, song_id)
);

-- songs: solo lectura para usuarios con sesión; solo el script (service key) escribe.
alter table songs enable row level security;
create policy "leer canciones" on songs for select to authenticated using (true);

-- playlists: cada usuario solo ve y modifica las suyas.
alter table playlists enable row level security;
create policy "mis playlists" on playlists for all to authenticated
  using (owner = (select auth.uid()))
  with check (owner = (select auth.uid()));

alter table playlist_songs enable row level security;
create policy "canciones de mis playlists" on playlist_songs for all to authenticated
  using (exists (select 1 from playlists p where p.id = playlist_id and p.owner = (select auth.uid())))
  with check (exists (select 1 from playlists p where p.id = playlist_id and p.owner = (select auth.uid())));

-- ---------- Límites del plan gratis ----------
-- Tamaño de cada audio, para medir el uso de R2 sin listar el bucket.
alter table songs add column size_bytes bigint;

-- Contador mensual de uso de la función sign-urls.
create table usage_monthly (
  month date primary key,
  invocations int not null default 0,
  signed_urls int not null default 0
);
alter table usage_monthly enable row level security; -- sin políticas: solo service role

create function bump_usage(n int) returns usage_monthly
language sql security definer set search_path = public as $$
  insert into usage_monthly as u (month, invocations, signed_urls)
  values (date_trunc('month', now())::date, 1, n)
  on conflict (month) do update
    set invocations = u.invocations + 1, signed_urls = u.signed_urls + n
  returning *;
$$;
revoke execute on function bump_usage(int) from public, anon, authenticated;

-- Lo lee la pestaña de monitoreo del organizador (vía el conector de Supabase).
create function usage_stats() returns json
language sql security definer set search_path = public as $$
  select json_build_object(
    'month', to_char(now(), 'YYYY-MM'),
    'songs', (select count(*) from songs),
    'storage_bytes', (select coalesce(sum(size_bytes), 0) from songs),
    'db_bytes', pg_database_size(current_database()),
    'users', (select count(*) from auth.users),
    'active_users', (select count(*) from auth.users where last_sign_in_at >= date_trunc('month', now())),
    'invocations', coalesce((select invocations from usage_monthly where month = date_trunc('month', now())::date), 0),
    'signed_urls', coalesce((select signed_urls from usage_monthly where month = date_trunc('month', now())::date), 0)
  );
$$;
revoke execute on function usage_stats() from public, anon, authenticated;

-- Actualizaciones de la app: tools/release.py sube el APK al bucket 'apk' y registra la versión aquí.
create table public.app_releases (
  version_code int primary key,
  version_name text not null,
  apk_path text not null,
  notes text,
  created_at timestamptz not null default now()
);
alter table public.app_releases enable row level security;
create policy "usuarios leen releases" on public.app_releases for select to authenticated using (true);

-- Bucket privado; 50 MB es el máximo por archivo del plan gratis.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('apk', 'apk', false, 52428800, array['application/vnd.android.package-archive']);
create policy "usuarios descargan apk" on storage.objects for select to authenticated using (bucket_id = 'apk');
