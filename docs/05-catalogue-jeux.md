# 05 — Catalogue de jeux

20 jeux, définis dans `spec/games/`. Chaque définition précise ce dont le moteur a besoin : sens
du score, forme de la saisie, condition de fin, départage, variantes. Noms, descriptions et
libellés y sont traduits dans les cinq langues de l'app.

## Vue d'ensemble

| Jeu | Joueurs | Sens | Saisie | Fin de partie (variantes) | Moteur |
|---|---|---|---|---|---|
| **Belote** | 4 (2 équipes) | le plus haut gagne | points de l'équipe preneuse + capot, belote-rebelote | une équipe ≥ 1000 (501, 2000) | `belote.v1` |
| **Cornhole** | 2–4 | le plus haut gagne | entier 0–12 | un joueur ≥ 21 | `generic.sum.v1` |
| **Flip 7** | 2–12 | le plus haut gagne | entier | un joueur ≥ 200 (150, 250) | `generic.sum.v1` |
| **Jeu libre** | 1–12 | le plus haut gagne | entier, négatifs admis | arrêt manuel | `generic.sum.v1` |
| **Mölkky** | 2–8 | atteindre 50 exactement | entier 0–12 | un joueur à 50, ou un seul joueur en lice | `molkky.v1` |
| **Odin** | 2–6 | le plus bas gagne | entier 0–9 | un joueur ≥ 15 (10, 20) | `generic.sum.v1` |
| **Pétanque** | 2–6 | le plus haut gagne | entier 0–6 | un joueur ≥ 13 | `generic.sum.v1` |
| **Pictionary** | 2–12 | le plus haut gagne | entier | un joueur ≥ 20 (15, 25) | `generic.sum.v1` |
| **Qwixx** | 2–5 | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` |
| **Rami** | 2–8 | le plus bas gagne | entier 0–150 (pénalités) | un joueur ≥ 251 (100, 500) | `generic.sum.v1` |
| **Rummikub** | 2–6 | le plus haut gagne | entier, négatifs admis | arrêt manuel | `generic.sum.v1` |
| **Scrabble** | 2–4 | le plus haut gagne | entier | arrêt manuel | `generic.sum.v1` |
| **6 qui prend** | 2–10 | le plus bas gagne | entier (têtes de bœuf) | un joueur ≥ 66 | `generic.sum.v1` |
| **Skyjo** | 2–8 | le plus bas gagne | entier −24 à 156 + « a fermé la manche » | un joueur ≥ 100 (50, 150, 200) | `skyjo.v1` |
| **Tarot** | 3–5 | le plus haut gagne | contrat, bouts, points du preneur, poignée, petit au bout, chelem | 2 donnes par joueur (1, 3) | `tarot.v1` |
| **Time's Up !** | 4–12 | le plus haut gagne | entier (cartes devinées) | 3 manches | `generic.sum.v1` |
| **Triominos** | 2–6 | le plus haut gagne | entier | un joueur ≥ 400 (300, 500) | `generic.sum.v1` |
| **Trivial Pursuit** | 2–6 | le plus haut gagne | entier 0–6 (camemberts) | un joueur à 6 | `generic.sum.v1` |
| **Wizard** | 3–6 | le plus haut gagne | annonce + plis réalisés | 60 / nombre de joueurs manches | `wizard.v1` |
| **Yams** | 1–8 | le plus haut gagne | grille de 13 catégories | toutes les grilles remplies | `yams.v1` |

Six moteurs impératifs (`skyjo`, `yams`, `belote`, `tarot`, `wizard`, `molkky`) plus le moteur
générique `generic.sum.v1` : 14 jeux sur 20 se contentent de leur définition JSON, sans aucun
code spécifique.

Sauf mention contraire, une partie à seuil se termine **à la fin du tour de table** où le seuil
est atteint, pour que chacun ait joué le même nombre de manches. Les ex æquo partagent leur rang.

Les quatre familles de saisie — entier nu, entier + drapeau, grille structurée, saisie par
équipe — ont chacune leur écran : saisie générique (`LiveMatchView`), grille Yams
(`YamsSheetView`), et écrans dédiés pour la Belote, le Tarot et le Wizard.

---

## Fiches détaillées

### Skyjo — `skyjo.v1`

Détaillé dans [04 — Moteur de règles](04-moteur-de-regles.md#le-cas-skyjo-en-détail). Points
saillants :

- Le joueur qui ferme la manche voit son score **doublé** s'il n'est pas strictement le plus
  bas — l'égalité ne protège pas. Doublement uniquement si le score est > 0. Désactivable
  (variante « Doublement du score »).
- Seuil de fin : 100 (variantes 50, 150, 200).
- Départage : meilleure manche unique, puis nombre de manches fermées, puis ex æquo.
- Saisie : un entier de −24 à 156 par joueur, plus exactement un drapeau « a fermé la manche ».

### Yams — `yams.v1`

Grille individuelle de 13 catégories. La saisie n'est pas un nombre par manche mais **une
catégorie remplie par tour**, ce qui teste le cas « la manche n'est pas un tour de table
homogène ».

| Section haute | Valeur |
|---|---|
| As … Six | somme des dés de la valeur |
| **Bonus** | **+35 si la section haute atteint le seuil** (63, variante 60) |

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
- Saisie contextuelle par catégorie : nombre de dés pour la section haute, somme pour
  brelan/carré/chance, obtenu ou raté pour les figures.
- Départage : total de section basse, puis ex æquo.

### Belote — `belote.v1`

Premier jeu **par équipes** : les participants sont regroupés, le classement porte sur les
équipes. Le domaine gère cela par un champ `teamID` optionnel sur `Participant`, `nil` pour
tous les jeux individuels. Chaque coéquipier reçoit une entrée identique (le score de l'équipe) :
la fin de partie et le classement génériques s'appliquent alors sans code spécifique.

Belote classique, sans contrat chiffré :

- 162 points distribués par donne, dix de der inclus. On saisit l'équipe preneuse et ses points ;
  la défense reçoit le complément.
- Le preneur réussit s'il marque plus de 81 points ; sinon il **chute** et la défense encaisse
  162.
- Capot : 250 à l'équipe qui fait tous les plis, 0 à l'autre.
- Belote-rebelote : +20 à l'équipe qui l'annonce.
- Fin : première équipe à 1000 (variantes 501, 2000).

### Rami — `generic.sum.v1`

- Le joueur qui sort marque 0 ; les autres cumulent la valeur des cartes en main.
- Le plus bas gagne, fin à 251 (variantes 100, 500).
- Aucun code spécifique : le JSON déclaratif suffit intégralement.

### 6 qui prend — `generic.sum.v1`

- On accumule des têtes de bœuf, le plus bas gagne.
- Fin : un joueur atteint 66.
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

- Fin : nombre de donnes fixé, multiple du nombre de joueurs pour l'équité de la donne
  (2 donnes par joueur par défaut, variantes 1 et 3).
- Une donne peut être passée (personne ne prend) : tous à 0.

### Wizard — `wizard.v1`

- Nombre de manches = 60 / nombre de joueurs (20 à 3 joueurs, 15 à 4, 12 à 5, 10 à 6).
- Manche *n* : chacun annonce le nombre de plis qu'il pense réaliser.
- Annonce exacte → **+20 + 10 × plis annoncés**. Sinon → **−10 par pli d'écart**.
- Saisie : deux entiers par joueur (annonce, réalisé). Validation : la somme des plis réalisés
  doit égaler le numéro de la manche — contrôle bloquant, très utile en pratique.
- Fin : `roundLimit`.

### Mölkky — `molkky.v1`

- Une quille tombée → sa valeur ; plusieurs quilles → leur nombre. Saisie 0 à 12.
- **Dépasser 50 ramène à 25.** C'est la seule règle de « score non monotone » du catalogue.
- Trois échecs consécutifs (score 0) → joueur éliminé.
- Fin : `targetReached` dès qu'un joueur atteint exactement 50, ou `elimination` s'il ne reste
  qu'un joueur.
- Particularité : la partie s'arrête **immédiatement**, pas en fin de tour de table. Le
  `EndCheck` renvoie `.ended` sans passer par `.finalRound` — le cas qui justifie que les deux
  soient distincts dans l'énumération.

### Les 14 jeux sur `generic.sum.v1`

Aucun code. Uniquement un JSON déclaratif qui fixe le sens du score, les bornes de saisie, la
fin de partie et les libellés (voir la table en tête de document). Les jeux sans condition de
fin naturelle (Scrabble, Qwixx, Rummikub, Jeu libre) se terminent par un bouton « Terminer la
partie ».

---

## Ajouter un jeu

Procédure, dans cet ordre strict :

1. Écrire `spec/games/<id>.json`, valider contre le JSON Schema, traduire nom et libellés dans
   les cinq langues.
2. Écrire au moins un `spec/golden/<id>-*.json` couvrant un cas nominal **et** le cas limite
   qui rend le jeu particulier (le doublement, le bonus, la chute…).
3. Si `engine` ≠ `generic.sum.v1`, implémenter `GameRules` en Swift et en Kotlin jusqu'à ce que
   les golden files passent sur les deux plateformes.
4. Ajouter l'entrée à la table `engineID -> GameRules` de chaque catalogue.
5. Recopier `spec/` dans ses copies Apple (`Scripts/check-spec-sync.sh` vérifie l'égalité).

Écrire le golden **avant** l'implémentation n'est pas un dogme de TDD ici : c'est ce qui permet
d'écrire la seconde plateforme sans relire le code de la première.
