# 16 — Sessions en ligne et profils

Statut : **livré le 25 septembre 2026** (plan validé le 24), phases A à H sur iOS et Android. Ce document
remplace, une fois livré, le modèle « hôte autoritaire » de [09](09-partie-partagee.md) et le
mécanisme de résumés de [14](14-profils-partages.md).

## Pourquoi

Deux limites remontées à l'usage :

1. **Une partie partagée s'arrête avec le téléphone de l'hôte.** Aujourd'hui l'hôte valide,
   enregistre et redistribue chaque manche ; le serveur ne fait que relayer des messages, sans rien
   garder. Hôte éteint ou en veille : plus rien n'avance.
2. **L'historique commun est incomplet.** Un ami lié ne reçoit qu'un résumé (classement final),
   et seulement après un échange de QR de profil fait à part. Pas de page profil : l'identité de
   l'utilisateur est implicite (« la fiche que j'ai partagée »).

## Principe : deux modes

- **Mode local** (par défaut) — inchangé. La partie vit sur l'appareil, sans réseau.
- **Mode en ligne** — dès que le créateur touche « Partager », il ouvre une **session** sur le
  serveur. Chaque partie de la session y est stockée (journal d'événements chiffré de bout en
  bout) et c'est ce journal qui fait foi pour **tous** les appareils, créateur compris. Chacun en
  garde une copie locale pour l'affichage. N'importe quel participant peut saisir, même si le
  créateur est éteint.
- **Fin de session** — seul le créateur l'arrête. Les parties sont déjà dans l'historique de
  chaque participant ayant un profil ; les données serveur sont supprimées, sauf les livraisons
  en attente pour les amis absents.

## Décisions

| Sujet | Décision |
|---|---|
| Chiffrement | **De bout en bout pour l'historique partagé** (boîte aux lettres : clé tirée de l'identifiant de profil, que le serveur ne voit pas). **Sessions : chiffrées, mais pas de bout en bout vis-à-vis de l'opérateur** — la clé dérive du code à 6 chiffres que la base conserve (constat de la phase H, doc 09). La validation des règles se fait sur l'appareil qui saisit (même moteur partout, garanti par les golden files). |
| Ordre des manches | Numéro de séquence attribué par le serveur ; un ajout annonce le numéro attendu, refusé si quelqu'un l'a devancé (l'appareil rattrape, revalide, réessaie). L'écrasement d'une manche devient impossible par construction. |
| Conservation | **14 jours** après la dernière activité de la session. |
| Saisie hors ligne en mode en ligne | **Bloquée** : bouton grisé « Hors connexion », l'écran rattrape au retour du réseau. Pas de file d'attente (conflits). Une partie entièrement hors réseau se joue en mode local. |
| Arrêt de la session | **Créateur uniquement.** Une session couvre toutes ses parties successives. |
| Partie suivante dans une session | **N'importe quel participant** peut la lancer (mêmes joueurs), pour que la session ne dépende pas du téléphone du créateur. |
| Historique | **Chaque participant ayant un profil** garde la partie complète (toutes les manches, pas un résumé), comme le créateur. |
| Rejoindre | Écran « Qui es-tu dans cette partie ? » (s'associer à une fiche de l'hôte), avec **« Je regarde seulement »** pour les spectateurs et ceux sans profil. |
| Confiance | Le créateur voit « Théo s'est associé à la fiche Théo », avec annulation. Une fiche déjà liée à un autre profil ne peut pas être revendiquée sans accord. |
| Profil | **Obligatoire** (pseudo et avatar, aucun compte) : l'écran de création recouvre l'app tant qu'aucun profil n'existe (`ProfileRequirement`). Mon profil ne s'archive ni ne se supprime comme une fiche : « Supprimer mon profil » (onglet Profil) garde la fiche et son historique, retire son statut de profil, puis impose d'en créer un nouveau. |
| Onglets | **Profil remplace Rejoindre** : Joueurs · Jeux · Historique · Profil. « Rejoindre une partie » devient un bouton (Jeux, Profil) ; lien et QR système ouvrent toujours l'app. Réglages déplacés dans Profil. |

## Parcours utilisateur

1. **Premier lancement** — « Créer mon profil » (pseudo, avatar), obligatoire. Sur un nouvel
   appareil avec iCloud, le profil existant arrive seul.
2. **Erwan lance une session** — jeu, joueurs, « Partager » : code et QR, badge « En ligne » dans
   l'en-tête. Ses parties suivantes de la soirée sont partagées jusqu'à l'arrêt de la session.
3. **Théo rejoint** — scan, « Qui es-tu ? », il touche « Théo ». Son profil est désormais lié à la
   fiche « Théo » d'Erwan, durablement, sans échange de QR de profil à part.
4. **Le téléphone d'Erwan s'éteint** — la partie continue ; un retardataire peut rejoindre avec le
   code ; les écrans verrouillés se mettent à jour. Erwan rattrape tout à son retour.
5. **Fin de partie** — la partie complète arrive dans l'historique de chaque participant ayant un
   profil. Un ami lié absent la reçoit à sa prochaine ouverture de l'app.
6. **Erwan arrête la session** — suppression des données serveur (hors livraisons en attente,
   14 jours au plus).
7. **Soirée sans réseau** — comme aujourd'hui ; les parties jouées avec des amis liés leur sont
   livrées au retour du réseau.

**Page Profil** : avatar et pseudo, QR « M'ajouter comme ami », statistiques (écran actuel
réutilisé), amis liés, dernières parties, « Rejoindre une partie », Réglages.

