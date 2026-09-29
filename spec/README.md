# `spec/` — Spécification partagée

**Source de vérité inter-plateformes.** Ce dossier ne contient que du JSON. Ni Swift, ni
Kotlin, ni aucun code exécutable.

Les deux implémentations — Apple et Android — lisent ces fichiers et rejouent ces parties.
C'est le seul mécanisme qui garantit qu'un score calculé sur iPhone est identique à celui
calculé sur un Pixel, sans partager une ligne de code.

```
spec/
├── schema/game-definition.schema.json   contrat de format (JSON Schema 2020-12)
├── games/*.json                         définitions déclaratives des jeux
├── golden/*.json                        parties complètes + résultats attendus
├── session/*.json                       formats échangés entre appareils, produits par le code
│                                         réel d'une plateforme et relus par l'autre (doc 17)
└── screenshots/demo-data.json           joueurs et parties de démo des captures des stores
                                          (Scripts/store-screenshots.sh, doc 10)
```

## Règle d'or

> **Une modification de règle commence toujours ici, jamais dans le code.**

L'ordre est strict :

1. Modifier ou créer `games/<id>.json`, valider contre le schéma.
2. Écrire ou mettre à jour les `golden/<id>-*.json`.
3. Faire passer les tests Swift.
4. Faire passer les tests Kotlin.

Écrire le golden avant l'implémentation n'est pas un rituel de TDD : c'est ce qui permet à la
seconde plateforme d'être écrite **sans lire le code de la première**.

## Versionnage

Deux axes indépendants, à ne pas confondre :

| Champ | Porte sur | Change quand |
|---|---|---|
| `specVersion` | le **format** de `GameDefinition` | on ajoute ou modifie un champ du schéma |
| `rulesVersion` | les **règles d'un jeu** | un calcul de score change, pour quelque raison que ce soit |

Incrémenter `rulesVersion` est **obligatoire** dès qu'un résultat peut changer, même pour une
correction de bug. Chaque partie enregistrée mémorise la version avec laquelle elle a été
jouée, et les anciens moteurs sont conservés indéfiniment (`SkyjoRulesV1` reste après l'arrivée
de `SkyjoRulesV2`). Un score affiché ne change jamais rétroactivement.

Les golden files déclarent la version qu'ils testent : les anciens restent en place et
continuent de tourner.

## Synchronisation avec le code

Les définitions sont embarquées dans les applications, jamais téléchargées — l'app fonctionne
hors ligne. Côté Apple, SwiftPM exige des ressources locales à la cible : `games/` est copié dans
`apple/QuiMeneKit/Sources/Catalog/GameDefinitions/` (et les golden files et fixtures dans les
dossiers de ressources des tests). `Scripts/check-spec-sync.sh` **échoue si une copie diffère** ;
la CI le lance des deux côtés. `spec/` est la source ; les copies ne sont jamais éditées à la
main. (Le dossier ne s'appelle délibérément pas `Resources` : un dossier de ce nom copié tel quel
dans un bundle fait planter `codesign` sur certaines versions de macOS/Xcode.) Côté Android, une
tâche Gradle recopie `spec/` à chaque build, sans copie commitée.

## Ajouter un jeu

1. `games/<id>.json` — valider contre le schéma, traduire nom et libellés dans les cinq langues
2. Des golden files couvrant une partie nominale et, s'il existe, le cas limite qui fait la
   particularité du jeu
3. Si `engine` ≠ `generic.sum.v1`, implémenter `GameRules` en Swift et en Kotlin jusqu'à ce que
   les golden passent
4. Enregistrer l'`engineID` dans la table de chaque catalogue (un test vérifie l'exhaustivité)
5. Recopier dans les copies Apple (`Scripts/check-spec-sync.sh`)

## Format d'un golden file

```jsonc
{
  "goldenId": "…",           // identifiant unique, sert de nom de cas de test
  "gameId": "skyjo",
  "rulesVersion": 1,
  "variants": { … },         // options de partie
  "participants": [ … ],     // identifiants stables, noms lisibles
  "rounds": [ … ],           // saisies brutes, dans l'ordre
  "expected": {
    "roundResults": [ … ],   // après CHAQUE manche : scores calculés, cumuls, statut
    "final": { … },          // raison de fin, classement
    "insights": [ … ]        // statistiques notables (sous-ensemble vérifié)
  }
}
```

Vérifier l'état **après chaque manche**, et pas seulement à la fin, est délibéré : une erreur
de calcul qui se compense entre deux manches passerait sinon inaperçue.

Les `insights` attendus sont un **sous-ensemble** : le test vérifie que ceux listés sont
présents et exacts, sans exiger l'exhaustivité. Les statistiques évoluent plus vite que les
règles, et un golden ne doit pas casser parce qu'un nouvel indicateur a été ajouté.

## Format d'une fixture `session/`

Doc [09](../docs/09-partie-partagee.md) — un événement de session en ligne est un
`StampedEvent` en JSON, scellé en AES-GCM avec la clé de session (code d'appairage + identifiant
de session), puis en base64. `sealed-events.json` est **généré par le code Swift réel**
(`OnlineSession.seal`) avec un code et une session fixes : `plaintext` est le JSON que produit
`JSONEncoder`, `ciphertext` sa version scellée. Le test Android déchiffre `ciphertext` et doit
retrouver exactement l'événement décrit par `plaintext`. Le nonce étant aléatoire, l'inverse
(identité d'octets chiffrés) n'est ni atteignable ni pertinent.

`identity-events.json` suit le même principe pour les événements d'identité :
`plaintext` est l'enveloppe `{"identity": …}` produite par `JSONEncoder`, régénérée par
`QUIMENE_WRITE_SPEC=1` sur `SessionIdentityTests` (via `TEST_RUNNER_QUIMENE_WRITE_SPEC=1` avec
`xcodebuild test`).

`mailbox-package.json` : une partie complète (`SharedMatchPackage`) scellée
pour un profil (`MailboxCrypto`), avec l'adresse de sa boîte (`mailboxKey`, empreinte de
l'identifiant) : Android doit retrouver la même adresse et le même paquet.

Dans l'autre sens, `android-*.json` sont produits par le code Kotlin réel
(`AndroidFixturesTest`) et relus par `AndroidFixtureTests` (Swift), qui reconstruit les mêmes
valeurs indépendamment : événements de partie de toutes les sortes, identités, boîte aux lettres,
mise à jour d'écran verrouillé. Côté Apple, ces fichiers sont lus depuis leur copie
`Tests/SyncTests/SessionResources/` (vérifiée par `Scripts/check-spec-sync.sh`). Procédure de
régénération : doc [17](../docs/17-recette-croisee.md).
