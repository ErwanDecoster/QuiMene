-- Doc 16 — fin automatique des sessions inactives (migration `close_idle_sessions`). Même principe
-- que `quimene_sessions_test.sql` : ne modifie rien, à exécuter dans une transaction annulée, par
-- exemple
--
--   { echo "begin;"; cat supabase/migrations/20260928120000_close_idle_sessions.sql \
--       supabase/tests/quimene_idle_sessions_test.sql; echo "rollback;"; } > /tmp/dry.sql
--   supabase db query --linked -f /tmp/dry.sql
--
-- (sans la migration une fois celle-ci appliquée). Chaque ligne du résultat doit avoir ok = true.

create temp table t_results (n serial, label text, ok boolean, detail text);

do $$
declare
    idle uuid := 'cccccccc-0000-0000-0000-000000000001';
    active uuid := 'cccccccc-0000-0000-0000-000000000002';
    gone uuid := 'cccccccc-0000-0000-0000-000000000003';
    b boolean; v bigint; msg text;
begin
    perform public.quimene_session_open(idle, '999221', 'devA', true);
    perform public.quimene_session_open(active, '999222', 'devA', true);
    update public.quimene_sessions set last_activity_at = now() - interval '6 hours 1 minute'
    where session_id = idle;
    update public.quimene_sessions set last_activity_at = now() - interval '5 hours 59 minutes'
    where session_id = active;

    b := public.quimene_session_is_closed(idle);
    insert into t_results(label, ok, detail) values ('open session not closed', not b, b::text);
    b := public.quimene_session_is_closed(gone);
    insert into t_results(label, ok, detail) values ('unknown session reads as closed', b, b::text);

    perform public.quimene_session_close_idle();
    b := public.quimene_session_is_closed(idle);
    insert into t_results(label, ok, detail) values ('idle 6 h closed', b, b::text);
    b := public.quimene_session_is_closed(active);
    insert into t_results(label, ok, detail) values ('active session kept', not b, b::text);

    begin
        v := public.quimene_session_append(idle, 1, gen_random_uuid(), gen_random_uuid(), 'devB', 'CIPHER');
        insert into t_results(label, ok, detail) values ('append after idle close refused', false, 'accepted');
    exception when others then
        get stacked diagnostics msg = message_text;
        insert into t_results(label, ok, detail) values ('append after idle close refused', msg = 'session_closed', msg);
    end;

    select count(*) into v from public.quimene_session_resolve('999221');
    insert into t_results(label, ok, detail) values ('idle-closed session not resolvable', v = 0, v::text);

    perform public.quimene_session_close(active, 'devA');
    b := public.quimene_session_is_closed(active);
    insert into t_results(label, ok, detail) values ('closed by owner', b, b::text);

    select count(*) into v from realtime.messages
    where topic in ('session:' || idle::text, 'session:' || active::text) and event = 'event';
    insert into t_results(label, ok, detail) values ('each closure notified once', v = 2, v::text);
end;
$$;

select json_agg(json_build_object('label', label, 'ok', ok, 'detail', detail) order by n) as results from t_results;
