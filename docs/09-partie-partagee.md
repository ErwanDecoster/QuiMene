# 09 — Partie partagée en ligne

Réécrite après la doc [16](16-sessions-en-ligne-et-profils.md) (septembre 2026), qui a remplacé le
modèle « hôte autoritaire » d'origine (ADR-0008, ADR-0014, ADR-0016) par des **sessions stockées
côté serveur** (ADR-0017). L'historique de conception reste dans git et dans la doc 16. Les
identités (« Qui es-tu ? ») et l'historique partagé sont détaillés en [14](14-profils-partages.md) ;
la recette croisée iOS ↔ Android en [17](17-recette-croisee.md).

## Cas d'usage

Une soirée jeux : un appareil lance la partie et la partage ; les autres la rejoignent par QR ou
code à 6 chiffres, suivent le tableau en direct et, s'ils y sont autorisés, saisissent eux-mêmes
les manches. Le téléphone du créateur peut s'éteindre : la partie continue.

## Deux modes

- **Local** (par défaut) : la partie vit sur l'appareil, sans réseau.
- **En ligne** : dès « Partager », le créateur ouvre une **session**. Son journal d'événements,
  stocké sur le serveur (Supabase), fait foi pour **tous** les appareils, créateur compris ;
  chacun en garde une copie pour l'affichage.

## Session

| Élément | Où | Rôle |
|---|---|---|
| `quimene_sessions` | migration `create_quimene_sessions` | Code d'appairage, créateur, « contributeurs autorisés », fermeture |
| `quimene_session_events` | idem | Journal chiffré, numéroté par le serveur (`seq`) |
| Fonctions `quimene_session_*` | idem | Ouvrir, résoudre un code, ajouter, lire depuis un numéro, fermer |
| Canal Realtime `session:<id>` | déclencheur `realtime.send` | Prévient les appareils qu'un événement est arrivé ; présence des appareils |
| `OnlineSession` | `Sync` (Swift) / `:sync` (Kotlin) | Journal lisible, rattrapage, ajout |
| `SessionLink` | app | Canal, réseau, retour au premier plan, envoi |

- **Une session, plusieurs parties** : chaque partie commence par son `matchCreated` ; la partie
  courante est celle du plus récent. **N'importe quel participant** peut lancer la partie suivante
  (mêmes joueurs aux mêmes places, nouveaux identifiants de participant).
- **Seul le créateur arrête** la session (« Arrêter le partage »). Après fermeture, le journal
  reste lisible 24 h pour que les appareils rattrapent la fin, puis il est purgé. Une session
  inactive est purgée après **14 jours**.
- **Reprise** : créateur et participant retiennent la session (`PersistedOnlineSession`) et la
  reprennent après un redémarrage, sans ressaisir le code.

## Ordre et conflits

- Le serveur attribue les numéros de séquence ; un ajout **annonce le numéro qu'il attend**. S'il
  a été devancé, il est refusé (`stale_seq`) : l'appareil rattrape, montre l'état à jour et le
  message « X vient de valider une manche : vérifie avant de valider la tienne. » **Pas de nouvel
  essai automatique** : deux personnes qui saisissent la même manche physique créeraient un
  doublon silencieux.
- L'ajout est idempotent par identifiant d'événement (un envoi retenté ne duplique rien).
- Chaque appareil **valide sa saisie** avec le même moteur de règles avant d'envoyer (golden files
  communs, doc [10](10-tests-et-qualite.md)). Le rejeu (`MatchEngine.replay`) suit l'ordre du
  serveur : le `lamport` d'un événement publié vaut son `seq`.
- Le créateur **publie** le journal d'une partie commencée en local avec ses identifiants d'origine
  (celui du `matchCreated` est celui de la partie) ; sa copie locale devient ensuite le miroir du
  journal serveur. Une publication interrompue reprend au rattrapage suivant.

## Format d'un événement

