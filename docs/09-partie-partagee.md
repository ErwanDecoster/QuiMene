# 08 — Partie partagée en direct

Plusieurs appareils suivent la même partie autour de la table, **chacun avec une connexion
Internet, sans compte à créer, sans serveur à opérer soi-même** — et **quelle que soit leur
plateforme** : iPhone et Android doivent pouvoir rejoindre la même partie. C'est une contrainte
de premier ordre, pas un bonus : elle élimine d'office toute techno propriétaire à une seule
plateforme. L'exigence d'Internet est un changement assumé depuis [ADR-0016](13-decisions-adr.md) :
la première version (Wi-Fi local + Bluetooth LE, voir historique ADR-0014) fonctionnait bien
sans réseau, mais sa fiabilité de connexion ne s'est jamais avérée suffisante en pratique.

## Cas d'usage

Marion tient le score sur son iPhone. Théo, sceptique, veut voir le tableau sur son Pixel.
David, qui a une meilleure vue d'ensemble, voudrait saisir sa propre manche depuis son iPad.
Aucun des trois n'a de réseau : ils sont dans un gîte. Le groupe mélange les plateformes sans
même y penser — c'est le point de départ, pas un cas limite.

## Technologie

**Un seul transport : Supabase Realtime**, un service websocket managé, choisi après qu'une
première version bâtie sur deux frameworks système (Wi-Fi local + Bluetooth LE, historique
[ADR-0014](13-decisions-adr.md)) n'a jamais atteint une fiabilité de connexion suffisante —
voir [ADR-0016](13-decisions-adr.md) pour le détail de cette décision. C'est l'unique exception
tierce côté Apple à la règle « zéro dépendance » (ADR-0012).

| Rôle | Mécanisme |
|---|---|
| **Découverte** | Table Postgres `cacompte_open_games` (`supabase/migrations/`) : résout le code d'appairage à 6 chiffres tapé par le pair qui rejoint vers le `sessionID` de l'hôte. Aucune notion de proximité physique — le code seul suffit. |
| **Canal** | Un canal Realtime par session de partage, `session:<sessionID>` (indépendant de la partie courante — un hôte peut enchaîner plusieurs parties sans jamais rouvrir le canal, voir « Fin de partie » plus bas). |
| **Connexion / déconnexion** | **Presence** : l'hôte s'annonce sous une clé constante `"host"` (le pair n'a besoin de connaître aucun identifiant à l'avance) ; chaque pair s'annonce sous son `deviceID`. Une déconnexion, y compris abrupte (app tuée, réseau perdu), déclenche un événement de présence côté serveur. |
| **Données** | **Broadcast** : chaque `WireMessage` transite chiffré (voir « Appairage et chiffrement » plus bas), adressé par un en-tête `from`/`to` applicatif — `SupabaseTransportSession` filtre ce flux partagé pour se comporter comme une session point-à-point ordinaire du point de vue de `LiveSession`. |

`SupabaseTransport` (`CaCompteKit/Sources/Sync/SupabaseTransport.swift`) est la seule
implémentation du protocole `Transport` (voir plus bas) — `supabase-swift` côté Apple,
`supabase-kt` pour l'équivalent Android (doc [11](11-portage-android.md)), sur le même modèle
canal/presence/broadcast des deux côtés.

## Modèle : hôte autoritaire

Inchangé, et **indépendant du transport** — c'est tout l'intérêt de l'avoir découplé dès le
départ. Un pair et un seul est **l'hôte** : celui qui a créé la partie. Il détient la vérité et
est le seul à écrire dans le stockage local (SwiftData côté Apple, Room côté Android).

```
        ┌──────────────┐
        │  HÔTE        │  crée la partie, persiste, arbitre
        │  Marion      │  seul écrivain du stockage local
        └──┬────────┬──┘
   invite  │        │  invite
      ┌────▼──┐  ┌──▼─────┐
      │ Théo  │  │ David  │   participants — plateforme indifférente
      │ vue   │  │ saisie │   état en mémoire uniquement
      └───────┘  └────────┘
```

Deux rôles pour un pair non-hôte, choisis par l'hôte à l'invitation :

