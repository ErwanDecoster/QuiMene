# 08 — Design system Apple

> **Ce document ne définit aucune valeur.** Couleurs, tailles, espacements, rayons, durées sont
> tous fixés dans la [charte graphique](07-charte-graphique.md), source unique des tokens.
> Ce qui suit décrit **comment ces tokens s'implémentent en SwiftUI**, plancher **iOS 18**, avec
> Liquid Glass en amélioration progressive sur iOS 26+ ([ADR-0015](13-decisions-adr.md)) — le
> pendant Android est en [11](11-portage-android.md).

## Posture

L'application **suit** les conventions du système, elle ne les réinvente pas. Pas de barre de
navigation maison, pas de composant recodé, aucune police embarquée (la display du logo est
vectorisée). Une app de score se juge à sa vitesse de saisie, pas à son originalité graphique —
et le système fournit gratuitement Dynamic Type, VoiceOver, le mode sombre, les transitions et,
sur iOS 26+, Liquid Glass.

Corollaire : **aucune dépendance UI tierce**.

## Cible `DesignSystem`

```
DesignSystem/
├── Tokens/        Space, Radius, Motion, Sizes, Color+Tokens, Font+Tokens, Motion+Accessible
├── Components/    PrimaryButtonStyle, SecondaryButtonStyle, TertiaryButtonStyle, Card, Chip,
│                  Banner, EmptyState, AvatarView, AccessibleScoreRow
├── Previews/      galeries de previews (boutons, composants, identité)
├── Avatar.swift, PlayerPalette.swift, LogoMark.swift, LogoWordmark.swift
└── Resources/     Assets.xcassets (tous les tokens couleur)
```

## Liquid Glass (amélioration progressive iOS 26+)

Le plancher de l'app est iOS 18 : aucune vue ne peut supposer Liquid Glass disponible. Chaque
usage se code avec un repli explicite sur le Material system classique :

```swift
if #available(iOS 26, *) {
    view.glassEffect(in: .rect(cornerRadius: radius))
} else {
    view.background(.regularMaterial, in: .rect(cornerRadius: radius))
}
```

- Les boutons de la barre de saisie utilisent `.buttonStyle(.glass)` sur iOS 26+, les styles du
  design system en dessous.
- Les surfaces flottantes (`Banner`) sont en `.regularMaterial`.
- Le tableau des scores n'est **jamais** en verre, sur aucune version : c'est la donnée, elle
  doit être nette. Du texte chiffré sur un fond translucide devient illisible dès qu'une couleur
  passe dessous.
- La tab bar système prend d'elle-même le rendu de chaque version d'iOS.

## Traduction des tokens en Swift

Aucune valeur littérale dans les vues. La cible `DesignSystem` transpose mécaniquement le
tableau de synthèse de la [charte §14](07-charte-graphique.md#14-tableau-de-synthèse-des-tokens),
sans en réinterpréter aucune : `Space`, `Radius`, `Motion`, tailles d'icônes, de boutons et de
zones tactiles.

**Couleurs** — jamais littérales en Swift : chaque token de la charte est une entrée du catalogue
d'assets, avec ses variantes claire et sombre (et contraste augmenté pour les couleurs de
joueur), exposée par une extension :

```swift
extension Color {
    public static var brandInk: Color { Color("brand/ink", bundle: .designSystem) }
    public static var neutralSurface: Color { Color("neutral/surface", bundle: .designSystem) }
    // …une entrée par token de la charte §14
}
```

La bascule de thème et de contraste est ainsi gérée par le système, sans code.

**Typographie** — chaque token typographique référence le style système correspondant, jamais
une taille en points, afin que Dynamic Type opère sans intervention :

```swift
extension Font {
    static let h1 = Font.largeTitle.weight(.bold)          // type/h1
    static let h5 = Font.headline                          // type/h5
    static let scoreXL = Font.system(.largeTitle, design: .rounded,
                                     weight: .semibold).monospacedDigit()
    static let scoreL  = Font.system(.title2, design: .rounded,
                                     weight: .semibold).monospacedDigit()
    static let scoreM  = Font.system(.body, design: .rounded,
                                     weight: .medium).monospacedDigit()
}
```

Le tracking n'est jamais posé à la main sur iOS : SF Pro applique le sien. Les valeurs listées
dans la charte §2.2 n'existent que pour qu'Android les reproduise.

**Élévation** — les cinq niveaux sémantiques de la charte §5.2 se rendent par matériau, jamais
par ombre ([ADR-0010](13-decisions-adr.md)).

## Couleurs des joueurs

