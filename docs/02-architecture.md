# 02 — Architecture

## Principe directeur

> Le calcul des scores ne connaît ni SwiftUI, ni SwiftData, ni le réseau.

Tout ce qui décide d'un score, d'un classement ou d'une fin de partie vit dans un module de
**fonctions pures sur des structures `Sendable`**. Il en découle trois bénéfices directs :

- il se teste sans simulateur, sans base, sans horloge, en quelques millisecondes ;
- il se rejoue à l'identique à partir d'un fichier golden ;
- il se transpose mécaniquement en Kotlin, parce qu'il n'utilise aucun concept propre à Apple.

Tout le reste — persistance, réseau, interface — est un **adaptateur** autour de ce noyau.

## Modules

Un package Swift local, `QuiMeneKit`, contient plusieurs cibles. Un package plutôt qu'un projet
monolithique parce que les dépendances entre cibles sont alors vérifiées à la compilation :
`Domain` ne *peut pas* importer SwiftUI, le compilateur le refuse.

```
                        ┌──────────────────┐
                        │   QuiMene.app    │  cible Xcode
                        │  écrans + flux   │  (+ widget Live Activity)
                        └────────┬─────────┘
             ┌───────────────┬───┴────┬───────────────┐
             ▼               ▼        ▼               ▼
      ┌────────────┐  ┌───────────┐ ┌──────────┐ ┌──────────────┐
      │   Store    │  │  Catalog  │ │   Sync   │ │ DesignSystem │
      │ SwiftData  │  │  20 jeux  │ │ Supabase │ │   SwiftUI    │
      └──────┬─────┘  └─────┬─────┘ └────┬─────┘ └──────────────┘
             └──────────────┼────────────┘
                            ▼
                    ┌───────────────┐
                    │    Domain     │   Foundation uniquement
                    │ types + moteur│   100 % pur, 100 % Sendable
                    └───────────────┘
```

| Cible | Dépend de | Contient | Ne contient jamais |
|---|---|---|---|
| **Domain** | `Foundation`¹ | Types du domaine, protocole `GameRules`, `MatchEngine`, `StatsEngine`, définitions de jeux | Aucune I/O, aucun `Date()` implicite, aucun singleton |
| **Catalog** | Domain | Une implémentation `GameRules` par jeu à moteur propre + les JSON de `spec/games/` embarqués | Persistance, UI |
| **Store** | Domain | Modèles `@Model`, repositories, statistiques de profil et classements, mapping domaine ↔ persistance | Règles de jeu |
| **Sync** | Domain, `supabase-swift` | `OnlineSession` (journal de session), `SessionCrypto`, `SessionIdentity`, `SharedMatchMailbox`, `LiveActivityPushClient` | UI, persistance |
| **DesignSystem** | `SwiftUI` | Tokens, composants réutilisables, avatars, logo | Domain (délibérément : composants agnostiques) |
| **QuiMene.app** | tout | Écrans, navigation, objets `@Observable` de flux, composition des dépendances | Logique de calcul |

