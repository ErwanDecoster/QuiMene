# 03 — Modèle de données

Deux modèles coexistent, délibérément :

| | Domaine (`Domain`) | Persistance (`Store`) |
|---|---|---|
| Nature | `struct` immuables, `Sendable` | `final class @Model` SwiftData |
| Rôle | calcul, règles, échange entre appareils | stockage disque + sync iCloud |
| Durée de vie | le temps d'un calcul | des années |
| Testé par | golden files | tests de repositories et de schéma |

Le mapping entre les deux est explicite, dans les repositories. C'est le prix à payer pour que
les règles de jeu ne dépendent jamais du schéma disque — donc pour qu'une migration SwiftData
ne casse jamais un calcul de score, et pour qu'Android n'ait pas à reproduire SwiftData.

---

## Modèle de persistance (SwiftData)

Trois modèles, déclarés dans `QuiMeneSchemaV1` (`Store/Schema.swift`). Android les reproduit un
pour un en entités Room (`PlayerEntity`, `MatchEntity`, `ParticipantEntity`).

### PlayerRecord

La fiche joueur, réutilisée d'une partie à l'autre.

| Champ | Type | Notes |
|---|---|---|
| `id` | `UUID` | stable, généré à la création, jamais réattribué |
| `nickname` | `String` | pseudo affiché |
| `avatarKind` | `String` | `"emoji"` \| `"photo"` (`"symbol"` reste lu pour les anciennes fiches) |
| `avatarValue` | `String` | emoji, ou `""` si photo |
| `avatarPhoto` | `Data?` | `@Attribute(.externalStorage)`, JPEG 512 px max |
| `paletteID` | `String` | `"1"`…`"10"`, voir [charte §1.5](07-charte-graphique.md#15-palette-des-joueurs) |
| `createdAt` | `Date` | |
| `isArchived` | `Bool` | masqué des sélections, conservé dans l'historique |
| `sortIndex` | `Int` | ordre d'ajout ; départage le tri de la liste (les habitués d'abord) |
| `sharedProfileID` | `UUID?` | identifiant de profil partageable, jamais régénéré ([14](14-profils-partages.md)) |
| `sharedProfileIsMine` | `Bool` | `true` pour la fiche qui représente l'utilisateur de l'appareil |
| `sharedProfileLinkedName` / `sharedProfileLinkedAt` | `String?` / `Date?` | pseudo et date au moment de la liaison à un ami |

Pas de contrainte d'unicité sur `nickname` : deux Alice sont autorisées, la couleur et
l'avatar les distinguent.

### MatchRecord

Une partie, du premier tap à l'archivage.

| Champ | Type | Notes |
|---|---|---|
| `id` | `UUID` | |
| `gameID` | `String` | ex. `"skyjo"`, référence le catalogue |
| `rulesVersion` | `Int` | version des règles au moment de la partie — **ne jamais recalculer une vieille partie avec des règles récentes** |
| `variantsData` | `Data` | options choisies, encodées en JSON |
| `startedAt` / `endedAt` | `Date` / `Date?` | |
| `statusRaw` | `String` | `"inProgress"` \| `"finalRound"` \| `"ended"` \| `"abandoned"`, exposé en `status` |
| `endReasonRaw` | `String?` | condition de fin déclenchée |
| `isArchived` | `Bool` | masque la partie de l'Historique ; les statistiques la comptent toujours |
| `deviceOrigin` | `String` | `"local"`, ou `"received"` pour une partie jouée sur un autre appareil |
| `eventLogData` | `Data` | journal d'événements — **la source de vérité** |
| `participants` | `[ParticipantRecord]` | cascade |

