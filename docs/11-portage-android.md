# 11 — Portage Android

## Stratégie

**Ré-implémentation 100 % native, pilotée par une spécification partagée.**

Aucune couche de partage de code : pas de Kotlin Multiplatform, pas de Swift compilé pour
Android, pas de moteur JavaScript. Les deux applications sont pleinement idiomatiques sur leur
plateforme. Ce qu'elles partagent n'est pas du code, c'est **`spec/`** — du JSON, lu et rejoué
par les deux.

```
                    spec/                   source de vérité
        ┌──────────────┴──────────────┐
        │  games/*.json               │     définitions déclaratives
        │  golden/*.json              │     parties + résultats attendus
        │  schema/*.schema.json       │     contrat de format
        └──────────────┬──────────────┘
          ┌────────────┴────────────┐
          ▼                         ▼
   ┌─────────────┐          ┌──────────────┐
   │  Swift 6    │          │  Kotlin      │
   │  SwiftUI    │          │  Compose     │
   │  SwiftData  │          │  Room        │
   └─────────────┘          └──────────────┘
     rejoue golden/           rejoue golden/
```

**Le coût** : le moteur de règles s'écrit deux fois, soit environ 2 000 lignes dupliquées.
**Le bénéfice** : zéro compromis d'idiome, zéro outillage croisé (pas de Gradle dans le build
iOS), et deux apps qui ressemblent chacune à leur plateforme. Sur une application dont
l'interface représente 80 % du code et dont le moteur est de l'arithmétique pure et stable,
c'est le bon arbitrage. Décision et alternatives : [ADR-0004](13-decisions-adr.md).

**Le garde-fou** : les golden files. Une divergence de calcul entre les deux plateformes est
impossible à ignorer — elle fait échouer la suite de tests Android.

---

## Équivalences techniques

| Apple | Android | Note |
|---|---|---|
| Swift 6 (concurrence stricte) | Kotlin 2.x + coroutines | `Sendable` → immuabilité + `data class` |
| SwiftUI | Jetpack Compose | Modèle déclaratif équivalent |
| `@Observable` | `StateFlow` dans un `ViewModel` | Compose n'a pas d'équivalent d'observation implicite |
| `@MainActor` | `Dispatchers.Main` | |
| `struct` / valeur | `data class` (immuable) | Le domaine reste sans mutation |
| SwiftData | Room + KSP | Voir « Persistance » |
| CloudKit privé | *(aucun équivalent)* | Voir « Synchronisation » |
| `supabase-swift` (`RealtimeChannelV2`, Presence, Broadcast) | `supabase-kt` (module Realtime) | Même dépendance des deux côtés, pas une paire d'équivalents natifs — voir [09](09-partie-partagee.md) et [ADR-0016](13-decisions-adr.md). Découverte par `cacompte_open_games` (Postgrest, REST) : rien de spécifique à une plateforme, aucune API de découverte réseau à porter. |
| Swift Testing | JUnit 5 + Kotest | Tests paramétrés des deux côtés |
| Swift Charts | Vico | Bibliothèque tierce assumée — Compose n'a pas de graphiques natifs |
| SF Symbols | Material Symbols Rounded | Voir charte §4 |
| `ImageRenderer` | `GraphicsLayer.toImageBitmap()` | Image de résultats partagée |
| WidgetKit | Glance | |
| App Intents | App Actions + `ShortcutService` | |
| Live Activity | Notification persistante + `MediaStyle` | Équivalent partiel |
| Xcode Cloud | GitHub Actions | |
| `Localizable.xcstrings` | `strings.xml` | Pas de `<plurals>` à gérer — le projet n'utilise déjà pas les variations plurielles côté `.xcstrings` (convention « partie(s) » en toutes lettres), voir étape G |

**Deux dépendances tierces, de nature différente** (ADR-0012, [ADR-0016](13-decisions-adr.md)).
Vico est **propre à Android** : Compose n'a pas d'équivalent natif à Swift Charts, c'est l'écart
d'écosystème le plus concret du projet. Supabase (`supabase-swift`/`supabase-kt`) est en
revanche **symétrique** : voulue des deux côtés dès le départ pour le transport de la partie
partagée, pas un choix propre à une plateforme avec un équivalent à inventer sur l'autre.

