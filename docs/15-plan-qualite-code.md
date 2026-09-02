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

### Phase C — 🔶 Combler le trou de tests côté App, partiellement fait

- ✅ **Cible `CaCompteUITests`** créée (absente du `.xcodeproj` au départ). Édition manuelle de
  `project.pbxproj` — plus lourde que celle de la Phase G : un *target* entier (`PBXNativeTarget`,
  `PBXContainerItemProxy`/`PBXTargetDependency` vers `CaCompte`, `XCBuildConfiguration` Debug/
  Release avec `TEST_TARGET_NAME`, `TargetAttributes.TestTargetID`, entrée dans le `.xcscheme`
  partagé) plutôt qu'un fichier ajouté à un target existant. Validée par étapes : `plutil -lint`
  et `xmllint --noout` sur les fichiers édités, `xcodebuild -list` confirmant les 3 targets,
  avant tout test réel.
- ✅ **Parcours n°1 (créer un joueur)** et **n°3 (reprise après relance)** — le plus important
  des trois, scénario « soirée perdue » de la [vision produit](01-vision-produit.md) — écrits et
  stables sur deux exécutions consécutives de la suite complète.
- ✅ **Magasin de test isolé** (`CaCompteApp.isUITesting`) — nécessaire dès l'écriture du premier
  parcours : sans lui, les joueurs créés par un test s'accumulaient d'une exécution à l'autre
  jusqu'à sortir de l'écran visible. `-uitesting-reset` (magasin sur disque dédié, effacé avant
  ouverture) pour le premier lancement d'un test, `-uitesting` (même fichier, non effacé) pour
  une relance au sein du même test — un magasin en mémoire pure aurait cassé le parcours n°3, qui
  doit justement retrouver ses données après un `terminate()`.
