# 10 — Tests et qualité

## Ce qu'on cherche à empêcher

Un seul défaut est réellement grave : **un score faux**. Une animation ratée se corrige la
semaine suivante ; un total erroné détruit la confiance dans l'app définitivement, et personne
ne la rouvre. La stratégie de test est donc déséquilibrée volontairement : l'essentiel de
l'effort porte sur le domaine, très peu sur l'interface.

## Pyramide

```
        ╱  Interface — 3 parcours     ╲       lents, fragiles, indispensables
       ╱     création joueur           ╲      quand même
      ╱      partie Skyjo complète      ╲
     ╱       reprise après relance       ╲
    ╱──────────────────────────────────────╲
   ╱  Intégration — Store, Sync             ╲   base en mémoire,
  ╱     repositories, schéma, sessions        ╲  serveur simulé
 ╱────────────────────────────────────────────╲
╱  Unitaires — Domain, Catalog, Stats          ╲  l'essentiel de l'effort
│  ▸ golden files rejoués                       │ < 1 s au total
│  ▸ invariants, propriétés                     │
└────────────────────────────────────────────────┘
```

**Swift Testing** côté Apple (XCTest pour les tests d'interface), **JUnit 5** et Robolectric
côté Android.

## Les golden files

C'est la pièce maîtresse, et le pivot de la cohérence entre les deux plateformes.

Un golden file (`spec/golden/`) décrit une partie complète et ses résultats attendus, en JSON,
sans une ligne de Swift ni de Kotlin. Les deux plateformes le chargent, le rejouent, et
comparent (`GoldenFileTests.swift`, `GoldenFileTest.kt`). Le principe :

```swift
@Test(arguments: GoldenFile.all)          // découverte automatique du dossier
func replay(_ golden: GoldenFile) throws {
    // rejoue chaque manche avec le moteur du jeu (catalogue embarqué)…
    // …et vérifie après CHAQUE manche : scores calculés, cumuls, statut
    // puis à la fin : raison de fin, classement, faits marquants attendus
}
```

Un test paramétré : ajouter un golden au dossier ajoute un cas, sans toucher au code de test.
Un échec nomme le fichier fautif. Vérifier l'état après chaque manche, et pas seulement à la fin,
est délibéré : une erreur qui se compense entre deux manches passerait sinon inaperçue.

**Couverture par jeu** : au moins un golden file par jeu, et pour les jeux à moteur propre le
cas limite qui fait leur particularité (doublement Skyjo, bonus du Yams, preneur à 3, 4 ou
5 joueurs au Tarot, dépassement de 50 au Mölkky, ex æquo au sommet). Les branches qu'un golden
n'atteint pas sont couvertes par des tests unitaires ciblés (départage Yams, capot Belote, fin
manuelle).

## Invariants et tests de propriété

Certaines vérités doivent tenir pour *toute* partie, pas seulement pour les cas écrits à la
main. Elles sont vérifiées sur des entrées générées par un générateur reproductible
(`SeededGenerator`, même graine = mêmes tirages).

| Invariant | Portée |
|---|---|
| Somme des scores d'une donne = 0 | Tarot — attrape immédiatement toute erreur de formule ou de répartition |
| `replay(L) == replay(σ(L))` pour toute permutation σ | journal d'événements — fondement de la sync |
| `replay(L + L) == replay(L)` | idempotence — doublons réseau |
| `standings()` est un ordre total, ex æquo compris | tous les jeux |
| `total(joueur) == Σ computedValue` de ses entrées | tous les jeux |
| `endCheck` ne repasse jamais de `.ended` à `.continue` | monotonie — évite une partie qui « redémarre » |
| Un `MatchState` encodé puis décodé est identique | `Codable`, échange entre appareils |

Ces sept lignes attrapent en pratique plus de bugs que cinquante tests d'exemple.

## Tests d'intégration

**Store** — `ModelConfiguration(isStoredInMemoryOnly: true)` (Room en mémoire côté Android) :
aller-retour domaine ↔ persistance, préservation de l'ordre, suppression en cascade, fiche
joueur supprimée alors qu'elle apparaît dans l'historique, statistiques de profil et
classements. `CloudKitSchemaTests` ouvre le vrai schéma avec un container CloudKit actif.