## Persistance

Room remplace SwiftData, avec deux différences à anticiper :

- **Pas de synchronisation intégrée.** SwiftData+CloudKit fait gratuitement ce que Room ne fait
  pas du tout.
- **Les relations sont explicites.** Room impose `@Relation` et des requêtes ; SwiftData les
  résout seul. En pratique cela avantage Android : l'ordre des collections y est déterministe
  par `ORDER BY`, alors que SwiftData impose les champs `index` explicites décrits en
  [03](03-modele-de-donnees.md).

Les entités sont transposées une pour une (`PlayerRecord` → `PlayerEntity`, etc.), avec le même
mapping vers le domaine. Le domaine Kotlin est identique au domaine Swift, au vocabulaire près.

## Synchronisation

La partie partagée en direct **n'est pas un point de divergence** ([ADR-0016](13-decisions-adr.md)) : Apple et Android parlent le même protocole (`WireMessage`) sur le même transport, Supabase
Realtime (canal par session, presence + broadcast) — pas deux implémentations qui s'interopèrent,
la même dépendance des deux côtés. Un iPhone et un Android rejoignent la même partie. Le seul vrai
point de divergence fonctionnel reste la synchronisation entre les appareils **du même
propriétaire**, hors partie en direct :

| Fonction | Apple | Android |
|---|---|---|
| **Partie partagée en direct** | Supabase Realtime — protocole et transport communs | idem, `supabase-kt` |
| **Sync entre appareils du propriétaire** | CloudKit privé, transparent | **Absent en v1** |
| **Sauvegarde** | iCloud | Android Auto Backup (quota 25 Mo, suffisant) |
| **Export / import** | fichier `.cacompte` | fichier `.cacompte` |

Il n'existe pas d'équivalent Android à CloudKit : pas de stockage privé, gratuit, lié au compte
système et synchronisé sans serveur. Les options seraient Google Drive App Data (API lourde,
nécessite OAuth) ou un backend maison (contredit « sans serveur »).

**Décision** : la v1 Android n'a pas de synchronisation multi-appareils. Elle propose à la
place un **export/import de fichier**, qui existe aussi côté Apple et sert de pont entre les
deux écosystèmes. Le format `.cacompte` est simplement le journal d'événements sérialisé — donc
déjà spécifié, déjà testé, et fusionnable par la même fonction de rejeu.

## Charte graphique sur Android

La [charte](07-charte-graphique.md) est écrite pour être bi-plateforme : chaque token y porte
déjà sa colonne Android. Les points d'attention au portage :

| Sujet | Règle |
|---|---|
| **Couleurs** | Valeurs hexadécimales **identiques**, injectées dans un `ColorScheme` Material 3 custom. On n'utilise **pas** `dynamicColor` (Material You) : il écraserait la palette de marque et casserait les contrastes vérifiés. |
| **Typographie** | Échelle **Material 3**, pas les tailles iOS. La hiérarchie est partagée, la mesure ne l'est pas — voir charte §2.2. `letterSpacing` doit être posé explicitement, contrairement à iOS. |
| **Espacements et rayons** | Valeurs identiques, en `dp`. 1 pt iOS ≈ 1 dp Android. |
| **Élévation** | Rendu Material (tonal + ombre), pas de verre. C'est l'inverse exact d'iOS et c'est voulu — [ADR-0010](13-decisions-adr.md). |
| **Icônes** | Material Symbols Rounded, `weight 400`, `grade 0`, `optical size 24`, `fill 0/1`. |
| **Zone tactile** | **48 dp** (Android), pas 44 pt. Le pavé numérique reste à 56 dp. |
| **Navigation** | `NavigationBar` Material en bas, pas la tab bar flottante iOS. Bouton retour système géré par `BackHandler`. |
| **Toasts** | `Snackbar` Material en bas, pas de bandeau en haut. |
| **Mouvement** | Mêmes durées, courbes Material (`emphasizedDecelerate` / `emphasizedAccelerate`). |