## Phases

Phase A livrée (iOS, puis Android à l'identique aux conventions Material près — Rejoindre y est un écran poussé avec retour) : « mon profil » reste la fiche marquée `sharedProfileIsMine` (aucune
migration de modèle), créée par `PlayerEditorMode.createProfile` ; onglet `ProfileTabView` ;
écran de création obligatoire `ProfileOnboardingView` (`ProfileRequirement`) ; « Rejoindre » en plein écran (`DeepLinkRouter.isPresentingJoin`)
avec « Fermer » distinct de « Quitter la partie » et un bandeau de reprise dans Jeux ;
« Ajouter un ami » (scan puis choix ou création de la fiche) ; doublon de profil arrivé par
iCloud résolu au lancement (`PlayerRepository.resolveDuplicateOwnProfiles`).

Phase C livrée (iOS puis Android) : `OnlineSession` (module Sync) tient le journal chiffré de la
session, `SessionLink` (app) le rattrapage — notification du canal, retour du réseau, retour au
premier plan — et l'ajout avec numéro attendu. Devancé, l'appareil rattrape et affiche « X vient
de valider une manche : vérifie avant de valider la tienne. », sans nouvel essai automatique.
Le créateur (`LiveShareCoordinator`) publie le journal local avec ses identifiants d'origine (celui
du `matchCreated` est celui de la partie), puis sa copie locale devient le miroir du serveur ; il
reprend sa session après un redémarrage, comme le participant (`PersistedOnlineSession`). La partie
suivante, lancée par n'importe qui, garde les joueurs à leur place mais **avec de nouveaux
identifiants de participant** (un identifiant ne sert qu'à une partie) ; le créateur retrouve fiche
et avatar par place et pseudo. Format commun vérifié par `spec/session/sealed-events.json`, qui
remplace `spec/wire`. Anciennes fonctions `quimene_open_games` inutilisées : à supprimer dans une
migration ultérieure.

Phase D livrée (iOS puis Android) : « qui est qui » voyage dans le même journal chiffré,
sous une enveloppe distincte (`{"identity": …}`, `SessionIdentity`) qu'une version antérieure
saute sans casser le rejeu. Trois événements : **revendication** (place = siège + pseudo, stable
d'une partie à l'autre, et carte de profil), **annulation** (par le créateur ou l'auteur) et
**registre** du créateur (son profil, et les places que ses fiches relient déjà à un profil).
`SessionIdentities` en déduit, sur chaque appareil, qui occupe quelle place : premier arrivé,
premier servi ; une place reliée à un autre profil ne peut pas être revendiquée (pas de demande
d'accord pour l'instant : le créateur peut délier la fiche) ; un ami déjà lié est reconnu sans
question. Le créateur lie la fiche de la place à la première revendication retenue et affiche
« X s'est associé à la fiche Y » avec « Annuler » ; le participant ajoute le créateur à ses amis
(fiche existante au même pseudo, sinon créée). « Je regarde seulement » ne permet pas de saisir
(et rend une place revendiquée). « Changer » est toujours possible, même reconnu d'office : une
revendication remplace la place reliée par le créateur, qui devient libre, et le créateur déplace
la liaison de fiche. « Qui es-tu ? » ne s'affiche jamais pour une partie terminée ou abandonnée.
Format commun : `spec/session/identity-events.json`.
Avec elle : badge « Moi » sur ma place partout où une partie liste ses joueurs (saisie, manches,
résultats, historique, choix des joueurs) et lien sur celles de mes amis pendant la saisie ;
« Ferme » (Skyjo…) sur la ligne de chaque joueur, et même barre au-dessus du clavier pour le
créateur et le participant (iOS).

Phase E livrée (iOS puis Android) : chaque participant connecté qui occupe une place
enregistre dans son historique chaque partie terminée de la session, complète (journal entier),
sa place reliée à sa fiche et celles de ses amis aux leurs (`MatchConnectionCoordinator.keep`).
Pour les absents, boîte aux lettres chiffrée par profil (migration `create_quimene_match_mailbox`,
`MailboxCrypto`) : adresse = empreinte SHA-256 de l'identifiant partageable, contenu = la partie
complète (`SharedMatchPackage`) scellée avec une clé HKDF de cet identifiant ; le serveur ne voit
ni identifiant, ni joueurs, ni scores. Chaque appareil qui a une partie terminée avec des amis
liés la dépose chez chacun (idempotent par partie) ; à l'ouverture, un appareil relève sa propre
boîte, enregistre les parties (rien si déjà connue) puis les retire. Conservation 14 jours.
Remplace les résumés du doc 14 (table et fonctions retirées en phase H, une fois Android passé à
la boîte). Les dates d'une copie sont celles de la partie (premier et dernier événement), plus
celles de l'enregistrement. Format commun : `spec/session/mailbox-package.json`. Dépôt immédiat
dès l'écran de résultats ; relève au lancement, au retour au premier plan, à l'ouverture de
l'Historique et en tirant la liste vers le bas. Une partie jouée sur un autre appareil
(`deviceOrigin = "received"`) est signalée « Reçue » dans l'Historique et dans son détail.

Phase F : l'appareil qui enregistre un événement dans la session (créateur ou participant) met à
jour l'écran verrouillé de tous les iPhone de la session via `quimene-live-activity-push`
(`isAuthoritative`), jamais ceux qui le reçoivent. Déjà vrai sur iOS depuis la phase C ; Android
envoie désormais la même mise à jour (`LiveActivityPushClient.kt`, même `ContentState` et même clé
`session:<UUID en majuscules>`) quand il saisit une manche, et, côté créateur, pour toute action
enregistrée (manche, annulation, fin, abandon). Android n'a pas encore d'écran verrouillé à lui.

Phase G : Android a suivi chaque phase au fil de l'eau ; la compatibilité croisée est vérifiée
dans les deux sens par des fichiers de référence produits par le code réel de chaque plateforme,
et une liste de scénarios à dérouler sur appareils avant publication ([doc 17](17-recette-croisee.md)).

Phase H : docs [09](09-partie-partagee.md) et [14](14-profils-partages.md) réécrites,
ADR-0017 (remplace ADR-0008 et, pour sa partie transport, ADR-0016), politique de confidentialité
et FAQ du site mises à jour, ancien partage retiré du code et du serveur (migration
`drop_legacy_sharing` : `quimene_open_games`, résumés du doc 14 et leurs purges). Les scénarios
« créateur éteint » et « ami absent » sont dans la recette (doc 17, n° 7 et 11). Constat : les
sessions ne sont pas chiffrées de bout en bout vis-à-vis de l'opérateur du serveur (doc 09) —
documenté partout, amélioration possible proposée à part.

| Phase | Contenu | Dépend de |
|---|---|---|
| **A. Profil local** ✅ iOS + Android | Page Profil, création au premier lancement, onglets réorganisés, profil explicite (plus « la fiche partagée »), amis liés, profil sauvegardé via iCloud. | — |
| **B. Sessions serveur** ✅ (migration `create_quimene_sessions`) | Tables session / parties / événements chiffrés, fonctions SQL (créer, ajouter avec numéro attendu, lire depuis un numéro, fermer), notification des appareils, expiration 14 jours. | — |
| **C. Mode en ligne dans l'app** ✅ iOS + Android | L'écran de partie lit et écrit la session ; écrans créateur/participant unifiés ; rattrapage par numéro ; saisie bloquée hors ligne ; partie suivante par n'importe quel participant. | B |
| **D. « Qui es-tu ? »** ✅ iOS + Android | Association à l'arrivée, liaison durable dans les deux sens, notification du créateur avec annulation, « Je regarde seulement ». | A, C |
| **E. Historique partagé** ✅ iOS + Android | Enregistrement de la partie complète chez chaque participant connecté ; boîte aux lettres chiffrée par profil pour les absents, qui remplace les résumés. | A, C |
| **F. Écran verrouillé** ✅ iOS + Android | Push envoyé par l'appareil qui saisit, indépendant du créateur. | C |
| **G. Android** ✅ ([doc 17](17-recette-croisee.md)) | Même protocole, tests de compatibilité croisés. | A–F |
| **H. Docs, site, recette** ✅ | Docs 09 et 14 réécrites, ADR, politique de confidentialité (14 jours, parties complètes chiffrées), scénarios « créateur éteint » et « ami absent ». | toutes |
