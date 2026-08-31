# 15 — Plan qualité de code

Le projet documente déjà une stratégie de qualité précise — pyramide de tests, golden files,
Swift 6 concurrence stricte, swift-format, zéro dépendance ([10](10-tests-et-qualite.md)) — et
15 ADR qui justifient chaque décision structurante ([13](13-decisions-adr.md)). Ce document
n'ajoute pas une nouvelle doctrine : il **audite l'écart entre ce que le projet dit faire et ce
qu'il fait réellement**, à un instant donné (39 commits, huit phases de la roadmap livrées), et
priorise ce qui reste pour combler cet écart.

## Méthode

Audit par lecture directe du code (comptages `grep`, tailles de fichiers, `git log`), pas par
génération de bonnes pratiques génériques — chaque constat ci-dessous référence un fichier et une
ligne. Deux catégories : ce qui a été **corrigé dans la foulée de cet audit** (risque faible,
vérifié par build), et ce qui reste **au plan** (risque de régression plus élevé, demande son
propre passage — tests UI, localisation).

## Constat (2026-08-31)

| Sujet | Ce que dit le projet | Ce que montrait le code |
|---|---|---|
| Concurrence Swift 6 | « Aucune exception, aucun `@unchecked Sendable`, aucun `@preconcurrency import` » ([10](10-tests-et-qualite.md)) | 2× `@unchecked Sendable` (`SupabaseTransport.swift`), 1× `@preconcurrency import AVFoundation` (`QRScannerView.swift`) |
| Outillage swift-format | « swift-format avec la configuration par défaut d'Apple, appliqué à la validation » ([10](10-tests-et-qualite.md)) | Aucun fichier `.swift-format`, aucun script, aucune étape CI qui l'exécute — seul `Scripts/check-spec-sync.sh` tourne réellement. **Corrigé, voir ci-dessous.** |
| Tests — pyramide | « ~80 % de l'effort sur Domain/Catalog/Stats », XCUITest sur 3 parcours critiques ([10](10-tests-et-qualite.md)) | `CaCompteKit/Tests` : 1521 lignes / 13 fichiers (Domain/Catalog/Store/Sync/DesignSystem) — cohérent avec la doctrine. `App/Features` (5408 lignes, ~46 % du code, dont `LiveMatchModel`/`MatchSetupModel`/`PlayerEditorModel`) : **0 test**. Cible `CaCompteUITests` : **absente du `.xcodeproj`**, pas même vide |
| Logging | (non spécifié explicitement, mais `print()` incompatible avec un produit livré) | 10 `print(...)` dans `Sync/LiveActivityPushClient.swift` et `App/MatchLiveActivityController.swift`, dont un littéral `"BUILD-MARKER-MINIMAL-FIX"` — résidu de debug |
| Gestion d'erreur | `try!` toléré seulement s'il est un fail-fast volontaire et documenté | 3 `try!` justifiés par commentaire (`GameCatalog+Embedded.swift`) ; 5 non justifiés, côté app (`CaCompteApp.swift:149`, `HistoryDetailView.swift`, `LiveMatchView.swift`, `YamsSheetView.swift`, `BeloteRoundView.swift`) |
| Documentation | Le README est la porte d'entrée du projet | Non modifié depuis le premier commit substantiel (`3d0d899`, 2026-07-30) alors que 38 commits l'ont suivi, dont le remplacement complet du transport Wi-Fi/BLE par Supabase Realtime — jamais répercuté |
| Localisation | « Toutes les chaînes sont dans le catalogue, français et anglais » — définition de « terminé » ([10](10-tests-et-qualite.md)) | Aucun `.xcstrings` n'existait ; 68 `Text("...")` en français codé en dur dans `App/Features` (sous-estimé — 184 chaînes au total sur tout le projet), 10 usages de `String(localized:)`/`LocalizedStringResource`. **Corrigé, voir ci-dessous.** |
| Périmètre de `Domain` | « `Domain` n'importe que `Foundation` » ([02](02-architecture.md), ADR-0002) | `Domain/LiveActivity/MatchActivityAttributes.swift` importe aussi `ActivityKit` — écart mineur, vraisemblablement nécessaire (le protocole `ActivityAttributes` doit être visible du widget), jamais documenté comme exception |
| Secrets | `.gitignore` exclut `*.p8`, `.env.local` | Vérifié : **jamais commités** (`git log --all -- '*.p8' '.env.local'` vide). Bonne hygiène, rien à corriger |
| Build | `SWIFT_TREAT_WARNINGS_AS_ERRORS = YES` sur toutes les configs | Vérifié : `swift build` et `xcodebuild` (scheme `CaCompte`) passent à 0 avertissement |