Le **logo** est identique : même SVG, mêmes déclinaisons, mêmes zones de protection. L'icône
d'application, elle, doit être fournie en **icône adaptative** (couche de fond + couche de
premier plan, zone sûre de 66 dp sur 108) — le masque Android est plus agressif que celui
d'iOS et rogne les angles de la marque si elle est fournie à plat.

## Règles de discipline côté Swift

Pour que le portage reste mécanique, le code Swift respecte quelques contraintes dès
maintenant. Elles ne coûtent rien à l'écriture et évitent une réécriture plus tard.

1. **Le domaine n'utilise que des types transposables** : `Int`, `String`, `Bool`, `UUID`,
   `Date`, tableaux, dictionnaires, `enum`, `struct`. Pas de `Measurement`, pas de
   `NSAttributedString`, pas de `KeyPath` dans une signature publique.
2. **Aucune date implicite.** Le domaine ne fait jamais `Date()` : l'instant est toujours passé
   en paramètre. Cela rend les tests déterministes et supprime la question des fuseaux au
   portage.
3. **Pas d'arithmétique de dates dans le domaine.** Les calculs de calendrier restent dans la
   couche présentation.
4. **Les noms sont partagés.** `MatchState`, `RoundDraft`, `EndCheck`, `Standing` s'appellent
   pareil en Kotlin. Une relecture croisée doit être possible sans table de correspondance.
5. **Le JSON est la seule frontière.** Le domaine encode et décode exactement les structures
   de `spec/schema/`. Aucun format de sérialisation propriétaire, aucun `NSKeyedArchiver`.
6. **Pas de `Foundation` au-delà du strict nécessaire** dans `Domain` : `UUID`, `Date`, `Data`
   et `Codable`. Rien d'autre.

## Plan de portage

| Étape | Contenu | Estimation |
|---|---|---|
| **A** | Projet Compose, thème Material 3 issu de la charte, composants de base | 1,5 sem |
| **B** | Domaine Kotlin — types, `MatchEngine`, `GameRules` génériques | 1,5 sem |
| **C** | **Golden files verts** — tous les jeux du catalogue | 1 sem |
| **D** | Room, repositories, mapping | 1 sem |
| **E** | Écrans : joueurs, configuration, partie, résultats, historique | 3 sem |
| **F** | Transport partagé — client `supabase-kt` (canal/presence/broadcast), appairage/chiffrement (`javax.crypto`), export/import | 1,5 sem |
| **G** | Accessibilité (TalkBack, échelle de police), localisation, recette | 1 sem |
| | **Total** | **~10,5 semaines** |

L'étape C est le jalon de vérité : à partir du moment où les golden files passent en Kotlin,
les deux applications calculent **prouvablement** la même chose, et le reste du portage ne
touche plus au métier.

### A — Projet Compose et thème · 1,5 semaine

- `android/` naît à la racine du mono repo, au même niveau que `apple/`, `spec/`, `docs/`,
  `supabase/`, `Licenses/`, `Scripts/`, `ci_scripts/`.
