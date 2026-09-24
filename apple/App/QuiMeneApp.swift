import Catalog
import Domain
import Store
import SwiftData
import SwiftUI

/// Doc utilisateur — onglets adressables par un deep link (`QuiMeneApp.selectedTab`) : sans ça,
/// un lien `quimene://`/Handoff qui arrive alors que l'onglet visé n'est pas actif
/// pouvait rester sans effet visible — `TabView` ne construit un onglet non sélectionné qu'à la
/// demande, la vue cible n'existait donc pas encore pour recevoir l'événement (remontée
/// utilisateur : « le scan du QR code ouvre bien l'application mais rien ne se passe »).
private enum AppTab: Hashable {
  case players, games, history, profile
}

@main
struct QuiMeneApp: App {
  private let settings = AppSettings()
  private let deepLinkRouter = DeepLinkRouter.shared
  /// Doc utilisateur P9 — seul rôle : exister dès le lancement (même patron que
  /// `deepLinkRouter`) pour reprendre une éventuelle partie partagée en cours sans attendre que
  /// l'utilisateur rouvre l'écran de partage (voir doc `MatchConnectionCoordinator`). Plus besoin
  /// d'un `AppDelegate` dédié depuis le passage à Supabase Realtime (l'ancien rôle — créer tôt le
  /// `CBCentralManager` pour la restauration d'état BLE — n'existe plus).
  private let matchConnectionCoordinator = MatchConnectionCoordinator.shared
  @State private var container: ModelContainer?
  @State private var selectedTab: AppTab = .players
  @Environment(\.scenePhase) private var scenePhase

  var body: some Scene {
    WindowGroup {
      Group {
        if let container {
          TabView(selection: $selectedTab) {
            PlayersListView()
              .tabItem { Label("Joueurs", systemImage: "person.2.fill") }
              .tag(AppTab.players)
            GamesTabView()
              .tabItem { Label("Jeux", systemImage: "die.face.5.fill") }
              .tag(AppTab.games)
            HistoryListView(context: container.mainContext, catalog: .embedded)
              .tabItem { Label("Historique", systemImage: "clock.arrow.circlepath") }
              .tag(AppTab.history)
            // Doc 16, phase A — Profil remplace l'onglet Rejoindre, devenu un écran plein
            // écran (ci-dessous) ouvert depuis Jeux, Profil, un lien ou un QR système.
            ProfileTabView()
              .tabItem { Label("Profil", systemImage: "person.crop.circle") }
              .tag(AppTab.profile)
          }
          .fullScreenCover(
            isPresented: Binding(
              get: { deepLinkRouter.isPresentingJoin },
              set: { deepLinkRouter.isPresentingJoin = $0 })
          ) {
            JoinTabView()
          }
          .modifier(ProfileRequirement())
          .environment(settings)
          .environment(deepLinkRouter)
          .modelContainer(container)
          // Doc 14 « Profils partagés », phase 2 — pousse/récupère les résumés en
          // attente dès que le conteneur est prêt, puis à chaque retour au premier
          // plan (voir `.onChange(of: scenePhase)` plus bas) : même déclencheur que
          // `MatchConnectionCoordinator`, pas de minuteur propre à inventer.
          .task {
            try? PlayerRepository(context: container.mainContext).resolveDuplicateOwnProfiles()
            let repository = MatchRepository(context: container.mainContext)
            MatchLiveActivityController.reconcileOnLaunch { matchID in
              guard let match = try? repository.match(withID: matchID) else { return false }
              return match.statusRaw == "inProgress" || match.statusRaw == "finalRound"
            }
            await SharedProfileSyncCoordinator.shared.sync(context: container.mainContext)
          }
        } else {
          ProgressView()
            .task {
              container = await Self.loadContainer(iCloudSyncEnabled: settings.iCloudSyncEnabled)
            }
        }
      }
      // Doc utilisateur — code d'appairage scanné par l'appareil photo système (schéma
      // `quimene://`, doc 09) : `DeepLinkRouter` fait le pont jusqu'à `JoinTabView`,
      // potentiellement affichée sur un autre onglet au moment où le lien s'ouvre.
      .onOpenURL { url in
        // Doc utilisateur — Live Activity (P9) : tap sur l'écran verrouillé ou la Dynamic
        // Island (`quimene://resume`, posé par `MatchLiveActivityWidget.widgetURL`).
        if url.host == "resume" {
          // Doc 16, phase A — un pair suit sa partie dans l'écran Rejoindre, plus dans un
          // onglet : c'est lui qu'il faut rouvrir, pas la partie locale la plus récente.
          if matchConnectionCoordinator.sharedModel != nil {
            deepLinkRouter.isPresentingJoin = true
          } else {
            deepLinkRouter.wantsResume = true
          }
          return
        }
        guard let payload = JoinLink.parse(url) else { return }
        deepLinkRouter.pendingJoin = payload
      }
      .onChange(of: scenePhase) { _, newPhase in
        if newPhase == .active, let container {
          try? PlayerRepository(context: container.mainContext).resolveDuplicateOwnProfiles()
          Task { await SharedProfileSyncCoordinator.shared.sync(context: container.mainContext) }
        }
      }
      // Doc utilisateur « Handoff » (P9) — reprise sur un autre appareil connecté au même
      // compte iCloud : même pont que `.onOpenURL` ci-dessus, jusqu'à `GamesTabView`.
      .onContinueUserActivity(MatchContinuation.activityType) { activity in
        guard let matchID = MatchContinuation.matchID(from: activity) else { return }
        deepLinkRouter.pendingContinuedMatchID = matchID
      }
      // Doc utilisateur — chacun de ces déclencheurs (lien, Handoff, Live Activity « Reprends »,
      // classement → historique) vise un onglet précis. Posé ici (le `Group`
      // racine, toujours monté dès le lancement) plutôt que dans la vue cible : c'est
      // justement ce qui manquait pour que l'onglet soit *construit* à temps.
      .onChange(of: deepLinkRouter.pendingJoin) { _, newValue in
        if newValue != nil { deepLinkRouter.isPresentingJoin = true }
      }
      .onChange(of: deepLinkRouter.pendingContinuedMatchID) { _, newValue in
        if newValue != nil { selectedTab = .games }
      }
      .onChange(of: deepLinkRouter.wantsResume) { _, newValue in
        if newValue { selectedTab = .games }
      }
      .onChange(of: deepLinkRouter.pendingHistoryGameID) { _, newValue in
        if newValue != nil { selectedTab = .history }
      }
      .onChange(of: deepLinkRouter.wantsProfileTab) { _, newValue in
        guard newValue else { return }
        deepLinkRouter.wantsProfileTab = false
        selectedTab = .profile
      }
    }
  }

