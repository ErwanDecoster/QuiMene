-- Remontée « la Live Activity ne disparaît jamais » : `quimene-live-activity-sweep` termine une
-- activité inactive en renvoyant `last_content_state`, mais ce contenu n'était enregistré qu'au
-- premier push réussi. Pour un jeton inscrit sans push ensuite, le balayage envoyait `{}`, qu'iOS
-- ignore (contenu non décodable) : l'activité ne se terminait jamais. L'app envoie désormais le
-- contenu affiché avec son jeton.
--
-- Nouveau paramètre facultatif (défaut null) : un build qui appelle encore la version à trois
-- paramètres continue de fonctionner.
drop function if exists public.quimene_register_live_activity_token(text, text, text);

create or replace function public.quimene_register_live_activity_token(
    p_activity_key text,
    p_device_id text,
    p_push_token text,
    p_content_state jsonb default null
)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.quimene_live_activity_tokens as t
        (activity_key, device_id, push_token, last_content_state)
    values (p_activity_key, p_device_id, p_push_token, p_content_state)
    on conflict (activity_key, device_id) do update
        set push_token = excluded.push_token,
            last_content_state = coalesce(excluded.last_content_state, t.last_content_state),
            updated_at = now();
$$;

revoke all on function public.quimene_register_live_activity_token(text, text, text, jsonb) from public;
grant execute on function public.quimene_register_live_activity_token(text, text, text, jsonb) to anon;
