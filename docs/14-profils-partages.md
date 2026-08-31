# 14 — Profils partagés entre appareils

Statut du document : **exploration**, pas encore de décision engagée sur le code — contrairement
aux autres docs numérotées, celle-ci précède l'implémentation plutôt que de la documenter.

## Le besoin

Je crée l'avatar d'un ami sur mon téléphone, je fais une partie avec lui. S'il a l'app, cette
partie devrait pouvoir apparaître dans son propre historique, sur son propre téléphone — sans
compte, sans friction, sans exploser la base Supabase.

Ce n'est **pas** la même chose que le partage en direct ([doc 09](09-partie-partagee.md)) :

| | Partage en direct (doc 09) | Profils partagés (ce doc) |
|---|---|---|
| Quand | Pendant la partie | Après coup, même hors ligne |
| Qui | Quelqu'un physiquement là, avec son téléphone sorti | N'importe quel ami ajouté comme joueur, présent ou non |
| Durée du lien | Une soirée (code réappairé à chaque session) | Permanent (une fois lié, toujours lié) |
| Ce qui est vu | Le tableau de scores en temps réel | Le résultat final, plus tard |

Les deux coexistent : rien dans ce document ne remplace le partage en direct, qui reste la bonne
réponse à « je veux que Théo suive la partie en cours depuis son canapé ».

## Pourquoi pas une simple extension de doc 09

