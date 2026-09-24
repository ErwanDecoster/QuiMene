-- Doc 09 / P9 — deux corrections sur `cacompte_live_activity_tokens`.
--
-- 1. `match_id` était resté `not null` après le passage à `activity_key` (migration
--    `add_activity_key_to_cacompte_live_activity_tokens`) alors que l'app ne l'envoie plus
--    (`LiveActivityPushClient.registerToken`) : chaque enregistrement de jeton échouait, donc
--    aucun push n'atteignait jamais l'écran verrouillé d'un pair. Colonne conservée (lignes
--    existantes), seulement rendue facultative. Sans effet si déjà fait à la main.
alter table public.cacompte_live_activity_tokens alter column match_id drop not null;

-- 2. Des policies `using (true)` laissaient n'importe quel détenteur de la clé publishable lister,
--    modifier ou supprimer les jetons de push de tout le monde. L'app ne fait qu'enregistrer son
--    propre jeton ; les fonctions Edge utilisent `service_role` et contournent RLS. Accès direct
--    retiré, une seule fonction `security definer` pour l'enregistrement.
drop policy if exists "anon can select own cacompte_live_activity_tokens"
    on public.cacompte_live_activity_tokens;
drop policy if exists "anon can upsert own cacompte_live_activity_tokens"
    on public.cacompte_live_activity_tokens;
drop policy if exists "anon can update own cacompte_live_activity_tokens"
    on public.cacompte_live_activity_tokens;
drop policy if exists "anon can delete own cacompte_live_activity_tokens"
    on public.cacompte_live_activity_tokens;
-- Policy de test restée en production (créée à la main, absente des migrations).
drop policy if exists "insert_test" on public.cacompte_live_activity_tokens;

-- Rotation de jeton (`Activity.pushTokenUpdates`) : remplace l'ancien plutôt que d'en accumuler.
-- `updated_at` rafraîchi aussi, pour que `cacompte-live-activity-sweep` ne clôture pas une
-- Activity dont le jeton vient d'être (ré)enregistré.
create or replace function public.cacompte_register_live_activity_token(
    p_activity_key text,
    p_device_id text,
    p_push_token text
)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.cacompte_live_activity_tokens (activity_key, device_id, push_token)
    values (p_activity_key, p_device_id, p_push_token)
    on conflict (activity_key, device_id) do update
        set push_token = excluded.push_token, updated_at = now();
$$;

revoke all on function public.cacompte_register_live_activity_token(text, text, text) from public;
grant execute on function public.cacompte_register_live_activity_token(text, text, text) to anon;
