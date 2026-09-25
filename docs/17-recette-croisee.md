# 17 — Recette croisée iOS ↔ Android

Doc [16](16-sessions-en-ligne-et-profils.md), phase G. Les deux applications parlent le même
protocole : journal de session chiffré, identités, boîte aux lettres, mises à jour d'écran
verrouillé. Deux garde-fous le vérifient.

## 1. Fichiers de référence (automatiques)

Chaque format échangé a un fichier de référence **dans chaque sens**, produit par le code réel
d'une plateforme et relu par les tests de l'autre (`spec/session/`, voir `spec/README.md`) :

| Format | iOS → Android | Android → iOS |
|---|---|---|
| Événements de partie scellés (toutes les sortes) | `sealed-events.json` | `android-sealed-events.json` |
| Identités (« Qui es-tu ? ») | `identity-events.json` | `android-identity-events.json` |
| Boîte aux lettres (partie complète) | `mailbox-package.json` | `android-mailbox-package.json` |
| Mise à jour d'écran verrouillé | — (Android n'en reçoit pas) | `android-live-activity-push.json` |

Régénérer après un changement de format, puis relancer les tests des **deux** plateformes :

- iOS : `TEST_RUNNER_QUIMENE_WRITE_SPEC=1 xcodebuild test -scheme QuiMeneKit-Package …`
- Android : `QUIMENE_WRITE_SPEC=1 ./gradlew :sync:test`, puis `./gradlew :sync:test` (la copie
  des ressources de test précède l'écriture).
- Puis recopier `spec/session/*.json` dans `apple/QuiMeneKit/Tests/SyncTests/SessionResources/` :
  le lanceur de tests du simulateur ne peut pas lire `~/Documents`, les tests Swift lisent donc
  cette copie, que `Scripts/check-spec-sync.sh` vérifie identique à `spec/`.

Un fichier régénéré sans changement de format ne diffère que par ses nonces aléatoires : ne pas
le commiter.

## 2. Scénarios sur appareils

À dérouler avant une publication, avec **un iPhone et un Android**, chacun tour à tour créateur
et participant (colonnes C/P). Chaque ligne se coche deux fois.

| # | Scénario | Attendu | iOS C / Android P | Android C / iOS P |
|---|---|---|---|---|
| 1 | Partager une partie, rejoindre par QR puis par code | Le tableau s'affiche chez le participant ; présence des deux côtés, avec les pseudos de profil | ☐ | ☐ |
| 2 | « Qui es-tu ? » : choisir sa place | Bandeau « X s'est associé à la fiche Y » chez le créateur ; « Moi » sur la place | ☐ | ☐ |
| 3 | « Annuler » chez le créateur | Le participant revient à « Qui es-tu ? » avec le message | ☐ | ☐ |
| 4 | Rejoindre une nouvelle session | Participant reconnu d'office ; « Changer » possible, l'ancienne place se libère | ☐ | ☐ |
| 5 | Saisir des manches des deux côtés, dont des négatifs et « Ferme » | Même tableau partout ; « X vient de valider une manche » si devancé | ☐ | ☐ |
| 6 | Couper le réseau du participant, puis le rétablir | Saisie bloquée « Hors connexion », rattrapage au retour | ☐ | ☐ |
| 7 | Créateur éteint, le participant continue | Les manches s'enregistrent ; le créateur rattrape à son retour | ☐ | ☐ |
| 8 | iPhone verrouillé pendant que l'autre saisit | Écran verrouillé mis à jour, puis retiré à la fin de partie | ☐ | ☐ |
| 9 | « Partie suivante » lancée par le participant | Tous les appareils basculent ; même jeu, mêmes places | ☐ | ☐ |
| 10 | Fin de partie | Partie complète dans l'historique du participant, marquée « Reçue » | ☐ | ☐ |
| 11 | Partie locale avec un ami lié absent | Reçue à l'ouverture de son Historique (tirer pour actualiser) | ☐ | ☐ |
| 12 | Abandon pendant que le participant choisit sa place | Le participant voit les résultats, pas une liste de places | ☐ | ☐ |
| 13 | « Arrêter le partage » | Feuille fermée ; « Le créateur a arrêté la session » chez le participant | ☐ | ☐ |
| 14 | Relancer l'app du créateur et du participant en pleine partie | Les deux reprennent la session sans code | ☐ | ☐ |