Le partage en direct est *éphémère* par construction (`cacompte_open_games` se purge après 24 h,
`LiveShareCoordinator` ne survit pas à l'arrêt du partage). Ce qu'on demande ici est *permanent* :
que « Marie », une fois liée, reste liée d'une partie à l'autre, des mois plus tard, sans
ré-appairage. Réutiliser le mécanisme de code d'appairage pour l'identité (plutôt que pour une
seule session) serait déformer un outil conçu pour l'éphémère.

## Question par question

**Une nouvelle page « Profil » doit-elle être ajoutée ?**
Non, pas un nouvel onglet. `ProfileView` existe déjà par joueur (stats d'un participant tel
qu'enregistré sur cet appareil) — ce qui manque n'est pas un écran de plus mais une action de
plus sur l'écran d'édition d'un joueur existant (`PlayerEditorView`) : « Partager ce profil » /
« Lier un profil ». Le modèle mental reste « des fiches joueur, dont certaines peuvent être
liées à l'installation d'un ami » — pas « l'app a maintenant des comptes ».

**Comment le profil d'une app se synchronise avec celui d'une autre ?**
Explicitement, une fois, par QR/code — jamais automatiquement. Voir « Identité cross-appareil »
ci-dessous.

**Sélection de profils locaux ou publiés à l'ajout des joueurs ?**
Ni l'un ni l'autre au sens d'un annuaire : il n'y a pas de liste publique de profils cherchables
(ce serait un vrai système d'identité publique — des gens retrouvables par leur nom — hors de
proportion avec le besoin, et un vrai souci de confidentialité). `MatchSetupView` ne change pas :
on choisit toujours parmi ses propres fiches locales. Qu'une fiche soit liée ou non est invisible
à cet écran — ça ne joue qu'au moment où la partie se termine (voir plus bas).

**Synchronisation automatique par nom identique ?**
Déconseillé comme mécanisme principal. Deux « Marie » différentes dans deux groupes d'amis
existent forcément ; une correspondance silencieuse par pseudo attribuerait des statistiques à la
mauvaise personne, ou pire, fusionnerait deux identités distinctes sans qu'aucune des deux ne
l'ait demandé. Si le besoin se confirme, une version *suggestive* (« quelqu'un d'autre utilise
aussi "Marie", lier les profils ? ») resterait possible plus tard, mais avec confirmation
explicite des deux côtés — jamais silencieuse. Non retenue pour la v1.

**Jouer hors connexion, synchroniser au retour du réseau ?**
Oui, nativement — voir « Économie de ressources et robustesse hors-ligne ». C'est le même patron
que `MatchConnectionCoordinator.scheduleAutoRetry` (déjà en place pour le partage en direct),
appliqué à une file d'attente différente.

## Identité cross-appareil — options considérées

**Option A — Lien explicite par code, sans compte (retenue).**
Une fiche joueur peut générer un identifiant partageable (`UUID`, jamais réutilisé). L'ami scanne
ce code une fois avec sa propre app ; sa fiche locale (qui le représente, lui, chez lui) enregistre
ce même `UUID`. Aucun compte, aucune connexion — le lien est une propriété de deux fiches locales,
pas une entité serveur.

**Option B — Correspondance automatique par pseudo.**
Écartée comme mécanisme principal (voir ci-dessus) — ambiguë, risque de confidentialité, aucune
confirmation possible avant que le mal soit fait.

**Option C — Vrais comptes (Sign in with Apple, email).**
Écartée pour la v1 : ajoute connexion, gestion de session, RGPD (suppression de compte), une
vraie surface de revue de sécurité — pour un besoin qui n'exige pas de compte durable, seulement
une identité stable entre deux appareils précis. À reconsidérer seulement si le produit évolue
vers des fonctionnalités sociales plus lourdes (annuaire public, classements entre inconnus).

**Pourquoi A l'emporte** : même modèle mental que le code d'appairage déjà compris par
l'utilisateur (doc 09), zéro compte, zéro ambiguïté (toujours un geste explicite et mutuel), et
un `UUID` transmis par QR plutôt que tapé à la main est *plus* sûr que le code à 6 chiffres déjà
accepté pour rejoindre une partie en direct — le seuil de sécurité de ce projet est déjà fixé par
doc 09 (« quiconque connaît le code peut rejoindre ») ; l'option A ne l'abaisse pas.

## Design retenu

### Phase 1 — lier deux fiches, aucun serveur ✅

`PlayerRecord` gagne un champ optionnel `sharedProfileID: UUID?`. Sur `PlayerEditorView`, une
nouvelle section « Profil partagé » :

- **Partager ce profil** — génère un `UUID` s'il n'existe pas encore, l'encode dans un lien
  `cacompte://claim-profile?id=<uuid>&name=<pseudo>` (même famille que `JoinLink`, un cas de plus
  dans son `enum`), affiché en QR (`QRCodeView`, déjà là) et en texte.
- **Lier un profil reçu** — réutilise le scanner déjà construit pour l'onglet « Rejoindre »
  (`QRScannerView`) : scanner le QR d'un ami enregistre son `sharedProfileID` sur *ma* fiche qui
  le représente.

Aucune table Supabase pour cette phase — le payload du QR se suffit à lui-même, exactement comme
un `JoinLink` aujourd'hui. Risque et coût d'implémentation minimaux ; testable et livrable seule,
sans que la phase 2 existe encore.

### Phase 2 — la partie apparaît chez l'ami ✅

À la fin d'une partie (`.ended` ou `.abandoned` — jamais en cours, pour éviter tout merge
incrémental dans le stockage SwiftData de quelqu'un d'autre pendant que la partie tourne), pour
chaque participant dont la fiche porte un `sharedProfileID` : pousser un résumé compact vers la
table Supabase `cacompte_shared_match_summaries` :

```sql
create table cacompte_shared_match_summaries (
    match_id uuid not null,
    shared_profile_id uuid not null,
    payload jsonb not null,      -- jeu, version de règles, date, classement complet (pseudo,
                                  -- avatar léger, rang, score) — pas le journal d'événements
    created_at timestamptz not null default now(),
    primary key (match_id, shared_profile_id)
);
```

