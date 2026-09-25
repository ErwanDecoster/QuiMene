# 14 — Profils, amis et historique partagé

Réécrite après la doc [16](16-sessions-en-ligne-et-profils.md) (septembre 2026). Remplace le
mécanisme d'origine (liaison de fiches par QR, résumés de classement poussés en clair), dont
l'historique de conception reste dans git. Le partage en direct est décrit en
[09](09-partie-partagee.md).

## Mon profil

- **Obligatoire**, sans compte : pseudo et avatar. L'écran de création recouvre l'app tant qu'aucun
  profil n'existe (`ProfileRequirement`). Sauvegardé avec les autres données (iCloud côté Apple).
- Techniquement, c'est la fiche de joueur marquée `sharedProfileIsMine`, porteuse d'un
  **identifiant partageable** (`sharedProfileID`, UUID aléatoire, jamais régénéré). C'est lui, et
  jamais le pseudo, qui identifie une personne d'un appareil à l'autre.
- Onglet **Profil** : avatar, pseudo, statistiques, QR « M'ajouter comme ami », amis liés,
  « Rejoindre une partie », réglages. « Supprimer mon profil » garde la fiche et son historique,
  lui retire son statut de profil, puis impose d'en créer un nouveau.
- Doublon après une synchronisation iCloud (profil créé sur un nouvel appareil avant l'arrivée de
  l'ancien) : le plus ancien reste le profil (`resolveDuplicateOwnProfiles`).

## Amis

Un **ami** est une fiche de joueur liée à l'identifiant d'un autre profil. Le pseudo de la fiche
appartient à celui qui l'a créée (le nom qu'il donne à son ami) ; il peut différer du pseudo du
profil sans rien casser. Trois façons de lier :

1. **« Ajouter un ami »** : scanner son QR de profil, puis choisir une fiche existante ou en créer
   une (pseudo et avatar du QR).
2. **« Qui es-tu ? »** en rejoignant une session : le joueur choisit sa place ; chez le créateur, la
   fiche de cette place est liée à son profil, **durablement** (bandeau « X s'est associé à la
   fiche Y », avec « Annuler »).
3. **Dans l'autre sens** : une fois sa place retenue, le joueur ajoute le créateur à ses amis (une
   fiche non liée portant exactement le même pseudo, et une seule, est réutilisée ; sinon une
   fiche est créée).

## « Qui es-tu ? »

Les identités voyagent dans le journal de la session (doc 09), sous une enveloppe distincte :

| Événement | Par | Contenu |
|---|---|---|
| Revendication | participant | Place (siège + pseudo) et carte de profil (identifiant, pseudo, avatar) |
| Annulation | créateur, ou auteur de la revendication | Revendication visée |
| Registre | créateur | Son profil, et les places que ses fiches relient déjà à un profil |

`SessionIdentities` en déduit, identiquement sur chaque appareil, qui occupe quelle place :

- seul le dernier registre **du créateur** compte ; un ami déjà lié est **reconnu d'office**, sans
  question ;
- premier arrivé, premier servi ; une place reliée à un autre profil ne peut pas être prise ;
- un profil n'occupe qu'une place : **« Changer »** est toujours possible, même reconnu d'office ;
  la nouvelle place remplace l'ancienne, qui se libère, et le créateur déplace la liaison de fiche ;
- **« Je regarde seulement »** ne permet pas de saisir et rend une place revendiquée ;
- « Qui es-tu ? » ne s'affiche jamais pour une partie terminée ou abandonnée.

## Badges

« Moi » sur ma place partout où une partie liste ses joueurs (saisie, historique des manches,
résultats, historique, choix des joueurs) ; un lien sur les places de mes amis pendant la saisie et
dans la liste des joueurs.

## Historique partagé

Chaque participant ayant un profil garde **la partie complète** (journal entier, pas un résumé) :

- **Connecté à la session** : chaque partie terminée où il a une place est enregistrée chez lui
  (`MatchConnectionCoordinator.keep`), sa place reliée à sa fiche, celles de ses amis aux leurs.
- **Absent** : chaque appareil qui a une partie terminée avec des amis liés la **dépose dans la
  boîte aux lettres** de chacun (migration `create_quimene_match_mailbox`), dès l'écran de
  résultats. L'ami la relève au lancement, au retour au premier plan, à l'ouverture de
  l'Historique et en tirant la liste vers le bas ; elle est enregistrée (rien si déjà connue) puis
  retirée de la boîte. Conservation : **14 jours** au plus.
- Une partie jouée sur un autre appareil est marquée **« Reçue »** dans l'Historique et son détail.

### Boîte aux lettres chiffrée

- Adresse = SHA-256 de `quimene.mailbox.lookup:<identifiant en majuscules>` : le serveur ne voit
  jamais l'identifiant.
- Contenu = la partie (`SharedMatchPackage` : journal et fiches des joueurs, jamais de photo)
  scellée en AES-GCM avec une clé HKDF-SHA256 de l'identifiant (sel `quimene.mailbox`, info
  `quimene.mailbox.v1`) — `MailboxCrypto`.
- Dépôt idempotent par (boîte, partie) ; fonctions `security definer`, aucune table accessible
  directement.

## Limites de confiance

Pas de compte, donc pas de preuve cryptographique qu'un identifiant appartient à la personne qu'il
prétend représenter. Le contexte physique (scanner le téléphone de quelqu'un, être assis à la même
table) **est** la vérification.

1. **Connaître un identifiant suffit** pour déposer dans sa boîte ou relever ses parties. Un
   identifiant se transmet par QR de profil, et par « Qui es-tu ? » (il est publié dans la
   session). Un QR retransmis par capture d'écran perd la garantie du contexte physique.
2. **Les sessions ne sont pas chiffrées de bout en bout vis-à-vis de l'opérateur du serveur**
   (doc 09) : un identifiant publié dans une session est lisible par qui a accès à la base, qui
   pourrait alors lire cette boîte aux lettres. Hors de ce cas, la boîte est illisible pour le
   serveur.
3. **Pas de révocation globale** : délier une fiche n'invalide pas l'identifiant chez ceux qui le
   connaissent déjà.
4. Coût d'une usurpation réussie : des statistiques de jeu de société, pas des données sensibles.
   Ajouter des comptes (connexion, RGPD, revue de sécurité) reste disproportionné.

## Tests

`SessionIdentityTests` / `SessionIdentityTest` (règles des places), `SharedMatchMailboxTests` /
`SharedMatchMailboxTest` (chiffrement, adresse), import d'une partie reçue (Store, deux
plateformes), fichiers de référence croisés (doc [17](17-recette-croisee.md)).