## Corrigé dans cet audit

- ✅ **`SupabaseTransportSession` (`SupabaseTransport.swift:311`)** — `@unchecked Sendable` →
  `Sendable` tout court. Toutes ses propriétés stockées sont `let`, et `RealtimeChannelV2` est
  déjà `Sendable` côté SDK (`supabase-swift/Sources/RealtimeV2/RealtimeChannelV2.swift:113`) : le
  compilateur vérifie maintenant la conformité au lieu de la présumer.
- ✅ **`SupabaseTransport` (`SupabaseTransport.swift:28`)** — classe entière annotée `@MainActor`
  (retrait de `@unchecked Sendable` et des 3 annotations `@MainActor` désormais redondantes au
  niveau méthode). Les deux seuls sites d'instanciation (`LiveShareCoordinator`,
  `MatchConnectionCoordinator`) sont eux-mêmes `@MainActor` : aucun appelant à changer. Corrige au
  passage un vrai trou d'isolation — `advertise`/`connect`/`stopAdvertising`/`init` mutaient le
  même état que les méthodes `@MainActor` sans aucune protection du compilateur.
- ✅ **`@preconcurrency import AVFoundation` (`QRScannerView.swift`)** — conservé : nécessité SDK
  documentée (`AVCaptureMetadataOutputObjectsDelegate` non audité `Sendable` par Apple), pas un
  relâchement. Documenté formellement dans [10](10-tests-et-qualite.md) comme unique exception
  restante, plutôt que silencieusement en contradiction avec la règle qu'il enfreint.
- ✅ **`print()` → `os.Logger`** — `LiveActivityPushClient.swift` et
  `MatchLiveActivityController.swift`, un logger par fichier (`subsystem: "com.cacompte.app"`),
  niveaux `.debug`/`.info`/`.error` selon le cas. Le littéral de debug
  `"BUILD-MARKER-MINIMAL-FIX"` a été retiré ; le jeton de push APNs n'est plus loggé en clair
  (amélioration incidente).
- ✅ **README** — table « Sync » et « Dépendances » mises à jour (Supabase Realtime, exception
  ADR-0012), « Premier commit git »/« Remote git » marqués faits (39 commits, remote GitHub
  configuré), note ajoutée en tête du journal « Étape suivante » pour signaler que la section
  Wi-Fi/BLE qui suit est historique. Le journal détaillé lui-même n'a pas été réécrit
  rétroactivement — voir recommandation ci-dessous.
- ✅ **`GoldenFileTests` en échec sur `main`** (trouvé pendant la vérification de cet audit, pas
  par les corrections elles-mêmes — confirmé pré-existant par `git stash`) — `skyjo-02-egalite-finale`
  attendait un insight `bestRoundScore` alors que `extremeSingleRound` (`StatsEngine.swift`) a
  été modifié en cours de route (commit `9a40c15`) pour ne plus désigner de gagnant quand
  l'extrême est atteint par deux joueurs distincts. Le golden lui-même construit précisément ce
  cas (p1 = 50 en manche 0, p2 = 50 en manche 1 — `tieBreakTrace.bestSingleRound` le documente
  déjà comme `"tie"` côté classement), donc c'est le golden qui était en retard sur le
  comportement, pas le code. Corrigé : `bestRoundScore` retiré de `expected.insights` dans
  `spec/golden/skyjo-02-egalite-finale.json` (et sa copie `CaCompteKit/Tests/CatalogTests/GoldenResources/`),
  avec une phrase ajoutée à `description` pour que ça ne soit pas repris par erreur. Les 195
  tests du package passent de nouveau.
