-- Doc 16, phase H — retrait de l'ancien partage, remplacé sur les deux apps :
-- - `quimene_open_games` (annonce d'une partie par l'hôte, doc 09) : remplacé par les sessions en
--   ligne (`create_quimene_sessions`, phase C) ;
-- - `quimene_shared_match_summaries` (résumés en clair poussés aux amis, doc 14) : remplacé par la
--   boîte aux lettres chiffrée (`create_quimene_match_mailbox`, phase E).
-- Plus aucune version de l'app n'appelle ces fonctions. Les parties déjà importées comme résumés
-- restent dans l'historique de chaque appareil (données locales, non touchées).

select cron.unschedule(jobid) from cron.job
where jobname in ('quimene-purge-open-games', 'quimene-purge-shared-match-summaries');

drop function if exists public.quimene_advertise_game(text, uuid, uuid, text, integer, text, text);
drop function if exists public.quimene_update_open_game(uuid, uuid, text, integer);
drop function if exists public.quimene_resolve_game(text);
drop function if exists public.quimene_close_open_game(uuid);
drop function if exists public.quimene_push_shared_match_summaries(jsonb);
drop function if exists public.quimene_fetch_shared_match_summaries(uuid[]);
drop function if exists public.quimene_delete_shared_match_summary(uuid, uuid);

drop table if exists public.quimene_open_games;
drop table if exists public.quimene_shared_match_summaries;
