-- Doc 16, phase B — sessions en ligne. Une session partagée ne dépend plus du téléphone du
-- créateur : son journal d'événements est stocké ici, et c'est lui qui fait foi pour tous les
-- appareils. Chiffré de bout en bout (clé dérivée du code d'appairage et de `session_id`,
-- `SessionCrypto`) : le serveur ne voit ni pseudos ni scores, seulement des blobs, leur ordre et
-- l'appareil qui les a ajoutés.
--
-- Un seul journal par session, toutes parties confondues : une nouvelle partie commence par son
-- événement `matchCreated` (n'importe quel participant peut la lancer, doc 16), chaque appareil
-- rejoue les événements de la partie courante. Numérotation continue par session (`seq`), sans
-- trou : un ajout annonce le numéro qu'il attend, refusé si quelqu'un l'a devancé — l'écrasement
-- d'une manche devient impossible par construction.
--
-- Comme le reste du projet (`secure_cacompte_open_games`) : aucune policy `anon`, tout passe par
-- des fonctions `security definer` qui exigent un code ou un identifiant de session.

create table if not exists public.quimene_sessions (
    session_id uuid primary key,
    pairing_code text not null unique,
    owner_device_id text not null,
    allows_contributors boolean not null default true,
    created_at timestamptz not null default now(),
    last_activity_at timestamptz not null default now(),
    -- Posé par le créateur à l'arrêt : plus aucun ajout, lecture encore possible 24 h pour que
    -- les appareils en veille rattrapent la fin avant la purge.
    closed_at timestamptz
);

create table if not exists public.quimene_session_events (
    session_id uuid not null references public.quimene_sessions (session_id) on delete cascade,
    seq bigint not null,
    event_id uuid not null,
    match_id uuid not null,
    device_id text not null,
    -- `WireCodec`/`SessionCrypto` : événement horodaté, scellé (AES-GCM), encodé en base64.
    ciphertext text not null,
    created_at timestamptz not null default now(),
    primary key (session_id, seq),
    unique (session_id, event_id)
);

alter table public.quimene_sessions enable row level security;
alter table public.quimene_session_events enable row level security;

create index if not exists quimene_sessions_last_activity_idx
    on public.quimene_sessions (last_activity_at);

-- Ouvrir -----------------------------------------------------------------------------------------

-- Rejouable par la même session ; un code déjà pris par une autre session encore vivante est
-- refusé (le créateur relance, nouveau code), jamais écrasé.
create or replace function public.quimene_session_open(
    p_session_id uuid,
    p_pairing_code text,
    p_owner_device_id text,
    p_allows_contributors boolean
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    delete from public.quimene_sessions as s
    where s.pairing_code = p_pairing_code
        and s.session_id <> p_session_id
        and (s.closed_at is not null or s.last_activity_at < now() - interval '14 days');

    insert into public.quimene_sessions as s
        (session_id, pairing_code, owner_device_id, allows_contributors)
    values (p_session_id, p_pairing_code, p_owner_device_id, p_allows_contributors)
    on conflict (session_id) do update
        set allows_contributors = excluded.allows_contributors, last_activity_at = now()
        where s.owner_device_id = excluded.owner_device_id;
exception
    when unique_violation then
        raise exception 'pairing_code_taken' using errcode = 'P0001';
end;
$$;

-- Trouver par code ---------------------------------------------------------------------------------

-- Une ligne, jamais la liste. `last_seq` permet au nouvel arrivant de savoir d'emblée jusqu'où
-- lire ; une session fermée n'est plus trouvable (on ne rejoint pas une session terminée).
create or replace function public.quimene_session_resolve(p_pairing_code text)
returns table (
    session_id uuid,
    owner_device_id text,
    allows_contributors boolean,
    last_seq bigint
)
language sql
stable
security definer
set search_path = ''
as $$
    select s.session_id, s.owner_device_id, s.allows_contributors,
        coalesce((select max(e.seq) from public.quimene_session_events as e
            where e.session_id = s.session_id), 0)
    from public.quimene_sessions as s
    where s.pairing_code = p_pairing_code
        and s.closed_at is null
        and s.last_activity_at >= now() - interval '14 days';
$$;

-- Ajouter ----------------------------------------------------------------------------------------

-- `p_expected_seq` : le numéro que l'appareil croit être le suivant (dernier connu + 1). S'il ne
-- l'est plus, `stale_seq` : l'appareil relit depuis son dernier numéro, revalide sa manche contre
-- l'état à jour, puis réessaie. Idempotent sur `p_event_id` : un envoi retenté après une réponse
-- perdue renvoie le numéro déjà attribué au lieu d'échouer ou de dupliquer.
create or replace function public.quimene_session_append(
    p_session_id uuid,
    p_expected_seq bigint,
    p_event_id uuid,
    p_match_id uuid,
    p_device_id text,
    p_ciphertext text
)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_existing bigint;
    v_next bigint;
begin
    select e.seq into v_existing from public.quimene_session_events as e
    where e.session_id = p_session_id and e.event_id = p_event_id;
    if v_existing is not null then
        return v_existing;
    end if;

    -- Verrou de la ligne de session : deux ajouts simultanés sont sérialisés, le second voit le
    -- numéro pris par le premier et reçoit `stale_seq`.
    perform 1 from public.quimene_sessions as s
    where s.session_id = p_session_id and s.closed_at is null
    for update;
    if not found then
        raise exception 'session_closed' using errcode = 'P0001';
    end if;

    if length(p_ciphertext) > 65536 then
        raise exception 'event_too_large' using errcode = 'P0001';
    end if;

    select coalesce(max(e.seq), 0) + 1 into v_next from public.quimene_session_events as e
    where e.session_id = p_session_id;
    if p_expected_seq <> v_next then
        raise exception 'stale_seq' using errcode = 'P0001', detail = v_next::text;
    end if;

    insert into public.quimene_session_events
        (session_id, seq, event_id, match_id, device_id, ciphertext)
    values (p_session_id, v_next, p_event_id, p_match_id, p_device_id, p_ciphertext);

    update public.quimene_sessions set last_activity_at = now() where session_id = p_session_id;
    return v_next;
end;
$$;

-- Lire -------------------------------------------------------------------------------------------

-- Rattrapage : tout ce qui suit `p_after_seq`, par lots de 500 au plus (l'appareil relit tant
-- qu'un lot est plein). Fonctionne encore 24 h après la fermeture.
create or replace function public.quimene_session_events_after(
    p_session_id uuid,
    p_after_seq bigint
)
returns table (
    seq bigint,
    event_id uuid,
    match_id uuid,
    device_id text,
    ciphertext text,
    created_at timestamptz
)
language sql
stable
security definer
set search_path = ''
as $$
    select e.seq, e.event_id, e.match_id, e.device_id, e.ciphertext, e.created_at
    from public.quimene_session_events as e
    where e.session_id = p_session_id and e.seq > p_after_seq
    order by e.seq
    limit 500;
$$;

-- Fermer -----------------------------------------------------------------------------------------

-- Doc 16 : seul le créateur arrête la session. `owner_device_id` est déclaré par l'appareil (pas
-- de compte) — même seuil de confiance que le reste : il faut déjà le code et la clé pour lire.
create or replace function public.quimene_session_close(
    p_session_id uuid,
    p_owner_device_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.quimene_sessions
    set closed_at = now()
    where session_id = p_session_id and owner_device_id = p_owner_device_id and closed_at is null;
    if not found then
        raise exception 'not_session_owner' using errcode = 'P0001';
    end if;
end;
$$;

-- Notification -----------------------------------------------------------------------------------

-- Prévient les appareils abonnés au canal de la session (`session:<id>`, déjà utilisé pour la
-- présence) qu'un événement vient d'arriver : seulement son numéro, jamais son contenu. Chacun
-- relit ensuite via `quimene_session_events_after`.
create or replace function public.quimene_session_notify_event()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform realtime.send(
        jsonb_build_object('seq', new.seq, 'match_id', new.match_id),
        'event',
        'session:' || new.session_id::text,
        false
    );
    return new;
end;
$$;

drop trigger if exists quimene_session_events_notify on public.quimene_session_events;
create trigger quimene_session_events_notify
    after insert on public.quimene_session_events
    for each row execute function public.quimene_session_notify_event();

-- Droits -----------------------------------------------------------------------------------------

revoke all on function public.quimene_session_open(uuid, text, text, boolean) from public;
revoke all on function public.quimene_session_resolve(text) from public;
revoke all on function public.quimene_session_append(uuid, bigint, uuid, uuid, text, text) from public;
revoke all on function public.quimene_session_events_after(uuid, bigint) from public;
revoke all on function public.quimene_session_close(uuid, text) from public;
revoke all on function public.quimene_session_notify_event() from public;
grant execute on function public.quimene_session_open(uuid, text, text, boolean) to anon;
grant execute on function public.quimene_session_resolve(text) to anon;
grant execute on function public.quimene_session_append(uuid, bigint, uuid, uuid, text, text) to anon;
grant execute on function public.quimene_session_events_after(uuid, bigint) to anon;
grant execute on function public.quimene_session_close(uuid, text) to anon;

-- Purge ------------------------------------------------------------------------------------------

-- Doc 16 : 14 jours après la dernière activité ; 24 h après la fermeture par le créateur.
select cron.unschedule(jobid) from cron.job where jobname = 'quimene-purge-sessions';
select cron.schedule(
    'quimene-purge-sessions',
    '23 * * * *',
    $$delete from public.quimene_sessions
      where last_activity_at < now() - interval '14 days'
         or closed_at < now() - interval '24 hours'$$
);