Clé primaire composite plutôt qu'un `id` généré, pour que le push soit un `upsert` idempotent
(`SharedProfileTransport.push`) : une tentative retentée après une réponse perdue ne duplique
jamais la ligne, même si le serveur avait bien reçu la première. Même politique RLS que le reste
(`anon`, connaître l'id suffit — cohérent avec le seuil de sécurité déjà accepté doc 09). Purge de
sécurité à 30 jours (comme `cacompte_open_games`, en plus généreux puisqu'un ami peut rester hors
ligne des semaines) pour le cas où personne ne vient jamais la récupérer — l'index existe
(`cacompte_shared_match_summaries_created_at_idx`), le job de purge lui-même reste à brancher
séparément (même remarque que `cacompte-live-activity-sweep`, hors de ce dépôt).

`MatchRecord.pendingSharedProfileSync` marque une partie conclue avec au moins un participant lié,
mis à jour à chaque conclusion (`MatchRepository.persist`, jamais figé à la création). Côté ami,
au lancement et à chaque retour au premier plan (`SharedProfileSyncCoordinator`, même déclencheur
que `MatchConnectionCoordinator`, pas de minuteur propre) : pousser les parties en attente,
interroger les résumés en attente pour ses propres fiches liées, les matérialiser en `MatchRecord`
local (`MatchRepository.materializeSharedSummary`), puis **supprimer** la ligne côté serveur — la
table ne sert que de boîte aux lettres transitoire, jamais de copie durable. Même discipline que
`cacompte_open_games` : Supabase est un relais, jamais la source de vérité. `SharedMatchSummaryPayload`
vit dans `Domain` (pas `Store` ni `Sync`) : les deux en ont besoin, aucun des deux ne dépend de
l'autre.

**Ce qui est matérialisé n'est pas une partie rejouable.** Le `MatchRecord` reçu
(`isImportedSummary = true`) a un journal d'événements vide, jamais rejoué (`HistoryDetailView`
s'en assure explicitement — le rejouer planterait, `MatchEngine.replay` exige un premier événement
`matchCreated`) ; seuls `ParticipantRecord.finalRank`/`finalScore` sont renseignés depuis le
résumé. C'est délibéré et suffisant : `LeaderboardRepository` et `ProfileRepository` (parties
jouées, victoires, taux de victoire, rang moyen normalisé) ne lisent que ces deux champs, jamais
le détail manche par manche — les statistiques de l'ami restent donc justes sans qu'il ait besoin
du journal complet. `ReceivedMatchDetailView` remplace `ResultsView` pour ces parties (un podium
simple à partir des *snapshots*, pas de courbe ni de manche par manche) ; `HistoryListView` les
distingue d'un simple « · Reçue » à côté de la date.

Simplification par rapport à l'esquisse initiale : le nombre de manches n'est pas transporté (pas
de champ pour le stocker côté matérialisé, et il n'était pas indispensable au besoin — voir
« marches jouées » plus haut, laissé de côté plutôt que d'ajouter un champ pour une valeur
d'affichage secondaire).

Ce choix — résumé, pas copie intégrale — reste le point du design le plus arbitraire de ce document
(voir « Décisions ouvertes »).

### Synergie avec l'onglet « Rejoindre »

L'onglet ajouté aujourd'hui pour rejoindre une partie en direct (caméra prête à scanner) devient
le point d'entrée naturel pour « lier un profil » aussi — même geste (scanner un QR d'ami),
distingué par le contenu du lien (`cacompte://join` vs `cacompte://claim-profile`), zéro écran
supplémentaire.

## Économie de ressources et robustesse hors-ligne

- Phase 1 : zéro coût serveur.
- Phase 2 : une ligne JSON de quelques centaines d'octets par (partie, participant lié) —
  supprimée dès réception, pas accumulée. Un groupe de 6 amis qui joue trois fois par semaine
  produit un trafic négligeable face à la fenêtre de purge de 24 h déjà en place pour
  `cacompte_open_games`.
- Hors ligne à la fin d'une partie : la tentative d'envoi échoue simplement et se met en attente
  (`MatchRecord.pendingSharedProfileSync` reste `true`), rejouée au prochain retour au premier
  plan avec réseau — même patron que `scheduleAutoRetry` (`MatchConnectionCoordinator.swift`), pas
  un nouveau système de synchronisation à inventer.

## Phasage

1. **Phase 1** ✅ — champ `sharedProfileID`, UI de partage/liaison, réutilisation de `JoinLink` +
   `QRCodeView`/`QRScannerView`.
2. **Phase 2** ✅ — table `cacompte_shared_match_summaries`, envoi à la conclusion d'une partie
   (`SharedProfileSyncCoordinator`), réception au premier plan, matérialisation en `MatchRecord`
   minimal (`isImportedSummary`), écran de détail dédié (`ReceivedMatchDetailView`).
3. **Plus tard, si demandé** — copie intégrale rejouable (journal d'événements complet plutôt
   qu'un résumé) ; alimente aussi « Statistiques de groupe » ([roadmap](12-roadmap.md), face-à-face
   entre profils liés).

Reste manuel, hors de ce dépôt : brancher une purge programmée (30 jours) sur
`cacompte_shared_match_summaries`, comme `cacompte-live-activity-sweep` pour les Live Activity —
l'index existe, pas le job.

