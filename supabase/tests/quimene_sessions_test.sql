-- Doc 16, phase B — scénario de vérification des sessions en ligne. Ne modifie rien : à exécuter
-- dans une transaction annulée, avant ou après la migration, par exemple
--
--   { echo "begin;"; cat supabase/migrations/20260924150000_create_quimene_sessions.sql \
--       supabase/tests/quimene_sessions_test.sql; echo "rollback;"; } > /tmp/dry.sql
--   supabase db query --linked -f /tmp/dry.sql
--
-- (sans la migration une fois celle-ci appliquée). Chaque ligne du résultat doit avoir ok = true.

create temp table t_results (n serial, label text, ok boolean, detail text);

do $$
declare
    s1 uuid := 'aaaaaaaa-0000-0000-0000-000000000001';
    s2 uuid := 'aaaaaaaa-0000-0000-0000-000000000002';
    m1 uuid := 'bbbbbbbb-0000-0000-0000-000000000001';
    e1 uuid := gen_random_uuid(); e2 uuid := gen_random_uuid(); e3 uuid := gen_random_uuid();
    r record; v bigint; msg text;
begin
    perform public.quimene_session_open(s1, '999111', 'devA', true);
    select * into r from public.quimene_session_resolve('999111');
    insert into t_results(label, ok, detail) values ('resolve after open', r.session_id = s1 and r.last_seq = 0, r::text);

    v := public.quimene_session_append(s1, 1, e1, m1, 'devA', 'CIPHER1');
    insert into t_results(label, ok, detail) values ('append #1', v = 1, v::text);
    v := public.quimene_session_append(s1, 2, e2, m1, 'devB', 'CIPHER2');
    insert into t_results(label, ok, detail) values ('append #2 by another device', v = 2, v::text);

    begin
        v := public.quimene_session_append(s1, 2, e3, m1, 'devC', 'CIPHER3');
        insert into t_results(label, ok, detail) values ('stale append refused', false, 'accepted as ' || v);
    exception when others then
        get stacked diagnostics msg = message_text;
        insert into t_results(label, ok, detail) values ('stale append refused', msg = 'stale_seq', msg);
    end;

    v := public.quimene_session_append(s1, 99, e2, m1, 'devB', 'CIPHER2');
    insert into t_results(label, ok, detail) values ('retry is idempotent', v = 2, v::text);

    select count(*) into v from public.quimene_session_events_after(s1, 0);
    insert into t_results(label, ok, detail) values ('events_after(0) = 2', v = 2, v::text);
    select count(*) into v from public.quimene_session_events_after(s1, 1);
    insert into t_results(label, ok, detail) values ('events_after(1) = 1', v = 1, v::text);

    begin
        perform public.quimene_session_open(s2, '999111', 'devZ', true);
        insert into t_results(label, ok, detail) values ('code taken refused', false, 'accepted');
    exception when others then
        get stacked diagnostics msg = message_text;
        insert into t_results(label, ok, detail) values ('code taken refused', msg = 'pairing_code_taken', msg);
    end;

    begin
        perform public.quimene_session_close(s1, 'devB');
        insert into t_results(label, ok, detail) values ('close by non-owner refused', false, 'accepted');
    exception when others then
        get stacked diagnostics msg = message_text;
        insert into t_results(label, ok, detail) values ('close by non-owner refused', msg = 'not_session_owner', msg);
    end;

    perform public.quimene_session_close(s1, 'devA');
    begin
        v := public.quimene_session_append(s1, 3, e3, m1, 'devA', 'CIPHER3');
        insert into t_results(label, ok, detail) values ('append after close refused', false, 'accepted');
    exception when others then
        get stacked diagnostics msg = message_text;
        insert into t_results(label, ok, detail) values ('append after close refused', msg = 'session_closed', msg);
    end;

    select count(*) into v from public.quimene_session_resolve('999111');
    insert into t_results(label, ok, detail) values ('closed session not resolvable', v = 0, v::text);
    select count(*) into v from public.quimene_session_events_after(s1, 0);
    insert into t_results(label, ok, detail) values ('closed session still readable', v = 2, v::text);

    perform public.quimene_session_open(s2, '999111', 'devZ', true);
    select * into r from public.quimene_session_resolve('999111');
    insert into t_results(label, ok, detail) values ('code reusable after close', r.session_id = s2, r::text);
end;
$$;

select json_agg(json_build_object('label', label, 'ok', ok, 'detail', detail) order by n) as results from t_results;
