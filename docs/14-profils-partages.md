# 14 — Profils partagés entre appareils

Statut du document : **exploration**, pas encore de décision engagée sur le code — contrairement
aux autres docs numérotées, celle-ci précède l'implémentation plutôt que de la documenter.

## Le besoin

Je crée l'avatar d'un ami sur mon téléphone, je fais une partie avec lui. S'il a l'app, cette
partie devrait pouvoir apparaître dans son propre historique, sur son propre téléphone — sans
compte, sans friction, sans exploser la base Supabase.

Ce n'est **pas** la même chose que le partage en direct ([doc 09](09-partie-partagee.md)) :

| | Partage en direct (doc 09) | Profils partagés (ce doc) |
|---|---|---|
| Quand | Pendant la partie | Après coup, même hors ligne |
| Qui | Quelqu'un physiquement là, avec son téléphone sorti | N'importe quel ami ajouté comme joueur, présent ou non |
| Durée du lien | Une soirée (code réappairé à chaque session) | Permanent (une fois lié, toujours lié) |
| Ce qui est vu | Le tableau de scores en temps réel | Le résultat final, plus tard |

Les deux coexistent : rien dans ce document ne remplace le partage en direct, qui reste la bonne
réponse à « je veux que Théo suive la partie en cours depuis son canapé ».

## Pourquoi pas une simple extension de doc 09