**Migrations** — dès qu'une V2 du schéma existe : un magasin de test figé par version publiée,
ouvert par la version courante ; le test échoue si une migration perd une donnée.

**Sessions** — `OnlineSessionTests` / `OnlineSessionTest` sur un serveur en mémoire qui applique
les mêmes règles que le SQL (numérotation, `stale_seq`, idempotence, lots) ; tests SQL dans
`supabase/tests/` ; chiffrement, identités et boîte aux lettres testés séparément.

**Compatibilité croisée** — chaque format échangé a un fichier de référence produit par le code
réel d'une plateforme et relu par les tests de l'autre, dans les deux sens (`spec/session/`,
doc [17](17-recette-croisee.md)).

**Modèles de flux** — `QuiMeneTests` (Apple) et les tests de ViewModel (Android) couvrent la
saisie, la configuration de partie, l'éditeur de joueur et l'accord des pluriels.

## Tests d'interface

Trois parcours seulement, sur le chemin critique (`QuiMeneUITests`) :

1. Créer un joueur, le retrouver dans la liste.
2. Partie de Skyjo jusqu'à la fin, vérifier l'écran de résultats.
3. Démarrer une partie, tuer l'app, la relancer, vérifier que la partie est proposée en reprise.

Le troisième est le plus important : il couvre le scénario « soirée perdue », identifié comme
rédhibitoire dans la [vision produit](01-vision-produit.md).

## Ce qui n'est pas testé automatiquement

Assumé explicitement, pour ne pas dépenser l'effort au mauvais endroit :

- L'apparence. Pas de tests de capture d'écran : Liquid Glass et Dynamic Type les rendraient
  instables à chaque version d'iOS pour un bénéfice faible. Les galeries de previews du design
  system jouent ce rôle en revue.
- Le partage en direct sur appareils réels, entre iPhone et Android : scénarios de recette de la
  doc [17](17-recette-croisee.md).
- La synchronisation CloudKit, qui nécessite deux appareils et un compte réel.

## Qualité de code

- **Mode langage Swift 6, concurrence stricte**, sur toutes les cibles. Ni `@unchecked Sendable`
  ni `@preconcurrency import`, à une exception près : `QRScannerView.swift` importe
  `AVFoundation` avec `@preconcurrency`, parce que `AVCaptureMetadataOutputObjectsDelegate` n'est
  pas annoté `Sendable` par Apple. Le fichier isole le risque (`nonisolated` pour le délégué,
  retour explicite sur `@MainActor` pour toute mutation d'état).
- **Avertissements = erreurs** partout : cibles Xcode (`SWIFT_TREAT_WARNINGS_AS_ERRORS`),
  package (`.treatAllWarnings(as: .error)` dans `Package.swift`), compilateur Kotlin
  (`allWarningsAsErrors`) et Android Lint (`warningsAsErrors`, sauf les vérifications « nouvelle
  version disponible », dépendantes du réseau et de la date).
- **Formatage** : swift-format avec la configuration par défaut d'Apple (`.swift-format`,
  `Scripts/lint.sh`), ktlint côté Android.
- **Dépendances** : chaque dépendance tierce passe par un ADR ([ADR-0012](13-decisions-adr.md)).
  Côté Apple, seule `supabase-swift`.
- **Pas de `print`** : côté Apple, journalisation par `os.Logger`, sans données personnelles ni
  jetons.

## Intégration continue

- **GitHub Actions** (`.github/workflows/android-ci.yml`) : ktlint, build avec Android Lint et
  tests unitaires ; synchronisation de `spec/` avec ses copies Apple ; `strings.xml` à jour par
  rapport au catalogue Apple.
- **Xcode Cloud** : `ci_scripts/ci_post_clone.sh` fait échouer le build tôt si `spec/` est
  désynchronisé ou si un fichier Swift est mal formaté, avant la compilation et les tests.

Les tests unitaires du domaine se comptant en millisecondes, le temps de CI est presque
entièrement celui de la compilation — d'où l'intérêt d'un `Domain` sans dépendance.

## Captures des stores

`Scripts/store-screenshots.sh [ios|android|slides|all]` produit, sans manipulation à la main, les
images des fiches App Store et Play Store dans toutes les langues. Résultat dans
`store-screenshots/` (non versionné) :