- **Observateur** — reçoit et affiche, ne peut rien modifier.
- **Contributeur** — peut proposer une manche ; l'hôte l'accepte automatiquement si elle est
  valide.

Pourquoi un hôte autoritaire plutôt qu'une réplication pair-à-pair symétrique : le journal
d'événements rendrait techniquement la seconde possible, mais elle impose de gérer les
partitions réseau (deux sous-groupes qui divergent), l'élection d'un nouveau maître si l'hôte
part, et une interface pour arbitrer les conflits. Pour quelques téléphones à un mètre les uns
des autres pendant une heure, le rapport coût/bénéfice est mauvais. La décision est
[ADR-0008](13-decisions-adr.md) et ne change pas avec le choix de transport.

Elle reste réversible : le protocole étant un journal d'événements commutatif, passer à une
réplication symétrique plus tard ne changerait ni le format des messages, ni le modèle de
données.

## Abstraction de transport

`Sync` expose un protocole unique, et deux implémentations interchangeables derrière lui —
c'est la même idée que `GameRules` pour le moteur : une interface stable, plusieurs moteurs.

```swift
public protocol Transport: Sendable {
    /// Découvre les hôtes annonçant une partie, pendant la fenêtre donnée.
    func discover(timeout: Duration) -> AsyncStream<DiscoveredHost>
    /// Annonce cette partie aux appareils à portée.
    func advertise(matchID: UUID, gameID: String, participantCount: Int) async throws
    func stopAdvertising() async
    /// Connexions entrantes acceptées côté hôte (une par pair qui rejoint).
    func acceptIncoming() -> AsyncStream<any TransportSession>
    /// Connexion sortante côté pair qui rejoint — le code d'appairage n'y intervient pas : il
    /// n'est utilisé qu'ensuite, par `LiveSession`, pour dériver la clé de chiffrement.
    func connect(to host: DiscoveredHost) async throws -> any TransportSession
}

public protocol TransportSession: Sendable {
    var incoming: AsyncStream<Data> { get }
    func send(_ data: Data) async throws
    func close() async
}
```

`SupabaseTransport` est aujourd'hui la seule implémentation de ce protocole. `LiveSession` (dans
`Sync`) ne connaît que `Transport` — elle ignore laquelle est active, exactement comme
`MatchEngine` ignore quel `GameRules` elle appelle. Cette indirection reste utile même à un seul
transport réel : `SyncTests` s'appuie sur une troisième implémentation, `InMemoryTransport`,
réservée aux tests (voir « Tests » plus bas).

## Sélection du transport

**Supabase Realtime, seul transport.** Pas de découverte de proximité, pas de bascule entre
plusieurs mécanismes : le code d'appairage est la seule information nécessaire pour se
connecter, où que soient les deux appareils.

```
Hôte (Marion)                                          Rejoint (Théo)
 │
 ├─ advertise(sessionID, ...) : upsert cacompte_open_games,
 │  souscrit au canal session:<sessionID>
 │  affiche : code d'appairage à 6 chiffres
 │
 │                                                       ├─ saisit ou scanne le code
 │                                                       ├─ resolveGame(code) → lit
 │                                                       │  cacompte_open_games, obtient sessionID
 │                                                       ├─ connect(host) : souscrit au même canal
 │◀──────────────────────────────────────────────────────┤  attachToHost(...) : hello chiffré
 │  welcome(log) ────────────────────────────────────────▶│  (le code ne sert qu'au chiffrement,
 │                                                             pas à l'établissement de la connexion)
```