  /// Doc 03 : la première activation de CloudKit (création des zones, poussée du schéma) peut
  /// prendre plusieurs secondes — hors du thread principal pour ne jamais figer le premier
  /// écran pendant ce temps (un blocage synchrone ici se lisait comme une page blanche
  /// indéfinie, pas comme un chargement). Si CloudKit échoue à s'initialiser (compte
  /// indisponible, container mal provisionné, réseau absent), on retombe sur un stockage
  /// local : « un utilisateur qui refuse iCloud garde une app pleinement fonctionnelle »
  /// s'applique aussi si iCloud est coché mais indisponible.
  /// Doc utilisateur — le store vit dans le conteneur App Group (`SharedStore`) quand il est
  /// disponible, plutôt qu'à l'emplacement par défaut. Conservé tel quel après le retrait du
  /// widget d'écran d'accueil (P9, plus rien ne lit ce store hors de l'app) pour ne pas migrer
  /// l'emplacement des données des installations existantes — changer d'emplacement de store
  /// sans migration ferait apparaître les parties et joueurs déjà enregistrés comme perdus.
  private nonisolated static func configuration(
    schema: Schema, cloudKitDatabase: ModelConfiguration.CloudKitDatabase
  ) -> ModelConfiguration {
    guard let sharedURL = SharedStore.storeURL else {
      return ModelConfiguration(schema: schema, cloudKitDatabase: cloudKitDatabase)
    }
    return ModelConfiguration(schema: schema, url: sharedURL, cloudKitDatabase: cloudKitDatabase)
  }

