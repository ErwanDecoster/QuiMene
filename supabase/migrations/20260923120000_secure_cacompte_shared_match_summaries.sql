-- Doc 14 « Profils partagés » — le modèle de confiance voulu (« connaître l'identifiant partagé
-- suffit ») n'était pas celui appliqué : des policies `using (true)` laissaient n'importe quel
-- détenteur de la clé publishable (embarquée dans l'app) lister toute la table sans filtre
-- (pseudos, avatars, scores de tout le monde) et la vider. RLS ne sait pas exiger qu'une requête
-- filtre sur une colonne, donc l'accès direct est retiré (RLS reste active sans policy `anon` :
-- table fermée) et passe par trois fonctions `security definer` qui prennent l'identifiant en
-- paramètre — impossible de lire ou supprimer une boîte aux lettres sans en connaître l'UUID.

drop policy if exists "anon can read cacompte_shared_match_summaries"
    on public.cacompte_shared_match_summaries;
drop policy if exists "anon can insert cacompte_shared_match_summaries"
    on public.cacompte_shared_match_summaries;
drop policy if exists "anon can delete cacompte_shared_match_summaries"
    on public.cacompte_shared_match_summaries;

-- Même sémantique que l'ancien `upsert` sur `(match_id, shared_profile_id)` : un envoi retenté ne
-- duplique jamais la ligne (`SharedProfileTransport.push`). `created_at` n'est pas rafraîchi, pour
-- que la purge ci-dessous reste bornée même si un résumé est renvoyé en boucle.
create or replace function public.cacompte_push_shared_match_summaries(p_rows jsonb)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.cacompte_shared_match_summaries (match_id, shared_profile_id, payload)
    select (r->>'match_id')::uuid, (r->>'shared_profile_id')::uuid, r->'payload'
    from jsonb_array_elements(p_rows) as r
    on conflict (match_id, shared_profile_id) do update set payload = excluded.payload;
$$;

create or replace function public.cacompte_fetch_shared_match_summaries(p_shared_profile_ids uuid[])
returns table (match_id uuid, shared_profile_id uuid, payload jsonb)
language sql
stable
security definer
set search_path = ''
as $$
    select s.match_id, s.shared_profile_id, s.payload
    from public.cacompte_shared_match_summaries as s
    where s.shared_profile_id = any (p_shared_profile_ids);
$$;

create or replace function public.cacompte_delete_shared_match_summary(
    p_match_id uuid,
    p_shared_profile_id uuid
)
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.cacompte_shared_match_summaries
    where match_id = p_match_id and shared_profile_id = p_shared_profile_id;
$$;

revoke all on function public.cacompte_push_shared_match_summaries(jsonb) from public;
revoke all on function public.cacompte_fetch_shared_match_summaries(uuid[]) from public;
revoke all on function public.cacompte_delete_shared_match_summary(uuid, uuid) from public;
grant execute on function public.cacompte_push_shared_match_summaries(jsonb) to anon;
grant execute on function public.cacompte_fetch_shared_match_summaries(uuid[]) to anon;
grant execute on function public.cacompte_delete_shared_match_summary(uuid, uuid) to anon;

-- Doc 14 — purge de sécurité à 30 jours, restée « à brancher » jusqu'ici : un résumé que l'ami ne
-- vient jamais chercher ne doit pas rester indéfiniment sur le serveur. Quotidienne, s'appuie sur
-- `cacompte_shared_match_summaries_created_at_idx`.
create extension if not exists pg_cron;

select cron.schedule(
    'cacompte-purge-shared-match-summaries',
    '17 3 * * *',
    $$delete from public.cacompte_shared_match_summaries where created_at < now() - interval '30 days'$$
);
