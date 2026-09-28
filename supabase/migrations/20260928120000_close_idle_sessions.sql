-- Doc 16 — une session se termine d'elle-même après 6 h sans activité (aucune manche, aucune
-- nouvelle partie) : un créateur qui oublie de l'arrêter ne la laisse plus traîner des jours chez
-- tous les participants. Toute fermeture, par le créateur ou faute d'activité, prévient les
-- appareils connectés, et chacun peut vérifier au rattrapage si sa session est encore ouverte —
-- auparavant, un appareil ne l'apprenait qu'en essayant d'ajouter une manche (`session_closed`).

-- Statut -----------------------------------------------------------------------------------------

-- Fermée (par le créateur ou faute d'activité) ou déjà purgée.
create or replace function public.quimene_session_is_closed(p_session_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(
        (select s.closed_at is not null
         from public.quimene_sessions as s
         where s.session_id = p_session_id),
        true
    );
$$;

-- Notification de fermeture ----------------------------------------------------------------------

-- Même canal et même événement que `quimene_session_notify_event` : chaque appareil connecté
-- rattrape (les versions qui ignorent la fermeture aussi, sans effet), puis vérifie le statut.
create or replace function public.quimene_session_notify_closed()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform realtime.send(
        jsonb_build_object('closed', true),
        'event',
        'session:' || new.session_id::text,
        false
    );
    return new;
end;
$$;

drop trigger if exists quimene_sessions_notify_closed on public.quimene_sessions;
create trigger quimene_sessions_notify_closed
    after update of closed_at on public.quimene_sessions
    for each row
    when (old.closed_at is null and new.closed_at is not null)
    execute function public.quimene_session_notify_closed();

-- Fin automatique --------------------------------------------------------------------------------

create or replace function public.quimene_session_close_idle()
returns void
language sql
security definer
set search_path = ''
as $$
    update public.quimene_sessions
    set closed_at = now()
    where closed_at is null and last_activity_at < now() - interval '6 hours';
$$;

-- Droits -----------------------------------------------------------------------------------------

revoke all on function public.quimene_session_is_closed(uuid) from public;
revoke all on function public.quimene_session_notify_closed() from public, anon, authenticated;
revoke all on function public.quimene_session_close_idle() from public, anon, authenticated;
grant execute on function public.quimene_session_is_closed(uuid) to anon;

-- Toutes les 10 minutes : une session inactive depuis 6 h est fermée au plus 10 minutes plus
-- tard, puis purgée 24 h après (`quimene-purge-sessions`).
select cron.unschedule(jobid) from cron.job where jobname = 'quimene-close-idle-sessions';
select cron.schedule(
    'quimene-close-idle-sessions',
    '*/10 * * * *',
    $$select public.quimene_session_close_idle()$$
);
