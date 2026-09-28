# Fiches App Store et Play Store

Un fichier par langue, avec les champs propres à chaque langue, prêts à coller :
[fr-FR](fr-FR.md) · [en-US](en-US.md) · [es-ES](es-ES.md) · [de-DE](de-DE.md) · [it-IT](it-IT.md).
Les images viennent de `Scripts/store-screenshots.sh`, dans `store-screenshots/slides/`
(doc 10 « Captures des stores »). Leurs légendes se trouvent dans `store/slides/captions.json`.

Les deux descriptions ne sont pas identiques, parce que les apps diffèrent :

| | App Store | Play Store |
|---|---|---|
| Partie partagée entre iPhone et Android | oui | oui |
| Score sur l'écran verrouillé, Dynamic Island | oui | — |
| Image des résultats à partager | oui | — |
| Sauvegarde | iCloud, entre iPhone et iPad | sauvegarde automatique d'Android |

Toute nouvelle fonction doit être ajoutée aux deux descriptions, et aux cinq langues.

## App Store Connect — champs communs

| Champ | Valeur |
|---|---|
| Catégorie principale | Divertissement |
| Catégorie secondaire | Utilitaires |
| Prix | Gratuit |
| Classification par âge | 4+ (aucun contenu sensible au questionnaire) |
| Copyright | © 2026 Erwan Decoster |
| URL marketing | https://quimene.vercel.app |
| URL d'assistance | fr : https://quimene.vercel.app/assistance · autres langues : https://quimene.vercel.app/en/support |
| URL de la politique de confidentialité | fr : https://quimene.vercel.app/confidentialite · autres langues : https://quimene.vercel.app/en/privacy |
| Nouveautés | Non demandées pour la version 1.0 |

**Confidentialité de l'app** (étiquette, identique à `PrivacyInfo.xcprivacy`) :

- Données collectées : oui, deux types, tous deux pour la **fonctionnalité de l'app**,
  **non liés** à l'identité de l'utilisateur, **sans suivi** :
  - *Autre contenu de l'utilisateur* : pseudos, avatars simples et scores des parties
    partagées, et des parties déposées pour un ami ;
  - *Identifiant de l'appareil* : identifiant aléatoire créé par l'app, jeton des Activités en
    direct.

**Informations pour la revue** (à coller en anglais dans « Notes ») :

```text
Qui Mène ? needs no account and no sign-in.

Main flow: Games tab > pick a game (e.g. Skyjo) > select players > Start, then enter each round's scores.

Live sharing is optional and needs two devices with the app installed and an Internet connection:
1. On device A, during a game, open the menu at the top left (…) > "Share Live". A six-digit code and a QR code appear.
2. On device B, tap "Join a match" (QR icon at the top of the Games tab, or in the Profile tab), scan the QR code or type the code, then pick a seat (or "I'm Just Watching").
3. Rounds entered on either device appear on both. While device A is locked, rounds entered on device B update its Lock Screen and Dynamic Island (Live Activity).

The camera is only used to scan these QR codes. Game names in the catalog belong to their owners; the app only keeps score and is not affiliated with any publisher.
```

## Play Console — champs communs

| Champ | Valeur |
|---|---|
| Catégorie | Divertissement |
| Coordonnées | contact@erwan-decoster.com · https://quimene.vercel.app |
| Politique de confidentialité | https://quimene.vercel.app/confidentialite |
| Annonces | Non, l'application ne contient pas d'annonces |
| Accès à l'application | Toutes les fonctionnalités sont disponibles sans restriction (aucune connexion) |
| Public cible | 13 ans et plus. L'app convient à tous les âges, mais cibler les moins de 13 ans soumet l'app au programme Familles de Google (exigences supplémentaires). |
| Classification du contenu | Catégorie « Utilitaire, productivité, communication ou autre ». Aucun contenu sensible. Interaction entre utilisateurs : oui, car les participants d'une partie partagée voient les pseudos et les scores des autres, uniquement avec le code. |

**Sécurité des données :**

- Données collectées : oui. Aucune donnée n'est partagée : Supabase est un prestataire, ce qui
  n'est pas un partage au sens de Google.
- Types de données, tous pour le **fonctionnement de l'application** et **facultatifs** (collectés seulement si l'utilisateur partage une partie ou lie un ami) :
  - *Activité dans les applications › Autre contenu généré par l'utilisateur* : pseudos,
    avatars simples, scores ;
  - *Identifiants de l'appareil ou autres* : identifiant aléatoire créé par l'app.
- Chiffrement en transit : oui.
- Suppression : automatique, 24 h après la fin d'une session partagée et 14 jours au plus
  pour une partie déposée. Une demande reste possible par e-mail.

**Éléments graphiques :** captures `store-screenshots/slides/play-store/<langue>/`
(téléphone, tablette 7", tablette 10").

## Avant de publier

1. **Marques.** Les noms de jeux déposés (Skyjo, Mölkky, Yahtzee, Kniffel, Scrabble…)
   n'apparaissent que dans les descriptions, accompagnés de la mention de non-affiliation.
   Ils ne figurent jamais dans le nom, le sous-titre ni les mots-clés : Apple le refuse
   (règle 2.3.7) et Google aussi. Les mots-clés ne contiennent que des noms génériques
   (tarot, belote, rami, pétanque).
2. **Play Store, lien de confidentialité dans l'app.** Google l'exige comme Apple ; il manque
   encore sur Android, où il est prévu après la validation iOS.
3. **Play Store, image de présentation (1024 × 500) et icône 512 × 512 :** obligatoires, pas
   encore produites.
4. **Politique de confidentialité :** rédigée pour iOS (iCloud, Activités en direct). Ajouter
   la sauvegarde automatique d'Android avant la publication sur le Play Store.
5. **Nom sur l'App Store :** « Qui Mène ? – Scores de jeux » doit être libre, car les noms
   sont uniques sur l'App Store. Sous l'icône, l'app s'affiche toujours « Qui Mène ? ».