- Gradle multi-module, miroir des 5 cibles `CaCompteKit` + le module app :
  - `android/settings.gradle.kts` — `include(":app", ":domain", ":catalog", ":store", ":sync", ":designsystem")`.
  - `android/gradle/libs.versions.toml` — catalogue de versions unique (Kotlin, Compose BOM,
    Room, KSP, `supabase-kt`, Material 3, Navigation Compose, Vico), même rôle qu'une seule
    épingle de version par dépendance que joue `Package.swift` côté Apple.
  - `:domain` — Kotlin/JVM pur (`kotlin("jvm")`, pas `com.android.library`) : aucune dépendance
    au SDK Android sur le classpath, donc aucun moyen d'y importer `android.*` même par erreur.
    La contrainte « `Domain` ne connaît aucune implémentation concrète de `GameRules` » (doc 04)
    devient vérifiable par construction du module, pas seulement par convention.
  - `:catalog` — Kotlin/JVM pur, dépend de `:domain`.
  - `:store` — `com.android.library`, dépend de `:domain` (Room a besoin d'un `Context`).
  - `:sync` — Kotlin/JVM pur, dépend de `:domain` + `supabase-kt` + `ktor-client-okhttp` (le
    moteur HTTP de Ktor est un artefact JVM ordinaire, pas Android-spécifique — même contrainte
    que côté Swift où `Sync` ne dépend que de `Domain` + `supabase-swift`).
  - `:designsystem` — `com.android.library`, Compose activé, zéro dépendance vers `:domain`
    (comme `DesignSystem` dans `Package.swift`).
  - `:app` — `com.android.application`, dépend des 5 modules ci-dessus.
  - Sens de dépendance identique à `Package.swift` : `app → {domain, catalog, store, sync,
    designsystem}` ; `catalog → domain` ; `store → domain` ; `sync → domain` ; `designsystem →`
    rien.
- `:designsystem` — thème Material 3 à partir de la charte : `Space.kt`, `Radius.kt`, `Motion.kt`,
  `IconSize.kt`, `Touch.kt`, `ButtonHeight.kt` (mêmes noms que `DesignSystem/Tokens/*.swift`, en
  `dp`), `Color.kt` (`ColorScheme` custom depuis les hex de la charte §14, pas de
  `dynamicColor`), `Typography.kt` (échelle Material 3, pas un report de `Font+Tokens.swift`).
- Composants de base, mêmes noms où ça a du sens (`PrimaryButton.kt`, `SecondaryButton.kt`,
  `TertiaryButton.kt`, `ScoreField.kt`, `Card.kt`, `Chip.kt`, `Banner.kt`, `AvatarView.kt`,
  `EmptyState.kt`), implémentation idiomatique Compose.
- CI : nouveau `.github/workflows/android-ci.yml` (le dépôt n'a pas de `.github/` aujourd'hui —
  Xcode Cloud reste propre à Apple, cohérent avec la ligne « Xcode Cloud → GitHub Actions » du
  tableau d'équivalences).

**Fini quand** : le projet Gradle compile à vide, une galerie de previews Compose montre chaque
composant en clair/sombre, l'app se lance sur écran blanc sur émulateur et appareil réel, la CI
est verte.

### B — Domaine Kotlin · 1,5 semaine

- `:domain`, un fichier Kotlin pour un fichier Swift : `Model/Participant.kt`, `ScoreInput.kt`,
  `RoundDraft.kt`, `ScoreEntry.kt`, `Round.kt`, `MatchState.kt`, `MatchStatus.kt`,
  `ModifierID.kt`, `ScoreDetail.kt`, `ValidationResult.kt`, `VariantSelection.kt` ;
  `Engine/MatchEngine.kt`, `MatchEvent.kt` (`sealed interface` à 6 cas, `data class
  StampedEvent`) ; `Rules/GameCatalog.kt`, `GameDefinition.kt`, `GameRules.kt` (`interface` à 4
  méthodes, valeurs par défaut couvrant `generic.sum.v1`) ; `Stats/StatsEngine.kt`, `Insight.kt`,
  `Badge.kt`, `ParticipantSeries.kt`. `data class` immuable partout, `sealed interface`/`sealed
  class` pour les énumérations à valeur associée (pas d'équivalent natif en Kotlin).
- `:catalog` : `GameCatalogEmbedded.kt` (miroir de `GameCatalog+Embedded.swift`), table
  moteur→ID à 7 entrées (voir étape C).
- **Chargement des ressources — la contrainte SwiftPM ne s'applique pas telle quelle.**
  `Scripts/check-spec-sync.sh` existe parce que SwiftPM exige des ressources locales à la cible,
  forçant une copie physique commitée que le script vérifie. Un `sourceSet` Gradle peut pointer
  vers n'importe quel chemin relatif hors module (`resources.srcDir("../../spec/games")`) —
  `:catalog` pourrait donc en théorie lire `spec/` directement, sans copie ni script.
  **Décision retenue** : une tâche Gradle (`copySpecResources`, câblée sur `processResources`)
  qui régénère une copie de `spec/games/` dans `build/generated/resources/` à chaque build,
  jamais commitée — reproduit le geste Swift (copie locale au module) sans le risque de dérive
  qu'un `check-spec-sync.sh` étendu au monde Android devrait surveiller : la copie ne peut pas
  diverger puisqu'elle est régénérée, jamais maintenue à la main.
- Vérification golden-file-driven : `GoldenFileTests.kt` (`:catalog`, JUnit 5), même patron que
  `GoldenFile.swift`/`GoldenFileTests.swift` — décodage `spec/golden/*.json` (kotlinx.serialization),
  rejeu par `MatchEngine.replay`, comparaison stricte des totaux/`Standing`/`Insight`.

**Fini quand** : `:domain`/`:catalog` compilent, `GameCatalogEmbedded` charge les 20 définitions
sans exception, la table moteur→ID est exhaustive (test dédié, miroir du test Swift qui échoue
si un JSON référence un moteur absent).

### C — Golden files verts · 1 semaine ◆ jalon de vérité

- Catalogue réel (`spec/games/`, 20 fichiers — voir [05](05-catalogue-jeux.md)) : 14 jeux sur
  `generic.sum.v1`, aucun code (Jeu libre, Rami, 6 qui prend, Scrabble, Triominos, Cornhole,
  Flip 7, Odin, Pétanque, Pictionary, Qwixx, Rummikub, Time's Up, Trivial Pursuit) ; 6 jeux sur
  un moteur impératif dédié, un fichier Kotlin par jeu, même nom que côté Swift :
  `SkyjoRulesV1.kt`, `YamsRulesV1.kt`, `BeloteRulesV1.kt`, `MolkkyRulesV1.kt`, `TarotRulesV1.kt`,
  `WizardRulesV1.kt`.
- Les 24 golden files existants dans `spec/golden/` sont rejoués tels quels — rien de nouveau à
  écrire, ils sont déjà la spécification exécutable.
- Tests d'invariants portés en Kotlin (ex. somme nulle au Tarot à chaque donne), `SeededGenerator.kt`
  miroir de `SeededGenerator.swift`.

**Fini quand** : les 24 golden files passent en Kotlin, les invariants de propriété tiennent sur
des entrées générées — à partir de là, les deux plateformes calculent prouvablement la même
chose, et aucune étape suivante ne retouche le métier.

### D — Room, repositories, mapping · 1 semaine

- Trois entités, miroir un-pour-un des modèles SwiftData (`CaCompteSchemaV1`) :
  - `PlayerEntity.kt` — mêmes champs que `PlayerRecord.swift` (`id`, `nickname`, `avatarKind`,
    `avatarValue`, `avatarPhoto: ByteArray?`, `paletteID`, `createdAt`, `isArchived`,
    `sortIndex`, `sharedProfileID`, `sharedProfileIsMine`, `sharedProfileLinkedName`,
    `sharedProfileLinkedAt`).
  - `MatchEntity.kt` — miroir de `MatchRecord.swift` (`id`, `gameID`, `rulesVersion`,
    `variantsData: ByteArray`, `startedAt`, `endedAt`, `status`, `endReasonRaw`, `isArchived`,
    `deviceOrigin`, `eventLogData: ByteArray` — **la source de vérité**, exactement comme côté
    Swift : la reprise après relance rejoue ce journal, jamais un total en cache —,
    `pendingSharedProfileSync`, `isImportedSummary`).
  - `ParticipantEntity.kt` — miroir de `ParticipantRecord.swift` (`id`, `playerId: UUID?` FK
    `ON DELETE SET NULL`, `nicknameSnapshot`, `avatarKindSnapshot`, `avatarValueSnapshot`,
    `paletteIDSnapshot`, `seatIndex`, `teamID: String?`, `finalRank`, `finalScore`, `matchId` FK
    `ON DELETE CASCADE`).
- `@Relation` explicites (Room n'a pas de résolution implicite) : `MatchWithParticipants`,
  `PlayerWithParticipations`. Ordre déterministe par `ORDER BY sortIndex`/`seatIndex` en
  requête — l'avantage que la section « Persistance » ci-dessus note déjà pour Room.
- `PlayerDao.kt`, `MatchDao.kt`, `ParticipantDao.kt` — requêtes `Flow<...>`, équivalent du
  rafraîchissement automatique SwiftData.
- Repositories, mêmes noms que côté Swift : `PlayerRepository.kt`, `MatchRepository.kt`,
  `LeaderboardRepository.kt`, `ProfileRepository.kt`. `DeviceIdentity.kt`, `AppSettings.kt` —
  Preferences DataStore au lieu d'UserDefaults/Keychain.
- Pas d'équivalent CloudKit (déjà tranché ci-dessus, section « Synchronisation »). Sauvegarde :
  Android Auto Backup — `allowBackup="true"` + `dataExtractionRules.xml` incluant la base Room
  (quota 25 Mo).
- Widget non porté : la ligne `WidgetKit → Glance` du tableau d'équivalences n'a plus de
  correspondant à construire ici — le widget d'écran d'accueil Apple a été construit puis
  retiré, jugé sans intérêt réel face à la Live Activity (docs 15/12, P9).

**Fini quand** : on crée dix joueurs, dix parties, on relance l'app, tout est là ; une partie
interrompue reprend en rejouant `eventLogData`, jamais un total mis en cache.

### E — Écrans · 3 semaines

Inventaire réel (`apple/App/Features/`, 8 dossiers) — sert à ne rien oublier, pas un modèle à
recopier : `LiveMatch/` (`LiveMatchModel`/`View`, `ScoreBoardView`, `RoundHistoryView`, + 4
écrans de saisie dédiés — `BeloteRoundView`, `TarotRoundView`, `WizardRoundView`,
`YamsSheetView` — Skyjo/Mölkky utilisent le pavé générique + drapeaux ; `ShareSessionView`/
`SharedMatchView`/`QRCodeView`) · `MatchSetup/` (`GamesTabView`, `JoinTabView`,
`MatchSetupModel`/`View`, `QRScannerView`) · `Players/` · `Results/` · `History/` (+
`ReceivedMatchDetailView`, doc 14) · `Leaderboard/` · `Profile/` · `Settings/`.

Proposition Compose, idiomatique, pas un calque (voir « Ce qui n'est pas partagé » ci-dessous) :

- `NavigationBar` Material en bas (charte : pas la tab bar flottante iOS). Destinations racines
  proposées, à valider une fois les écrans en main : **Jouer** (catalogue + rejoindre),
  **Historique**, **Classements**, **Profil** — `Settings` atteint depuis Profil, convention
  Android courante, pas une destination de barre séparée.
- Un écran = un `@Composable` + un `ViewModel` (`StateFlow`), même préfixe que le Model Swift :
  `LiveMatchScreen.kt`/`LiveMatchViewModel.kt`, `MatchSetupScreen.kt`/`MatchSetupViewModel.kt`,
  `PlayerEditorScreen.kt`, `HistoryListScreen.kt`, `ResultsScreen.kt`, `ProfileScreen.kt`,
  `SettingsScreen.kt`, `GameLeaderboardScreen.kt`.
- Écrans de saisie dédiés, mêmes 4 jeux : `BeloteRoundScreen.kt`, `TarotRoundScreen.kt`,
  `WizardRoundScreen.kt`, `YamsSheetScreen.kt`. Le reste partage `ScoreBoardScreen.kt` (pavé
  numérique + drapeaux).
- Pavé numérique et enchaînement de saisie (objectif produit P4 : 5 scores en < 15 s, doc 12) à
  revalider sur Android — pas un simple portage visuel.
- `ResultsShareCard` → `GraphicsLayer.toImageBitmap()`. Courbe d'évolution → Vico (seule
  dépendance UI tierce Android, déjà actée ADR-0012/0016).

**Fini quand** : une partie de chaque famille de saisie (entier nu, entier + drapeau, grille, par
équipe — doc 05) se joue de bout en bout jusqu'aux résultats sur appareil réel, et la reprise
après relance fonctionne.

### F — Transport partagé · 1,5 semaine

Cette étape est un portage assez mécanique d'un client SDK déjà écrit une fois (`SupabaseTransport.swift`),
pas la construction de deux transports bas niveau (mDNS/socket, GATT) que l'ancienne architecture
Wi-Fi/BLE exigeait — l'estimation en tient compte. Timebox court en tête d'étape pour vérifier la
parité `supabase-kt`/`supabase-swift` sur canal/presence/broadcast avant de s'engager sur le
reste de l'estimation.

- `Transport.kt` — `interface TransportSession` (`incoming: Flow<ByteArray>`, `suspend fun
  send`, `suspend fun close`), miroir de `TransportSession`. `DiscoveredHost.kt`.
- `SupabaseTransport.kt` — client `supabase-kt` (modules Realtime + Postgrest), même modèle
  canal/presence/broadcast que `SupabaseTransport.swift` : canal par session, presence
  connexion/déconnexion, broadcast pour `WireMessage` chiffré, requête Postgrest sur
  `cacompte_open_games` pour la découverte par code.
- `WireMessage.kt` — `data class` + `sealed interface Kind`, les 8 cas exacts de
  `WireMessage.swift` (`Hello`, `Welcome`, `Events`, `MatchChanged`, `Proposal`, `Rejection`,
  `Heartbeat`, `Goodbye`), sérialisation kotlinx.serialization polymorphe. `WireCodec.kt`,
  `Role.kt`, `LamportClock.kt`.
- `SessionCrypto.kt` — AES-GCM direct (`Cipher.getInstance("AES/GCM/NoPadding")` couvre
  exactement `AES.GCM.seal`/`open`). **HKDF n'a pas de primitive JDK prête à l'emploi**
  (contrairement à `CryptoKit.HKDF<SHA256>`) : réimplémenter RFC 5869 à la main sur
  `javax.crypto.Mac("HmacSHA256")` (~20 lignes) plutôt qu'importer une bibliothèque de crypto
  entière (Tink/Bouncy Castle) pour un seul primitif — cohérent avec l'esprit « zéro dépendance
  tierce » d'ADR-0012.
- `LiveSession.kt` — **pas d'équivalent direct à l'`actor` Swift.** À trancher en tête d'étape :
  `Mutex` (kotlinx.coroutines.sync) enveloppant chaque fonction publique, ou dispatcher
  mono-thread dédié (`Dispatchers.Default.limitedParallelism(1)`). C'est la seule vraie décision
  d'architecture de cette étape — le reste est un portage mécanique d'un client déjà écrit une
  fois.
- `SharedProfileTransport.kt` (doc 14) — priorité basse : sa phase 3 (copie rejouable) n'est pas
  faite côté Apple non plus.
- Hors périmètre v1 : `LiveActivityPushClient` — Live Activity est Apple-only, aucune
  notification persistante à construire dans cette étape.
- `WireGoldenTest.kt` (`:sync`, JVM) — même patron que `WireGoldenTests.swift` : décode les 8
  fixtures de `spec/wire/*.json`, vérifie le round-trip, vérifie que les 8 cas de `Kind` ont
  chacun leur fixture.

**Fini quand** : un iPhone et un Android suivent la même partie via Supabase Realtime en recette
croisée manuelle sur appareils réels, et l'un des deux perd puis retrouve sa connexion sans
perdre l'état.

### G — Accessibilité, localisation, recette · 1 semaine

Réutilise la portée de la Phase H Apple (doc 15) plutôt que de la redécouvrir — mêmes 8 zones +
les 4 écrans de saisie dédiés :

- `AccessibleMotion.kt` (`:designsystem`) — lit `Settings.Global.ANIMATOR_DURATION_SCALE` via
  `ContentResolver`, exposé en `CompositionLocal<Boolean>` (`LocalReducedMotion`), miroir de
  `Motion+Accessible.swift` : `snap()` au lieu de `tween()` quand actif.
- `AccessibleScoreRow.kt` — `Modifier.semantics(mergeDescendants = true) { contentDescription =
  ... }`, un seul arrêt TalkBack par ligne, même principe que `AccessibleScoreRow.swift`.
- `Chip.kt` — zone tactile `Touch.minimum` (48 dp, charte Android), `Modifier.semantics {
  selected = ... }`.
- `Banner.kt` — `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`, équivalent
  Compose-natif de `UIAccessibility.post`/`Banner.announce(_:)`.
- Contraste augmenté des couleurs `player/1`…`player/10` : pas de signal système Android aussi
  net que le trait `.increaseContrast` d'iOS à cette date — réglage explicite dans l'app plutôt
  qu'un signal système incertain, mêmes paires vérifiées WCAG ≥ 4,5. `ContrastTests.kt`
  (`:designsystem`, JVM pur — l'arithmétique de contraste ne dépend d'aucune API Android), même
  volume que les 20 cas ajoutés en Phase H côté Apple.
- Échelle de police : Compose respecte `fontScale` (jusqu'à ×2.0) automatiquement sur `sp` — le
  travail réel est la vérification visuelle qu'aucun écran ne tronque à `fontScale = 2.0`
  (équivalent AX5).
- Traversée manuelle TalkBack sur appareil réel, mêmes 8 zones + 4 écrans dédiés — jugement
  humain, rien d'automatisable ici (même constat que la traversée VoiceOver Apple).
- **Localisation — chemin d'extraction mécanique, pas une retraduction.** Deux sources
  distinctes : (1) **contenu des jeux** (`spec/games/*.json`, champs `fr`/`en`/`es`/`de`/`it`) —
  déjà partagé tel quel via `spec/`, lu directement par `:catalog` au runtime, rien à traduire
  ni extraire. (2) **chrome d'interface** (`Localizable.xcstrings`, 232 clés) — script
  d'extraction ponctuel vers `res/values{,-en,-es,-de,-it}/strings.xml` : synthétiser un nom de
  ressource stable (la clé source est le texte français lui-même, pas un identifiant symbolique
  — table de correspondance committée pour ne pas se réordonner à chaque régénération) ;
  convertir les spécificateurs positionnels (`%1$@`→`%1$s`, `%lld`/`%ld`→`%d`) ; échapper le
  XML ; **pas de `<plurals>`** — le projet n'utilise déjà pas les variations plurielles
  `.xcstrings` (convention « partie(s) »/« victoire(s) » en toutes lettres), donc pas de bascule
  vers le système de quantités CLDR d'Android à gérer ; `values/` (sans qualificatif) porte le
  **français**, cohérent avec `sourceLanguage: fr` et le repli déjà choisi côté
  `GameDefinition.LocalizedText.localized`. Repasse humaine légère après coup (conventions
  Android, débordements de texte propres à Compose), pas une retraduction.
- Raccourci de langue : `Settings.ACTION_APP_LOCALE_SETTINGS` (API 33+), équivalent
  d'`UIApplication.openSettingsURLString`.
- Recette finale : Play Internal Testing track (équivalent TestFlight), fiche Play Store
  (captures, section « Sécurité des données » — équivalent de l'étiquette de confidentialité
  iOS).

**Fini quand** : TalkBack traverse les 8 zones + 4 écrans dédiés sans blocage, `fontScale = 2.0`
n'écrête aucun écran, les 5 langues s'affichent correctement, la check-list « définition de
terminé » (variante Android de doc 10) passe sur tous les écrans, l'app est installable via Play
Internal Testing.

## Ce qui n'est pas partagé, et c'est voulu

- Les composants d'interface. Un bouton SwiftUI et un `Button` Compose n'ont aucune raison de
  se ressembler dans le code, seulement à l'écran.
- La navigation. `NavigationStack` et Navigation Compose ont des modèles différents ; les
  aligner produirait une abstraction inutile des deux côtés.
- Les gestes et les micro-interactions. Chaque plateforme a ses conventions, et l'utilisateur
  attend celles de son téléphone.
- Le rythme des versions. Rien n'oblige les deux apps à sortir la même fonctionnalité le même
  jour, tant que `spec/` reste commun et versionné.
