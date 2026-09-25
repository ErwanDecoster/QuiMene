-- Doc 16, phase E — historique partagé. Une partie terminée est déposée, complète (journal
-- d'événements et fiches de ses joueurs), dans la boîte aux lettres de chaque ami lié qui y a
-- joué ; il la récupère à sa prochaine ouverture de l'app. Remplace les résumés en clair du doc 14
-- (`quimene_shared_match_summaries`, retirés dans une migration ultérieure, une fois les deux
-- apps passées à la boîte aux lettres).
--
-- Chiffré de bout en bout (`MailboxCrypto`) : une boîte se désigne par une empreinte de
-- l'identifiant partageable de son profil (`mailbox_key`), le contenu est scellé avec une clé qui
-- en est dérivée. Le serveur ne voit ni identifiant, ni joueurs, ni scores.
--
-- Comme le reste du projet : aucune policy `anon`, tout passe par des fonctions `security
-- definer` qui exigent l'adresse de la boîte. Conservation : 14 jours au plus (doc 16).

create table if not exists public.quimene_match_mailbox (
    mailbox_key text not null check (mailbox_key ~ '^[0-9a-f]{64}$'),
    match_id uuid not null,
    ciphertext text not null,
    deposited_at timestamptz not null default now(),
    primary key (mailbox_key, match_id)
);

alter table public.quimene_match_mailbox enable row level security;

create index if not exists quimene_match_mailbox_deposited_idx
    on public.quimene_match_mailbox (deposited_at);

-- Déposer ----------------------------------------------------------------------------------------

-- Idempotent par `(mailbox_key, match_id)` : un dépôt retenté (réponse perdue) ou fait par
-- plusieurs appareils de la même partie remplace la copie précédente au lieu de la dupliquer.
-- Taille bornée (une partie, pas un fichier) : `event_too_large`.
create or replace function public.quimene_mailbox_deposit(p_items jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_item jsonb;
begin
    if jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) > 50 then
        raise exception 'invalid_items' using errcode = 'P0001';
    end if;
    for v_item in select * from jsonb_array_elements(p_items) loop
        if length(v_item ->> 'ciphertext') > 524288 then
            raise exception 'event_too_large' using errcode = 'P0001';
        end if;
        insert into public.quimene_match_mailbox as m (mailbox_key, match_id, ciphertext)
        values (v_item ->> 'mailbox_key', (v_item ->> 'match_id')::uuid, v_item ->> 'ciphertext')
        on conflict (mailbox_key, match_id) do update
            set ciphertext = excluded.ciphertext, deposited_at = now();
    end loop;
end;
$$;

-- Lire -------------------------------------------------------------------------------------------

-- Le contenu d'une seule boîte, jamais la liste des boîtes.
create or replace function public.quimene_mailbox_fetch(p_mailbox_key text)
returns table (mailbox_key text, match_id uuid, ciphertext text)
language sql
stable
security definer
set search_path = ''
as $$
    select m.mailbox_key, m.match_id, m.ciphertext
    from public.quimene_match_mailbox as m
    where m.mailbox_key = p_mailbox_key
    order by m.deposited_at
    limit 200;
$$;

-- Retirer ----------------------------------------------------------------------------------------

-- Par le destinataire, une fois la partie enregistrée chez lui.
create or replace function public.quimene_mailbox_remove(p_mailbox_key text, p_match_id uuid)
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.quimene_match_mailbox as m
    where m.mailbox_key = p_mailbox_key and m.match_id = p_match_id;
$$;

revoke all on table public.quimene_match_mailbox from anon, authenticated;
revoke all on function public.quimene_mailbox_deposit(jsonb) from public;
revoke all on function public.quimene_mailbox_fetch(text) from public;
revoke all on function public.quimene_mailbox_remove(text, uuid) from public;
grant execute on function public.quimene_mailbox_deposit(jsonb) to anon;
grant execute on function public.quimene_mailbox_fetch(text) to anon;
grant execute on function public.quimene_mailbox_remove(text, uuid) to anon;

-- Purge ------------------------------------------------------------------------------------------

select cron.unschedule(jobid) from cron.job where jobname = 'quimene-purge-mailbox';
select cron.schedule(
    'quimene-purge-mailbox',
    '37 * * * *',
    $$delete from public.quimene_match_mailbox where deposited_at < now() - interval '14 days'$$
);
