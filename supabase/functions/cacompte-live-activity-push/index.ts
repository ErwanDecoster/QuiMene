// Doc utilisateur P9 — seul moyen fourni par Apple de mettre à jour une Live Activity (écran
// verrouillé / Dynamic Island) pendant que l'app est suspendue en arrière-plan : un push APNs
// dédié (`apns-push-type: liveactivity`), envoyé ici depuis un serveur plutôt que depuis l'app
// elle-même (qui ne tourne justement plus). Appelée par l'hôte (le seul appareil qui fait foi sur
// le journal) juste après chaque `syncHostLog`, jamais par les pairs.
//
// Doc 09 « Fin de partie » — routée par `activityKey` (session de partage si active, sinon
// partie), pas par `matchID` : c'est ce qui permet à un changement de partie au sein d'une même
// session d'atteindre un appareil suspendu déjà enregistré sous cette clé, sans qu'il ait besoin
// de créer une nouvelle Live Activity pour la nouvelle partie.
//
// Ne stocke jamais le score dans une colonne dédiée : uniquement les jetons de push
// (`cacompte_live_activity_tokens`, RLS), lus ici avec la clé `service_role` (injectée
// automatiquement par le runtime Edge Functions, jamais commit dans le repo). `last_content_state`
// fait exception (voir plus bas) : conservé uniquement pour permettre à
// `cacompte-live-activity-sweep` de clore proprement une Activity inactive.
//
// Doc utilisateur — le code de signature APNs est dupliqué avec `cacompte-live-activity-sweep`
// plutôt que factorisé dans un dossier `_shared` : un import relatif hors du dossier de la
// fonction n'est pas fiable selon la méthode de déploiement (constaté en recette — le bundler
// distant échoue à résoudre `../_shared/apns.ts`), alors que chaque fonction reste déployable
// isolément une fois autonome.
import { createClient } from "jsr:@supabase/supabase-js@2";

interface Standing {
  id: string;
  name: string;
  score: number;
}

interface ContentState {
  matchID: string;
  gameName: string;
  gameSymbol: string;
  roundNumber: number;
  standings: Standing[];
  isStale: boolean;
}

interface PushRequest {
  activityKey: string;
  event: "update" | "end";
  contentState: ContentState;
}

const APNS_KEY_ID = Deno.env.get("APNS_KEY_ID")!;
const APNS_TEAM_ID = Deno.env.get("APNS_TEAM_ID")!;
const APNS_PRIVATE_KEY = Deno.env.get("APNS_PRIVATE_KEY")!;
const APNS_BUNDLE_ID = Deno.env.get("APNS_BUNDLE_ID") ?? "com.cacompte.app";
// Doc utilisateur — un jeton ActivityKit n'est valide que sur le serveur APNs de l'environnement
// qui l'a émis : sandbox pour une app lancée depuis Xcode, production pour TestFlight et l'App
// Store (Xcode réécrit `aps-environment` à l'export, l'entitlement du repo reste "development").
// Les deux coexistent en permanence (l'auteur en debug, les testeurs et le public en production),
// donc par défaut ("auto") on tente la production puis on retombe sur le sandbox quand Apple
// répond `BadDeviceToken`. "production" ou "development" forcent un seul serveur.
const APNS_ENVIRONMENT = Deno.env.get("APNS_ENVIRONMENT") ?? "auto";
const APNS_HOSTS = APNS_ENVIRONMENT === "production"
  ? ["api.push.apple.com"]
  : APNS_ENVIRONMENT === "development"
  ? ["api.sandbox.push.apple.com"]
  : ["api.push.apple.com", "api.sandbox.push.apple.com"];

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

let cachedKey: CryptoKey | null = null;
// Doc utilisateur — Apple recommande de réutiliser le même jeton fournisseur ~55 min plutôt que
// d'en resigner un par requête (limite de fréquence documentée par Apple sur ces jetons).
let cachedProviderToken: { token: string; issuedAt: number } | null = null;

function base64URLFromBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64URLFromString(value: string): string {
  return base64URLFromBytes(new TextEncoder().encode(value));
}

async function importApnsKey(): Promise<CryptoKey> {
  if (cachedKey) return cachedKey;
  const pemBody = APNS_PRIVATE_KEY
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const raw = atob(pemBody);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  cachedKey = await crypto.subtle.importKey(
    "pkcs8",
    bytes.buffer,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  return cachedKey;
}

/// Jeton fournisseur APNs (JWT ES256, doc Apple « Establishing a token-based connection ») —
/// distinct du jeton de push par appareil stocké dans `cacompte_live_activity_tokens`.
async function providerToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedProviderToken && now - cachedProviderToken.issuedAt < 55 * 60) {
    return cachedProviderToken.token;
  }
  const key = await importApnsKey();
  const header = base64URLFromString(JSON.stringify({ alg: "ES256", kid: APNS_KEY_ID }));
  const claims = base64URLFromString(JSON.stringify({ iss: APNS_TEAM_ID, iat: now }));
  const unsigned = `${header}.${claims}`;
  // Doc utilisateur — `crypto.subtle.sign` avec ECDSA/P-256 rend directement la signature au
  // format brut R||S (64 octets) qu'attend un JWT ES256 (RFC 7518), pas le DER que produit
  // OpenSSL par défaut : aucune conversion supplémentaire n'est nécessaire ici.
  const signature = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    new TextEncoder().encode(unsigned),
  );
  const token = `${unsigned}.${base64URLFromBytes(new Uint8Array(signature))}`;
  cachedProviderToken = { token, issuedAt: now };
  return token;
}