Le code s'affiche aussi en QR (`QRCodeView`, `CIFilter.qrCodeGenerator()` — système, zéro
dépendance) et se scanne de deux façons : un scanner intégré à l'écran « Rejoindre une partie »
(`QRScannerView`, `AVCaptureMetadataOutput`), ou l'appareil photo système via le schéma d'URL
personnalisé `cacompte://join?matchID=…&code=…` (`JoinLink`). Un schéma personnalisé plutôt qu'un
lien universel `https://` : ce dernier demanderait de posséder un nom de domaine et d'y héberger
un fichier de vérification (Associated Domains/App Links) — hors de portée pour l'instant. En
échange, l'ouverture depuis l'appareil photo système n'est garantie que sur iOS (Camera propose
« Ouvrir dans Ça Compte » pour un schéma personnalisé si l'app est installée) ; le scanner intégré
reste le chemin fiable sur toutes les plateformes, y compris une future version Android. Dans les
deux cas, le QR ne remplace que la frappe des 6 chiffres, jamais une découverte physique — il n'y
en a pas avec Supabase.

Si la connexion est perdue (réseau coupé, app suspendue longtemps), l'app relance simplement une
souscription au canal — traité comme une reconnexion normale (voir plus bas), pas comme un
handoff entre transports puisqu'il n'y en a qu'un.

## Protocole applicatif

Un seul type de message, versionné, encodé en JSON compact et compressé, **identique quel que
soit le transport actif** — c'est la couche qui ne change jamais, celle que Wi-Fi et BLE
transportent aussi bien l'une que l'autre.

```swift
public struct WireMessage: Codable, Sendable {
    public let protocolVersion: Int          // 1
    public let sessionID: UUID               // la session de partage, pas la partie courante — voir « Fin de partie »
    public let kind: Kind

    public enum Kind: Codable, Sendable {
        case hello(deviceName: String, appVersion: String, platform: Platform, role: Role, deviceID: String)
        case welcome(log: [StampedEvent], role: Role)   // hôte → nouveau pair, journal complet
        case events([StampedEvent])                     // diffusion
        case matchChanged(log: [StampedEvent])           // hôte → tous les pairs déjà connectés, nouvelle partie
        case proposal([StampedEvent])                    // contributeur → hôte
        case rejection(eventID: UUID, reason: String)
        case heartbeat(lamport: UInt64)
        case goodbye
    }

    public enum Platform: String, Codable, Sendable { case apple, android }
}
```

`hello` porte `deviceID` — le même identifiant qui horodate les `StampedEvent` du pair
(`StampedEvent.deviceID`) — pour que l'hôte puisse relier « cette manche vient de X » à « X,
c'est Théo » et notifier (doc utilisateur : sans ça, une manche distante se contente de faire
monter les totaux sans expliquer pourquoi). Republié dans `LiveSession.ConnectedPeer`, `nil` tant
que le `hello` n'est pas encore arrivé.

`welcome` transporte le **journal d'événements** (`[StampedEvent]`), pas un type d'instantané
séparé : le nouveau pair appelle `MatchEngine.replay(log:)` en local, exactement comme au
lancement de l'app — une seule façon de reconstruire un `MatchState`, jamais deux. Un instantané
de partie pèse quelques kilo-octets, largement dans le budget des deux transports.

**Séquence de connexion**

```
Théo                                     Marion (hôte)
 │── connect(host) ─────────────────────▶│  transport.connect, sans le code
 │── attachToHost(…, pairingCode) ──────▶│  dérive la clé localement, envoie hello
 │── hello(role) ───────────────────────▶│
 │◀── welcome(log, role) ────────────────│  état complet, une seule fois
 │                                       │
 │◀── events([roundCommitted]) ──────────│  incréments ensuite
```

**Code erroné ou hôte injoignable — deux erreurs distinctes.** `resolveGame(code:)` échoue
immédiatement (`SupabaseTransportError.gameNotFound`) si aucune ligne `cacompte_open_games` ne
correspond au code — invalide, expiré, ou partie déjà arrêtée. Si le code résout bien une ligne
mais que la connexion n'aboutit jamais (l'hôte a arrêté le partage entre la lecture du code et la
poignée de main, par exemple), `attachToHost` attend la confirmation `welcome` avec un délai
explicite de 8 secondes avant de lever `SessionError.noResponseFromHost` — sans ce délai, l'écran
resterait sur « Connexion à la partie… » indéfiniment. `JoinTabView` distingue ces deux cas par
deux messages différents plutôt qu'un seul générique « vérifie le code », plus utile pour
distinguer un code mal saisi d'un problème de connexion en cours de poignée de main.