```
store-screenshots/
├── app-store/<langue>/iphone-6.9/   1320 × 2868   captures brutes (iPhone 17 Pro Max)
├── app-store/<langue>/ipad-13/      2064 × 2752   (iPad Pro 13 pouces)
├── play-store/<langue>/phone/       1200 × 2400
├── play-store/<langue>/tablet-7/    1200 × 1920
├── play-store/<langue>/tablet-10/   1600 × 2560
└── slides/                          les images à publier, mêmes dossiers :
                                     App Store aux tailles ci-dessus, Play Store en 1440 × 2560,
                                     plus play-store/<langue>/feature-graphic.png (1024 × 500)
                                     et play-store/icon-512.png
```

Chaque dossier contient six écrans, numérotés dans l'ordre de la fiche : `01-partie` (partie de
Skyjo en cours), `02-resultats`, `03-jeux`, `04-profil`, `05-historique`, `06-joueurs`.
`LOCALES=fr-FR,en-US` limite les langues (défaut : les cinq). `slides` seul recompose les slides
à partir des captures existantes, en quelques secondes — utile après une retouche de texte.

- **Données** : `spec/screenshots/demo-data.json`, un seul fichier pour les deux apps (6 joueurs,
  une partie en cours, 5 terminées). Les dates sont relatives au moment de la capture. Une partie
  dont le statut final ne correspond pas à son champ `end` (seuil jamais atteint…) fait échouer
  la capture plutôt que de produire une image fausse.
- **iOS** : `StoreScreenshotTests` (XCUITest, sauté sans `QUIMENE_SCREENSHOT_LOCALES`) lance
  l'app avec `-uitesting-reset -screenshots` ; l'app (Debug uniquement) charge les données de
  démo dans le magasin des tests d'interface et coupe ses appels au serveur. Le script utilise
  ses propres simulateurs « Qui Mène Screenshots … », effacés à chaque passage et redémarrés dans
  chaque langue (la date de la barre d'état iPad suit la langue du système), barre d'état à
  9:41, et extrait les images des pièces jointes du `.xcresult`.
- **Android** : `StoreScreenshotsTest` rend les vrais écrans (`QuiMeneApp`) par Robolectric, sans
  émulateur, au SDK 30 : la palette de la marque plutôt que les couleurs dynamiques d'un fond
  d'écran arbitraire. Proportions d'appareils réels, dans la limite de la Play Console (un côté au
  plus deux fois plus long que l'autre). Robolectric ne dessine ni barre d'état, ni clavier, ni
  ombres portées : la barre d'état est ajoutée par les slides.
- **Slides** (`store/slides/`) : `captions.json` porte le titre et le sous-titre de chaque écran
  dans les cinq langues ; `slide.html` les met en page, `render.mjs` les fait rendre par Chrome
  sans interface (Node 22+ et Google Chrome, aucune dépendance). Mise en page fidèle à la charte
  ([07](07-charte-graphique.md)) : fond `brand/ink` uni (le seul fond coloré admis sous le logo,
  sans dégradé), déclinaison horizontale du logo en blanc sur la première slide, SF Pro côté App
  Store et Roboto côté Play Store (chargée depuis Google Fonts : le rendu Android demande le
  réseau), laiton réservé au mot « podium » de la slide des résultats. Play Store en 9:16, le
  format que la Play Console met en avant.

## Définition de « terminé »

Un écran ou une fonctionnalité n'est terminé que si :

- [ ] les tests unitaires du domaine concerné passent, golden files inclus ;
- [ ] la fonctionnalité est traversable **entièrement à VoiceOver / TalkBack**, sans regarder
      l'écran ;
- [ ] elle est lisible en Dynamic Type AX5 (échelle de police ×2 sur Android) sans troncature
      ni chevauchement ;
- [ ] elle est correcte en mode clair et en mode sombre ;
- [ ] elle est correcte sur petit écran (iPhone SE) et sur tablette ;
- [ ] toutes les chaînes sont dans le catalogue et traduites dans les 5 langues, libellés
      d'accessibilité compris — aucun texte affiché en dur, ni dans une `String` Swift qui
      contourne le catalogue, ni dans un littéral Kotlin ;
- [ ] aucun nouvel avertissement de compilation ;
- [ ] le parcours a été fait une fois sur un appareil réel, pas seulement en simulateur.

Les deux derniers points sont ceux qu'on saute quand on est pressé, et ceux qui coûtent le plus
cher plus tard.