## Limites de confiance — ce que `sharedProfileID` prouve, et ce qu'il ne prouve pas

Question posée après coup, en relisant les phases 1/2 avec un œil critique : *un ami pourrait
créer sur son téléphone une fiche nommée « Erwan » et partager ce lien à d'autres personnes —
est-ce que je pourrais alors lier mon propre téléphone à cette fiche ? Quelles sont les limites
réelles du partage de profil ?*

### Ce qui se passe mécaniquement

Rien dans le code n'empêche ce scénario. `sharedProfileID` est un `UUID` nu : n'importe quel
appareil qui l'a vu (par QR, capture d'écran, lien retransmis) peut l'écrire dans
`PlayerRecord.sharedProfileID` via « Lier un profil reçu ». Aucune limite au nombre d'appareils
qui peuvent partager le même `UUID`, et c'est volontaire — c'est précisément ce qui permet à
plusieurs amis de suivre la même personne chacun depuis sa propre fiche (Théo *et* Marie lient
chacun leur fiche « Erwan » au vrai code d'Erwan : les deux verront ses parties, c'est le
fonctionnement voulu).

Le problème n'est donc pas « trop d'appareils liés à un même identifiant » — c'est qu'**aucune
vérification ne relie un `UUID` à la personne réelle qu'il prétend représenter**. Un pseudo est
une chaîne de caractères libre, jamais authentifiée. Si un ami crée une fiche « Erwan » qui n'est
pas la mienne et la partage, c'est un `UUID` entièrement différent du mien — je n'ai littéralement
aucun moyen de savoir qu'il existe, encore moins de le lier à mon téléphone (je ne peux lier que
ce que je scanne moi-même). Le risque réel n'est donc pas que *je* sois piégé, mais que **d'autres
lient leur fiche « Erwan » à cette fausse identité en croyant que c'est la mienne** — leurs
statistiques et leur historique s'y retrouveraient mélangés à des parties que je n'ai jamais
jouées, sans que je le sache ni que je puisse le corriger.

### D'où vient la confiance, en pratique

Le lien n'est fiable que si le QR est scanné directement depuis l'écran de la personne
concernée — le contexte physique (« je suis avec Théo, je scanne son téléphone ») *est* la
vérification, il n'y en a pas d'autre. Un lien retransmis (capture d'écran envoyée dans un groupe,
« scanne ça pour me lier ») perd cette garantie et redevient un pur acte de confiance envers qui
l'a transmis. C'est exactement le modèle déjà assumé par le partage en direct (doc 09,
« quiconque connaît le code peut rejoindre ») — les profils partagés en héritent, avec un
identifiant permanent plutôt qu'une session d'une soirée.

### Les limites, énumérées

1. **Aucune preuve cryptographique de propriété.** Un `UUID` connu = un `UUID` qu'on peut lier.
   Rien ne distingue « la bonne personne l'a partagé » de « quelqu'un l'a vu passer ».
2. **Aucune visibilité sur qui est lié.** Une fois `sharedProfileID` posé, rien n'indique dans
   l'app à qui (quel appareil, quel pseudo au moment du scan) cette fiche est liée. Impossible
   d'auditer, de remarquer une liaison inattendue.