**Une manche saisie par un contributeur**

```
David (Android)                        Marion (hôte, iPhone)
 │── proposal([roundCommitted]) ──────▶│
 │                                     │  rules.validate(…)
 │                                     │  ├─ valide  → reduce, persiste
 │◀── events([roundCommitted]) ────────│  │           puis rediffuse à tous
 │                                     │  └─ invalide
 │◀── rejection(eventID, reason) ──────│
```

Le contributeur applique **optimistement** l'événement en local et l'annule si une `rejection`
arrive. La latence d'un aller-retour Supabase Realtime reste de l'ordre de la centaine de
millisecondes en usage normal. L'annulation ne sera visible que dans des cas pathologiques.

**L'annulation sur rejet doit vraiment annuler.** Une première version affichait le message de
rejet (`latestRejectionReason`) sans jamais retirer l'événement optimiste du journal local
(`SharedMatchModel.apply`, déduplication par id) : la manche restait affichée comme validée
malgré le rejet, donnant l'impression qu'aucune validation n'avait lieu côté hôte — alors
qu'elle avait bien lieu, seul l'affichage ne la reflétait pas. Corrigé : une `rejection` retire
l'événement du journal par son id et rejoue, ce qui annule visuellement la manche proposée.

**Reconnexion.** Un pair qui perd la connexion (poche, mise en veille, sortie de portée) relance
`discover()` et reçoit un `welcome` complet à la reconnexion. Pas de reprise incrémentale : un
instantané de partie pèse quelques kilo-octets, la complexité d'un delta ne se justifie pas.

**Départ explicite d'un pair.** `LiveSession.leave()` (pair) envoie `goodbye` puis **ferme
réellement la connexion** — les deux étapes comptent. Une première version ne fermait que la
référence locale à la session sans jamais fermer le socket sous-jacent : l'hôte ne voyait alors
jamais la connexion se terminer et continuait de lister ce pair comme connecté longtemps après
qu'il ait quitté l'écran (bug trouvé en recette sur appareils réels). Symétriquement,
`LiveSession.stopHosting()` (hôte) prévient chaque pair connecté puis ferme chaque connexion une
par une, pour la même raison.

**Fin de partie (révisé — la session survit à la partie).** Une session de partage n'est plus
liée à une seule partie : l'hôte peut enchaîner plusieurs parties (même jeu rejoué ou jeu
différent) sans jamais rompre la connexion des pairs, rouvrir le canal Realtime, ni changer de
code d'appairage. Une partie qui se conclut (fin normale, fin manuelle, abandon) diffuse son état
final aux pairs connectés, exactement comme avant, mais **n'arrête plus automatiquement le
partage** — c'était le comportement d'une première version, qui empêchait justement d'enchaîner
sur une autre partie sans se réappairer. Seul un geste explicite (« Arrêter le partage »,
`ShareSessionView`) termine désormais une session.

Techniquement, ceci sépare deux identifiants qui étaient confondus jusqu'ici : `sessionID`
(`WireMessage.sessionID`, stable pour toute la durée de la session — c'est lui qui adresse le
canal Realtime `session:<sessionID>` et la ligne `cacompte_open_games`) et `matchID`
(`MatchState.matchID`, propre à la partie affichée à un instant donné). La clé de chiffrement
(`SessionCrypto.deriveKey`) est désormais salée par `sessionID`, pas par `matchID` — sans ce
changement, l'hôte n'aurait pas pu chiffrer le message annonçant une nouvelle partie avec une clé
que le pair connaît déjà.

Quand l'hôte lance une nouvelle partie pendant qu'une session est active
(`LiveShareCoordinator.attach`), `LiveSession.switchMatch` remplace l'état arbitré et diffuse
`WireMessage.Kind.matchChanged(log:)` à tous les pairs déjà connectés — contrairement à `welcome`,
qui ne sert qu'au pair qui vient de rejoindre. Côté pair, `SharedMatchModel` repart d'un journal
vide avant de rejouer ce nouveau journal (`log` n'a aucun événement en commun avec la partie
précédente). `isHostConnected` (vraie perte de connexion) et `isConcluded` (partie terminée, dérivé
du `MatchState` affiché) restent deux notions indépendantes : `SharedMatchView` distingue toujours
« La partie est terminée. » d'une vraie coupure (« Connexion à l'hôte perdue. »), mais la première
ne déclenche plus la seconde.

