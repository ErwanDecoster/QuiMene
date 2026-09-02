# 05 — Catalogue de jeux

20 jeux dans le catalogue (`spec/games/`). Chaque fiche précise ce dont le moteur a besoin :
sens du score, forme de la saisie, condition de fin, départage.

## Vue d'ensemble

| Jeu | Sens | Saisie | Fin de partie | Moteur | Vague |
|---|---|---|---|---|---|
| **Jeu libre** | au choix | entier | au choix | `generic.sum.v1` | v1 |
| **Skyjo** | le plus bas gagne | entier + « a fermé » | un joueur ≥ 100 | `skyjo.v1` | v1 |
| **Yams** | le plus haut gagne | grille 13 catégories | toutes les grilles remplies | `yams.v1` | v1 |
| **Belote** | le plus haut gagne | points par équipe + annonces | une équipe ≥ 1000 | `belote.v1` | v1 |
| **Rami** | le plus bas gagne | entier (pénalités) | un joueur ≥ 251 | `generic.sum.v1` | v1 |
| **6 qui prend** | le plus bas gagne | entier (têtes de bœuf) | un joueur ≥ 66 | `generic.sum.v1` | v1 |
| **Tarot** | le plus haut gagne | contrat + bouts + points | nombre de donnes fixé | `tarot.v1` | v1.1 |
| **Wizard** | le plus haut gagne | annonce + plis réalisés | 60 / nb joueurs manches | `wizard.v1` | v1.1 |
| **Mölkky** | atteindre 50 exactement | entier 0–12 | un joueur = 50 | `molkky.v1` | v1.1 |
| **Scrabble** | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` | v1.1 |
| **Triominos** | le plus haut gagne | entier | un joueur ≥ 400 | `generic.sum.v1` | v1.2 |
| **Cornhole** | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` | hors plan initial |
| **Flip 7** | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` | hors plan initial |
| **Odin (Odin's Ravens)** | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` | hors plan initial |
| **Pétanque** | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` | hors plan initial |
| **Pictionary** | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` | hors plan initial |
| **Qwixx** | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` | hors plan initial |
| **Rummikub** | le plus bas gagne | entier (pénalités) | arrêt manuel | `generic.sum.v1` | hors plan initial |
| **Time's Up** | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` | hors plan initial |
| **Trivial Pursuit** | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` | hors plan initial |

Six moteurs impératifs (`skyjo`, `yams`, `belote`, `tarot`, `wizard`, `molkky`) plus le moteur
générique déclaratif `generic.sum.v1` — sept au total — pour 20 jeux : 14 se contentent de
`generic.sum.v1`, sans aucun code spécifique.

**Écart avec le plan initial** : le catalogue n'a pas suivi les trois vagues prévues à la
lettre. Cinq jeux planifiés n'ont finalement jamais été construits — **Uno**, **1000 Bornes**
(`mille.v1`), **Phase 10** (`phase10.v1`), **Qwirkle**, **Président** (`rank.v1`) : ces trois
moteurs n'existent nulle part dans `GameCatalog+Embedded.swift`, ils sont restés à l'état de
plan. Neuf jeux hors plan initial ont été ajoutés à la place, tous sur `generic.sum.v1` (aucun
n'a eu besoin d'un moteur dédié).

## Critère de priorisation

Vague 1 = popularité élevée × diversité de moteurs. Les six jeux de la v1 exercent
délibérément quatre familles de saisie différentes (entier nu, entier + drapeau, grille
structurée, saisie par équipe). Si l'architecture tient sur ces quatre-là, les dix suivants
sont de l'application de recette.

---

## Fiches détaillées

### Skyjo — `skyjo.v1`

Détaillé dans [04 — Moteur de règles](04-moteur-de-regles.md). Points saillants :

- Le joueur qui ferme la manche voit son score **doublé** s'il n'est pas strictement le plus
  bas — l'égalité ne protège pas. Doublement uniquement si le score est > 0.
- Seuil de fin : 100 (variantes 150 / 200).
- Départage : meilleure manche unique, puis nombre de manches fermées, puis ex æquo.
- Saisie : un entier de −24 à 156 par joueur, plus exactement un drapeau « a fermé la manche ».

### Yams — `yams.v1`

Grille individuelle de 13 catégories. La saisie n'est pas un nombre par manche mais **une
catégorie remplie par tour**, ce qui teste le cas « la manche n'est pas un tour de table
homogène ».

| Section haute | Valeur |
|---|---|
| As … Six | somme des dés de la valeur |
| **Bonus** | **+35 si la section haute ≥ 63** |

| Section basse | Valeur |
|---|---|
| Brelan | somme des 5 dés |
| Carré | somme des 5 dés |
| Full | 25 |
| Petite suite | 30 |
| Grande suite | 40 |
| Yams | 50 |
| Chance | somme des 5 dés |

- Fin : `allSheetsComplete` — les 13 cases de chaque joueur sont remplies (une case barrée
  compte comme remplie, à 0).
- Variantes : Yams supplémentaire (+100), section haute assistée (saisie du nombre de dés
  plutôt que du total).
- Départage : total de section basse, puis ex æquo.
- La saisie assistée est ici un vrai gain : on tape « trois 4 » et l'app écrit 12.

### Belote — `belote.v1`

Premier jeu **par équipes** : les participants sont regroupés, le classement porte sur les
équipes. Le domaine gère cela par un champ `teamID` optionnel sur `Participant`, `nil` pour
tous les jeux individuels.

- 162 points distribués par donne, dix de der inclus.
- Saisie : points de l'équipe preneuse ; le complément est déduit automatiquement.
- Annonces : belote-rebelote (+20), capot (250), dedans (le preneur chute → l'adversaire
  encaisse 162 + le contrat).
- Fin : première équipe à 1000 (variantes 501 / 2000).
- Départage : pas d'ex æquo possible au-delà du seuil ; en cas d'égalité exacte, une donne
  supplémentaire.
- Une variante « coinche / contrée » est prévue en v1.2 : contrat annoncé, multiplicateurs
  ×2 et ×4.

### Rami — `generic.sum.v1`

- Le joueur qui sort marque 0 ; les autres cumulent la valeur des cartes en main.
- Le plus bas gagne, fin à 251 (variantes 100 / 500).
- Aucun code spécifique : le JSON déclaratif suffit intégralement.

### 6 qui prend — `generic.sum.v1`

- On accumule des têtes de bœuf, le plus bas gagne.
- Fin : un joueur atteint 66 (variante : nombre de manches fixé).
- Sert de démonstration que deux jeux très différents partagent le même moteur.

### Tarot — `tarot.v1`

Le calcul le plus dense du catalogue, et la raison d'être de la couche impérative.

**Contrat à réaliser selon le nombre de bouts détenus par le preneur :**

| Bouts | 0 | 1 | 2 | 3 |
|---|---|---|---|---|
| Points requis | 56 | 51 | 41 | 36 |

**Formule :**

```
écart      = points du preneur − points requis        (positif = contrat réussi)
base       = 25 + |écart| + petitAuBout               (petitAuBout = 10 ou 0)
score      = base × multiplicateur + poignée + chelem
```

| Contrat | Multiplicateur |
|---|---|
| Petite | ×1 |
| Garde | ×2 |
| Garde sans le chien | ×4 |
| Garde contre le chien | ×6 |

- Poignée : simple 20, double 30, triple 40 — **ajoutée après** la multiplication, et toujours
  au bénéfice du camp vainqueur de la donne.
- Chelem : annoncé et réussi +400, non annoncé et réussi +200, annoncé et manqué −200.
- Le signe du score suit la réussite du contrat ; il est ensuite réparti :

| Joueurs | Preneur | Partenaire | Chaque défenseur |
|---|---|---|---|
| 3 | +2S | — | −S |
| 4 | +3S | — | −S |
| 5 (roi appelé) | +2S | +S | −S |

La somme des scores d'une donne est toujours nulle : c'est un **invariant testé** à chaque
manche, et le meilleur garde-fou contre une erreur de formule.

- Fin : nombre de donnes fixé (multiple du nombre de joueurs, pour l'équité de la donne).
- Le preneur peut être « personne » (donne passée) : tous à 0.

### Wizard — `wizard.v1`

- Nombre de manches = 60 / nombre de joueurs (20 à 3 joueurs, 15 à 4, 12 à 5, 10 à 6).
- Manche *n* : chacun annonce le nombre de plis qu'il pense réaliser.
- Annonce exacte → **+20 + 10 × plis annoncés**. Sinon → **−10 par pli d'écart**.
- Saisie : deux entiers par joueur (annonce, réalisé). Validation : la somme des plis réalisés
  doit égaler le numéro de la manche — contrôle bloquant, très utile en pratique.
- Fin : `roundLimit`.

### Mölkky — `molkky.v1`

- Un quille tombée → sa valeur ; plusieurs quilles → leur nombre. Saisie 0 à 12.
- **Dépasser 50 ramène à 25.** C'est la seule règle de « score non monotone » du catalogue.
- Trois échecs consécutifs (score 0) → joueur éliminé.
- Fin : `targetReached` dès qu'un joueur atteint exactement 50, ou `elimination` s'il ne reste
  qu'un joueur.
- Particularité : la partie s'arrête **immédiatement**, pas en fin de tour de table. Le
  `EndCheck` renvoie `.ended` sans passer par `.finalRound` — le cas qui justifie que les deux
  soient distincts dans l'énumération.

### 1000 Bornes — `mille.v1` — non construit

Ne figure pas dans `spec/games/` ni dans `GameCatalog+Embedded.swift` — resté à l'état de plan.
Fiche conservée telle quelle pour une reprise éventuelle.

Saisie par grille de primes plutôt que par nombre :

| Prime | Points |
|---|---|
| Distance parcourue | 1 / km |
| Manche terminée (1000 km) | 400 |
| Chaque botte | 100 |
| Les quatre bottes | +300 |
| Chaque coup-fourré | 300 |
| Trajet sans carte 200 | 300 |
| Capot (adversaire à 0 km) | 500 |
| Allonge (700 → 1000) | 200 |

- Fin : première équipe à 5000.
- Bon candidat à une saisie par steppers et interrupteurs plutôt que par pavé numérique.

### Phase 10 — `phase10.v1` — non construit

Ne figure pas dans `spec/games/` ni dans `GameCatalog+Embedded.swift` — resté à l'état de plan.
Fiche conservée telle quelle pour une reprise éventuelle.

Double critère : la progression en phases prime, les points départagent.

- Chaque manche : phase franchie ou non (booléen) + points de pénalité des cartes restantes.
- Fin : un joueur termine la phase 10.
- Classement : phase atteinte décroissante, puis points croissants.
- Le seul jeu du catalogue dont le classement n'est pas un simple tri sur le cumul — il
  valide que `standings()` soit bien un point d'extension du protocole et pas une fonction
  générique.

### Président — `rank.v1` — non construit

Ne figure pas dans `spec/games/` ni dans `GameCatalog+Embedded.swift` — resté à l'état de plan.
Fiche conservée telle quelle pour une reprise éventuelle.

- Saisie : un rang par joueur (Président, Vice-président, Neutre, Vice-trouduc, Trouduc).
- Barème par défaut : 5 / 3 / 2 / 1 / 0, ajustable en variante.
- Fin : nombre de manches fixé.
- Validation : les rangs doivent former une permutation complète.

### Les 14 jeux sur `generic.sum.v1`

Aucun code. Uniquement un JSON déclaratif qui change le sens du score, le seuil et les libellés
(voir la table en tête de document pour la liste complète — Jeu libre, Rami, 6 qui prend,
Scrabble, Triominos et neuf jeux ajoutés hors plan initial). Uno et Qwirkle, prévus sur ce même
moteur, ne figurent pas dans `spec/games/` — jamais construits, comme 1000 Bornes/Phase 10/
Président ci-dessus.

---

## Ajouter un jeu

Procédure, dans cet ordre strict :

1. Écrire `spec/games/<id>.json`, valider contre le JSON Schema.
2. Écrire au moins un `spec/golden/<id>-*.json` couvrant un cas nominal **et** le cas limite
   qui rend le jeu particulier (le doublement, le bonus, la chute…).
3. Si `engine` ≠ `generic.sum.v1`, implémenter `GameRules` côté Swift jusqu'à ce que les
   golden files passent.
4. Ajouter l'entrée à la table `engineID -> GameRules`.
5. Traduire nom et libellés dans `Localizable.xcstrings`.
6. Côté Android, à la vague de portage : rejouer les mêmes golden files.

Écrire le golden **avant** l'implémentation n'est pas un dogme de TDD ici : c'est ce qui rend
la ré-implémentation Kotlin possible sans relire le Swift.