Le partage en direct est *éphémère* par construction (`cacompte_open_games` se purge après 24 h,
`LiveShareCoordinator` ne survit pas à l'arrêt du partage). Ce qu'on demande ici est *permanent* :
que « Marie », une fois liée, reste liée d'une partie à l'autre, des mois plus tard, sans
ré-appairage. Réutiliser le mécanisme de code d'appairage pour l'identité (plutôt que pour une
seule session) serait déformer un outil conçu pour l'éphémère.

## Question par question

**Une nouvelle page « Profil » doit-elle être ajoutée ?**
Non, pas un nouvel onglet. `ProfileView` existe déjà par joueur (stats d'un participant tel
qu'enregistré sur cet appareil) — ce qui manque n'est pas un écran de plus mais une action de
plus sur l'écran d'édition d'un joueur existant (`PlayerEditorView`) : « Partager ce profil » /
« Lier un profil ». Le modèle mental reste « des fiches joueur, dont certaines peuvent être
liées à l'installation d'un ami » — pas « l'app a maintenant des comptes ».

**Comment le profil d'une app se synchronise avec celui d'une autre ?**
Explicitement, une fois, par QR/code — jamais automatiquement. Voir « Identité cross-appareil »
ci-dessous.

**Sélection de profils locaux ou publiés à l'ajout des joueurs ?**
Ni l'un ni l'autre au sens d'un annuaire : il n'y a pas de liste publique de profils cherchables
(ce serait un vrai système d'identité publique — des gens retrouvables par leur nom — hors de
proportion avec le besoin, et un vrai souci de confidentialité). `MatchSetupView` ne change pas :
on choisit toujours parmi ses propres fiches locales. Qu'une fiche soit liée ou non est invisible
à cet écran — ça ne joue qu'au moment où la partie se termine (voir plus bas).

**Synchronisation automatique par nom identique ?**
Déconseillé comme mécanisme principal. Deux « Marie » différentes dans deux groupes d'amis
existent forcément ; une correspondance silencieuse par pseudo attribuerait des statistiques à la
mauvaise personne, ou pire, fusionnerait deux identités distinctes sans qu'aucune des deux ne
l'ait demandé. Si le besoin se confirme, une version *suggestive* (« quelqu'un d'autre utilise
aussi "Marie", lier les profils ? ») resterait possible plus tard, mais avec confirmation
explicite des deux côtés — jamais silencieuse. Non retenue pour la v1.

**Jouer hors connexion, synchroniser au retour du réseau ?**
Oui, nativement — voir « Économie de ressources et robustesse hors-ligne ». C'est le même patron
que `MatchConnectionCoordinator.scheduleAutoRetry` (déjà en place pour le partage en direct),
appliqué à une file d'attente différente.

## Identité cross-appareil — options considérées

**Option A — Lien explicite par code, sans compte (retenue).**
Une fiche joueur peut générer un identifiant partageable (`UUID`, jamais réutilisé). L'ami scanne
ce code une fois avec sa propre app ; sa fiche locale (qui le représente, lui, chez lui) enregistre
ce même `UUID`. Aucun compte, aucune connexion — le lien est une propriété de deux fiches locales,
pas une entité serveur.

**Option B — Correspondance automatique par pseudo.**
Écartée comme mécanisme principal (voir ci-dessus) — ambiguë, risque de confidentialité, aucune
confirmation possible avant que le mal soit fait.

**Option C — Vrais comptes (Sign in with Apple, email).**
Écartée pour la v1 : ajoute connexion, gestion de session, RGPD (suppression de compte), une
vraie surface de revue de sécurité — pour un besoin qui n'exige pas de compte durable, seulement
une identité stable entre deux appareils précis. À reconsidérer seulement si le produit évolue
vers des fonctionnalités sociales plus lourdes (annuaire public, classements entre inconnus).

**Pourquoi A l'emporte** : même modèle mental que le code d'appairage déjà compris par
l'utilisateur (doc 09), zéro compte, zéro ambiguïté (toujours un geste explicite et mutuel), et
un `UUID` transmis par QR plutôt que tapé à la main est *plus* sûr que le code à 6 chiffres déjà
accepté pour rejoindre une partie en direct — le seuil de sécurité de ce projet est déjà fixé par
doc 09 (« quiconque connaît le code peut rejoindre ») ; l'option A ne l'abaisse pas.

## Design retenu

### Phase 1 — lier deux fiches, aucun serveur ✅

`PlayerRecord` gagne un champ optionnel `sharedProfileID: UUID?`. Sur `PlayerEditorView`, une
nouvelle section « Profil partagé » :

- **Partager ce profil** — génère un `UUID` s'il n'existe pas encore, l'encode dans un lien
  `cacompte://claim-profile?id=<uuid>&name=<pseudo>` (même famille que `JoinLink`, un cas de plus
  dans son `enum`), affiché en QR (`QRCodeView`, déjà là) et en texte.
- **Lier un profil reçu** — réutilise le scanner déjà construit pour l'onglet « Rejoindre »
  (`QRScannerView`) : scanner le QR d'un ami enregistre son `sharedProfileID` sur *ma* fiche qui
  le représente.

Aucune table Supabase pour cette phase — le payload du QR se suffit à lui-même, exactement comme
un `JoinLink` aujourd'hui. Risque et coût d'implémentation minimaux ; testable et livrable seule,
sans que la phase 2 existe encore.

### Phase 2 — la partie apparaît chez l'ami

À la fin d'une partie (`.ended` ou `.abandoned` — jamais en cours, pour éviter tout merge
incrémental dans le stockage SwiftData de quelqu'un d'autre pendant que la partie tourne), pour
chaque participant dont la fiche porte un `sharedProfileID` : pousser un résumé compact vers une
nouvelle table Supabase, `cacompte_shared_match_summaries` :

```sql
create table cacompte_shared_match_summaries (
  id uuid primary key default gen_random_uuid(),
  shared_profile_id uuid not null,
  payload jsonb not null,      -- jeu, date, manches jouées, classement complet (pseudo,
                                -- avatar léger, rang, score) — pas le journal d'événements
  created_at timestamptz not null default now()
);
```

