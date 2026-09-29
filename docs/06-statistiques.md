# 06 — Statistiques

L'écran de résultats est la récompense de la soirée. Il ne doit pas être un tableau : il doit
**raconter la partie**.

## Principe

Le `StatsEngine` est un ensemble de fonctions pures sur `MatchState`. Rien n'est stocké,
tout est recalculé — une partie de 40 manches à 8 joueurs se traite en microsecondes, et cela
supprime toute classe de bugs de cache invalide.

```swift
public struct StatsEngine: Sendable {
    public func insights(for state: MatchState,
                         definition: GameDefinition) -> [Insight]
    public func series(for state: MatchState) -> [ParticipantSeries]
    public func badges(for state: MatchState,
                       definition: GameDefinition) -> [Badge]
}

public struct Insight: Sendable, Identifiable {
    public let id: InsightID             // .highestRoundScore
    public let value: Value              // .single(Chloé, 40, manche 4) ou une valeur par joueur
    …
}
```

Le moteur ne produit que des valeurs : titre (« Plus gros tour »), texte (« Chloé — 40 points,
manche 5 ») et icône sont rédigés par chaque app à partir de `id` et `value`, dans la langue de
l'utilisateur (`Insight+Presentation.swift`, `InsightText.kt`). Les valeurs, elles, sont
vérifiées par les golden files, identiques sur les deux plateformes.

## Sélection : quelques faits, pas une liste

Le moteur calcule tous les indicateurs candidats, puis n'en retient **six au plus** selon leur
*intérêt narratif*. Un indicateur est intéressant s'il est saillant — pas s'il existe.

Chaque candidat porte un score d'intérêt :

- **Écart à la normale** — un « plus gros tour » à 40 quand la moyenne est de 18 vaut mieux
  qu'un à 22 quand la moyenne est de 20. Mesuré en écarts-types.
- **Rareté** — une partie sans aucun changement de leader, ou au contraire très disputée, est
  plus remarquable qu'une partie ordinaire.
- **Diversité des sujets** — on évite que tous les faits parlent du vainqueur : pénalité
  progressive à chaque réapparition d'un même joueur.

Les faits sous un seuil d'intérêt sont écartés. Sur une partie plate et sans relief, l'écran
affiche peu de faits plutôt que six remplis de vide.

Un même tour (joueur, manche, valeur) n'est raconté qu'une fois : quand le plus haut score
gagne, le « plus gros tour » est aussi le « meilleur tour », et seul le premier retenu s'affiche.

## Indicateurs

### Universels

| Indicateur | Définition | Note |
|---|---|---|
| **Podium** | classement final avec écarts | toujours affiché, hors sélection |
| **Plus gros tour** | max de `computedValue` sur une manche | dans les jeux « le plus bas gagne », c'est le fait le plus drôle |
| **Meilleur tour** | min ou max selon le sens du jeu | pas de gagnant si l'extrême est partagé |
| **Le plus régulier** | plus faible écart-type des scores de manche | |
| **Le plus irrégulier** | plus fort écart-type | |
| **Écart final** | différence entre le premier et le deuxième | retenu quand il est très serré |
| **Changements de leader** | nombre de fois où la première place change | 0 → « domination » ; ≥ 4 → « partie disputée » |
| **Plus longue série en tête** | en nombre de manches consécutives | |

### Propres à certains jeux

Déclarés par `statsProfiles` dans le JSON du jeu, ce qui évite tout `switch` sur `gameID` dans
le moteur.

| Profil | Indicateurs |
|---|---|
| `skyjo` | manches fermées par joueur · doublements subis |

## Courbe d'évolution

Une ligne par joueur, cumul en ordonnée, manches en abscisse (Swift Charts sur iOS, `Canvas`
Compose sur Android).

- L'axe des ordonnées est **inversé** dans les jeux où le plus bas gagne, pour que « en haut »
  signifie toujours « en train de gagner ».
