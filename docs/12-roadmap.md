# 12 — Roadmap

Estimations pour **une personne à temps plein**. Elles supposent la maîtrise de SwiftUI ;
ajouter 30 à 40 % en phase d'apprentissage.

## Vue d'ensemble

```
  Apple v1                                                    ~17 semaines
  ├─ P0  Fondations ...................... 1 sem   ██
  ├─ P1  Identité & design system ........ 1 sem   ██
  ├─ P2  Joueurs ......................... 1 sem   ██
  ├─ P3  Moteur + Skyjo .................. 1,5 sem ███          ◆ jalon 1
  ├─ P4  Partie en direct ................ 2 sem   ████         ◆ jalon 2
  ├─ P5  Résultats & statistiques ........ 1,5 sem ███
  ├─ P6  Historique & iCloud ............. 1,5 sem ███          ◆ jalon 3
  ├─ P7  Catalogue élargi ................ 2 sem   ████
  ├─ P8  Partie partagée ................. 3 sem   ██████
  └─ P9  Finitions & TestFlight .......... 2,5 sem █████        ◆ v1.0

  Android v1 ............................ ~10,5 sem █████████████████████
```

Le **chemin critique** est P3 → P4 → P5 : le moteur, la saisie, les résultats. Tout le reste
peut glisser sans bloquer une mise en main. P7 et P8 sont ré-ordonnançables selon l'envie.

---

## P0 — Fondations · 1 semaine

- `git init`, structure de dossiers, `.gitignore` Swift
- Package `CaCompteKit` : cibles `Domain`, `Catalog`, `Store`, `Sync`, `DesignSystem`
- Projet Xcode, cible iOS 18 minimum (Liquid Glass en amélioration progressive iOS 26+, voir
  [ADR-0015](13-decisions-adr.md)), iPhone + iPad, identifiant de bundle et container CloudKit
- Mode langage Swift 6 + concurrence stricte + avertissements en erreurs, **dès la première
  ligne** — l'activer plus tard coûte des jours de reprise
- Script de build qui vérifie l'égalité entre `spec/games/` et `Catalog/Resources/`
- Xcode Cloud : workflow de test sur push

**Fini quand** : le package compile à vide, un test bidon passe en CI, l'app se lance sur un
écran blanc.

## P1 — Identité & design system · 1 semaine

- Logo : marque, déclinaisons horizontale / verticale / icône, icône d'app (claire, sombre,
  teintée)
- Catalogue d'assets : tous les tokens couleur de la [charte §14](07-charte-graphique.md), avec
  variantes *Dark* et *High Contrast*
- Cible `DesignSystem` : `Space`, `Radius`, `Motion`, extensions `Font` et `Color`
- Composants : `PrimaryButton`, `SecondaryButton`, `ScoreField`, `Card`, `Chip`, `Banner`,
  `AvatarView`, `EmptyState` — **tous les états** de la charte §5
- **Le script de vérification de contraste devient un test unitaire** et tourne en CI

**Fini quand** : une galerie de previews montre chaque composant dans tous ses états, en clair,
en sombre, en contraste augmenté et en AX5.

> Placer cette phase avant tout écran réel est délibéré : reprendre 15 écrans pour aligner des
> couleurs coûte bien plus qu'une semaine passée en amont.

## P2 — Joueurs · 1 semaine

- Modèle `PlayerRecord`, `ModelContainer`, repository
- Liste, création, édition, archivage, réordonnancement
- Trois sources d'avatar (symbole, emoji, photo) + génération déterministe
- Attribution automatique de couleur

**Fini quand** : on crée dix joueurs, on relance l'app, ils sont là.

## P3 — Moteur + Skyjo · 1,5 semaine ◆ **Jalon 1 : le calcul est juste**

- Types du domaine complets ([04](04-moteur-de-regles.md))
- `MatchEngine` : `reduce`, `replay`, journal d'événements, horloge de Lamport
- `GameCatalog`, chargement et validation des JSON
- `generic.sum.v1` et `skyjo.v1`
- `spec/games/skyjo.json` + 2 golden files
- Tests d'invariants et de propriété

**Fini quand** : les golden files passent et les invariants tiennent sur des entrées générées.
**Aucune interface à ce stade** — c'est le seul jalon purement métier, et le plus important.

## P4 — Partie en direct · 2 semaines ◆ **Jalon 2 : première partie réelle**

- Configuration : choix du jeu, des joueurs, des variantes
- `LiveMatchModel`, écran de partie, tableau des scores
- **Pavé numérique** et son enchaînement de saisie (charte §5.4, doc [08](08-design-system.md))
- Validation, correction, annulation d'une manche
- Bandeau « dernière manche », détection de fin
- Persistance à chaque manche validée + reprise après relance

**Fini quand** : une vraie partie de Skyjo est jouée de bout en bout sur un appareil réel, et
survit à un arrêt forcé de l'app.

