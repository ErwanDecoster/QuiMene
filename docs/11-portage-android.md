# 11 — Android

## Stratégie

**Ré-implémentation 100 % native, pilotée par une spécification partagée.**

Aucune couche de partage de code : pas de Kotlin Multiplatform, pas de Swift compilé pour
Android, pas de moteur JavaScript. Les deux applications sont pleinement idiomatiques sur leur
plateforme. Ce qu'elles partagent n'est pas du code, c'est **`spec/`** — du JSON, lu et rejoué
par les deux ([ADR-0004](13-decisions-adr.md)).

```
                    spec/                   source de vérité
        ┌──────────────┴──────────────┐
        │  games/*.json               │     définitions déclaratives
        │  golden/*.json              │     parties + résultats attendus
        │  session/*.json             │     formats échangés, dans les deux sens
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

**Le coût** : le moteur de règles existe en deux exemplaires. **Le bénéfice** : zéro compromis
d'idiome, zéro outillage croisé (pas de Gradle dans le build iOS), et deux apps qui ressemblent
chacune à leur plateforme. Sur une application dont l'interface représente l'essentiel du code
et dont le moteur est de l'arithmétique pure et stable, c'est le bon arbitrage.

**Le garde-fou** : les golden files. Une divergence de calcul entre les deux plateformes fait
échouer la suite de tests Android ; une divergence de format d'échange fait échouer les tests
croisés ([17](17-recette-croisee.md)).

---

## Équivalences techniques

| Apple | Android | Note |
|---|---|---|
| Swift 6 (concurrence stricte) | Kotlin 2 + coroutines | `Sendable` → immuabilité + `data class` |
| SwiftUI | Jetpack Compose, Material 3 | Modèle déclaratif équivalent |
| `@Observable` | `StateFlow` dans un `ViewModel` | Compose n'a pas d'équivalent d'observation implicite |
| `@MainActor` | `Dispatchers.Main` | |
| `struct` / valeur | `data class` (immuable) | Le domaine reste sans mutation |
| enum à valeur associée | `sealed interface` | |
| SwiftData | Room + KSP | Voir « Persistance » |
| CloudKit privé | *(aucun équivalent)* | Sauvegarde automatique Android, voir « Synchronisation » |
| `UserDefaults` | Preferences DataStore | |
| `supabase-swift` | `supabase-kt` (Realtime, Postgrest) | Même service des deux côtés ([ADR-0017](13-decisions-adr.md)) |
| `CryptoKit` (AES-GCM, HKDF) | `javax.crypto` | HKDF (RFC 5869) écrit sur `Mac("HmacSHA256")`, sans bibliothèque de crypto |
| Swift Testing | JUnit 5, Kotest (assertions), Robolectric | Tests paramétrés des deux côtés |
| Swift Charts | `Canvas` Compose | Courbe dessinée à la main, aucune bibliothèque de graphiques |
| SF Symbols | Material Icons (Compose) | |
| `AVCaptureMetadataOutput` / `CIFilter` | CameraX + ML Kit / ZXing | Lecture et génération des QR codes |
| `ImageRenderer` + `ShareLink` | — | Image de résultats propre à iOS |
| Live Activity | — | Android envoie les mises à jour aux iPhone de la session, n'en affiche pas |
| Xcode Cloud | GitHub Actions | |
| `Localizable.xcstrings` | `strings.xml` | Généré depuis le catalogue Apple, voir « Localisation » |

## Modules

Gradle multi-module, miroir des cibles de `QuiMeneKit`, avec le même sens de dépendance :

| Module | Type | Dépend de | Rôle |
|---|---|---|---|
| `:domain` | Kotlin/JVM pur | — | Types, `MatchEngine`, `GameRules`, `StatsEngine` |
| `:catalog` | Kotlin/JVM pur | `:domain` | Moteurs des jeux, définitions embarquées |
| `:store` | bibliothèque Android | `:domain` | Room, repositories, statistiques, identité d'appareil |
| `:sync` | Kotlin/JVM pur | `:domain`, `supabase-kt` | Sessions en ligne, chiffrement, identités, boîte aux lettres |
| `:designsystem` | bibliothèque Android (Compose) | — | Thème, tokens, composants |
| `:app` | application | tous | Écrans, navigation, ViewModels |

`:domain` est un module Kotlin/JVM pur (`kotlin("jvm")`, pas `com.android.library`) : aucune
dépendance au SDK Android sur le classpath, donc aucun moyen d'y importer `android.*`, même par
erreur. Les versions sont centralisées dans `gradle/libs.versions.toml`.

Chaque fichier du domaine Kotlin a son pendant Swift, au même nom (`MatchState.kt` /
`MatchState.swift`, `SkyjoRulesV1.kt` / `SkyjoRulesV1.swift`…) : une relecture croisée est
possible sans table de correspondance.

## Chargement des définitions

SwiftPM impose une copie des JSON dans la cible Apple, vérifiée par `Scripts/check-spec-sync.sh`.
Côté Android, une tâche Gradle (`copySpecResources`, branchée sur `processResources`) recopie
`spec/games/` dans `build/` à chaque build : la copie n'est jamais commitée et ne peut donc pas
diverger. Les tests rejouent `spec/golden/` de la même façon.

## Persistance

Room remplace SwiftData. Trois entités, miroir un pour un des modèles SwiftData
([03](03-modele-de-donnees.md)) : `PlayerEntity`, `MatchEntity`, `ParticipantEntity`, avec le
même mapping vers le domaine. `eventLogData` reste **la source de vérité** : la reprise après
relance rejoue ce journal, jamais un total mis en cache.

Deux différences :

- **Pas de synchronisation intégrée.** SwiftData+CloudKit fait gratuitement ce que Room ne fait
  pas du tout.
- **Les relations sont explicites.** Les participants d'une partie se lisent par
  `ParticipantDao`, dans un ordre déterministe (`ORDER BY`), alors que SwiftData impose des
  champs d'ordre explicites et un tri à la lecture.

Les DAO exposent des `Flow`, équivalent du rafraîchissement automatique de `@Query`. L'identité
d'appareil vit dans Preferences DataStore.

## Synchronisation

| Fonction | Apple | Android |
|---|---|---|
| **Partie partagée en direct** | Supabase — protocole et format communs | idem, `supabase-kt` |
| **Historique partagé entre amis** | boîte aux lettres chiffrée | idem |
| **Sync entre appareils du propriétaire** | CloudKit privé | **absente** |
| **Sauvegarde** | iCloud | sauvegarde automatique Android (`allowBackup`, `dataExtractionRules`) |

La partie partagée **n'est pas un point de divergence** : un iPhone et un Android rejoignent la
même session, avec les mêmes messages chiffrés ([09](09-partie-partagee.md)). Le seul vrai point
de divergence est la synchronisation entre les appareils **d'un même propriétaire** : il n'existe
pas d'équivalent Android à CloudKit (stockage privé, gratuit, lié au compte système et
synchronisé sans serveur). Les options seraient Google Drive App Data (API lourde, OAuth) ou un
backend maison, contraire au principe « sans compte ».

## Charte graphique sur Android

La [charte](07-charte-graphique.md) est écrite pour être bi-plateforme : chaque token y porte
déjà sa colonne Android.

| Sujet | Règle |
|---|---|
| **Couleurs** | Sur Android 12+ (API 31), l'app suit les **couleurs dynamiques** du téléphone (Material You) pour les rôles Material 3 ; en dessous, `ColorScheme` construit depuis les hex de la charte. Les couleurs sémantiques et la palette des dix joueurs restent **toujours** celles de la charte : l'identité visuelle d'un joueur doit être stable d'un appareil à l'autre. |
| **Typographie** | Échelle **Material 3**, pas les tailles iOS. La hiérarchie est partagée, la mesure ne l'est pas — voir charte §2.2. `letterSpacing` est posé explicitement. |
| **Espacements et rayons** | Valeurs identiques, en `dp` (`Space.kt`, `Radius.kt`, `Motion.kt`… mêmes noms que côté Swift). |
| **Élévation** | Rendu Material (tonal + ombre), pas de verre. C'est l'inverse d'iOS et c'est voulu — [ADR-0010](13-decisions-adr.md). |
| **Zone tactile** | **48 dp** (Android), pas 44 pt. |
| **Navigation** | Barre de navigation Material en îlot flottant : Joueurs · Jeux · Historique · Profil. Bouton retour système. |
| **Bandeaux** | `Snackbar` Material en bas, pas de bandeau en haut. |
| **Mouvement** | Mêmes durées, courbes Material (`emphasizedDecelerate` / `emphasizedAccelerate`). |

Le **logo** est identique : même SVG, mêmes déclinaisons, mêmes zones de protection. L'icône
d'application est une **icône adaptative** (fond + premier plan, zone sûre de 66 dp sur 108,
couche monochrome pour les icônes thématiques) : le masque Android est plus agressif que celui
d'iOS et rognerait la marque si elle était fournie à plat.

## Règles de discipline du domaine

Pour que les deux domaines restent comparables ligne à ligne :

1. **Le domaine n'utilise que des types transposables** : entiers, chaînes, booléens, `UUID`,
   dates, listes, dictionnaires, énumérations, structures. Pas de `Measurement`, pas de
   `NSAttributedString`, pas de `KeyPath` dans une signature publique.
2. **Aucune date implicite.** Le domaine ne lit jamais l'horloge : l'instant est toujours passé
   en paramètre. Cela rend les tests déterministes et supprime la question des fuseaux.
3. **Pas d'arithmétique de dates dans le domaine.** Les calculs de calendrier restent dans la
   couche présentation.
4. **Les noms sont partagés.** `MatchState`, `RoundDraft`, `EndCheck`, `Standing` s'appellent
   pareil en Kotlin.
5. **Le JSON est la seule frontière.** Le domaine encode et décode exactement les structures
   de `spec/`. Aucun format de sérialisation propriétaire.
6. **Aucun texte d'interface dans le domaine** : les moteurs renvoient des raisons typées, que
   chaque app rédige ([04](04-moteur-de-regles.md#validationresult)).

## Localisation

- **Contenu des jeux** (`spec/games/*.json`, champs `fr`/`en`/`es`/`de`/`it`) : partagé tel quel,
  lu directement par `:catalog`.
- **Interface** : `Scripts/extract-android-strings.py` génère `values*/strings.xml` depuis
  `Localizable.xcstrings`. La clé source étant le texte français, un nom de ressource stable est
  attribué une fois pour toutes dans `android/l10n-correspondence.json`. Le script convertit les
  spécificateurs (`%1$@` → `%1$s`, `%lld` → `%d`), échappe le XML et produit des `<plurals>` avec
  les quantités CLDR (« many » en français, espagnol et italien). Seules les chaînes référencées
  par le Kotlin (`R.string.<nom>`) sont écrites. `values/` porte le **français**, langue source.
- **Textes propres à Android** (permission caméra, recherche…) : `values*/strings_android.xml`,
  maintenus à la main dans les 5 langues ; ceux du design system dans ses propres ressources.
- Les messages produits par un ViewModel sont des `UiText` (ressource + arguments), résolus à
  l'affichage.
- La CI vérifie que `strings.xml` est à jour par rapport au catalogue Apple.
- Choix de la langue par application : `Settings.ACTION_APP_LOCALE_SETTINGS` (Android 13+).

## Accessibilité

Mêmes exigences que sur iOS ([08](08-design-system.md#accessibilité)) : une ligne de tableau de
scores = un seul arrêt TalkBack (`accessibleScoreRow`, `:designsystem`), libellés explicites sur
les contrôles sans texte, échelle de police jusqu'à ×2 sans troncature, zones tactiles de 48 dp.

## Repères

Les commentaires du code Android renvoient aux étapes dans lesquelles l'app a été construite :

| Étape | Sujet | Section |
|---|---|---|
| A | Projet Gradle multi-module, thème Material 3, composants de base, CI | Modules, Charte graphique sur Android |
| B | Domaine Kotlin, chargement des définitions | Modules, Chargement des définitions |
| C | Golden files verts sur tous les jeux | [10](10-tests-et-qualite.md#les-golden-files) |
| D | Room, repositories, mapping | Persistance |
| E | Écrans | Charte graphique sur Android |
| F | Sessions en ligne, chiffrement | Synchronisation, [09](09-partie-partagee.md) |
| G | Accessibilité, localisation | Localisation, Accessibilité |

## Ce qui n'est pas partagé, et c'est voulu

- Les composants d'interface. Un bouton SwiftUI et un `Button` Compose n'ont aucune raison de
  se ressembler dans le code, seulement à l'écran.
- La navigation. `NavigationStack` et Navigation Compose ont des modèles différents ; les
  aligner produirait une abstraction inutile des deux côtés.
- Les gestes et les micro-interactions. Chaque plateforme a ses conventions, et l'utilisateur
  attend celles de son téléphone.
- Le rythme des versions. Rien n'oblige les deux apps à sortir la même fonctionnalité le même
  jour, tant que `spec/` reste commun et versionné.