- Une ligne de seuil matérialise la condition de fin.
- Les couleurs sont celles des joueurs, et un symbole distinct par série double le codage
  (voir [charte §1.5](07-charte-graphique.md#15-palette-des-joueurs)) : le graphique reste
  lisible sans distinguer les couleurs.

## Badges

Décernés en fin de partie, un par joueur au maximum, purement décoratifs. Ils donnent une
raison de regarder l'écran de résultats en entier.

| Badge | Condition |
|---|---|
| 🏆 **Vainqueur** | rang 1, seul |
| ⏱️ **Le Métronome** | plus faible écart-type, et < 60 % de la moyenne des écarts-types |
| 🎢 **Les montagnes russes** | plus fort écart-type, et > 160 % de la moyenne |
| 🚀 **La remontada** | gain ≥ 3 places entre le pire rang atteint et le rang final |
| 💥 **Le kamikaze** | détient le plus gros tour dans un jeu où le plus bas gagne |
| 🧊 **Imperturbable** | leader pendant ≥ 80 % des manches |
| 🍀 **Photo finish** | vainqueur avec moins de 3 points d'écart |
| 🏹 **Le Sniper** | détient le meilleur tour du match, dans le sens favorable au jeu — non attribué dans les jeux à cible exacte (Mölkky) |
| 🪨 **Le Boulet** | détient le pire tour du match, dans un jeu où le plus haut gagne |
| 🌊 **Le Fossé** | victoire écrasante : l'écart avec le deuxième dépasse 3× la dispersion habituelle des manches |

Règles d'attribution : un joueur ne reçoit qu'un badge, le plus rare l'emporte (Fossé, Photo
finish, Remontada, Montagnes russes, Sniper, Boulet, Kamikaze, Métronome, Imperturbable,
Vainqueur) ; un badge dont la condition n'est remplie par personne n'est pas affiché ; aucun
badge n'est décerné avant la manche 3 — des statistiques sur deux manches n'ont aucun sens.

## Statistiques de profil

Sur la fiche d'un joueur, agrégées sur tout l'historique (`ProfileRepository`).

| Bloc | Contenu |
|---|---|
| **En bref** | parties jouées · victoires · taux de victoire · rang moyen |
| **Par jeu** | mêmes chiffres, ventilés ; meilleur et pire score personnel, avec la date |
| **Némésis** | adversaire rencontré au moins 5 fois contre qui le taux de victoire est le plus faible |
| **Séries** | série de victoires en cours et record |
| **Activité** | parties par mois, sur 12 mois |

Le taux de victoire brut est trompeur à nombre de joueurs variable : gagner à 2 n'est pas
gagner à 8. La fiche affiche donc aussi le **rang moyen normalisé**
`(nbJoueurs − rang) / (nbJoueurs − 1)`, dans `[0, 1]`, comparable entre parties.

Pas de classement Elo : sur des groupes de 4 à 8 personnes qui jouent quelques dizaines de
parties par an, il produirait un chiffre instable et illisible.

## Classement par jeu

Qui est le meilleur à un jeu donné, tous joueurs confondus, à travers toutes les parties
terminées. `LeaderboardRepository` agrège les participants d'un `gameID`, groupés par joueur :
parties jouées, victoires, taux de victoire, rang moyen normalisé. Tri par taux de victoire,
puis rang moyen normalisé, puis nombre de parties — jamais par ordre d'itération d'un
dictionnaire ([03](03-modele-de-donnees.md), contrainte n° 5).

Deux points d'entrée :

- **Onglet Jeux** — une icône trophée sur chaque jeu ouvre son classement.
- **Fiche joueur**, section « Par jeu » — chaque jeu mène à son classement ; un jeu où ce joueur
  est en tête affiche un badge « Meilleur joueur », dès lors qu'au moins un autre joueur y a
  aussi joué.

Même politique que le reste des statistiques : calcul à la demande, aucun agrégat persisté. Un
agrégat stocké est un agrégat qui finira désynchronisé.
