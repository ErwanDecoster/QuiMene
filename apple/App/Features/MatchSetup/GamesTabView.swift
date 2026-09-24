import Catalog
import DesignSystem
import Domain
import Store
import SwiftData
import SwiftUI

/// Onglet « Jeux » — point de départ d'une partie (doc 05 : le catalogue s'élargit au fil des
/// phases, cette liste ne fige donc aucun jeu en particulier). `MatchSetupView` reste présentée
/// en feuille : elle porte sa propre `NavigationStack` et ses actions Annuler/Commencer, un
/// `NavigationLink` imbriquerait deux piles de navigation.
struct GamesTabView: View {
  @Environment(\.modelContext) private var modelContext
  @Environment(DeepLinkRouter.self) private var deepLinkRouter
  @Query(sort: \PlayerRecord.sortIndex) private var allPlayers: [PlayerRecord]
  @State private var searchText = ""
  @FocusState private var isSearchFocused: Bool
  @State private var currentScrollOffset: CGFloat = 0
  @State private var hasTriggeredSearchFromPull = false
  @State private var selectedDefinition: GameDefinition?
  /// `String` (l'id du jeu) plutôt que `GameDefinition` — `.navigationDestination(item:)`
  /// exige `Hashable`, que `GameDefinition` n'a pas besoin de porter par ailleurs.
  @State private var leaderboardGameID: String?
  @State private var activeMatch: MatchRecord?
  /// Doc utilisateur — remontée : rien n'empêche de démarrer plusieurs parties sans terminer la
  /// précédente ; toutes doivent apparaître ici, pas seulement la première trouvée.
  @State private var inProgressMatches: [MatchRecord] = []
  @State private var matchPendingAbandon: MatchRecord?
  @State private var showsNoMailClientAlert = false
  /// Doc 09 « Fin de partie » — une session de partage démarrée depuis une partie survit à sa
  /// fin (`LiveShareCoordinator`) : ce bouton laisse l'hôte la retrouver (code, pairs connectés,
  /// « Arrêter le partage ») même en revenant ici entre deux parties, sans avoir à en rouvrir
  /// une pour y accéder.
  @State private var isPresentingActiveShare = false

  /// Distance de glissement du doigt (pas du décalage de contenu) avant d'activer la
  /// recherche. Un tiré volontaire depuis le haut, quand la barre de recherche est déjà
  /// affichée, ne produit aucun décalage de défilement exploitable — le tiroir système de
  /// `.searchable` semble absorber le geste avant qu'il n'atteigne le défilement de la liste.
  /// On suit donc directement le doigt, indépendamment du défilement.
  private static let pullToSearchThreshold: CGFloat = 40

  private var catalog: GameCatalog { .embedded }
  private var activePlayers: [PlayerRecord] { allPlayers.filter { !$0.isArchived } }

  private var games: [GameDefinition] {
    let all = catalog.allGames.sorted { $0.name.localized < $1.name.localized }
    guard !searchText.isEmpty else { return all }
    return all.filter {
      $0.name.matches(searchText) || ($0.shortDescription?.matches(searchText) ?? false)
    }
  }

  // Doc utilisateur — le vérificateur de types de Swift met un temps déraisonnable à résoudre
  // une seule très longue chaîne de modificateurs SwiftUI ; scindée en deux (`listView` /
  // `withNavigationHandling`) pour que chaque moitié reste vérifiable indépendamment plutôt
  // qu'en une seule expression géante (même raison que `gameRow`/`leaderboardSwipeAction`).
  var body: some View {
    NavigationStack {
      withNavigationHandling(listView)
    }
  }