3. **Le pseudo transporté par le QR n'est même pas utilisé.** `ProfileShareLink.Payload.name` est
   décodé (`PlayerEditorView.swift`) puis jeté — aucune confirmation n'est montrée avant de lier
   (« Tu es sur le point de lier cette fiche à *Marie* » n'existe pas). Un scan accidentel du
   mauvais code n'est jamais signalé.
4. **Pas de garde contre une double liaison sur le même appareil.**
   `PlayerRepository.linkSharedProfile` écrit `sharedProfileID` sans vérifier qu'il n'est pas déjà
   utilisé par une *autre* fiche locale — un scan malencontreux de son propre code, ou de deux
   codes différents sur la même fiche, ne produit aucun avertissement.
5. **Aucune révocation qui compte vraiment.** « Ne plus partager » efface `sharedProfileID`
   *localement* — l'`UUID` lui-même reste valable pour quiconque l'a déjà lié ailleurs. Impossible
   de dire « cet identifiant est désormais invalide partout » puisque rien ne sait qui d'autre le
   détient (voir point 2).
6. **Pas de notion de « c'est moi ».** Une fiche qui représente un ami et une fiche qui me
   représente moi-même sont structurellement identiques — rien ne permet à l'app (ni à
   l'utilisateur, d'un coup d'œil) de distinguer les deux.

### Ce qu'on ne peut pas corriger sans compte réel — et pourquoi ce n'est pas grave

Empêcher purement et simplement qu'un pseudo mente sur qui il représente exigerait une
authentification — Option C déjà écartée plus haut, pour de bonnes raisons (connexion, gestion de
session, RGPD, surface de revue de sécurité, alors que le besoin ne demande qu'une identité stable
entre deux appareils précis). Ce n'est **pas** une régression à corriger : c'est la même limite
que doc 09 assume déjà pour le partage en direct, dans un contexte — des amis qui jouent
ensemble — où le coût d'une usurpation réussie reste faible (des statistiques de jeu de société,
pas des données sensibles) face au coût d'ajouter des comptes.

### Ce qu'on peut améliorer sans compte, en restant dans la philosophie actuelle

| # | Amélioration | Répond à | Coût |
|---|---|---|---|
| 1 | Afficher le pseudo (et l'avatar) scannés avant de confirmer la liaison (« Lier cette fiche à *Marie* ? ») ✅ | Limite 3 | Trivial — le champ existe déjà, juste jamais lu |
| 2 | Mémoriser localement un libellé « Lié à *Marie*, le 2 sept. 2026 » sur la fiche ✅ | Limite 2 | Faible — deux champs de plus sur `PlayerRecord` |
| 3 | Bloquer (avec confirmation explicite pour passer outre) la liaison à un `UUID` déjà utilisé par une *autre* fiche locale ✅ | Limite 4 | Faible — une vérification dans `PlayerRepository` |
| 4 | Marquer une fiche comme « C'est moi » (une seule par appareil) ✅ — voir plus bas, simplifié | Limite 6 | Modéré — un champ, une action dans `PlayerEditorView`, une mise en avant dans `PlayersListView` |
| 5 | Registre léger côté serveur : qui a revendiqué mon `UUID`, et quand (nouvelle table, sur le modèle de `cacompte_open_games`) | Limite 2 et 5 (partiellement) | Modéré à élevé — nouvelle table, UI de consultation, ne détecte que les liaisons sur *mon propre* identifiant, jamais une fausse fiche créée sous un `UUID` distinct |

Le point 5 mérite une précision importante : il ne répond **pas** au scénario initial (une fausse
fiche « Erwan » créée sous un `UUID` différent du mien reste invisible pour moi, quel que soit le
registre) — il aide seulement à détecter une utilisation *inattendue* de mon propre lien une fois
partagé (ex. je l'ai partagé une fois à Théo, je vois pourtant trois appareils l'avoir revendiqué).

**Implémenté (points 1-3)** : `ProfileShareLink.Payload` transporte maintenant aussi l'avatar
(`avatarKind`/`avatarValue`/`paletteID`, jamais une photo — trop lourde pour un QR), pas
seulement le pseudo. Scanner un code n'écrit plus rien directement : une feuille de confirmation
(`ConfirmProfileLinkView`) montre le pseudo et l'avatar avant toute écriture, avec un choix
« Adopter aussi son pseudo et son avatar » (adopte les deux plutôt que de garder ceux,
potentiellement approximatifs, déjà choisis sur la fiche locale — reprend le pseudo seul si
l'avatar de l'autre est une photo). Si l'identifiant scanné est déjà utilisé par une *autre* fiche
locale, la feuille avertit et demande une confirmation supplémentaire (« Lier quand même ») plutôt
que de bloquer sans recours ou de lier en silence. `PlayerRecord.sharedProfileLinkedName`/
`sharedProfileLinkedAt` retiennent le pseudo et la date connus au moment de la liaison, affichés
sur `PlayerEditorView` (« Liée à Marie, le … ») — jamais mis à jour ensuite, puisque rien ne
prévient cet appareil si l'ami renomme sa propre fiche par la suite.

Le point 5 reste à faire, si demandé — voir « Décisions ouvertes ».

### Phase 4 — « C'est moi » implicite, et une vraie fuite entre amis corrigée ✅

Question posée après coup : si je lie mon profil à l'appareil d'un ami pour partager notre
historique commun, et que je joue ensuite une partie avec un *autre* ami — cette partie-là est-
elle aussi poussée vers le premier ami, alors qu'il n'y était pour rien ?

**Oui, avant cette phase.** `SharedProfileSyncCoordinator.pull` matérialisait tout ce qui était
poussé vers un identifiant détenu localement, sans se demander si *cette partie précise*
concernait l'appareil récepteur. Suivre un ami donnait donc accès à l'intégralité de son
historique, pas seulement aux parties jouées ensemble — plus que ce que le besoin initial
demandait, et une vraie fuite d'information (un ami apprend que j'ai joué avec quelqu'un d'autre,
sans y avoir participé).

