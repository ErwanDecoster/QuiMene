-- Doc 14 « Profils partagés », phase 2 — boîte aux lettres transitoire : un résumé de partie
-- terminée, pour chaque participant dont la fiche est liée à l'installation de quelqu'un d'autre
-- (doc 14, phase 1). Retirée dès que l'appareil concerné l'a récupérée
-- (`SharedProfileSyncCoordinator`) — jamais une copie durable, même discipline que
-- `cacompte_open_games`. `payload` porte le classement complet (pseudo, avatar léger, rang,
-- score) — jamais le journal d'événements, que le destinataire n'a pas besoin de rejouer
-- (`LeaderboardRepository`/`ProfileRepository` ne lisent que le classement final).
create table if not exists public.cacompte_shared_match_summaries (
    match_id uuid not null,
    shared_profile_id uuid not null,
    payload jsonb not null,
    created_at timestamptz not null default now(),
    primary key (match_id, shared_profile_id)
);

alter table public.cacompte_shared_match_summaries enable row level security;

-- Doc utilisateur — même modèle de confiance que `cacompte_open_games` : pas d'authentification
-- Supabase dans ce projet, connaître l'identifiant partagé (un UUID transmis par QR, jamais
-- énumérable ni affiché en clair) suffit à lire/écrire sa propre boîte aux lettres.
create policy "anon can read cacompte_shared_match_summaries" on public.cacompte_shared_match_summaries
    for select
    to anon
    using (true);

create policy "anon can insert cacompte_shared_match_summaries" on public.cacompte_shared_match_summaries
    for insert
    to anon
    with check (true);

create policy "anon can delete cacompte_shared_match_summaries" on public.cacompte_shared_match_summaries
    for delete
    to anon
    using (true);

-- Doc utilisateur — filet de sécurité si l'ami ne relance jamais l'app pour récupérer son résumé.
-- Fenêtre plus généreuse que `cacompte_open_games` (24h) : un ami peut rester hors ligne des
-- semaines, contrairement à une session de partage qui ne dure qu'une soirée. Purge à brancher
-- séparément (même patron que `cacompte-live-activity-sweep`, pas un fichier de ce dépôt) : cet
-- index ne fait que la rendre bon marché le jour où elle existe.
create index if not exists cacompte_shared_match_summaries_created_at_idx
    on public.cacompte_shared_match_summaries (created_at);
