-- AVI V1 - esquema de desarrollo.
-- IMPORTANTE: estas políticas permiten leer e insertar con la publishable/anon key.
-- Antes de producción, agregar Supabase Auth y políticas por usuario/rol.

create table if not exists public.registros (
    id uuid primary key default gen_random_uuid(),
    tipo text not null default 'FUGA',
    via integer not null check (via > 0 and via <= 9999),
    placa text null,
    texto_reconocido text null,
    creado_en timestamptz not null default now()
);

alter table public.registros enable row level security;

grant usage on schema public to anon, authenticated;
grant select, insert on table public.registros to anon, authenticated;

drop policy if exists "avi_dev_select_registros" on public.registros;
create policy "avi_dev_select_registros"
on public.registros for select to anon, authenticated using (true);

drop policy if exists "avi_dev_insert_registros" on public.registros;
create policy "avi_dev_insert_registros"
on public.registros for insert to anon, authenticated
with check (
    tipo = 'FUGA'
    and via > 0
    and via <= 9999
    and (placa is null or length(placa) <= 10)
);

create index if not exists idx_registros_creado_en
on public.registros (creado_en desc);
