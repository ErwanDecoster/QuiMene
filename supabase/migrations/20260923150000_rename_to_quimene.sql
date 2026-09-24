-- Renommage de l'app : « Ça Compte » devient « Qui Mène ? ». Tous les objets de la base passent
-- du préfixe `cacompte_` à `quimene_`. Les migrations précédentes gardent leur nom et leur
-- contenu : ce sont l'historique déjà appliqué, pas du code à maintenir.
--
-- Les fonctions `language sql` stockent leur corps en texte : renommer une table ne les met pas à
-- jour. Elles sont donc supprimées puis recréées sous leur nouveau nom, sur les nouvelles tables.
-- Idem pour les commandes des tâches pg_cron.

-- Tables, index, contraintes -------------------------------------------------------------------

alter table public.cacompte_open_games rename to quimene_open_games;
alter table public.quimene_open_games
    rename constraint cacompte_open_games_session_id_key to quimene_open_games_session_id_key;
alter index public.cacompte_open_games_created_at_idx rename to quimene_open_games_created_at_idx;

alter table public.cacompte_live_activity_tokens rename to quimene_live_activity_tokens;
alter index public.cacompte_live_activity_tokens_activity_key_idx
    rename to quimene_live_activity_tokens_activity_key_idx;

alter table public.cacompte_shared_match_summaries rename to quimene_shared_match_summaries;
alter index public.cacompte_shared_match_summaries_created_at_idx
    rename to quimene_shared_match_summaries_created_at_idx;

-- Clés primaires renommées d'après leur type : sur la base de production, certaines portent un
-- nom antérieur aux migrations (`open_games_pkey`, `live_activity_tokens_pkey`).
do $$
declare r record;
begin
    for r in
        select c.relname as tbl, k.conname
        from pg_constraint as k
        join pg_class as c on c.oid = k.conrelid
        join pg_namespace as n on n.oid = c.relnamespace
        where n.nspname = 'public' and k.contype = 'p'
            and c.relname in ('quimene_open_games', 'quimene_live_activity_tokens',
                'quimene_shared_match_summaries')
            and k.conname <> c.relname || '_pkey'
    loop
        execute format('alter table public.%I rename constraint %I to %I',
            r.tbl, r.conname, r.tbl || '_pkey');
    end loop;
end;
$$;

-- Anciennes fonctions ------------------------------------------------------------------------------

drop function if exists public.cacompte_push_shared_match_summaries(jsonb);
drop function if exists public.cacompte_fetch_shared_match_summaries(uuid[]);
drop function if exists public.cacompte_delete_shared_match_summary(uuid, uuid);
drop function if exists public.cacompte_advertise_game(text, uuid, uuid, text, integer, text, text);
drop function if exists public.cacompte_resolve_game(text);
drop function if exists public.cacompte_update_open_game(uuid, uuid, text, integer);
drop function if exists public.cacompte_close_open_game(uuid);
drop function if exists public.cacompte_register_live_activity_token(text, text, text);

-- Résumés de profils partagés (voir `secure_cacompte_shared_match_summaries`) --------------------

create or replace function public.quimene_push_shared_match_summaries(p_rows jsonb)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.quimene_shared_match_summaries (match_id, shared_profile_id, payload)
    select (r->>'match_id')::uuid, (r->>'shared_profile_id')::uuid, r->'payload'
    from jsonb_array_elements(p_rows) as r
    on conflict (match_id, shared_profile_id) do update set payload = excluded.payload;
$$;

create or replace function public.quimene_fetch_shared_match_summaries(p_shared_profile_ids uuid[])
returns table (match_id uuid, shared_profile_id uuid, payload jsonb)
language sql
stable
security definer
set search_path = ''
as $$
    select s.match_id, s.shared_profile_id, s.payload
    from public.quimene_shared_match_summaries as s
    where s.shared_profile_id = any (p_shared_profile_ids);
$$;

create or replace function public.quimene_delete_shared_match_summary(
    p_match_id uuid,
    p_shared_profile_id uuid
)
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.quimene_shared_match_summaries
    where match_id = p_match_id and shared_profile_id = p_shared_profile_id;
$$;

-- Découverte des parties partagées (voir `secure_cacompte_open_games`) ---------------------------

