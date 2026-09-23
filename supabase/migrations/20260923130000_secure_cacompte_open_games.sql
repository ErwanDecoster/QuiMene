-- Doc 09 — le modèle voulu est « connaître le code d'appairage suffit à rejoindre », mais les
-- policies `using (true)` en appliquaient un autre : n'importe quel détenteur de la clé
-- publishable (embarquée dans l'app) pouvait lister *tous* les codes ouverts — or la clé de
-- chiffrement des manches (`SessionCrypto`) dérive de ce code et de `session_id`, tous deux dans
-- la ligne. Il pouvait aussi réécrire ou supprimer la partie d'un autre (l'`upsert` sur
-- `pairing_code` écrasait la ligne existante). L'accès direct est retiré (RLS active, aucune
-- policy `anon`) et passe par quatre fonctions `security definer` qui exigent le code ou la
-- session en paramètre.

drop policy if exists "anon can read cacompte_open_games" on public.cacompte_open_games;
drop policy if exists "anon can insert cacompte_open_games" on public.cacompte_open_games;
drop policy if exists "anon can delete own cacompte_open_games" on public.cacompte_open_games;
drop policy if exists "anon can update cacompte_open_games" on public.cacompte_open_games;

-- Annonce d'une session. Rejouable par la même session (même `session_id`), mais un code déjà
-- pris par une autre session encore valide (< 24 h) est refusé au lieu d'être écrasé : l'hôte
-- échoue au démarrage du partage et l'utilisateur relance (nouveau code), plutôt que de détourner
-- silencieusement la partie de quelqu'un d'autre.
create or replace function public.cacompte_advertise_game(
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
    insert into public.cacompte_open_games as g
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

-- Une seule ligne, jamais la liste : c'est ce qui rend l'énumération impossible.
create or replace function public.cacompte_resolve_game(p_pairing_code text)
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
    from public.cacompte_open_games as g
    where g.pairing_code = p_pairing_code
        and g.created_at >= now() - interval '24 hours';
$$;

create or replace function public.cacompte_update_open_game(
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
    update public.cacompte_open_games
    set match_id = p_match_id, game_id = p_game_id, participant_count = p_participant_count
    where session_id = p_session_id;
$$;

create or replace function public.cacompte_close_open_game(p_session_id uuid)
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.cacompte_open_games where session_id = p_session_id;
$$;

revoke all on function public.cacompte_advertise_game(text, uuid, uuid, text, integer, text, text) from public;
revoke all on function public.cacompte_resolve_game(text) from public;
revoke all on function public.cacompte_update_open_game(uuid, uuid, text, integer) from public;
revoke all on function public.cacompte_close_open_game(uuid) from public;
grant execute on function public.cacompte_advertise_game(text, uuid, uuid, text, integer, text, text) to anon;
grant execute on function public.cacompte_resolve_game(text) to anon;
grant execute on function public.cacompte_update_open_game(uuid, uuid, text, integer) to anon;
grant execute on function public.cacompte_close_open_game(uuid) to anon;

-- Purge des sessions abandonnées (app tuée en plein partage), annoncée depuis la création de la
-- table mais jamais branchée. Horaire : un code expiré est déjà invisible pour
-- `cacompte_resolve_game`, la purge ne fait que libérer la place.
create extension if not exists pg_cron;

select cron.schedule(
    'cacompte-purge-open-games',
    '7 * * * *',
    $$delete from public.cacompte_open_games where created_at < now() - interval '24 hours'$$
);