  private var listView: some View {
    List {
      // Doc 16, phase A — la partie suivie chez quelqu'un d'autre vit dans l'écran Rejoindre,
      // qu'on peut fermer sans la quitter : ce bandeau est le chemin du retour.
      if MatchConnectionCoordinator.shared.sharedModel != nil, searchText.isEmpty {
        Section {
          Button {
            deepLinkRouter.isPresentingJoin = true
          } label: {
            Label("Partie partagée en cours · Reprendre", systemImage: "dot.radiowaves.left.and.right")
              .font(.bodyText)
              .foregroundStyle(.brandInk)
          }
        }
      }

      // Doc 01 : reprendre une partie en cours reste possible, mais en simple
      // suggestion — un onglet qu'on revisite pour parcourir le catalogue ne doit pas
      // y être redirigé de force à chaque fois. Masquée pendant une recherche active
      // (remontée : elle restait sinon affichée quel que soit le terme cherché, et
      // masquait même le message « aucun résultat » ci-dessous).
      if !inProgressMatches.isEmpty, searchText.isEmpty {
        Section {
          ForEach(inProgressMatches, id: \.id) { match in
            Button {
              activeMatch = match
            } label: {
              resumeRow(for: match)
            }
            .buttonStyle(.plain)
            .swipeActions {
              Button("Abandonner", role: .destructive) {
                matchPendingAbandon = match
              }
            }
          }
        }
      }

      if !games.isEmpty {
        Section {
          ForEach(games) { definition in
            gameRow(for: definition)
          }
        }
      } else {
        EmptyState(
          icon: "magnifyingglass",
          message: "Aucun jeu ne correspond à ta recherche.",
          actionTitle: "Demander ce jeu",
          action: requestGame
        )
        .listRowSeparator(.hidden)
      }
    }
    .navigationTitle("Jeux")
    .toolbar {
      ToolbarItem(placement: .topBarLeading) {
        Button {
          deepLinkRouter.isPresentingJoin = true
        } label: {
          Label("Rejoindre une partie", systemImage: "qrcode.viewfinder")
        }
      }
      if LiveShareCoordinator.shared.isSharing {
        ToolbarItem(placement: .topBarTrailing) {
          Button {
            isPresentingActiveShare = true
          } label: {
            Label("Session partagée en cours", systemImage: "wifi.circle.fill")
          }
        }
      }
    }
    .searchable(
      text: $searchText,
      placement: .navigationBarDrawer(displayMode: .automatic),
      prompt: "Rechercher un jeu"
    )
    .searchFocused($isSearchFocused)
    // Doc utilisateur : reproduire le geste de l'écran d'accueil (tiré vers le bas ->
    // recherche activée). `.onScrollGeometryChange` sert uniquement à savoir si on est
    // déjà en haut de la liste — le déclenchement lui-même suit le doigt directement via
    // `simultaneousGesture` ci-dessous, pas le décalage de défilement (qui n'évolue pas
    // de façon exploitable quand on tire depuis le repos avec la barre déjà visible).
    .onScrollGeometryChange(for: CGFloat.self) { geometry in
      geometry.contentOffset.y
    } action: { _, offsetY in
      currentScrollOffset = offsetY
    }
    .simultaneousGesture(
      DragGesture(minimumDistance: 10)
        .onChanged { value in
          guard currentScrollOffset <= 5, !hasTriggeredSearchFromPull else { return }
          if value.translation.height > Self.pullToSearchThreshold {
            hasTriggeredSearchFromPull = true
            isSearchFocused = true
          }
        }
        .onEnded { _ in
          hasTriggeredSearchFromPull = false
        }
    )
    .sheet(item: $selectedDefinition) { definition in
      MatchSetupView(definition: definition, availablePlayers: activePlayers, context: modelContext)
      { match in
        selectedDefinition = nil
        activeMatch = match
      }
    }
    .sheet(isPresented: $isPresentingActiveShare) {
      ShareSessionView(startAction: nil)
    }
    .gameRequestMailFallback(isPresented: $showsNoMailClientAlert)
  }

  private func requestGame() {
    if !GameRequestMail.open(searchTerm: searchText) {
      showsNoMailClientAlert = true
    }
  }

  // Doc utilisateur — Handoff et « Reprends » (Live Activity) arrivent ici,
  // potentiellement alors qu'on est sur un autre onglet ; `DeepLinkRouter` fait le pont depuis
  // `.onContinueUserActivity`/`.onOpenURL` (QuiMeneApp). Le lien `quimene://join` est consommé
  // par `JoinTabView`, pas ici (doc utilisateur — onglet dédié). `QuiMeneApp.selectedTab`
  // garantit que cet onglet est déjà construit quand l'un de ces événements arrive — reste à le
  // consommer, ici et dans `.onAppear` ci-dessous pour le cas où il était déjà en attente au
  // moment du montage.
  private func withNavigationHandling<Content: View>(_ content: Content) -> some View {
    content
      .onChange(of: deepLinkRouter.pendingContinuedMatchID) { _, _ in consumePendingDeepLinks() }
      .onChange(of: deepLinkRouter.wantsResume) { _, _ in consumePendingDeepLinks() }
      .navigationDestination(item: $activeMatch) { match in
        MatchPlayView(match: match, context: modelContext, catalog: catalog)
      }
      .navigationDestination(item: $leaderboardGameID) { gameID in
        GameLeaderboardView(gameID: gameID, gameName: gameName(forGameID: gameID))
      }
      .onAppear {
        refreshInProgressMatches()
        consumePendingDeepLinks()
      }
      .onChange(of: activeMatch) { oldValue, newValue in
        // La partie ouverte via la bannière peut s'être terminée (ou une autre abandonnée)
        // entre-temps : on rafraîchit dès le retour à la liste plutôt que de garder une
        // référence figée (sinon la bannière reste affichée indéfiniment après coup).
        if newValue == nil, oldValue != nil {
          refreshInProgressMatches()
        }
      }
      .confirmationDialog(
        "Abandonner cette partie ?",
        isPresented: Binding(
          get: { matchPendingAbandon != nil }, set: { if !$0 { matchPendingAbandon = nil } }),
        titleVisibility: .visible
      ) {
        Button("Abandonner", role: .destructive) {
          if let match = matchPendingAbandon {
            _ = try? MatchRepository(context: modelContext).abandonMatch(match, catalog: catalog)
          }
          matchPendingAbandon = nil
          refreshInProgressMatches()
        }
      } message: {
        Text(
          "La partie sera classée comme abandonnée dans l'historique, avec le classement atteint jusque-là. Cette action ne peut pas être annulée."
        )
      }
  }

