# Qui Mène ?

Application de suivi de scores pour jeux de société : on saisit les scores de chaque manche,
l'app applique les règles du jeu, détecte la fin de partie et raconte ce qui s'est passé.

> Sobre et précis pendant la partie, ludique au moment du résultat.

- **iPhone et iPad** (SwiftUI, SwiftData) et **Android** (Kotlin, Jetpack Compose, Room), deux
  applications natives.
- **Sans compte.** Un pseudo et un avatar suffisent.
- **Hors ligne** pour tout ce qui se joue sur un seul appareil. Seuls le partage en direct,
  l'historique partagé entre amis et la mise à jour de l'écran verrouillé passent par un serveur
  (Supabase), avec des données chiffrées et supprimées automatiquement.
- Français, anglais, espagnol, allemand et italien.

Site : [quimene.vercel.app](https://quimene.vercel.app)

## Fonctionnalités

- **20 jeux** avec leurs règles : Tarot, Belote, Rami, Yams, Skyjo, Mölkky, Wizard, 6 qui prend,
  Pétanque, Scrabble… et un jeu libre pour le reste ([catalogue](docs/05-catalogue-jeux.md)).
- **Règles appliquées automatiquement** : doublement du Skyjo, contrats du Tarot, grille du Yams,
  équipes de la Belote, retour à 25 au Mölkky. Fin de partie détectée, dernière manche annulable.
- **Partie partagée** : un code à 6 chiffres ou un QR code, et chacun suit le tableau sur son
  téléphone, iPhone et Android mélangés. N'importe quel participant peut saisir une manche, même
  si l'appareil qui a lancé la partie s'éteint.
- **Écran verrouillé et Dynamic Island** (iOS) mis à jour à chaque manche.
- **Résultats** : podium, faits marquants, badges, courbe d'évolution, détail manche par manche,
  image à partager (iOS).
- **Historique et statistiques** : filtres par jeu et par joueur, fiches de profil, classement
  par jeu.
- **Profils et amis** : les parties jouées ensemble arrivent dans l'historique de chacun.
- **Sauvegarde** : iCloud entre les appareils Apple, sauvegarde automatique sur Android.

## Organisation du dépôt

```
.
├── apple/
│   ├── QuiMeneKit/     package Swift : Domain, Catalog, Store, Sync, DesignSystem + tests
│   └── App/            application iOS/iPadOS, widget Live Activity, tests unitaires et UI
├── android/            projet Gradle : :domain, :catalog, :store, :sync, :designsystem, :app
├── spec/               spécification partagée (JSON) : jeux, golden files, fixtures croisées
├── supabase/           migrations Postgres, fonctions Edge, tests SQL
├── website/            site vitrine, confidentialité et assistance (Astro)
├── store/              textes des fiches App Store / Play Store, slides des captures
├── docs/               documentation de conception
├── Scripts/            vérifications et outils (spec, lint, traductions, captures)
├── ci_scripts/         hook Xcode Cloud
└── Licenses/           licences des ressources tierces
```

## Principes

- **Le calcul des scores est isolé.** `Domain` (Swift) et `:domain` (Kotlin) ne contiennent que
  des types immuables et des fonctions pures, sans persistance, réseau ni interface
  ([architecture](docs/02-architecture.md)).
- **Une spécification commune, pas de code partagé.** Les deux applications sont écrites dans
  leur langage et rejouent les mêmes fichiers de `spec/` : définitions de jeux et parties de
  référence (*golden files*) avec leurs résultats attendus. Un score ne peut pas diverger d'une
  plateforme à l'autre sans faire échouer les tests ([ADR-0004](docs/13-decisions-adr.md)).
- **Une modification de règle commence toujours par `spec/`**, jamais par le code
  ([spec/README.md](spec/README.md)).
- **Journal d'événements.** L'état d'une partie est le rejeu d'un journal : annulation,
  synchronisation et tests reposent sur le même mécanisme ([moteur de règles](docs/04-moteur-de-regles.md)).

## Compiler et tester

### Apple

Prérequis : Xcode 26 ou plus récent (iOS 18 minimum, Swift 6 en concurrence stricte).

- Ouvrir `apple/App/QuiMene.xcodeproj`, schéma `QuiMene`.
- Tests du package (domaine, catalogue, golden files, persistance, sessions) :

  ```sh
  cd apple/QuiMeneKit
  xcodebuild test -scheme QuiMeneKit-Package -destination 'platform=iOS Simulator,name=iPhone 17'
  ```

- Le projet est signé pour l'équipe de l'auteur. Pour le compiler sur un appareil, remplacer
  l'équipe de développement, l'identifiant de bundle, le conteneur iCloud
  (`iCloud.com.quimene.app`) et l'App Group (`group.com.quimene.app`).

Une seule dépendance tierce : [`supabase-swift`](https://github.com/supabase/supabase-swift),
versions figées par `Package.resolved`.

### Android

Prérequis : JDK 17 (le wrapper Gradle suffit). Android 8.0 (API 26) minimum.

```sh
cd android
./gradlew build            # ktlint, Android Lint, tests unitaires
./gradlew :app:installDebug
```

Les textes d'interface viennent du catalogue Apple (`Localizable.xcstrings`). Après l'avoir
modifié : `python3 Scripts/extract-android-strings.py` (vérifié par la CI).

### Supabase

Le serveur ne sert qu'aux fonctions en ligne. Prérequis : [Supabase CLI](https://supabase.com/docs/guides/cli)
et Docker.

```sh
supabase start      # pile locale, applique supabase/migrations
supabase test db    # tests SQL de supabase/tests
```

Les applications pointent vers le projet de production (`SupabaseSyncConfig.swift`,
`SupabaseSession.kt`), avec sa clé *publishable*, faite pour être embarquée dans un client :
aucune table n'est accessible directement, tout passe par des fonctions qui exigent un code ou
un identifiant. Pour un fork, utiliser son propre projet. La fonction d'envoi des mises à jour
d'écran verrouillé attend les secrets APNs `APNS_KEY_ID`, `APNS_TEAM_ID`, `APNS_PRIVATE_KEY`,
`APNS_BUNDLE_ID` et `APNS_ENVIRONMENT`.

### Site

Voir [website/README.md](website/README.md).

### Vérifications

| Commande | Rôle |
|---|---|
| `Scripts/check-spec-sync.sh` | `spec/` et ses copies embarquées côté Apple sont identiques |
| `Scripts/lint.sh` | swift-format, configuration par défaut d'Apple |
| `Scripts/store-screenshots.sh` | captures des fiches App Store et Play Store ([doc 10](docs/10-tests-et-qualite.md#captures-des-stores)) |

Intégration continue : GitHub Actions pour Android et la cohérence de `spec/` et des traductions
(`.github/workflows/android-ci.yml`), Xcode Cloud pour Apple (`ci_scripts/ci_post_clone.sh`).

## Documentation

| # | Document | Contenu |
|---|---|---|
| 01 | [Vision produit](docs/01-vision-produit.md) | Problème, personas, parcours, périmètre |
| 02 | [Architecture](docs/02-architecture.md) | Modules, dépendances, concurrence, flux de données |
| 03 | [Modèle de données](docs/03-modele-de-donnees.md) | Persistance, contraintes CloudKit, conflits, migrations |
| 04 | [Moteur de règles](docs/04-moteur-de-regles.md) | Types du domaine, `GameRules`, journal d'événements, cas Skyjo |
| 05 | [Catalogue de jeux](docs/05-catalogue-jeux.md) | Les 20 jeux, leurs règles et fins de partie |
| 06 | [Statistiques](docs/06-statistiques.md) | Faits marquants, badges, profils, classements |
| 07 | [Charte graphique](docs/07-charte-graphique.md) | Source unique des tokens : couleurs, typographie, grille, logo, ton |
| 08 | [Design system Apple](docs/08-design-system.md) | Implémentation SwiftUI de la charte |
| 09 | [Partie partagée](docs/09-partie-partagee.md) | Sessions en ligne, ordre, chiffrement, écran verrouillé |
| 10 | [Tests et qualité](docs/10-tests-et-qualite.md) | Golden files, invariants, CI, captures des stores |
| 11 | [Android](docs/11-portage-android.md) | Équivalences, modules, charte sur Android, localisation |
| 13 | [Décisions (ADR)](docs/13-decisions-adr.md) | Décisions structurantes et alternatives écartées |
| 14 | [Profils partagés](docs/14-profils-partages.md) | Profil, amis, « Qui es-tu ? », historique partagé |
| 16 | [Sessions en ligne et profils](docs/16-sessions-en-ligne-et-profils.md) | Pourquoi et comment les sessions serveur ont remplacé l'hôte |
| 17 | [Recette croisée](docs/17-recette-croisee.md) | Compatibilité iOS ↔ Android : fixtures et scénarios |

## Licence

© 2026 Erwan Decoster. Tous droits réservés : le dépôt est publié pour consultation, sans
licence de réutilisation (voir [LICENSE](LICENSE)).

Wordmark en [Outfit](https://fonts.google.com/specimen/Outfit) (SIL Open Font License, voir
`Licenses/`), vectorisé : la police n'est pas embarquée dans les applications.

Les noms de jeux cités appartiennent à leurs propriétaires respectifs. Qui Mène ? n'est affiliée
à aucun éditeur.