- 🔶 **Parcours n°2 (partie de Skyjo à 3 joueurs jusqu'aux résultats) — bloqué, pas écrit.** La
  mise en place (créer 3 joueurs, ouvrir Skyjo, les sélectionner, démarrer) fonctionne de façon
  fiable. La saisie des manches bute systématiquement sur la même ligne de `ScoreBoardView` (la
  dernière visible à l'écran, juste au-dessus du clavier) : un tap synthétisé dessus n'y déplace
  jamais le focus clavier, quel que soit le joueur qui s'y trouve après retri par rang — confirmé
  par `app.debugDescription` à chaque tentative (le focus restait sur le champ précédent, sans
  qu'aucune erreur ne remonte au moment du tap lui-même). Six stratégies essayées, toutes
  identiquement bloquées sur cette même ligne : tap élément, double tap, tap par coordonnées,
  ciblage dynamique du premier champ vide plutôt qu'un index fixe, tap sur le nom du joueur
  (cible bien plus grande que le `TextField` de 64×22 pt) plutôt que sur le champ, fermeture du
  clavier (`swipeDown()`) puis nouveau tap à écran plein. Hypothèse la plus probable, non
  confirmée : une limite d'automatisation propre à `List` + `.focused()` + clavier `.numberPad`
  sur cette ligne précise — pas nécessairement un bug de l'app (le parcours fonctionne à la main
  sur simulateur et appareil réel, voir README). `testSkyjoMatchReachesResults` passe la mise en
  place puis `throw XCTSkip(...)` avec ce diagnostic complet en commentaire — à reprendre avec
  l'enregistreur de tests d'Xcode (accès UI direct, hors de portée en CLI).
- ✅ **Cible `CaCompteTests`** créée (aucune cible de tests unitaires hébergée n'existait —
  `CaCompteUITests` est de l'UI-automation, pas un hôte `@testable import`). Même méthode que
  `CaCompteUITests` ci-dessus (édition manuelle de `project.pbxproj` : `PBXNativeTarget`,
  `PBXContainerItemProxy`/`PBXTargetDependency`, `XCBuildConfiguration` Debug/Release, entrée
  `.xcscheme`), avec en plus `TEST_HOST`/`BUNDLE_LOADER` (cible hébergée, pour `@testable import
  CaCompte`) et ses propres `packageProductDependencies` (Domain/Catalog/Store/DesignSystem —
  liés séparément de la cible `CaCompte`, un module ne rend pas ses propres dépendances
  visibles à un module qui l'importe en `@testable`). Validée par étapes : `plutil -lint`/
  `xmllint --noout`, `xcodebuild -list` confirmant les 4 cibles, avant tout test réel.
- ✅ **`LiveMatchModel`/`MatchSetupModel`/`PlayerEditorModel`, 12 tests** (`App/CaCompteTests/`) :
  `commitRound`/`undoLastRound`/rejet d'une validation invalide/transition à `.ended` pour
  `LiveMatchModel` ; bornes d'effectif, plafond de `toggle()`, complétude des équipes, `start()`
  pour `MatchSetupModel` ; validation du pseudo, régénération d'avatar jusqu'au premier choix
  manuel, `save()`/`archive()`/`delete()` pour `PlayerEditorModel`. Même patron `ModelContainer`
  en mémoire que `CaCompteKit/Tests/StoreTests`.
- ✅ **Deux bugs de test réels trouvés et corrigés en écrivant cette cible**, tous deux propres à
  l'hébergement dans le vrai process `CaCompte.app` (`TEST_HOST`) — invisibles dans
  `CaCompteKit/Tests`, qui tourne dans un exécutable non entitlé :
  - `CaCompteApp.loadContainer` tentait un vrai container CloudKit au lancement, indisponible en
    simulateur sans compte iCloud — plantait en cascade et détruisait des `ModelContainer` de
    test sans rapport (état SwiftData partagé au niveau du process). Corrigé par
    `CaCompteApp.isUnitTestHost` (détecte `XCTestConfigurationFilePath`, posé par XCTest sur
    tout process hôte d'un bundle injecté) qui bascule sur un container local en mémoire.
  - Même symptôme persistant après ce premier correctif : `ModelConfiguration(isStoredInMemoryOnly:
    true)` sans `cloudKitDatabase` explicite retombe sur `.automatic`, qui tente quand même
    CloudKit dans un process qui porte l'entitlement iCloud réel — absent d'un exécutable non
    hébergé comme `StoreTests`, où `.automatic` ne tente jamais rien. Corrigé par
    `cloudKitDatabase: .none` explicite, à la fois dans `CaCompteApp` et dans les trois fichiers
    de test.
  - Un troisième symptôme, sans rapport avec CloudKit celui-là (`SwiftData/BackingData.swift:835:
    Fatal error: This model instance was destroyed by calling ModelContext.reset`), venait d'un
    bug ordinaire du code de test : une fonction utilitaire renvoyait seulement
    `container.mainContext`, jamais le `ModelContainer` lui-même — désalloué dès le retour de la
    fonction, invalidant le contexte pour le corps du test qui suit. Corrigé en renvoyant (et en
    gardant vivant via `withExtendedLifetime`) le conteneur, même convention que `StoreTests` où
    il reste une variable du corps du test.
- Reste à faire : câbler les tests `CaCompteKit` dans le schéma `CaCompte` (action manuelle Xcode
  déjà listée au README — 30 secondes en UI, non fiabilisable en pbxproj à la main, contrairement
  à la création des deux cibles ci-dessus qui l'a été).

Vérification : `xcodebuild test` (scheme `CaCompte`) réussit — 12/12 sur `CaCompteTests`, 3 tests
(1 skip attendu, parcours n°2 ci-dessus) sur `CaCompteUITests` — et `Scripts/lint.sh` reste vert.

**Fini quand** : `App/Features` a une couverture de tests non nulle sur ses 3 flux `@Observable`,
et les 3 parcours XCUITest passent en CI (2 sur 3 le font désormais).

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

### Extension de la Phase G (2026-09-01) — es/de/it, contenu des jeux, raccourci de langue

La Phase G ci-dessus ne couvrait que les 184 chaînes d'interface (fr/en) — le contenu des jeux
(noms, descriptions, libellés de règles dans `spec/games/*.json`) restait figé en français quel
que soit `Bundle.main.preferredLocalizations`, malgré des champs `en` déjà présents dans le
schéma. Demande explicite : traduire l'intégralité (interface **et** contenu des jeux), ajouter
« les langues les plus intéressantes », et un raccourci vers le réglage de langue dans les
réglages de l'app. Espagnol, allemand, italien retenus (audience iOS App Store après fr/en/de/es
selon les parts de marché habituelles des 4 marchés européens les plus importants).

- ✅ **`GameDefinition.LocalizedText`** — champs `es`/`de`/`it` optionnels ajoutés, plus une
  propriété calculée `localized` qui résout via `Bundle.main.preferredLocalizations` (respecte le
  réglage de langue *par app* d'iOS, contrairement à `Locale.current` qui ne reflète que la
  langue système) avec repli sur le français. Placée dans `Domain` avec justification explicite
  en commentaire : résolution de présentation, jamais rejouée par un golden file (qui ne vérifie
  que les valeurs numériques du domaine), donc ADR-0002 tient toujours malgré la lecture de
  `Bundle.main`. Les 30 sites qui lisaient `.fr` en dur (`App` + `Store`) basculés sur
  `.localized` (`perl -pi -e 's/\.fr\b/.localized/g'` — `sed` BSD ne supporte pas `\b`).
- ✅ **Contenu des 18 jeux** — clés `es`/`de`/`it` ajoutées aux 82 objets `LocalizedText` de
  `spec/games/*.json`, par substitution texte ciblée (pas de reparse + `json.dump`, qui aurait
  détruit le formatage aligné à la main — colonnes des catégories Yams notamment). Copies
  synchronisées vers `CaCompteKit/Sources/Catalog/GameDefinitions/`, vérifiées par
  `Scripts/check-spec-sync.sh`.
- ✅ **`Localizable.xcstrings`** — les 179 chaînes traduisibles (sur 186 clés, 7 étant des
  symboles système) étendues à es/de/it, plus 2 nouvelles clés pour le raccourci de langue.
- ✅ **`knownRegions`** — `(fr, en, Base)` → `(fr, en, es, de, it, Base)`, condition pour qu'iOS
  propose ces langues dans son sélecteur par app (Réglages > Ça Compte > Langue).
- ✅ **Raccourci de langue** (`SettingsView.swift`) — iOS ne permet pas à une app tierce de
  changer sa propre langue en direct : seul levier, le sélecteur système par app. Une section
  « Langue » ouvre directement cette page (`UIApplication.openSettingsURLString`) plutôt que de
  laisser deviner où chercher dans l'app Réglages système.
- ✅ **Vérification visuelle par capture d'écran** (pas seulement une vérification d'existence) —
  `xcrun simctl launch ... -AppleLanguages` ne force pas la langue de façon fiable ; seul le
  lancement via `XCUIApplication.launch()` (déjà éprouvé plus tôt dans le projet pour forcer le
  français) applique `-AppleLanguages`/`-AppleLocale` de façon fiable. Captures prises en
  allemand, espagnol et italien via un test XCUITest jetable, extraites par `xcrun xcresulttool
  export attachments`, inspectées visuellement, puis le test et ses entrées `project.pbxproj`
  supprimés une fois la vérification faite.
- ✅ **Bug trouvé et corrigé : troncature de `EmptyState`** (`DesignSystem/Components/
  EmptyState.swift`) — remonté par la capture italienne (« Aggiungi un g… » tronqué au lieu de
  passer à la ligne). Cause : `.frame(maxHeight: 160)` sur le `VStack` parent contraint la
  hauteur proposée au `Text`, qui tronque plutôt que d'envelopper un message de deux lignes
  quand rien ne force sa hauteur naturelle. Corrigé par `.fixedSize(horizontal: false, vertical:
  true)` sur le `Text`. Bug latent préexistant (aurait pu toucher le français en Dynamic Type
  AX5), simplement plus visible avec une traduction italienne plus longue — illustre la valeur
  d'une vérification visuelle réelle par-dessus les tests d'existence automatisés.

Vérification finale : `xcodebuild test` (scheme `CaCompteKit-Package`, simulateur iOS — 34 tests
dans 5 suites, y compris `ContrastTests` qui nécessite `UIKit`) au vert ; `xcodebuild` (scheme
`CaCompte`) réussit ; `xcodebuild test` (même scheme, `CaCompteUITests`) 3 tests dont 1 skip
attendu (parcours n°2, voir Phase C) ; `Scripts/lint.sh` et `Scripts/check-spec-sync.sh` au vert.

### Écart mineur — `ActivityKit` dans `Domain` — ✅ documenté

`Domain/LiveActivity/MatchActivityAttributes.swift` importe `ActivityKit`, en désaccord avec
ADR-0002 (« `Domain` n'importe que `Foundation` »). Nécessaire — le protocole `ActivityAttributes`
doit être visible à la fois par `Domain` (qui définit le type) et par le widget (qui l'affiche).
Documenté comme exception explicite dans [02](02-architecture.md) (note ¹ sur la ligne `Domain`
du tableau des cibles), plutôt que déplacé hors de `Domain` : sans bénéfice pour le portage
Android (doc [11](11-portage-android.md)), ActivityKit n'ayant aucun équivalent sur cette
plateforme — `MatchActivityAttributes` n'aurait de toute façon jamais été porté tel quel, quel
que soit l'endroit où il vit côté Apple. N'affecte aucun autre invariant de `Domain` (toujours
zéro I/O, toujours `Sendable`).

### Phase H — Accessibilité — ✅ terminée

**Constat de départ** : 2 fichiers sur 38 dans `App/Features` touchaient l'accessibilité avant ce
chantier, zéro usage de `accessibilityReduceMotion` ou de variante haut-contraste dans les assets
de couleur joueur, malgré la prescription explicite de [07](07-design-system.md)/
[08](08-accessibilite.md) depuis le début du projet.

- ✅ **Infrastructure partagée** (construite en premier, puis utilisée dès l'écriture des écrans
  Tarot/Wizard — pas de rattrapage a posteriori sur ces deux-là) :
  - `Motion+Accessible.swift` (`DesignSystem/Tokens`) — `accessibleAnimation(_:value:)`, animation
    remplacée par `.linear(duration: Motion.fast.seconds)` quand `accessibilityReduceMotion` est
    actif. Appliqué aux 8 sites d'animation existants (`ScoreBoardView`, `SharedMatchView`,
    `LiveMatchView`, `PrimaryButtonStyle`) — `grep -rn "\.animation(\.default" App
    CaCompteKit/Sources` ne trouve plus rien hors de ce helper.
  - `AccessibleScoreRow.swift` (`DesignSystem/Components`) — `accessibleScoreRow(name:rank:score:
    delta:)`, un seul arrêt VoiceOver par ligne de tableau de scores (« Alice, deuxième, 44
    points ») plutôt que plusieurs `Text` séparés. Appliqué à 7 écrans : `ScoreBoardView`,
    `BeloteRoundView`, `TarotRoundView`, `ResultsView` (podium), `ReceivedMatchDetailView`,
    `GameLeaderboardView`, `GamesTabView`.
  - `Chip.swift` — zone tactile `Touch.minimum` (le commentaire de `Tokens/Sizes.swift` le
    promettait déjà sans jamais le câbler) + `.accessibilityAddTraits(.isSelected)`.
  - `Banner.announce(_:)` — annonce VoiceOver explicite (`UIAccessibility.post`) à l'apparition
    d'un bandeau, appelée depuis `LiveMatchView`/`SharedMatchView`.
  - Contraste augmenté des 10 couleurs `player/N` (`Assets.xcassets`) — entrées d'apparition
    `"contrast": "high"` ajoutées (valeurs renforcées côté clair, calculées pour un ratio WCAG
    ≥ 4.5, vérifiées côté sombre déjà suffisant sauf `player/5`). Aucun code Swift à écrire :
    `Color.player(n)` fait déjà une résolution de catalogue simple, la bascule est gratuite dès que
    la variante existe dans l'asset. `ContrastTests` étendu avec `UITraitCollection(
    accessibilityContrast: .high)` — 20 nouveaux cas, tous verts.
- ✅ **Tarot et Wizard construits accessibles dès leur premier commit** — `TarotRoundView`/
  `WizardRoundView` utilisent `accessibleScoreRow` et des `accessibilityLabel`/`accessibilityValue`
  explicites sur chaque `Stepper` (annonce, réalisé) sans étape de rattrapage séparée.
- ✅ **Rattrapage sur les grilles manche-par-manche** (chaque cellule est déjà un arrêt VoiceOver
  séparé par construction — pas de regroupement de ligne pertinent ici, seulement un label par
  cellule identifiant participant + manche) — `YamsSheetView`, `RoundHistoryView`,
  `ResultsView.roundByRoundSection`.
- ✅ **Rattrapage sur les 8 zones du plan**, par priorité :
  - **LiveMatch** — `ScoreBoardView` (regroupement de ligne + `accessibilityLabel` propre sur le
    `TextField` éditable, qui doit s'annoncer lui-même au focus plutôt que dépendre du
    regroupement) ; `HistoryListView` déjà couvert par le même motif.
  - **Results** — podium regroupé, `Chart` d'évolution sans équivalent VoiceOver natif pour
    `LineMark` : résumé composé (`accessibilityElement(children: .ignore)` +
    `accessibilityValue` du classement final) plutôt qu'une description point par point,
    inexploitable au doigt sur une dizaine de manches.
  - **Players** — `PlayerEditorView.emojiGrid` (trait `.isSelected`, `paletteRow` l'avait déjà) ;
    `PlayersListView`/`ArchivedPlayersView` (regroupement de ligne).
  - **MatchSetup** — `GamesTabView` (regroupement de ligne) ; `MatchSetupView` (trait
    `.isSelected` sur la ligne de sélection de joueur, même motif que `emojiGrid` — la coche
    n'était sinon jamais annoncée) ; `JoinTabView`/`QRScannerView` audités, aucun correctif
    nécessaire (contrôles système standards déjà accessibles, et le scan caméra a toujours un
    repli clavier accessible via « Saisir un code »).
  - **History** — `HistoryListView`, `ArchivedMatchesView` (regroupement de ligne) ;
    `HistoryDetailView` hérite des correctifs `ResultsView`/`ReceivedMatchDetailView` sans rien à
    faire en propre (pur routage).
  - **Leaderboard** — `GameLeaderboardView` (regroupement de ligne).
  - **Profile** — `ProfileView`, `BarMark` d'activité : même défaut et même correctif que le
    `Chart` de `ResultsView`.
  - **Settings** — audité, aucun correctif nécessaire : uniquement des `Toggle`/`Picker`/`Button`
    de `Form` système, déjà accessibles par défaut.
- ✅ **`ResultsShareCard`** — délibérément hors périmètre : image statique rendue une fois pour
  `ShareLink`, pas un écran interactif.

**Résultat chiffré** : 20 fichiers sur 42 dans `App/Features` touchent maintenant
l'accessibilité (2 au départ), contre un dénominateur qui a grandi de 4 (les écrans Tarot/Wizard).

Vérification : `xcodebuild test` (scheme `CaCompteKit-Package`) — 52 tests dans 5 suites, y
compris les 20 nouveaux cas `ContrastTests` haut-contraste ; `xcodebuild test` (scheme `CaCompte`,
`CaCompteTests`) — 12/12 ; `xcodebuild` (scheme `CaCompte`) réussit à 0 avertissement ;
`Scripts/lint.sh` et `Scripts/check-spec-sync.sh` verts ; `grep -rn "\.animation(\.default" App
CaCompteKit/Sources` ne trouve plus rien hors `accessibleAnimation`.

**Traversée manuelle sur appareil réel** — VoiceOver (créer une partie, saisir plusieurs manches,
consulter les résultats, sans regarder l'écran) sur les 8 zones et sur le parcours Tarot/Wizard,
Dynamic Type AX5, Reduce Motion, contraste augmenté : vérifiée par l'utilisateur, aucune
régression signalée. Même principe que les autres vérifications sur appareil physique déjà menées
sur ce projet (partie partagée Supabase, Dynamic Island) — un jugement humain sur ce qui « sonne
bien » à l'oreille, qu'un test automatisé ne peut pas remplacer.

**Fini** — ligne accessibilité de [12-roadmap.md](12-roadmap.md) (P9) passée de ⏳ à ✅.

### Vérification P9 sur appareil réel — Widget retiré, Siri mis en pause

En marge de la traversée accessibilité ci-dessus, l'utilisateur a aussi vérifié sur appareil
physique les autres finitions P9 restées jusque-là au stade « build + simulateur seulement »
(Widget, Handoff, Siri).

- ❌ **Widget d'écran d'accueil retiré** — présent, mais jugé sans intérêt réel une fois vu en
  usage (classement figé jusqu'au retour de l'app en arrière-plan, contrairement à la Live
  Activity qui suit chaque manche en direct — les deux affichaient la même information, l'une
  en retard sur l'autre). Supprimé plutôt que laissé en l'état : `MatchWidget.swift` retiré du
  projet (fichier + 4 entrées `project.pbxproj`), `CaCompteWidgetBundle` ne déclare plus que
  `MatchLiveActivityWidget`, `WidgetCenter.shared.reloadAllTimelines()` retiré de `CaCompteApp`
  (n'avait plus de destinataire). `SharedStore`/le conteneur App Group ne sont **pas** retirés
  malgré n'être plus lus par rien après ce changement — y toucher changerait l'emplacement du
  store SwiftData des installations existantes, ce qui ferait apparaître les parties et joueurs
  déjà enregistrés comme perdus. Coût accepté : quelques dizaines de lignes mortes plutôt qu'un
  risque de perte de données perçue.
- ✅ **Handoff** — fonctionne tel quel sur appareil réel, jugé d'un intérêt limité au quotidien.
  Décision : garder l'existant, aucun développement supplémentaire prévu.
- ⏳ **Siri (`StartMatchIntent`) — ne parvient pas à lancer une partie, mis en pause sans être
  résolu.** Constaté sur appareil réel, en trois temps :
  1. Premier symptôme : l'app ne s'ouvrait pas du tout — signe que `perform()` n'était jamais
     appelé (le code en aval, identique au chemin déjà éprouvé des liens `cacompte://`/Handoff,
     n'était donc pas en cause).
  2. Deuxième essai, symptôme différent : « lance une partie de Skyjo sur CaCompte » déclenchait
     un intent **musique** du système au lieu de l'app. Diagnostiqué : `CaCompteShortcuts` ne
     déclarait qu'**une seule** formulation par intent (« Commence…dans… ») — Siri ne fait pas de
     correspondance sémantique libre sur les App Shortcuts, un verbe (« lance ») ou une
     préposition (« sur ») absents des phrases déclarées laissent le champ libre à un intent
     système concurrent mieux couvert. Corrigé en couvrant les verbes/prépositions les plus
     probables : 4 phrases pour `StartMatchIntent` (commence/lance/démarre, dans/sur), 3 pour
     `ResumeMatchIntent` (reprends ×2/continue).
  3. Après réinstallation, troisième symptôme : « Siri ne prend pas en charge cette
     fonctionnalité sur Ça Compte », reproductible seulement de façon intermittente (~1 essai sur
     10). La capacité **Siri** (`com.apple.developer.siri`), absente des entitlements, a été
     ajoutée — signature vérifiée sur appareil réel avec le compte payant de l'utilisateur
     (`xcodebuild build`, provisioning automatique, aucune erreur). N'a pas résolu le problème.

  Cause exacte non identifiée après ces trois correctifs successifs (couverture de phrases,
  capacité Siri) — chacun plausible, aucun suffisant. Le comportement intermittent pointe vers un
  problème d'indexation Siri/App Intents côté système plutôt que vers le code de l'app, mais ça
  reste une hypothèse non confirmée. **Mis en pause à la demande de l'utilisateur** plutôt que de
  continuer à corriger à l'aveugle sans nouvelle piste : les trois correctifs restent en place
  (ils ne peuvent pas nuire), mais Siri lui-même n'est pas considéré fonctionnel.

Vérification : `xcodebuild` (scheme `CaCompte`) réussit à 0 avertissement après le retrait du
widget et l'ajout de la capacité Siri ; `grep -rn "MatchWidget" App CaCompteKit` ne trouve plus
rien hors de ce journal.

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