Même politique RLS que le reste (`anon`, connaître l'id suffit — cohérent avec le seuil de
sécurité déjà accepté doc 09). Purge de sécurité à 30 jours (comme `cacompte_open_games`, en plus
généreux puisqu'un ami peut rester hors ligne des semaines) pour le cas où personne ne vient
jamais la récupérer.

Côté ami, au premier plan (même déclencheur que `MatchConnectionCoordinator` :
`willEnterForegroundNotification`) : interroger les résumés en attente pour son propre
`sharedProfileID`, les matérialiser en `MatchRecord` local, puis **supprimer** la ligne côté
serveur — la table ne sert que de boîte aux lettres transitoire, jamais de copie durable. C'est
la même discipline que `cacompte_open_games` : Supabase est un relais, jamais la source de vérité.

**Ce qui est matérialisé n'est pas une partie rejouable.** Le `MatchRecord` reçu a un journal
d'événements minimal (pas de manches) ; seuls `ParticipantRecord.finalRank`/`finalScore` sont
renseignés depuis le résumé. C'est délibéré et suffisant : `LeaderboardRepository` et
`ProfileRepository` (parties jouées, victoires, taux de victoire, rang moyen normalisé) ne lisent
que ces deux champs, jamais le détail manche par manche — les statistiques de l'ami restent donc
justes sans qu'il ait besoin du journal complet. Seul l'écran de résultats détaillé (courbe,
manche par manche, badges) resterait indisponible pour une partie reçue ; `HistoryListView`
distinguerait visuellement ces entrées (« reçue de Marion », pas de bouton Abandonner puisque ce
n'est jamais « en cours » localement).

Ce choix — résumé, pas copie intégrale — est le point du design le plus arbitraire de ce document
(voir « Décisions ouvertes »).

### Synergie avec l'onglet « Rejoindre »

L'onglet ajouté aujourd'hui pour rejoindre une partie en direct (caméra prête à scanner) devient
le point d'entrée naturel pour « lier un profil » aussi — même geste (scanner un QR d'ami),
distingué par le contenu du lien (`cacompte://join` vs `cacompte://claim-profile`), zéro écran
supplémentaire.

## Économie de ressources et robustesse hors-ligne

- Phase 1 : zéro coût serveur.
- Phase 2 : une ligne JSON de quelques centaines d'octets par (partie, participant lié) —
  supprimée dès réception, pas accumulée. Un groupe de 6 amis qui joue trois fois par semaine
  produit un trafic négligeable face à la fenêtre de purge de 24 h déjà en place pour
  `cacompte_open_games`.
- Hors ligne à la fin d'une partie : la tentative d'envoi échoue simplement et se met en attente
  (un indicateur sur `MatchRecord`, ex. `pendingProfileSyncParticipantIDs`), rejouée au prochain
  retour au premier plan avec réseau — même patron que `scheduleAutoRetry`
  (`MatchConnectionCoordinator.swift`), pas un nouveau système de synchronisation à inventer.

## Phasage

1. **Phase 1** — champ `sharedProfileID`, UI de partage/liaison, réutilisation de `JoinLink` +
   `QRCodeView`/`QRScannerView`. Livrable et testable seul, sans backend.
2. **Phase 2** — table `cacompte_shared_match_summaries`, envoi à la fin d'une partie, réception
   au premier plan, matérialisation en `MatchRecord` minimal.
3. **Plus tard, si demandé** — copie intégrale rejouable (journal d'événements complet plutôt
   qu'un résumé) ; alimente aussi « Statistiques de groupe » ([roadmap](12-roadmap.md), face-à-face
   entre profils liés).

## Décisions ouvertes

Ce que ce document tranche par hypothèse plutôt que par confirmation — à valider avant la phase
correspondante :

1. **Résumé vs copie intégrale (phase 2).** Retenu : résumé (voir plus haut). Si l'ami doit
   pouvoir rejouer le détail manche par manche d'une partie où il n'était pas au clavier, il faut
   le journal complet — plus lourd, et pose la question du droit de l'hôte à republier le détail
   de saisie de quelqu'un d'autre.
2. **Suggestion de liaison par pseudo identique.** Non retenue pour la v1 (voir Option B) — à
   reconsidérer seulement si des utilisateurs la demandent explicitement.
3. **Une fiche liée peut-elle être déliée ?** Oui — implémenté phase 1 (« Ne plus partager »,
   `PlayerEditorView`). Efface `sharedProfileID` localement ; aucun état serveur à nettoyer
   puisque la phase 1 n'en a pas.