async function sendToToken(pushToken: string, body: { event: "update" | "end"; contentState: unknown }): Promise<{ ok: boolean; shouldForget: boolean }> {
  const token = await providerToken();
  const payload: Record<string, unknown> = {
    aps: {
      timestamp: Math.floor(Date.now() / 1000),
      event: body.event,
      "content-state": body.contentState,
    },
  };
  let response: Response | null = null;
  let reason: string | undefined;
  for (const host of APNS_HOSTS) {
    response = await fetch(`https://${host}/3/device/${pushToken}`, {
      method: "POST",
      headers: {
        authorization: `bearer ${token}`,
        "apns-topic": `${APNS_BUNDLE_ID}.push-type.liveactivity`,
        "apns-push-type": "liveactivity",
        "apns-priority": "10",
      },
      body: JSON.stringify(payload),
    });
    if (response.ok) return { ok: true, shouldForget: false };
    // Doc utilisateur — la raison d'Apple est la seule piste quand un écran verrouillé ne suit
    // plus : visible dans les logs de la fonction (tableau de bord Supabase).
    reason = (await response.json().catch(() => ({})))?.reason;
    console.warn(`APNs ${host} status=${response.status} reason=${reason} token=${pushToken.slice(0, 8)}…`);
    // Doc utilisateur — un jeton de l'autre environnement ne produit pas toujours
    // `BadDeviceToken` : une clé APNs restreinte à un seul environnement répond 403
    // `BadEnvironmentKeyInToken` sur l'autre serveur. On ne s'arrête donc que sur 410
    // (`Unregistered` : bon serveur, jeton mort) ; le coût d'un essai inutile est un appel de plus.
    if (response.status === 410) break;
  }
  if (!response) return { ok: false, shouldForget: false };
  // Doc utilisateur — un jeton révoqué/expiré ne redeviendra jamais valide : autant nettoyer
  // `cacompte_live_activity_tokens` tout de suite plutôt que de le retenter indéfiniment à chaque manche.
  // Seulement sur ces deux verdicts : toute autre erreur 400 (payload, horodatage…) ne dit rien du
  // jeton, et le supprimer figeait l'écran verrouillé du pair pour le reste de la session.
  const shouldForget = response.status === 410 || reason === "BadDeviceToken";
  return { ok: false, shouldForget };
}

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  let body: PushRequest;
  try {
    body = await request.json();
  } catch {
    return new Response("Invalid JSON", { status: 400 });
  }
  if (!body.activityKey || !body.contentState || !body.event) {
    return new Response("Missing activityKey, event or contentState", { status: 400 });
  }

  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
  const { data: tokens, error } = await supabase
    .from("cacompte_live_activity_tokens")
    .select("device_id, push_token")
    .eq("activity_key", body.activityKey);

  if (error) {
    return new Response(`Failed to load tokens: ${error.message}`, { status: 500 });
  }
  if (!tokens || tokens.length === 0) {
    return Response.json({ sent: 0, failed: 0 });
  }

  const results = await Promise.allSettled(
    tokens.map(async (row) => {
      const result = await sendToToken(row.push_token, { event: body.event, contentState: body.contentState });
      if (result.shouldForget) {
        await supabase
          .from("cacompte_live_activity_tokens")
          .delete()
          .eq("activity_key", body.activityKey)
          .eq("device_id", row.device_id);
      }
      return result.ok;
    }),
  );

  const sent = results.filter((r) => r.status === "fulfilled" && r.value).length;
  const failed = results.length - sent;

  // Doc 09 « Fin de partie » — repère d'activité pour `cacompte-live-activity-sweep` : seul un
  // push « update » réussi compte comme signe de vie (une partie qui vient de se terminer n'a pas
  // besoin d'être « tenue en vie » par son propre événement de fin). `last_content_state` permet
  // au balayage de renvoyer un contenu final valide plutôt que d'inventer un contenu vide.
  if (body.event === "update" && sent > 0) {
    await supabase
      .from("cacompte_live_activity_tokens")
      .update({ updated_at: new Date().toISOString(), last_content_state: body.contentState })
      .eq("activity_key", body.activityKey);
  }

  return Response.json({ sent, failed });
});