**La correction retenue est plus simple que le point 4 initialement esquissé** (un champ « C'est
moi » séparé, à cocher explicitement) : plutôt qu'une désignation à part, *partager* une fiche
**est** la désignation. Une seule fiche par appareil peut être partagée
(`PlayerRepository.sharedProfileID(for:)` refuse d'en désigner une seconde,
`PlayerRecord.sharedProfileIsMine`) — *lier* une fiche à l'identifiant d'un ami reste possible en
nombre, mais ne pose jamais ce drapeau. L'origine de l'identifiant (généré ici vs scanné ailleurs)
suffit donc à distinguer « mon identité » de « un ami que je suis », sans action supplémentaire à
retenir pour l'utilisateur.

`SharedProfileSyncCoordinator.pull` applique désormais un filtre : une partie n'est matérialisée
sans condition que pour *ma propre* fiche partagée (je veux tout consolider, où que ce soit
joué) ; pour une fiche qui *suit* un ami, une partie n'est matérialisée que si mon propre
identifiant partagé apparaît aussi parmi les *autres* participants du résumé — autrement dit,
seulement si j'y étais moi-même. La partie jouée avec un autre ami n'est simplement jamais
matérialisée sur l'appareil du premier.

**Effet de bord corrigé en même temps, découvert en construisant ce filtre** : la boîte aux
lettres supprimait chaque résumé dès sa première lecture, par n'importe quel appareil — si
plusieurs amis suivent la même personne (cas normal et voulu, voir plus haut), le premier à lire
supprimait la ligne avant que les autres n'aient pu la récupérer, leur faisant perdre la partie
silencieusement. Seul l'appareil qui fait autorité sur un identifiant (le sien, jamais partagé par
construction désormais) supprime après lecture ; les autres laissent la purge programmée (30
jours, toujours hors de ce dépôt) s'en charger.

**Effet de bord additionnel** : la fiche partagée d'un appareil apparaît maintenant toujours en
tête des listes de joueurs (`PlayersListView`, présélection de `MatchSetupModel`), quel que soit
le tri par ailleurs choisi — c'est la seule fiche qui représente l'utilisateur de cet appareil,
elle ne doit pas se perdre dans un tri par fréquence ou alphabétique.

### Phase 4, suite — un vrai trou dans « une seule fiche partageable », et le scan qui traînait ✅

Deux remontées après coup sur la phase 4.