Un `StampedEvent` en JSON (même encodage sur les deux plateformes), scellé en **AES-GCM** avec la
clé de session, puis en base64 :

- clé = HKDF-SHA256(code d'appairage, sel = identifiant de session **en majuscules**, info
  `quimene.livesession.v1`) — `SessionCrypto` ;
- la base ne stocke que des blobs, leur ordre, l'appareil qui les a ajoutés et la partie à
  laquelle ils appartiennent — **mais elle stocke aussi le code d'appairage** (pour qu'on puisse
  rejoindre en le saisissant) : quiconque accède à la base peut donc dériver la clé. Voir
  « Sécurité et vie privée » ;
- les identités (« Qui es-tu ? », doc 14) partagent ce journal sous une enveloppe distincte
  (`{"identity": …}`) : une version qui ne la connaît pas la saute sans casser le rejeu.

Fichiers de référence communs, dans les deux sens : `spec/session/` (doc 17).

## Rôles et saisie

- **Contributeurs autorisés** (réglage du créateur, modifiable en cours de session) : un
  participant qui a une place peut saisir ; sinon il observe.
- **« Je regarde seulement »** : suit la partie sans saisir.
- **Hors ligne, la saisie est bloquée** (« Hors connexion ») : pas de file d'attente, source de
  conflits. L'écran rattrape au retour du réseau, au retour au premier plan ou à la prochaine
  notification du canal. Une partie entièrement hors réseau se joue en mode local.

## Écran verrouillé (iOS)

La Live Activity d'une partie partagée est rattachée à la session (`session:<ID en majuscules>`),
pas à la partie. **L'appareil qui enregistre un événement** — créateur ou participant, iOS ou
Android — envoie la mise à jour via la fonction `quimene-live-activity-push` (APNs) ; ceux qui le
reçoivent ne renvoient rien. Les écrans verrouillés suivent donc même quand le créateur est éteint.
La fin de partie retire la carte (`dismissal-date` immédiate) ; `quimene-live-activity-sweep` clôt
les activités inactives. Seul contenu en clair transmis au serveur : celui de la carte (nom du jeu,
numéro de manche, quatre premiers pseudos et scores), qu'Apple exige pour l'afficher.

## Sécurité et vie privée

- **Quiconque connaît le code** (ou scanne le QR) peut lire la session : c'est l'invitation. Un
  code n'est valable que pour une session vivante (ouverte et active depuis moins de 14 jours).
- Aucune table n'est accessible directement : tout passe par des fonctions `security definer` qui
  exigent un code ou un identifiant de session (pas de policy `anon`). Un tiers sans le code ne
  peut ni lister ni lire les sessions.
- **Limite connue — ce n'est pas du bout en bout vis-à-vis de l'opérateur du serveur.** La clé
  dérive du code à 6 chiffres, que la base conserve en clair pour la résolution ; et même haché,
  un code à 6 chiffres (un million de possibilités) se retrouverait instantanément. Une personne
  ayant accès à la base (l'opérateur du projet Supabase) pourrait donc lire pseudos et scores
  d'une session en cours ou de moins de 24 h après sa fermeture. Le chiffrement protège contre
  une fuite des seuls blobs, pas contre l'accès complet à la base. L'historique partagé, lui
  (boîte aux lettres, doc 14), est chiffré avec une clé que le serveur ne peut pas dériver.
- Contenu de la carte d'écran verrouillé : transmis en clair à la fonction d'envoi, qu'Apple
  exige pour l'afficher.

## Tests

- Unitaires : `OnlineSessionTests` (Swift) / `OnlineSessionTest` (Kotlin) sur un serveur en mémoire
  aux mêmes règles que le SQL (numérotation, `stale_seq`, idempotence, lots de 500).
- SQL : `supabase/tests/quimene_sessions_test.sql`.
- Croisés : fichiers de référence dans les deux sens et scénarios sur appareils (doc 17).