  /// Doc utilisateur — un seul point qui vérifie les déclencheurs possibles
  /// (`DeepLinkRouter`) et agit sur ceux effectivement en attente ; appelé aussi bien depuis
  /// chaque `.onChange` (nouvel événement pendant que cet onglet est déjà affiché) que depuis
  /// `.onAppear` (événement déjà arrivé avant que cet onglet n'existe).
  private func consumePendingDeepLinks() {
    if let matchID = deepLinkRouter.pendingContinuedMatchID {
      deepLinkRouter.pendingContinuedMatchID = nil
      activeMatch = try? MatchRepository(context: modelContext).match(withID: matchID)
    }
    if deepLinkRouter.wantsResume {
      deepLinkRouter.wantsResume = false
      refreshInProgressMatches()
      // Doc utilisateur — Live Activity « Reprends » : sans précision de laquelle,
      // reprend la plus récemment démarrée (déjà l'ordre de `inProgressMatches`).
      if let mostRecent = inProgressMatches.first {
        activeMatch = mostRecent
      }
    }
  }

  private func refreshInProgressMatches() {
    let repository = MatchRepository(context: modelContext)
    inProgressMatches = (try? repository.inProgressMatches()) ?? []
  }

  private func resumeRow(for match: MatchRecord) -> some View {
    HStack(spacing: Space.md) {
      Image(systemName: "arrow.clockwise.circle.fill")
        .font(.system(size: IconSize.lg))
        .foregroundStyle(.brandBrass)
        .frame(width: 32)
      VStack(alignment: .leading, spacing: Space.xxs) {
        Text("Reprendre la partie en cours").font(.h6).foregroundStyle(.textPrimary)
        Text(gameName(for: match)).font(.bodySmall).foregroundStyle(.textSecondary)
      }
      Spacer(minLength: 0)
    }
    .padding(.vertical, Space.xs)
    .frame(maxWidth: .infinity, alignment: .leading)
    .contentShape(Rectangle())
    // Doc 08 « Accessibilité » — même principe que `ScoreBoardView`, forme composée ici (pas de
    // score à afficher pour une ligne de reprise).
    .accessibilityElement(children: .combine)
    .accessibilityLabel("Reprendre la partie en cours, \(gameName(for: match))")
  }

  private func gameName(for match: MatchRecord) -> String {
    (try? catalog.definition(for: match.gameID, version: match.rulesVersion))?.name.localized
      ?? match.gameID
  }

  private func gameName(forGameID gameID: String) -> String {
    catalog.allGames.first { $0.id == gameID }?.name.localized ?? gameID
  }

  // Doc utilisateur — remontée : le bouton trophée à côté de chaque ligne rendait la liste
  // encombrée (deux cibles de tap par jeu, « pas idéal »). Un swipe pour révéler « Meilleurs
  // joueurs » libère la ligne pour son seul rôle (démarrer une partie), sans faire disparaître
  // l'accès au classement. Extrait en fonctions dédiées (plutôt qu'en ligne dans le `ForEach`) :
  // au-delà d'un certain nombre de modificateurs chaînés, le vérificateur de types de Swift met
  // un temps déraisonnable à résoudre une seule grosse expression.
  private func gameRow(for definition: GameDefinition) -> some View {
    Button {
      selectedDefinition = definition
    } label: {
      row(for: definition)
    }
    .buttonStyle(.plain)
    .swipeActions(edge: .trailing) {
      leaderboardSwipeAction(for: definition)
    }
  }

  private func leaderboardSwipeAction(for definition: GameDefinition) -> some View {
    Button {
      leaderboardGameID = definition.id
    } label: {
      Label("Meilleurs joueurs", systemImage: "trophy")
    }
    .tint(.brandBrass)
  }

  private func row(for definition: GameDefinition) -> some View {
    HStack(spacing: Space.md) {
      Image(systemName: definition.symbol)
        .font(.system(size: IconSize.lg))
        .foregroundStyle(.brandInk)
        .frame(width: 32)
      VStack(alignment: .leading, spacing: Space.xxs) {
        Text(definition.name.localized).font(.h6).foregroundStyle(.textPrimary)
        if let description = definition.shortDescription?.localized {
          Text(description).font(.bodySmall).foregroundStyle(.textSecondary)
        }
      }
      Spacer(minLength: 0)
    }
    .padding(.vertical, Space.xs)
    .frame(maxWidth: .infinity, alignment: .leading)
    .contentShape(Rectangle())
    // Doc 08 « Accessibilité » — même principe que `ScoreBoardView`.
    .accessibilityElement(children: .combine)
    .accessibilityLabel(
      definition.shortDescription.map { "\(definition.name.localized), \($0.localized)" }
        ?? definition.name.localized
    )
  }
}