**« Partager » restait possible sur une fiche qui suit déjà un ami.** `sharedProfileID(for:)`
retournait tout simplement l'identifiant déjà présent dès qu'il y en avait un, sans vérifier qu'il
s'agissait bien du sien — sur une fiche *liée* à un ami (donc déjà pourvue d'un identifiant, celui
de l'ami), ça revenait à rediffuser l'identité de l'ami comme si c'était la sienne propre :
exactement l'usurpation par pseudo décrite plus haut, mais auto-infligée par inadvertance.
Corrigé à deux niveaux : `sharedProfileID(for:)` refuse désormais explicitement
(`PlayerRepositoryError.cannotShareALinkedProfile`) si l'identifiant déjà présent n'est pas celui
que cette fiche partage elle-même ; `PlayerEditorView` ne propose même plus le bouton
« Partager » sur une fiche déjà liée à un ami (trois cas désormais distingués : la mienne, celle
qui suit un ami, celle qui n'est encore ni l'une ni l'autre).

**Scanner puis confirmer transitionnait lentement.** Deux présentations système enchaînées dans
le même geste (`.fullScreenCover` pour la caméra, puis `.sheet` pour la confirmation) — SwiftUI
doit terminer de refermer la première avant d'ouvrir la seconde, ce qui pouvait se lire comme un
écran qui ne réagit plus. `ProfileLinkScanFlow` remplace les deux par une seule présentation dont
le contenu bascule en interne (scan → confirmation), sans seconde transition système à attendre.
Le bouton « Lier » affiche en plus un indicateur de chargement pendant l'écriture (`Task { @MainActor
in }` cède la main une fois pour laisser SwiftUI l'afficher avant qu'un travail potentiellement
bloquant ne démarre) — pour toute latence encore perceptible au-delà de cette transition (écriture
SwiftData sous CloudKit, par exemple).

### Phase 4, suite — re-lier une fiche remplaçait son lien existant en silence ✅

Remontée : après avoir lié une fiche à Théo, la relier (« Lier un autre profil ») à Marie
remplaçait le lien vers Théo sans le dire — rien ne distinguait ce cas d'un premier lien. Théo
continuait de croire que cette fiche lui restait associée, alors qu'elle ne recevrait plus les
parties jouées ensemble. `ConfirmProfileLinkView` n'avait qu'un seul avertissement possible
(`conflictingPlayerName` : l'identifiant scanné appartient déjà à une *autre* fiche locale) — rien
ne portait sur le lien *propre* de la fiche en cours d'édition.

Corrigé en faisant remonter jusqu'à `ConfirmProfileLinkView` le lien déjà en place sur cette fiche
(`currentlyLinkedID`/`currentlyLinkedName`, portés par `ProfileLinkScanFlow`) : un second
avertissement distinct s'affiche quand le scan changerait vraiment de personne, et le bouton devient
« Remplacer le lien ». Rescanner le code de la même personne (par exemple pour rafraîchir son
pseudo ou son avatar) ne déclenche pas cet avertissement — comparaison sur l'identifiant scanné, pas
seulement sur le fait qu'un lien existe déjà.

### Phase 4, suite — « Lier » renommé « Suivre », et un bouton qui ne disait pas son importance ✅

Deux remontées de vocabulaire. D'une part, « Lier » (le verbe utilisé pour suivre un ami) et
« Ne plus suivre » (déjà présent pour délier) désignaient la même relation avec deux mots
différents — incohérence qui n'aidait pas à comprendre le concept. D'autre part, sur une fiche
déjà liée, le bouton « Lier un autre profil » ne laissait rien deviner de son importance :
tapé, il remplace le suivi actuel (voir l'entrée précédente) sans que le libellé du bouton
lui-même ne l'annonce — seul l'écran de confirmation, une étape plus loin, en parlait.

Corrigé en renommant « Lier » en « Suivre » partout (boutons, titre de l'écran de confirmation,
textes d'avertissement) — le vocabulaire est désormais celui déjà utilisé par « Ne plus suivre »,
qui n'a pas changé. Le bouton de remplacement rappelle en plus directement le nom de la personne
actuellement suivie : « Suivre quelqu'un d'autre (remplace Théo) », pour que l'enjeu soit visible
avant même d'ouvrir le scanner, pas seulement à la confirmation. Le texte d'explication en pied de
section (« Profil partagé ») a aussi été simplifié : une phrase par action, à l'impératif, plutôt
qu'une seule phrase dense mêlant les deux notions.

## Décisions ouvertes

Ce que ce document tranche par hypothèse plutôt que par confirmation — à valider avant la phase
correspondante :

1. **Résumé vs copie intégrale (phase 2).** Retenu : résumé (voir plus haut). Si l'ami doit
   pouvoir rejouer le détail manche par manche d'une partie où il n'était pas au clavier, il faut
   le journal complet — plus lourd, et pose la question du droit de l'hôte à republier le détail
   de saisie de quelqu'un d'autre.
2. **Suggestion de liaison par pseudo identique.** Non retenue pour la v1 (voir Option B) — à
   reconsidérer seulement si des utilisateurs la demandent explicitement.
3. **Une fiche liée peut-elle être déliée ?** Oui — implémenté phase 1 (« Ne plus partager »,
   `PlayerEditorView`). Efface `sharedProfileID` localement ; aucun état serveur à nettoyer
   puisque la phase 1 n'en a pas.