- ✅ **Les 6 `try!` non justifiés côté app** (le constat initial en recensait 5 par fichier, mais
  `HistoryDetailView.swift` en portait 3 à lui seul, et `YamsSheetModel.swift:74` avait été omis
  de la liste — 6 sites au total) :
  - `MatchPlayView.swift` — le `switch` sur `EntryKind` est passé de `default:` implicite à
    exhaustif (`.integer, .rank, .predictionAndResult` vs `nil`) : le cas `nil` (version de
    règles disparue du catalogue) affiche désormais un `EmptyState` (DesignSystem) au lieu de
    router vers `LiveMatchView`, qui plantait en refaisant le même lookup. `MatchPlayView` est le
    **seul** point d'entrée de `LiveMatchView`/`YamsSheetView`/`BeloteRoundView` (vérifié par
    `grep`), donc ce correctif couvre les trois d'un coup pour leur risque principal.
  - `HistoryDetailView.swift` (3 `try!`) — passés en `try?` avec liaison combinée ; `body` a
    maintenant un troisième cas (`EmptyState`, même copie que `MatchPlayView`) là où il ne
    rendait rien avant en cas d'échec silencieux.
  - `LiveMatchView.swift`, `YamsSheetView.swift`, `BeloteRoundView.swift` — `try!` conservés
    (risque résiduel restreint à `repository.loadState`, un journal d'événements corrompu — pas
    encore de parcours de secours pour ce cas précis), mais documentés en commentaire plutôt que
    silencieux, avec renvoi explicite à la garantie apportée par `MatchPlayView`.
  - `YamsSheetModel.swift:74` — `try!` conservé et documenté : encode un `YamsCategoryDetail`
    (une seule propriété `String`), `JSONEncoder` ne peut pas échouer dessus en pratique.
  - `CaCompteApp.swift:149` — troisième palier ajouté : CloudKit (`try?`) → local sur disque
    (`try?`, nouveau) → mémoire (`try!`, désormais justifié — un store frais sans migration ne
    peut pas échouer). Un store disque corrompu n'empêche plus l'app de s'ouvrir.

  Vérifié par `xcodebuild` (scheme `CaCompte`, avertissements en erreurs) et les 195 tests du
  package, tous verts après ces changements.
- ✅ **Phase B — Outillage swift-format**, appliqué à la lettre (config par défaut d'Apple, pas
  une config adaptée au style existant — choix explicite : reformater plutôt que dévier de ce
  que `docs/10` documente). `.swift-format` ajouté à la racine (`xcrun swift-format
  dump-configuration`). `xcrun swift-format lint --recursive --strict` remontait **~11 900**
  violations sur le code existant (indentation 4 espaces vs 2, lignes plus longues que 100
  colonnes) : reformaté en un seul passage (`swift-format format --in-place --recursive`, 142
  fichiers), puis 7 violations restantes corrigées à la main (`.forEach` → `for`-in, 3
  commentaires de fin de ligne trop longs déplacés au-dessus). `Scripts/lint.sh` (même style que
  `check-spec-sync.sh`) et branché dans `ci_scripts/ci_post_clone.sh`, juste après le
  spec-sync — Xcode Cloud échoue maintenant sur un fichier mal formaté, pas seulement une
  relecture humaine. Revérifié : `swift build`, `xcodebuild` (scheme `CaCompte`) et les 195 tests
  du package tous verts après le reformatage.

Vérification : `swift build` (package) et `xcodebuild` (scheme `CaCompte`, avertissements en
erreurs) passent tous les deux à 0 avertissement après ces changements. Aucun test n'exerce
`SupabaseTransport` directement aujourd'hui (`SyncTests` teste `LiveSession` via
`InMemoryTransport`) : la vérification du fix de concurrence repose sur la compilation stricte,
pas sur une exécution — limite à garder en tête, voir Phase C.

