-- Doc 09 « Fin de partie » — `quimene-live-activity-sweep` termine les Live Activities inactives
-- depuis 30 minutes et supprime leurs jetons (durée annoncée par la politique de confidentialité).
-- Elle doit être appelée périodiquement, mais aucun déclencheur n'avait jamais été créé : la
-- fonction n'a jamais tourné. Appel toutes les 5 minutes via pg_cron + pg_net.
--
-- La clé passée est la clé publishable, déjà publique (embarquée dans l'app) : aucun secret ici.
-- La fonction elle-même travaille avec `service_role`, fournie par le runtime Edge Functions.
create extension if not exists pg_net with schema extensions;

select cron.unschedule(jobid) from cron.job where jobname = 'quimene-live-activity-sweep';

select cron.schedule(
    'quimene-live-activity-sweep',
    '*/5 * * * *',
    $$
    select net.http_post(
        url := 'https://hcjehnnvqmkdwirgpcgu.supabase.co/functions/v1/quimene-live-activity-sweep',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'Authorization', 'Bearer sb_publishable_YhV5A3mH3aUCejLx1QC2OQ_Hc18SA_b'
        ),
        body := '{}'::jsonb
    );
    $$
);