Mesurer ici l'objectif produit : **5 scores saisis en moins de 15 secondes**. S'il n'est pas
atteint, corriger avant d'avancer — c'est la métrique numéro un de la
[vision produit](01-vision-produit.md).

## P5 — Résultats & statistiques · 1,5 semaine

- `StatsEngine` : indicateurs universels, score d'intérêt, sélection
- Écran de résultats : podium, faits marquants, badges
- Courbe d'évolution (Swift Charts), axe inversé selon le sens du jeu
- Image de résumé partageable (`ImageRenderer` + `ShareLink`)

**Fini quand** : l'écran de résultats donne envie d'être regardé jusqu'en bas.

## P6 — Historique & iCloud · 1,5 semaine ◆ **Jalon 3 : utilisable au quotidien**

- Historique, filtres par jeu et par joueur, détail manche par manche
- Fiches de profil et statistiques agrégées
- CloudKit : activation, réglage de consentement, recette sur deux appareils
- `VersionedSchema` et plan de migration

**Fini quand** : une partie créée sur iPhone apparaît sur iPad sans rien faire.

À ce stade, l'app est **utilisable en vrai**. C'est le bon moment pour la faire tester par le
groupe de joueurs cible, avant d'élargir le catalogue.

## P7 — Catalogue élargi · 2 semaines

Les cinq jeux restants de la vague 1 : Yams, Belote, Rami, 6 qui prend, Jeu libre — plus la
saisie structurée (grille Yams) et la notion d'équipe (Belote), qui touchent le domaine.

Pour chacun : JSON, golden files, moteur si nécessaire, écran de saisie adapté, traductions.

**Fini quand** : les six jeux de la v1 passent leurs golden files et se jouent réellement.

## P8 — Partie partagée · 3 semaines

- ✅ Protocole `Transport` + `SupabaseTransport` (canal Realtime par session, presence pour la
  connexion/déconnexion, broadcast pour `WireMessage` chiffré, `cacompte_open_games` pour la
  découverte par code — voir [ADR-0016](13-decisions-adr.md)), rôles hôte / contributeur /
  observateur
- ✅ Appairage par code (dérivation HKDF, chiffrement AES-GCM des deux côtés), avec délai
  explicite (`SessionError.noResponseFromHost`) si l'hôte ne répond jamais
- ✅ Interface d'invitation (`ShareSessionView`, hôte) et de découverte/appariement
  (`JoinMatchView`, pair), branchées dans `LiveMatchView`/`GamesTabView`
- ✅ Départ propre des deux côtés (`LiveSession.leave()`/`stopHosting()` ferment réellement la
  session, pas seulement la référence locale)
- ✅ Deux bugs trouvés en usage réel et corrigés sur `SupabaseTransport` : perte de présence après
  une reconnexion sous-jacente en arrière-plan (ré-enregistrement à chaque `.subscribed`), course
  entre présence et premier message d'un pair (session créée au premier des deux événements) —
  voir [09](09-partie-partagee.md)
- ✅ Recette manuelle du transport Supabase sur appareils réels par l'auteur du projet — partie
  partagée créée et suivie avec succès, en plus d'un usage général de l'app hors simulateur
- ✅ Golden du protocole (`spec/wire/`), côté Swift (`WireGoldenTests`)
- ⏳ Portage Android, recette croisée Apple + Android, golden du protocole côté Kotlin

**Fini quand** : un iPhone et un Android suivent la même partie via Supabase Realtime, et l'un
d'eux perd puis retrouve sa connexion sans perdre l'état. **Supabase Realtime en production côté
Apple, recette manuelle réussie sur appareil réel ; Android reste à faire.** Voir
[09 — Partie partagée](09-partie-partagee.md) et [ADR-0016](13-decisions-adr.md).

## P9 — Finitions & TestFlight · 2,5 semaines

- ✅ Handoff (`MatchContinuation`, `NSUserActivity` sur `MatchPlayView`) — reprise d'une partie en
  cours sur un autre appareil au même compte iCloud. Vérifié par l'auteur sur appareil physique :
  fonctionne tel quel, jugé d'un intérêt limité au quotidien — aucun développement supplémentaire
  prévu au-delà de ce qui existe