  /// Doc utilisateur (audit qualité, 15) — trois paliers, du meilleur au pire, aucun ne plante :
  /// container CloudKit si demandé, sinon container local sur disque, sinon un container en
  /// mémoire (perte de la persistance pour la session, mais l'app s'ouvre quand même plutôt que
  /// de planter en boucle à chaque lancement sur un store disque corrompu — écriture interrompue,
  /// disque plein). Le dernier repli n'a plus besoin de `try!` documenté comme un risque : un
  /// store en mémoire fraîchement créé, sans plan de migration à appliquer, ne peut pas échouer
  /// en pratique.
  /// Doc utilisateur (audit qualité, 15, Phase C) — `QuiMeneUITests` a besoin d'un magasin
  /// propre à chaque *test*, mais qui survive un `terminate()`/relance *au sein* d'un même test
  /// (parcours n°3, reprise après relance). Ni le magasin réel (s'accumule d'un lancement à
  /// l'autre, jamais réinitialisé entre deux `xcodebuild test`, jusqu'à ce qu'une fiche
  /// fraîchement créée sorte de l'écran visible) ni un magasin en mémoire pur (perdu au premier
  /// `terminate()`, casserait justement le parcours qu'il s'agit de vérifier) ne conviennent
  /// seuls. `-uitesting-reset` efface le fichier dédié avant de l'ouvrir (premier lancement d'un
  /// test) ; `-uitesting` seul l'ouvre tel quel (relance dans le même test) — les deux passent
  /// par le même fichier sur disque, jamais celui de l'utilisateur réel.
  private nonisolated static var isUITesting: Bool {
    ProcessInfo.processInfo.arguments.contains("-uitesting")
      || ProcessInfo.processInfo.arguments.contains("-uitesting-reset")
  }

  /// Doc utilisateur — Phase C (`QuiMeneTests`) : une cible de tests unitaires *hébergée*
  /// (`TEST_HOST`) injecte le bundle XCTest dans le vrai process de l'app, qui démarre donc
  /// normalement — y compris sa tentative de container CloudKit réel, indisponible en
  /// CI/simulateur sans compte iCloud connecté. L'échec en cascade qui en résultait faisait
  /// planter des `ModelContainer` de test sans rapport (état SwiftData partagé au niveau du
  /// process). `XCTestConfigurationFilePath` est posé par XCTest sur tout process hôte d'un
  /// bundle de test injecté — contrairement à `QuiMeneUITests`, qui lance `QuiMene.app` comme
  /// une app normale via `XCUIApplication` (jamais injectée), donc jamais concernée par cet
  /// indicateur ni par cette branche.
  private nonisolated static var isUnitTestHost: Bool {
    ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
  }

  private nonisolated static var uiTestingStoreURL: URL {
    URL.applicationSupportDirectory.appending(path: "QuiMeneUITesting.store")
  }

  private static func loadContainer(iCloudSyncEnabled: Bool) async -> ModelContainer {
    await Task.detached(priority: .userInitiated) {
      let schema = Schema(QuiMeneSchemaV1.models)
      if isUITesting {
        let url = uiTestingStoreURL
        if ProcessInfo.processInfo.arguments.contains("-uitesting-reset") {
          try? FileManager.default.removeItem(at: url)
        }
        return try! ModelContainer(
          for: schema, configurations: [ModelConfiguration(schema: schema, url: url)])
      }
      if isUnitTestHost {
        // Doc utilisateur — `cloudKitDatabase` explicite à `.none` : sans lui, le réglage par
        // défaut (`.automatic`) tente quand même CloudKit dans ce process précis, puisque
        // l'entitlement iCloud du host (`QuiMene.app`) est bien réel, contrairement à un
        // magasin en mémoire construit dans un exécutable de test non hébergé (`StoreTests`),
        // sans entitlement, où `.automatic` ne tente jamais rien.
        return try! ModelContainer(
          for: schema,
          configurations: [
            ModelConfiguration(schema: schema, isStoredInMemoryOnly: true, cloudKitDatabase: .none)
          ])
      }
      if iCloudSyncEnabled,
        let cloudContainer = try? ModelContainer(
          for: schema,
          migrationPlan: QuiMeneMigrationPlan.self,
          configurations: [
            configuration(schema: schema, cloudKitDatabase: .private("iCloud.com.quimene.app"))
          ]
        )
      {
        return cloudContainer
      }
      if let localContainer = try? ModelContainer(
        for: schema,
        migrationPlan: QuiMeneMigrationPlan.self,
        configurations: [configuration(schema: schema, cloudKitDatabase: .none)]
      ) {
        return localContainer
      }
      return try! ModelContainer(
        for: schema, configurations: [ModelConfiguration(isStoredInMemoryOnly: true)])
    }.value
  }
}
