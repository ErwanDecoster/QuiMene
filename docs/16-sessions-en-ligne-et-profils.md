# 16 — Sessions en ligne et profils

Pourquoi et comment les parties partagées reposent sur des sessions stockées côté serveur, et
pourquoi chaque utilisateur a un profil. Le fonctionnement détaillé est décrit en
[09 — Partie partagée](09-partie-partagee.md) et [14 — Profils partagés](14-profils-partages.md) ;
la décision est actée par [ADR-0017](13-decisions-adr.md).

## Pourquoi

Le premier modèle de partage avait deux limites, remontées à l'usage :

1. **Une partie partagée s'arrêtait avec le téléphone de l'hôte.** L'hôte validait, enregistrait
   et redistribuait chaque manche ; le serveur ne faisait que relayer des messages, sans rien
   garder. Hôte éteint ou en veille : plus rien n'avançait.
2. **L'historique commun était incomplet.** Un ami lié ne recevait qu'un résumé (classement
   final), et seulement après un échange de QR de profil fait à part. L'identité de
   l'utilisateur restait implicite.

## Principe : deux modes

- **Mode local** (par défaut) — la partie vit sur l'appareil, sans réseau.
- **Mode en ligne** — dès que le créateur touche « Partager », il ouvre une **session** sur le
  serveur. Chaque partie de la session y est stockée (journal d'événements chiffré) et c'est ce
  journal qui fait foi pour **tous** les appareils, créateur compris. Chacun en garde une copie
  locale pour l'affichage. N'importe quel participant peut saisir, même si le créateur est
  éteint.
- **Fin de session** — le créateur l'arrête (« Terminer la session » sous les résultats), sinon
  elle s'arrête d'elle-même après **6 h sans activité**. Les parties sont déjà dans l'historique
  de chaque participant ayant un profil ; les données serveur sont supprimées, sauf les
  livraisons en attente pour les amis absents.

## Décisions

| Sujet | Décision |
|---|---|
| Chiffrement | **De bout en bout pour l'historique partagé** (boîte aux lettres : clé tirée de l'identifiant de profil, que le serveur ne voit pas). **Sessions : chiffrées, mais pas de bout en bout vis-à-vis de l'opérateur** — la clé dérive du code à 6 chiffres que la base conserve (doc 09, « Sécurité et vie privée »). La validation des règles se fait sur l'appareil qui saisit (même moteur partout, garanti par les golden files). |
| Ordre des manches | Numéro de séquence attribué par le serveur ; un ajout annonce le numéro attendu, refusé si quelqu'un l'a devancé. L'écrasement d'une manche devient impossible par construction. |
| Conservation | Session : **24 h** après sa fin (14 jours au plus, filet de sécurité). Livraisons en attente : 14 jours. |
| Saisie hors ligne en mode en ligne | **Bloquée** : bouton grisé « Hors connexion », l'écran rattrape au retour du réseau. Pas de file d'attente (conflits). Une partie entièrement hors réseau se joue en mode local. |
| Arrêt de la session | **Créateur**, ou d'elle-même après **6 h** sans activité (ni manche ni nouvelle partie) : un oubli la laisserait sinon ouverte des jours chez tout le monde. Une session couvre toutes ses parties successives. |
| Partie suivante dans une session | **N'importe quel participant** peut la lancer (mêmes joueurs), pour que la session ne dépende pas du téléphone du créateur. |
| Historique | **Chaque participant ayant un profil** garde la partie complète (toutes les manches, pas un résumé), comme le créateur. |
| Rejoindre | Écran « Qui es-tu dans cette partie ? » (s'associer à une fiche du créateur), avec **« Je regarde seulement »** pour les spectateurs et ceux sans profil. |
| Confiance | Le créateur voit « Théo s'est associé à la fiche Théo », avec annulation. Une fiche déjà liée à un autre profil ne peut pas être revendiquée. |
| Profil | **Obligatoire** (pseudo et avatar, aucun compte) : l'écran de création recouvre l'app tant qu'aucun profil n'existe. « Supprimer mon profil » garde la fiche et son historique, retire son statut de profil, puis impose d'en créer un nouveau. |
| Onglets | Joueurs · Jeux · Historique · Profil. « Rejoindre une partie » est un bouton (Jeux, Profil) ; lien et QR système ouvrent toujours l'app. Réglages dans Profil. |

## Parcours utilisateur

1. **Premier lancement** — « Créer mon profil » (pseudo, avatar), obligatoire. Sur un nouvel
   appareil avec iCloud, le profil existant arrive seul.
2. **Marion lance une session** — jeu, joueurs, « Partager » : code et QR, badge « En ligne »
   dans l'en-tête. Ses parties suivantes de la soirée sont partagées jusqu'à l'arrêt de la
   session.
3. **Théo rejoint** — scan, « Qui es-tu ? », il touche « Théo ». Son profil est désormais lié à
   la fiche « Théo » de Marion, durablement, sans échange de QR de profil à part.
4. **Le téléphone de Marion s'éteint** — la partie continue ; un retardataire peut rejoindre avec
   le code ; les écrans verrouillés se mettent à jour. Marion rattrape tout à son retour.
5. **Fin de partie** — la partie complète arrive dans l'historique de chaque participant ayant
   un profil. Un ami lié absent la reçoit à sa prochaine ouverture de l'app.
6. **Marion termine la session** — « Terminer la session » sous les résultats (ou oubli : elle
   s'arrête d'elle-même après 6 h sans activité). Chaque appareil le constate aussitôt : plus de
   bandeau de reprise, « La session partagée est terminée » une fois dans Jeux. Suppression des
   données serveur 24 h plus tard (hors livraisons en attente, 14 jours au plus).
7. **Soirée sans réseau** — les parties se jouent en mode local ; celles jouées avec des amis
   liés leur sont livrées au retour du réseau.

**Onglet Profil** : avatar et pseudo, QR « M'ajouter comme ami », statistiques, amis liés,
« Rejoindre une partie », réglages.

## Découpage

La fonctionnalité a été livrée en huit phases, sur iOS puis Android ; les commentaires du code y
renvoient (« doc 16, phase D »).

| Phase | Contenu |
|---|---|
| **A. Profil local** | Onglet Profil, création obligatoire au premier lancement, profil explicite (`sharedProfileIsMine`), amis liés, doublon de profil arrivé par iCloud résolu au lancement. |
| **B. Sessions serveur** | Migration `create_quimene_sessions` : tables session et événements chiffrés, fonctions SQL (ouvrir, résoudre un code, ajouter avec numéro attendu, lire depuis un numéro, fermer), notification des appareils, expiration. |
| **C. Mode en ligne dans l'app** | `OnlineSession` (journal de session), `SessionLink` (rattrapage, ajout), publication du journal local par le créateur, reprise après redémarrage, partie suivante par n'importe quel participant. Format commun : `spec/session/sealed-events.json`. |
| **D. « Qui es-tu ? »** | Identités dans le même journal (`SessionIdentity`) : revendication, annulation, registre du créateur ; badge « Moi » et lien sur les amis. Format commun : `spec/session/identity-events.json`. |
| **E. Historique partagé** | Partie complète enregistrée chez chaque participant connecté (`MatchConnectionCoordinator.keep`) ; boîte aux lettres chiffrée par profil pour les absents (`create_quimene_match_mailbox`, `MailboxCrypto`). Format commun : `spec/session/mailbox-package.json`. |
| **F. Écran verrouillé** | L'appareil qui enregistre un événement, iOS ou Android, met à jour l'écran verrouillé des iPhone de la session (`quimene-live-activity-push`). |
| **G. Compatibilité croisée** | Fichiers de référence dans les deux sens et scénarios sur appareils ([17](17-recette-croisee.md)). |
| **H. Documentation et nettoyage** | Docs 09 et 14, ADR-0017, politique de confidentialité ; ancien partage retiré du code et du serveur (migration `drop_legacy_sharing`). |