create or replace function public.quimene_advertise_game(
    p_pairing_code text,
    p_session_id uuid,
    p_match_id uuid,
    p_game_id text,
    p_participant_count integer,
    p_device_name text,
    p_platform text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.quimene_open_games as g
        (pairing_code, session_id, match_id, game_id, participant_count, device_name, platform)
    values
        (p_pairing_code, p_session_id, p_match_id, p_game_id, p_participant_count, p_device_name, p_platform)
    on conflict (pairing_code) do update set
        session_id = excluded.session_id,
        match_id = excluded.match_id,
        game_id = excluded.game_id,
        participant_count = excluded.participant_count,
        device_name = excluded.device_name,
        platform = excluded.platform,
        created_at = now()
    where g.session_id = excluded.session_id
        or g.created_at < now() - interval '24 hours';

    if not found then
        raise exception 'pairing_code_taken' using errcode = 'P0001';
    end if;
end;
$$;

create or replace function public.quimene_resolve_game(p_pairing_code text)
returns table (
    pairing_code text,
    session_id uuid,
    match_id uuid,
    game_id text,
    participant_count integer,
    device_name text,
    platform text
)
language sql
stable
security definer
set search_path = ''
as $$
    select g.pairing_code, g.session_id, g.match_id, g.game_id, g.participant_count,
        g.device_name, g.platform
    from public.quimene_open_games as g
    where g.pairing_code = p_pairing_code
        and g.created_at >= now() - interval '24 hours';
$$;

create or replace function public.quimene_update_open_game(
    p_session_id uuid,
    p_match_id uuid,
    p_game_id text,
    p_participant_count integer
)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.quimene_open_games
    set match_id = p_match_id, game_id = p_game_id, participant_count = p_participant_count
    where session_id = p_session_id;
$$;

create or replace function public.quimene_close_open_game(p_session_id uuid)
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.quimene_open_games where session_id = p_session_id;
$$;

-- Jetons Live Activity (voir `secure_cacompte_live_activity_tokens`) ------------------------------

create or replace function public.quimene_register_live_activity_token(
    p_activity_key text,
    p_device_id text,
    p_push_token text
)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.quimene_live_activity_tokens (activity_key, device_id, push_token)
    values (p_activity_key, p_device_id, p_push_token)
    on conflict (activity_key, device_id) do update
        set push_token = excluded.push_token, updated_at = now();
$$;

-- Droits -----------------------------------------------------------------------------------------

revoke all on function public.quimene_push_shared_match_summaries(jsonb) from public;
revoke all on function public.quimene_fetch_shared_match_summaries(uuid[]) from public;
revoke all on function public.quimene_delete_shared_match_summary(uuid, uuid) from public;
revoke all on function public.quimene_advertise_game(text, uuid, uuid, text, integer, text, text) from public;
revoke all on function public.quimene_resolve_game(text) from public;
revoke all on function public.quimene_update_open_game(uuid, uuid, text, integer) from public;
revoke all on function public.quimene_close_open_game(uuid) from public;
revoke all on function public.quimene_register_live_activity_token(text, text, text) from public;

grant execute on function public.quimene_push_shared_match_summaries(jsonb) to anon;
grant execute on function public.quimene_fetch_shared_match_summaries(uuid[]) to anon;
grant execute on function public.quimene_delete_shared_match_summary(uuid, uuid) to anon;
grant execute on function public.quimene_advertise_game(text, uuid, uuid, text, integer, text, text) to anon;
grant execute on function public.quimene_resolve_game(text) to anon;
grant execute on function public.quimene_update_open_game(uuid, uuid, text, integer) to anon;
grant execute on function public.quimene_close_open_game(uuid) to anon;
grant execute on function public.quimene_register_live_activity_token(text, text, text) to anon;

-- Purges pg_cron ---------------------------------------------------------------------------------

-- Par `jobid` plutôt que `cron.unschedule(nom)`, qui échoue si la tâche n'existe pas.
select cron.unschedule(jobid) from cron.job
where jobname in ('cacompte-purge-shared-match-summaries', 'cacompte-purge-open-games');

select cron.schedule(
    'quimene-purge-shared-match-summaries',
    '17 3 * * *',
    $$delete from public.quimene_shared_match_summaries where created_at < now() - interval '30 days'$$
);

select cron.schedule(
    'quimene-purge-open-games',
    '7 * * * *',
    $$delete from public.quimene_open_games where created_at < now() - interval '24 hours'$$
);
