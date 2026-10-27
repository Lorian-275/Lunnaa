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