¹ **Exception** : `Domain/LiveActivity/MatchActivityAttributes.swift` importe aussi `ActivityKit`.
Le protocole `ActivityAttributes` doit être visible à la fois par `Domain` (qui définit le type)
et par le widget (qui l'affiche). Sans conséquence pour Android, où ce type n'a pas d'équivalent,
ni sur les autres invariants de `Domain` (zéro I/O, tout `Sendable`).

`DesignSystem` ne dépend pas de `Domain` volontairement : ses composants prennent des valeurs
brutes en entrée. Cela évite qu'un bouton finisse par embarquer une règle de jeu, et rend les
previews Xcode instantanées.

Le projet Android reproduit exactement ce découpage en modules Gradle, avec le même sens de
dépendance ([11](11-portage-android.md)).

## Pattern d'interface : MV, pas MVVM

SwiftUI + `@Observable` rendent la couche ViewModel systématique inutile
([ADR-0007](13-decisions-adr.md)). La règle retenue :

- **Listes et lectures simples** → `@Query` SwiftData directement dans la vue. Pas
  d'intermédiaire pour afficher l'historique des parties.
- **Flux avec état** → un objet `@Observable` `@MainActor` qui porte l'état et les intentions :
  `LiveMatchModel`, `MatchSetupModel`, `PlayerEditorModel`, `SharedMatchModel`,
  `HistoryListModel`, et un modèle par écran de saisie dédié (`YamsSheetModel`,
  `BeloteRoundModel`, `TarotRoundModel`, `WizardRoundModel`). Ce sont des objets **par flux
  métier**, pas un ViewModel par vue.
- **Aucun état métier dans `@State` de vue**. `@State` ne porte que du transitoire d'UI
  (feuille présentée, champ focalisé, animation en cours).

## Concurrence Swift 6

Le projet est en **mode langage Swift 6, concurrence stricte activée**, avertissements traités
comme des erreurs.

- Tous les types de `Domain` sont des `struct` immuables et `Sendable`. Aucune classe, aucun
  état partagé, donc aucune donnée à isoler.
- Les moteurs (`MatchEngine`, `StatsEngine`) sont des `struct` sans état : `nonisolated` par
  nature, appelables depuis n'importe quel contexte.
- L'UI et les modèles `@Observable` sont `@MainActor`.
- Les écritures SwiftData restent sur le `mainContext` : elles portent sur quelques objets, la
  latence est nulle, et cela évite tout aller-retour d'identifiants entre contextes.

## Arborescence

Mono dépôt : `apple/` et `android/` regroupent ce qui est propre à chaque plateforme ; le reste
de la racine est partagé ou transverse.

```
.
├── docs/                      documentation de conception
├── spec/                      source de vérité inter-plateformes (JSON)
├── supabase/                  migrations, fonctions Edge et tests SQL (doc 09)
├── Scripts/                   check-spec-sync.sh, lint.sh, extract-android-strings.py,
│                              store-screenshots.sh
├── ci_scripts/                hook Xcode Cloud (post-clone)
├── apple/
│   ├── QuiMeneKit/            package Swift local
│   │   ├── Package.swift
│   │   ├── Sources/
│   │   │   ├── Domain/
│   │   │   │   ├── Model/         Participant, MatchState, ScoreEntry, ValidationResult…
│   │   │   │   ├── Rules/         GameDefinition, GameRules, GameCatalog
│   │   │   │   ├── Engine/        MatchEngine, MatchEvent
│   │   │   │   ├── Stats/         StatsEngine, Insight, Badge
│   │   │   │   └── LiveActivity/  MatchActivityAttributes (exception ActivityKit)
│   │   │   ├── Catalog/
│   │   │   │   ├── Games/            SkyjoRulesV1, YamsRulesV1, BeloteRulesV1, TarotRulesV1…
│   │   │   │   ├── GenericRules/     GenericSumRules
│   │   │   │   └── GameDefinitions/  copie de spec/games/*.json
│   │   │   ├── Store/
│   │   │   ├── Sync/
│   │   │   └── DesignSystem/
│   │   └── Tests/
│   │       ├── DomainTests/
│   │       ├── CatalogTests/       ← rejoue spec/golden/*.json
│   │       ├── StoreTests/
│   │       ├── SyncTests/          ← relit spec/session/*.json
│   │       └── DesignSystemTests/
│   └── App/
│       ├── QuiMene.xcodeproj
│       ├── QuiMeneApp.swift
│       ├── Features/
│       │   ├── Players/  MatchSetup/  LiveMatch/  Results/  History/  Leaderboard/  Profile/
│       │   └── Settings/
│       ├── Resources/             Assets, Localizable.xcstrings
│       ├── QuiMeneWidget/         Live Activity (écran verrouillé, Dynamic Island)
│       ├── QuiMeneTests/          tests unitaires des modèles de flux
│       └── QuiMeneUITests/        parcours critiques, captures des stores
├── android/                   projet Gradle/Compose (doc 11)
├── website/                   site vitrine (Astro)
└── store/                     textes et slides des fiches des stores
```

Les JSON de `Catalog/GameDefinitions/` sont une copie de `spec/games/` : SwiftPM exige des
ressources locales à la cible. `Scripts/check-spec-sync.sh` (appelé par
`ci_scripts/ci_post_clone.sh` et par la CI GitHub) vérifie l'égalité : si les deux divergent,
la CI échoue. `spec/` reste la source, jamais l'inverse.

## Flux de données d'une manche validée

```
 Vue de saisie
     │ saisie des scores
     ▼
 LiveMatchModel                     brouillon, rien n'est encore acté
     │ valider
     ▼
 GameRules.validate(draft, in: state)          ─ refus possible, message affiché
     │ ok
     ▼
 MatchEvent.roundCommitted(...)     événement horodaté (Lamport), signé du deviceID
     │
     ├──▶ MatchEngine.reduce(state, event) → nouvel état
     │         ├─▶ GameRules.endCheck(state)   .continue / .finalRound / .ended
     │         └─▶ UI mise à jour
     │
     ├──▶ MatchRepository               journal persisté (SwiftData), immédiatement
     │
     └──▶ SessionLink → OnlineSession   si la partie est partagée : ajout au journal de la
                                        session sur le serveur (doc 09)
```

Un point important : `reduce` est une fonction pure `(MatchState, MatchEvent) -> MatchState`.
C'est elle qui rend l'annulation triviale (on retire l'événement et on rejoue), la
synchronisation possible (on fusionne deux journaux et on rejoue), et le test exhaustif
(un golden file *est* une liste d'événements et un état final attendu).

Voir [04 — Moteur de règles](04-moteur-de-regles.md) pour le détail des types.

## Injection de dépendances

Pas de conteneur DI, pas de framework. Le `ModelContainer` est créé dans `QuiMeneApp` ; les vues
construisent leurs repositories à partir du `modelContext` de l'environnement
(`PlayerRepository(context:)`, `MatchRepository(context:)`), et les objets `@Observable` partagés
par toute l'app (`AppSettings`, `DeepLinkRouter`) passent par `.environment(_:)`.