- ✅ **Phase G — Catalogue de localisation, terminé** (`App/Resources/Localizable.xcstrings`).
  Démarré avec une feature gabarit (`Settings`, 8 chaînes). Constat qui change la portée du
  chantier : les
  initialiseurs SwiftUI qui prennent un littéral (`Text("…")`, `Toggle("…", isOn:)`,
  `Button("…")`, `.navigationTitle("…")`…) résolvent déjà `LocalizedStringKey` contre le
  catalogue **sans aucun changement de code** — `SettingsView.swift` n'a pas été touché, seul le
  catalogue existe désormais. Le vrai travail par fonctionnalité sera donc surtout du remplissage
  de catalogue (extraire les littéraux, écrire la traduction anglaise), pas une réécriture de
  vues ; `String(localized:)`/`LocalizedStringResource` (10 usages déjà) restent nécessaires
  seulement pour les chaînes construites hors contexte SwiftUI direct (messages d'erreur stockés
  dans une `String`, etc.).

  Ajout de la cible `.xcstrings` au projet Xcode : édité `project.pbxproj` à la main (4 sections —
  `PBXBuildFile`, `PBXFileReference`, le groupe `Resources`, la phase `PBXResourcesBuildPhase` du
  target `CaCompte` — plus `en` ajouté à `knownRegions`), en suivant exactement le schéma déjà
  utilisé par `Assets.xcassets`. Contrairement à la cible `CaCompteUITests` de la Phase C (un
  nouveau *target* entier — configs de build, scheme, host application), ajouter un *fichier* à un
  target existant est une opération à 4 insertions bien isolées, vérifiable immédiatement par
  build. Vérifié : `xcodebuild` (scheme `CaCompte`) réussit, `xcstringstool` compile le catalogue
  en `en.lproj/Localizable.strings` et `fr.lproj/Localizable.strings` dans le bundle avec les
  bonnes valeurs (inspecté avec `plutil -p`) ; le fichier source `.xcstrings` reste à 8 clés après
  un build CLI classique — `xcodebuild build` ne le réécrit pas automatiquement avec les chaînes
  du reste du projet (cette auto-extraction est un comportement de l'éditeur Xcode).

  **Découverte qui a changé la portée du reste de la Phase G** : `xcodebuild -exportLocalizations`
  (contrairement à `-build`) synchronise réellement le catalogue avec le code source avant
  d'exporter — il a ajouté d'un coup les ~176 chaînes restantes du projet entier (pas seulement
  les 14 vues visées), avec entrée vide (= la clé française fait foi) pour les littéraux simples,
  et une localisation `fr` explicite à spécificateurs de format positionnels (`%1$@`, `%2$lld`…)
  calculée automatiquement pour les chaînes interpolées — exactement le calcul que j'aurais dû
  reproduire à la main (et risqué de mal faire) pour des chaînes comme
  `"\(entry.played) partie(s) · \(entry.wins) victoire(s)"`. Reproductible : `xcodebuild
  -exportLocalizations -project App/CaCompte.xcodeproj -localizationPath <dir> -exportLanguage fr`.

  Les 176 clés ont ensuite été traduites en anglais (contexte de chaque chaîne ambiguë vérifié par
  `grep` sur le site d'appel avant traduction, pas deviné), appliquées par script plutôt qu'à la
  main pour éviter une erreur de recopie sur 176 entrées. **184 chaînes au total** (176 + les 8 de
  Settings), seules 5 laissées sans traduction anglaise à dessein (`""`, `"—"`, `"%@"`, `"%lld"`,
  `"0"` — symboles/placeholders neutres, aucune information à traduire). Convention adoptée pour
  coller au style français existant (« partie(s) », « victoire(s) ») plutôt qu'au pluriel anglais
  standard : « match(es) », « win(s) », « round(s) », « player(s) ».

  Vérifié : `xcodebuild` (scheme `CaCompte`) réussit, `en.lproj`/`fr.lproj` compilés contiennent
  les 184/179 entrées attendues avec les bonnes valeurs (`plutil -p`, échantillon vérifié), le
  fichier source n'a pas été ré-étendu par le rebuild qui a suivi, aucun autre fichier du projet
  touché par l'export (`git status` — un seul fichier modifié), et les 195 tests du package
  restent verts.

## Reste au plan

### Phase C — Combler le trou de tests côté App

- Créer la cible `CaCompteUITests` (absente du `.xcodeproj`) avec les 3 parcours déjà spécifiés
  par [10](10-tests-et-qualite.md) : créer un joueur, partie Skyjo complète jusqu'aux résultats,
  relancer l'app et vérifier la reprise. C'est le parcours « soirée perdue », identifié comme
  rédhibitoire dans la [vision produit](01-vision-produit.md).
- Tests Swift Testing pour `LiveMatchModel`, `MatchSetupModel`, `PlayerEditorModel` — au minimum
  `commitRound`/`undoLastRound`, rejet d'une validation invalide, transition d'état après fin de
  partie. Ne dépend pas du simulateur (ce sont des objets `@Observable` purs, pas des vues).
- Câbler les tests `CaCompteKit` dans le schéma `CaCompte` (action manuelle Xcode déjà listée au
  README — 30 secondes en UI, non fiabilisable en pbxproj à la main) : sans ça, Xcode Cloud ne
  fait tourner aucun test unitaire sur push.

**Fini quand** : `App/Features` a une couverture de tests non nulle sur ses 3 flux `@Observable`,
et les 3 parcours XCUITest passent en CI.

### Phase G — Catalogue de localisation — ✅ terminée (voir « Corrigé dans cet audit »)

Complétée au-delà du plan initial : les 184 chaînes du projet entier (pas seulement Settings)
ont une traduction anglaise. Voir le détail dans « Corrigé dans cet audit ».

Chantier qui, en pratique, s'est avéré traitable en un seul passage — la portée initialement
redoutée (« le plus gros écart avec la doctrine, réécriture de ~14 vues ») ne s'est pas
matérialisée : `xcodebuild -exportLocalizations` a fait l'extraction et le calcul des spécificateurs
de format à la place d'un travail manuel par fichier, ce qui a ramené le chantier à de la
traduction pure. Sans cet outil, l'étalement en plusieurs passages serait resté justifié.
actuel (app monolingue française).

**Fini quand** : `grep -rn 'Text("' App/Features` ne trouve plus de littéral français —
uniquement des clés résolues par le catalogue.

### Écart mineur non traité — `ActivityKit` dans `Domain`

`Domain/LiveActivity/MatchActivityAttributes.swift` importe `ActivityKit`, en désaccord avec
ADR-0002 (« `Domain` n'importe que `Foundation` »). Vraisemblablement nécessaire — le protocole
`ActivityAttributes` doit être visible à la fois par `Domain` (qui définit le type) et par le
widget (qui l'affiche) — mais jamais acté comme exception. À trancher dans une passe dédiée :
soit documenter l'exception dans [02](02-architecture.md) (même esprit que l'amendement Supabase
à ADR-0012), soit déplacer le type hors de `Domain` si un découpage plus propre existe. Non
prioritaire : n'affecte aucun autre invariant de `Domain` (toujours zéro I/O, toujours
`Sendable`).

## Recommandation de pratique — README factuel plutôt que journal

La dérive constatée sur le README (huit semaines sans mise à jour malgré un changement
d'architecture majeur) vient de sa double fonction : porte d'entrée à jour **et** journal
chronologique détaillé phase par phase. Le journal est ce qui a le plus dérivé, parce que
chaque entrée demande de se souvenir a posteriori du contexte exact d'une décision.

`docs/12-roadmap.md` et l'historique Git jouent déjà ce rôle (phases avec statut ✅/🔶/⏳,
messages de commit descriptifs). Recommandation : garder la section « Étape suivante » du README
courte et pointer vers la roadmap et Git pour le détail, plutôt que dupliquer un récit qui doit
ensuite être maintenu à deux endroits — c'est cette duplication qui a laissé le README obsolète
huit semaines durant sans que rien ne le signale.

## Ce qui fonctionne déjà bien (à ne pas perdre en cours de route)

- Golden files + invariants de propriété : la stratégie de test la plus rentable du projet,
  déjà en place et suivie.
- Discipline ADR : chaque écart au « zéro dépendance » (Supabase) est déjà passé par une
  décision documentée et justifiée — c'est le même réflexe qui manquait pour les deux autres
  écarts (README, interdits Swift 6), maintenant comblé par ce document.
- Hygiène des secrets : `.p8`/`.env.local` gitignorés et jamais commités, vérifié par
  l'historique complet.
- Zéro avertissement de compilation, sur le package comme sur l'app, concurrence stricte
  activée dès la première ligne (P0) plutôt qu'ajoutée après coup.