- ❌ **App Intents / Siri (`MatchShortcuts`) — retiré de la v1.0** (fichier et capacité Siri
  supprimés, récupérables dans l'historique Git) : une fonction déclarée à Siri mais inopérante
  est un motif de refus App Store (2.1). Diagnostic d'origine : non fonctionnel. Testé par l'auteur
  sur appareil physique, plusieurs allers-retours : la couverture insuffisante des phrases
  déclarées (une seule formulation par intent à l'origine) et l'absence de la capacité Siri
  (`com.apple.developer.siri`, ajoutée depuis) ont chacune été corrigées sans résoudre le
  problème — Siri reste incapable de lancer ou reprendre une partie. Cause exacte non identifiée ;
  mis en pause à la demande de l'utilisateur plutôt que d'insister sans piste supplémentaire, voir
  [15](15-plan-qualite-code.md#vérification-p9-sur-appareil-réel--widget-retiré-siri-mis-en-pause)
- ✅ Live Activity (`MatchActivityAttributes` dans `Domain`, `MatchLiveActivityController`) — écran
  verrouillé et Dynamic Island, mis à jour à chaque manche depuis les trois écrans de saisie
- ✅ Dynamic Island vérifiée par l'auteur sur appareil physique — rendu réel conforme
- ❌ **Widget d'écran d'accueil retiré** — construit puis testé sur appareil physique par
  l'auteur, jugé sans intérêt réel à l'usage (classement figé jusqu'au retour en arrière-plan,
  contrairement à la Live Activity qui se met à jour en direct). `MatchWidget.swift` supprimé,
  `CaCompteWidgetBundle` ne déclare plus que `MatchLiveActivityWidget`. `SharedStore`/le
  conteneur App Group restent (Live Activity n'en a pas besoin, mais migrer l'emplacement du
  store ferait apparaître les données déjà enregistrées comme perdues sur les installations
  existantes) — voir le commentaire de `CaCompteApp.loadContainer`
- ✅ Passe d'accessibilité : infrastructure (Reduce Motion, regroupement de lignes VoiceOver,
  contraste augmenté) et rattrapage sur les 8 zones de l'app faits — 20 fichiers sur 42 dans
  `apple/App/Features` touchent maintenant l'accessibilité (2 au départ). Traversée VoiceOver, Dynamic
  Type AX5, Reduce Motion et contraste augmenté vérifiés par l'auteur sur appareil physique, voir
  Phase H de [15](15-plan-qualite-code.md)
- ✅ Localisation terminée — fr/en/es/de/it, interface et contenu des jeux (Phase G,
  [15](15-plan-qualite-code.md))
- ⏳ Fiche App Store, captures, confidentialité (« aucune donnée collectée »)
- ⏳ TestFlight interne, puis externe, correction des retours

**Fini quand** : la check-list « définition de terminé » de [10](10-tests-et-qualite.md) passe
sur tous les écrans.

## Android · ~10,5 semaines

Détail en [11 — Portage Android](11-portage-android.md). À démarrer **après** la v1.0 Apple :
`spec/` doit être stabilisé, sinon le portage suit une cible mouvante.

---

## Après la v1

Par valeur décroissante, sans engagement de calendrier :

| | Sujet |
|---|---|
| 1 | **Vagues 1.1 et 1.2 du catalogue** — 10 jeux de plus ([05](05-catalogue-jeux.md)) |
| 2 | **Mode week-end** — classement cumulé sur plusieurs parties, demandé explicitement par le persona « groupe du week-end » |
| 3 | **Saisie assistée** — grille Skyjo 3×4, dés du Yams, calcul du Tarot pas à pas |
| 4 | **Statistiques de groupe** — face-à-face, évolution sur l'année, records collectifs. Classement par jeu ✅ ([06](06-statistiques.md)) ; face-à-face et évolution sur l'année restent à faire |
| 5 | **Apple Watch** — saisie au poignet, WatchConnectivity |
| 6 | **Export/import `.cacompte`** — anticipé pour Android, utile aussi entre utilisateurs Apple |
| 7 | **macOS** — la cible SwiftUI existe déjà, coût faible, valeur faible |
| 8 | **Profils partagés entre appareils** — qu'une partie créée sur mon téléphone apparaisse dans l'historique d'un ami qui y participait. Phases 1 et 2 ✅ (lier deux fiches par QR, la partie apparaît chez l'ami sous forme de résumé) ; copie intégrale rejouable reste à faire si demandée : [14](14-profils-partages.md) |

## Risques

| Risque | Impact | Parade |
|---|---|---|
| **La saisie reste trop lente** | Produit inutilisable | Mesuré dès P4, avant d'écrire d'autres écrans. Si l'objectif n'est pas atteint, on retravaille le pavé plutôt que d'avancer. |
| **Règles de jeu mal comprises** | Scores faux, perte de confiance | Golden files écrits **avant** l'implémentation, validés contre les règles officielles ; invariants (somme nulle au Tarot) |
| **Contraintes CloudKit découvertes tard** | Migration douloureuse | Traitées comme invariants de schéma dès P2, pas comme un ajustement en P6 |
| **Dérive du catalogue** | P7 déborde | Le « jeu libre » est livré en P7 : même si un jeu manque, l'app reste utilisable |
| **`spec/` diverge du code** | Le portage Android casse | Vérification d'égalité au build dès P0 ; `spec/` est la seule source |
| **Sur-ingénierie du moteur** | Retard sur P3 | Deux couches seulement (déclaratif + impératif). Toute troisième abstraction passe par un ADR. |
| **`supabase-kt` moins mature que `supabase-swift`** | L'étape F du portage (doc 11) dérape si l'API Realtime Kotlin n'a pas la même couverture (canal, presence, broadcast) que côté Swift | Timebox court en tête d'étape F pour vérifier la parité avant de s'engager sur l'estimation |