`eventLogData` contient la vérité : la reprise d'une partie, son détail manche par manche et
ses résultats rejouent ce journal ([04](04-moteur-de-regles.md#event-sourcing)), ils ne lisent
jamais un total mis en cache. Seuls `finalRank`/`finalScore` des participants sont écrits à la
fin, pour que l'historique et les statistiques n'aient pas à rejouer chaque partie.

Deux champs servent l'historique partagé ([14](14-profils-partages.md)) :

- `pendingSharedProfileSync` passe à `true` à la fin d'une partie dont un participant est lié à
  un ami, jusqu'à ce qu'elle ait été déposée dans sa boîte aux lettres (nouvelle tentative à
  chaque retour au premier plan) ;
- `isImportedSummary` marque les parties reçues par un mécanisme antérieur, sous forme de
  résumé sans journal : seuls les rangs et scores des participants y font foi.

### ParticipantRecord

Un joueur **dans une partie donnée**.

| Champ | Type | Notes |
|---|---|---|
| `id` | `UUID` | |
| `player` | `PlayerRecord?` | relation, règle de suppression `.nullify` |
| `nicknameSnapshot` | `String` | figé à la création de la partie |
| `avatarKindSnapshot` / `avatarValueSnapshot` / `paletteIDSnapshot` | `String` | figés |
| `seatIndex` | `Int` | ordre de jeu |
| `teamID` | `String?` | jeux par équipes (Belote), `nil` sinon |
| `finalRank` | `Int?` | rempli à la fin, rangs ex æquo partagés |
| `finalScore` | `Int?` | |
| `match` | `MatchRecord?` | inverse de `MatchRecord.participants` |

Le *snapshot* est essentiel : si Marion renomme « Théo » en « Théo-le-tricheur » un an plus
tard, la partie d'origine doit continuer d'afficher « Théo ». Et si la fiche est supprimée,
l'historique reste lisible — d'où `player` optionnel avec la règle `.nullify`.

### Réglages

`AppSettings` (consentement à la synchronisation iCloud) vit dans `UserDefaults`, **hors** du
schéma SwiftData : le container CloudKit ne peut pas porter le réglage qui décide de son
existence.

---

## Contraintes CloudKit — respectées dès la première ligne

SwiftData + CloudKit impose des règles au schéma. Les violer se découvre au *runtime*, avec un
container qui refuse de s'ouvrir ou de synchroniser. Elles sont donc traitées comme des
invariants de conception ([ADR-0006](13-decisions-adr.md)).

1. **Aucun `@Attribute(.unique)`.** CloudKit ne connaît pas les contraintes d'unicité.
   L'unicité de `id` est garantie par la génération d'`UUID`, pas par le schéma.
2. **Toute propriété a une valeur par défaut, ou est optionnelle.** Sans exception, y compris
   les `Bool` et les `Int`.
3. **Toute relation est optionnelle et possède une relation inverse déclarée.** Les relations
   vers plusieurs sont elles-mêmes de type optionnel (`[T]?`) : `participantsStorage` et
   `participationsStorage` sont exposés par des propriétés calculées non optionnelles, sans
   changement pour le code appelant. `CloudKitSchemaTests` ouvre le vrai schéma avec un
   container CloudKit actif et échoue si une règle est enfreinte.
4. **Pas de règle de suppression `.deny`.** Seules `.cascade` et `.nullify` sont supportées.
5. **Pas d'ordre implicite dans les collections.** SwiftData ne préserve pas l'ordre d'un
   tableau de relations. D'où `seatIndex`/`sortIndex` explicites, et un tri systématique à la
   lecture.
6. **Les `enum` sont stockés en `String`** (`statusRaw`, `avatarKind`), jamais en `Int` brut :
   une valeur inconnue arrivant d'une version plus récente doit dégrader proprement, pas
   planter.

Configuration (`QuiMeneApp.loadContainer`) : CloudKit privé (`iCloud.com.quimene.app`) si
l'utilisateur l'a accepté, sinon stockage local. Le magasin vit dans le conteneur de l'App Group
(`group.com.quimene.app`). Si l'ouverture échoue, l'app retombe sur un magasin local, puis en
mémoire, plutôt que de ne pas démarrer. Un changement de réglage s'applique au lancement suivant
plutôt que par un remplacement à chaud du `ModelContainer`.

## Résolution de conflits

Deux appareils peuvent modifier la même partie hors ligne. CloudKit applique un
« dernier écrivain gagne » par enregistrement, ce qui produirait des scores incohérents si on
s'y fiait pour les manches.

C'est précisément pourquoi la vérité est le **journal d'événements** :

- fusionner deux journaux = les concaténer, dédoublonner par identifiant d'événement, trier par
  `(lamport, deviceID)`, rejouer ;
- l'opération est associative, commutative et idempotente — donc sûre quel que soit l'ordre
  d'arrivée ;
- `eventLogData` est un `Data` opaque pour CloudKit ; l'interprétation est faite par
  l'application, pas par CloudKit.

Pour une partie partagée en ligne, l'ordre est donné par le serveur ([09](09-partie-partagee.md)).

## Migrations

`VersionedSchema` + `SchemaMigrationPlan` posés dès la première version, même avec une seule
version : créer le plan de migration après coup coûte bien plus cher que de le poser vide.

```swift
public enum QuiMeneSchemaV1: VersionedSchema {
  public static var versionIdentifier: Schema.Version { Schema.Version(1, 0, 0) }
  public static var models: [any PersistentModel.Type] {
    [PlayerRecord.self, MatchRecord.self, ParticipantRecord.self]
  }
}

public enum QuiMeneMigrationPlan: SchemaMigrationPlan {
  public static var schemas: [any VersionedSchema.Type] { [QuiMeneSchemaV1.self] }
  public static var stages: [MigrationStage] { [] }
}
```

Règle : **une migration légère par version publiée au maximum**. Si un changement exige une
migration lourde, ajouter un champ optionnel et le remplir paresseusement plutôt que réécrire
le magasin.

`rulesVersion` sur `MatchRecord` joue le même rôle côté métier : les parties anciennes sont
rejouées avec le moteur de leur époque, conservé dans le catalogue. Un score enregistré ne
change jamais rétroactivement.

## Volumétrie

Un usage intensif — 200 parties par an, 6 joueurs, 20 manches — représente quelques mégaoctets
de journaux. Aucune contrainte de performance : pas d'index à ajouter, pas de pagination
nécessaire. Seules les photos d'avatar justifient `.externalStorage`.
