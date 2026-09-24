# 16 — Sessions en ligne et profils

Statut : **plan validé le 24 septembre 2026**, implémentation à venir phase par phase. Ce document
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
| Chiffrement | **De bout en bout**. Le serveur ne lit ni pseudos ni scores ; la validation des règles se fait sur l'appareil qui saisit (même moteur partout, garanti par les golden files). |
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

| Phase | Contenu | Dépend de |
|---|---|---|
| **A. Profil local** ✅ iOS + Android | Page Profil, création au premier lancement, onglets réorganisés, profil explicite (plus « la fiche partagée »), amis liés, profil sauvegardé via iCloud. | — |
| **B. Sessions serveur** ✅ (migration `create_quimene_sessions`) | Tables session / parties / événements chiffrés, fonctions SQL (créer, ajouter avec numéro attendu, lire depuis un numéro, fermer), notification des appareils, expiration 14 jours. | — |
| **C. Mode en ligne dans l'app** ✅ iOS + Android | L'écran de partie lit et écrit la session ; écrans créateur/participant unifiés ; rattrapage par numéro ; saisie bloquée hors ligne ; partie suivante par n'importe quel participant. | B |
| **D. « Qui es-tu ? »** | Association à l'arrivée, liaison durable dans les deux sens, notification du créateur avec annulation, « Je regarde seulement ». | A, C |
| **E. Historique partagé** | Enregistrement de la partie complète chez chaque participant connecté ; boîte aux lettres chiffrée par profil pour les absents, qui remplace les résumés. | A, C |
| **F. Écran verrouillé** | Push envoyé par l'appareil qui saisit, indépendant du créateur. | C |
| **G. Android** | Même protocole, tests de compatibilité croisés. | A–F |
| **H. Docs, site, recette** | Docs 09 et 14 réécrites, ADR, politique de confidentialité (14 jours, parties complètes chiffrées), scénarios « créateur éteint » et « ami absent ». | toutes |
