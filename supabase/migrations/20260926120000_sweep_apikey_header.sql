-- La tâche de balayage des Live Activities passe la clé publishable dans l'en-tête `apikey`
-- plutôt que `Authorization` : ce n'est pas un JWT, et Supabase réserve `Authorization` aux jetons
-- d'utilisateur (la vérification JWT des fonctions y accepte encore une clé, par compatibilité
-- seulement). Même changement dans les apps (`LiveActivityPushClient`). Planification inchangée :
-- toutes les 5 minutes.

select cron.unschedule(jobid) from cron.job where jobname = 'quimene-live-activity-sweep';

select cron.schedule(
    'quimene-live-activity-sweep',
    '*/5 * * * *',
    $$
    select net.http_post(
        url := 'https://hcjehnnvqmkdwirgpcgu.supabase.co/functions/v1/quimene-live-activity-sweep',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'apikey', 'sb_publishable_YhV5A3mH3aUCejLx1QC2OQ_Hc18SA_b'
        ),
        body := '{}'::jsonb
    );
    $$
);