Les dix teintes sont définies en [charte §1.5](07-charte-graphique.md#15-palette-des-joueurs).
Côté implémentation, deux garde-fous :

1. **Test de contraste** — `ContrastTests` rejoue le calcul WCAG sur le catalogue d'assets réel,
   pas sur une copie des valeurs, y compris en contraste augmenté. Une teinte modifiée dans Xcode
   sans revalidation fait échouer la suite.
2. **Redondance obligatoire** — `PlayerPalette` expose ensemble la couleur, le symbole de courbe
   et le nom accessible (« Azur »), ce qui rend l'oubli du double codage difficile.

## Avatars

Deux sources, toutes hors ligne.

1. **Emoji + couleur, dérivés du pseudo par défaut, modifiables à la main** — un emoji choisi
   dans une sélection curatée d'environ 60 et une teinte parmi les dix couleurs joueur, toutes
   deux dérivées d'un hachage stable du pseudo : même pseudo → même emoji et même couleur, sur
   tout appareil, iOS comme Android. L'éditeur de joueur propose la grille d'emoji et les dix
   couleurs ; un choix manuel désactive la régénération automatique au changement de pseudo,
   jusqu'à « Réinitialiser l'avatar généré ».
2. **Photo** — `PhotosPicker`, recadrée en carré, redimensionnée à 512 px, JPEG qualité 0,8,
   stockée en `@Attribute(.externalStorage)`. Aucune permission d'accès à la photothèque
   n'est requise.

Un ancien avatar « symbole » (`Avatar.Kind.symbol`) reste lu pour compatibilité, mais n'est
plus proposé à la création.

Un unique composant `AvatarView(avatar:size:)` rend tous les cas, avec des tailles nommées
(`.small` 28 pt en liste, `.medium` 44 pt en tableau, `.large` 96 pt en fiche). Le disque de
fond porte la couleur du joueur ; une photo occupe tout le disque, cerclée de cette couleur.

## L'écran critique : la saisie d'une manche

C'est l'écran qui décide du succès du produit. Objectif : **moins de 15 secondes pour
5 joueurs**, soit environ 2,5 secondes par joueur.

```
┌──────────────────────────────────┐
│  Skyjo · Manche 4          [ … ] │
├──────────────────────────────────┤
│  🦊 Alice    44          [  12 ]▌│ ← champ actif
│  🐻 Bob      13          [    ]  │
│  🦉 Chloé    24          [    ]  │
│  🐢 David    31          [    ]  │
├──────────────────────────────────┤
│  A fermé la manche :  🦊 Alice ⌄ │ ← modificateur propre au jeu
├──────────────────────────────────┤
│  ⌨︎ clavier système (.numberPad) │
│  [−/+]                Suivant   │ ← barre au-dessus du clavier
└──────────────────────────────────┘
```

Décisions de conception :

- **Clavier système** (`.numberPad`), pas un pavé maison — voir
  [ADR-0013](13-decisions-adr.md#adr-0013--retour-au-clavier-système-plutôt-que-le-pavé-propriétaire).
  L'enchaînement « joueur suivant » et la bascule de signe (scores négatifs) vivent dans la
  barre au-dessus du clavier.
- **Avancement automatique** : « Suivant » passe au joueur suivant ; sur le dernier joueur, la
  touche devient « Valider ». Un tap direct sur un autre champ déplace aussi le focus.
- **Cumul toujours visible** à côté de chaque champ : on saisit en contexte, sans changer
  d'écran pour vérifier où on en est.
- **Un seul composant de tableau** (`ScoreBoardView`) pour la partie locale et la partie
  partagée : une différence de comportement entre les deux est impossible par construction.
- **Haptique** : `.success` à la validation d'une manche, `.impact(weight: .heavy)` à la fin de
  partie.
- **Rien n'est écrit tant que la manche n'est pas validée**, et la dernière manche validée peut
  être annulée.

Les jeux à saisie structurée ont leur propre écran, aiguillé par `MatchPlayView` selon
`scoring.entry.kind` : grille Yams (`YamsSheetView`), Belote, Tarot et Wizard
(`BeloteRoundView`, `TarotRoundView`, `WizardRoundView`), à base de contrôles natifs
(`Stepper`, `Picker`).

## Adaptation iPhone / iPad

Une seule cible, les mêmes écrans. Aucun `UIDevice.current.userInterfaceIdiom` : la mise en page
s'adapte à la place disponible, pour rester correcte en Split View et en Stage Manager.

## Accessibilité

Traitée comme une exigence de départ, pas comme une passe de finition.

- **Dynamic Type jusqu'à AX5**, aucune taille figée.
- **VoiceOver** : chaque ligne de tableau de scores est un seul élément accessible
  (`accessibleScoreRow`), énoncé « Alice, deuxième, 44 points ». Les courbes (`Chart`) sont
  résumées par le classement final plutôt que décrites point par point. Les bandeaux sont
  annoncés à leur apparition (`Banner.announce`).
- **Reduce Motion** : `accessibleAnimation` remplace toute animation par un fondu court.
- **Contraste augmenté** : les couleurs de joueur ont une variante renforcée dans le catalogue
  d'assets, sélectionnée par le système.
- **Contrôle vocal** : chaque champ de score porte un libellé lié au pseudo, pour que « Appuyer
  sur Alice » fonctionne.
- **Zones tactiles** : 44 pt minimum, y compris pour les `Chip`.

Barrière de qualité : la traversée complète du parcours principal à VoiceOver, sans regarder
l'écran, fait partie de la définition de « terminé » ([10](10-tests-et-qualite.md)).

## Localisation

- `Localizable.xcstrings` : français (langue source), anglais, espagnol, allemand, italien. Les
  noms et libellés des jeux viennent de `spec/games/`, déjà traduits.
- Aucune concaténation de chaînes. Les pluriels passent par les variations du catalogue, jamais
  par un `if count > 1` (`LocalizationPluralTests`).
- Aucun texte d'interface dans `Domain` ni `Catalog` : les moteurs renvoient des raisons typées,
  que l'app rédige ([04](04-moteur-de-regles.md#validationresult)).
- `.formatted()` pour tous les nombres et toutes les dates.

## Intégrations système

- **Live Activity** (`QuiMeneWidget`) — score en cours sur l'écran verrouillé et dans la Dynamic
  Island, mis à jour à chaque manche, y compris à distance pour une partie partagée
  ([09](09-partie-partagee.md#écran-verrouillé-ios)).
- **Handoff** — reprendre une partie en cours sur un autre appareil du même compte iCloud.
- **Partage** — `ShareLink` d'une image de résumé générée par `ImageRenderer`
  (`ResultsShareCard`), toujours en mode clair.
- **Liens** — `quimene://` ouvre l'app sur « Rejoindre une partie » depuis l'appareil photo
  système ou un QR scanné.