Le partage lui-même (`LiveSession`/`SupabaseTransport`/le code d'appairage) est porté côté hôte par
`LiveShareCoordinator`, un singleton app-lifetime — calqué sur `MatchConnectionCoordinator` côté
pair —, plutôt que par `LiveMatchModel`, qui se recrée à chaque nouvelle partie. C'est ce
découplage qui permet à la session de survivre à la fermeture de l'écran de la partie qui l'a
démarrée.

**La feuille « Rejoindre une partie » ne doit pas se fermer par balayage.** Une fermeture
interactive contournerait `SharedMatchModel.stop()` (qui appelle `LiveSession.leave()`) : la
connexion resterait ouverte sans que l'hôte ne le voie jamais — le même bug de pair fantôme déjà
corrigé pour un vrai tap sur « Quitter », mais par un autre chemin. `.interactiveDismissDisabled(true)`
force le passage par le bouton, qui nettoie correctement.

**Départ de l'hôte.** La partie n'est pas perdue : chaque pair détient l'état complet en
mémoire. L'interface propose « Reprendre la partie sur cet appareil », ce qui crée une copie
locale persistée sous un nouveau `matchID`. Pas d'élection automatique — un choix explicite est
plus simple et plus prévisible qu'une bascule silencieuse.

## Horloge de Lamport

Chaque pair maintient un compteur :

- à chaque événement émis : `lamport += 1` ;
- à chaque événement reçu : `lamport = max(lamport, reçu) + 1`.

Le tri final se fait sur `(lamport, deviceID)`. `deviceID` est un `UUID` stable stocké de façon
durable et privée (Trousseau iOS, Keystore/`EncryptedSharedPreferences` Android), ce qui
départage de façon déterministe et identique sur tous les pairs — condition nécessaire pour que
le rejeu converge, quelle que soit la plateforme de chaque pair.

L'horloge murale (`occurredAt`) n'entre jamais dans l'ordonnancement. Deux appareils n'ont pas la
même heure, et un utilisateur peut changer la sienne en cours de partie.

## Appairage et chiffrement

MultipeerConnectivity et Nearby Connections chiffraient le transport pour nous, gratuitement.
Supabase Realtime chiffre le transport en son sein (TLS), mais pas pour l'application elle-même
— Supabase, en tant qu'opérateur du service, pourrait techniquement lire un message non chiffré
au niveau applicatif. Cette couche de chiffrement de bout en bout doit donc exister
indépendamment du transport, et reste inchangée depuis avant Supabase :

- L'hôte génère un **code d'appairage à 6 chiffres** à la création du partage (affiché en clair,
  aussi encodé dans un QR avec le `matchID` pour éviter la saisie).
- Le pair qui rejoint saisit ou scanne ce code **avant** toute connexion.
- Les deux côtés dérivent localement, par HKDF, une clé de session AES-GCM à partir du code —
  **le code ne transite jamais sur le réseau**, seul son résultat (la capacité à déchiffrer)
  prouve qu'on le connaît.
- Chaque `WireMessage` est chiffré avec cette clé avant émission, transporté par Supabase Realtime
  sans jamais être lisible par lui. `CryptoKit` côté Apple, `javax.crypto` (AES/GCM, HMAC pour
  HKDF) côté Android — aucune dépendance tierce supplémentaire pour cette couche.

Le code d'appairage est **le** geste d'invitation : l'hôte l'affiche, le pair le saisit ou le
scanne, identique sur Apple et Android.

## Configuration requise

**Apple** — aucune entitlement réseau local ni Bluetooth : Supabase Realtime est un client
HTTPS/WebSocket standard, qu'iOS ne soumet à aucune clé `Info.plist` particulière. Vérifié
directement dans `App/Info.plist` : ni `NSLocalNetworkUsageDescription`, ni `NSBonjourServices`,
ni `NSBluetoothAlwaysUsageDescription` n'y figurent plus. `NSCameraUsageDescription` reste
présente, mais pour le scanner de QR (`QRScannerView`), sans rapport avec le transport.

**Android** — permission `INTERNET` (permission normale, accordée à l'installation, sans invite
à l'exécution). Aucune permission Bluetooth ou Wi-Fi n'est nécessaire.

## Sécurité et vie privée

- Chaque `WireMessage` est chiffré de bout en bout par la clé dérivée du code d'appairage (voir
  plus haut) — ni Supabase, ni personne d'autre en possession du trafic, ne peut lire le contenu
  d'une manche sans connaître ce code.
- Aucune donnée personnelle transmise hors de la partie en cours : seuls les pseudos des
  participants circulent (`Participant` ne porte ni avatar ni photo, uniquement `id`,
  `displayName`, `seatIndex`, `teamID`) — jamais la liste complète des fiches joueurs, les
  avatars, ni l'historique.
- L'invitation reste explicite des deux côtés — l'hôte affiche le code, le pair le saisit ou le
  scanne.
- La clé Supabase embarquée dans le client est la clé **anon/publique**, conçue pour être
  distribuée (protégée par les politiques RLS de `cacompte_open_games`, pas par le secret) — le
  contenu des manches reste protégé par le chiffrement de bout en bout ci-dessus, pas par cette
  clé.

## Dégradation

La partie partagée est un **supplément**, jamais un prérequis. Si Supabase est inaccessible —
aucune connexion Internet, service indisponible — l'écran de partie fonctionne à l'identique en
solo. Aucun chemin de code du moteur ni de la persistance ne dépend de `Sync`, ce que garantit le
graphe de dépendances du package : `Store` n'importe pas `Sync`.

## Tests

- **Sans réseau** : deux instances de `LiveSession` reliées par un transport en mémoire
  (`InMemoryTransport`, un troisième cas du protocole `Transport`, réservé aux tests). Couvre
  convergence, idempotence, ordre inversé, doublons — indépendant du transport réellement actif.
  10 tests, `CaCompteKit/Tests/SyncTests`. `SupabaseTransport` lui-même n'est pas exercé
  directement par cette suite (voir [15](15-plan-qualite-code.md)) — la vérification de sa
  logique de concurrence repose sur la compilation Swift 6 stricte, pas sur une exécution testée.
- **Propriété testée** : pour tout journal `L` et toute permutation `σ`,
  `replay(L) == replay(σ(L))`. Vérifiée sur des permutations aléatoires via des tests
  paramétrés Swift Testing (et Kotest côté Android).
- **Usage réel** : plusieurs comportements de `SupabaseTransport` ont été trouvés et corrigés en
  usage réel plutôt qu'anticipés à l'écriture — une reconnexion sous-jacente du SDK après une
  mise en arrière-plan prolongée ne retrace pas la présence automatiquement (corrigé par un
  ré-enregistrement à chaque `.subscribed`), et une course entre l'événement de présence et le
  premier message d'un pair pouvait perdre son `hello` (corrigé en créant la session au premier
  des deux événements, quel que soit l'ordre). Ces deux correctifs sont documentés en commentaire
  dans `SupabaseTransport.swift`.
- **Sur appareil réel** : partie partagée créée et suivie avec succès par l'auteur du projet.
  Reste à faire : une check-list détaillée par rôle (observateur/contributeur), comme celle qui
  avait validé l'ancien transport Wi-Fi, et la recette croisée Apple/Android une fois le portage
  entamé.
- **Golden du protocole** (`spec/wire/`) : ✅ fait — un fichier par cas de `WireMessage.Kind`,
  vérifié côté Swift (`WireGoldenTests`, `CaCompteKit/Tests/SyncTests`). Le critère n'est pas une
  identité d'octets, hors de portée entre deux sérialiseurs JSON différents, mais un round-trip de
  schéma : décoder une fixture sur les deux plateformes doit produire une valeur équivalente. Le
  test Kotlin symétrique reste à écrire une fois le portage Android entamé.
