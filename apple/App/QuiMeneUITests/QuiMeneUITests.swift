import XCTest

/// Doc 10 « Tests d'interface » — Swift Testing partout ailleurs, XCTest ici : c'est la
/// contrainte du framework XCUITest, pas un choix. Les 3 parcours ci-dessous sont ceux que le
/// doc désigne comme critiques ; le troisième (reprise après relance) est le plus important —
/// c'est le scénario « soirée perdue » de la vision produit.
@MainActor
final class QuiMeneUITests: XCTestCase {
  /// Doc utilisateur (audit qualité, 15) — remontée en écrivant ces tests : quand plusieurs
  /// méthodes tournent dans la même invocation `xcodebuild test`, la toute première ne respecte
  /// pas toujours `-AppleLanguages (fr)`/`-AppleLocale fr_FR` à son premier lancement (l'écran
  /// affiche « Add a player » au lieu de « Ajouter un joueur ») alors que les lancements
  /// suivants, dans la même invocation, l'appliquent correctement — vraisemblablement une
  /// course avec l'installation initiale de l'app sur le simulateur. Un cycle lancement/arrêt
  /// « à blanc » avant toute méthode de test (une fois par classe, pas par test) fait passer
  /// cette installation avant que la logique des tests ne s'appuie sur la langue forcée.
  override class func setUp() {
    super.setUp()
    // Doc utilisateur — les rappels de cycle de vie `XCTestCase` (dont ce `class func setUp()`,
    // hérité `nonisolated`) tournent déjà sur le fil principal ; `assumeIsolated` l'affirme au
    // compilateur sans re-sauter de fil, `XCUIApplication` exigeant `@MainActor`.
    MainActor.assumeIsolated {
      let app = XCUIApplication()
      app.launchArguments += [
        "-AppleLanguages", "(fr)", "-AppleLocale", "fr_FR", "-uitesting-reset",
      ]
      app.launch()
      app.terminate()
    }
  }

  override func setUpWithError() throws {
    continueAfterFailure = false
  }

  // MARK: - Parcours 1 : créer un joueur, le retrouver dans la liste

  func testCreatePlayerAppearsInList() throws {
    let app = launchedApp()

    let nickname = uniqueName("Alice")
    createPlayer(named: nickname, in: app)

    XCTAssertTrue(
      app.staticTexts[nickname].waitForExistence(timeout: 5),
      "Le joueur créé devrait apparaître dans la liste des joueurs.")
  }

  // MARK: - Parcours 2 : partie de Skyjo à 3 joueurs jusqu'aux résultats

  /// Doc utilisateur (audit qualité, 15) — **bloqué**, pas écrit : la mise en place (créer 3
  /// joueurs, ouvrir Skyjo, les sélectionner, démarrer) fonctionne de façon fiable et reste
  /// ci-dessous. La saisie des manches, elle, bute systématiquement sur la même ligne de
  /// `ScoreBoardView` (la dernière visible à l'écran, juste au-dessus du clavier) : un tap
  /// synthétisé dessus n'y déplace jamais le focus clavier, quel que soit le joueur qui s'y
  /// trouve après retri par rang — confirmé par `app.debugDescription` à chaque tentative (le
  /// focus restait sur le champ précédent, sans qu'aucune erreur ne remonte au moment du tap
  /// lui-même). Six stratégies essayées, dans l'ordre, toutes identiquement bloquées sur cette
  /// même ligne :
  /// 1. Tap par élément (`XCUIElement.tap()`) sur le `TextField`, indexé par position.
  /// 2. Double tap sur le même élément.
  /// 3. Tap par coordonnées (`coordinate(withNormalizedOffset:).tap()`) sur le centre du champ.
  /// 4. Ciblage dynamique du premier champ à `value == nil` plutôt qu'un index fixe (élimine
  ///    l'hypothèse d'un recyclage de cellule `List` — l'élément existait bien, au bon endroit).
  /// 5. Tap sur le nom du joueur (`Text` de la ligne, cible bien plus grande que le `TextField`
  ///    de 64×22 pt) plutôt que sur le champ — la ligne entière porte `.onTapGesture` avec la
  ///    même liaison de focus (`ScoreBoardView`) : même échec.
  /// 6. `swipeDown()` pour fermer le clavier avant de retaper sur cette ligne à écran plein
  ///    (élimine l'hypothèse d'un chevauchement clavier/dernière ligne) : même échec après 278 s.
  ///
  /// Hypothèse la plus probable, non confirmée : une limite d'automatisation propre à
  /// `List` + `.focused()` + clavier `.numberPad` sur cette ligne précise, qui demanderait
  /// l'enregistreur de tests d'Xcode (accès UI direct, hors de portée en CLI) pour être
  /// diagnostiquée plus avant — pas nécessairement un bug de l'app elle-même : le parcours
  /// fonctionne normalement à la main sur simulateur et appareil réel (voir README).
  func testSkyjoMatchReachesResults() throws {
    let app = launchedApp()

    let names = [uniqueName("Bob"), uniqueName("Camille"), uniqueName("Diego")]
    for name in names {
      createPlayer(named: name, in: app)
    }

    openSkyjoSetup(in: app)
    ensurePlayersSelected(names, in: app)
    app.buttons["Commencer"].tap()
    XCTAssertTrue(app.textFields.firstMatch.waitForExistence(timeout: 5))

    throw XCTSkip(
      "Saisie des manches bloquée par une ligne de ScoreBoardView qui n'accepte jamais le focus clavier au tap synthétisé (6 stratégies essayées, voir le commentaire de cette méthode) — à reprendre avec l'enregistreur de tests d'Xcode."
    )
  }

  // MARK: - Parcours 3 : reprise après relance (« soirée perdue »)

  func testMatchResumesAfterRelaunch() throws {
    let app = launchedApp()

    let names = [uniqueName("Erwan"), uniqueName("Fanny")]
    for name in names {
      createPlayer(named: name, in: app)
    }

    openSkyjoSetup(in: app)
    ensurePlayersSelected(names, in: app)
    app.buttons["Commencer"].tap()

    // Doc 03 : la partie est persistée dès sa création (avant toute manche validée), pas
    // seulement après la première manche — inutile d'en jouer une pour vérifier la reprise.
    XCTAssertTrue(app.textFields.firstMatch.waitForExistence(timeout: 5))

    app.terminate()

    let relaunched = launchedApp(resettingState: false)
    relaunched.tabBars.buttons["Jeux"].tap()

    let resumeRow = relaunched.staticTexts["Reprendre la partie en cours"]
    XCTAssertTrue(
      resumeRow.waitForExistence(timeout: 5),
      "Après relance, la partie en cours doit être proposée en reprise dans l'onglet Jeux.")
    resumeRow.tap()

    XCTAssertTrue(
      relaunched.textFields.firstMatch.waitForExistence(timeout: 5),
      "La reprise doit rouvrir l'écran de saisie de la partie, pas rester sur la liste.")
  }

  // MARK: - Aides

  /// Doc utilisateur (audit qualité, 15) — deux réglages de lancement, indépendants de la
  /// machine qui exécute les tests :
  /// - `-AppleLanguages (fr) -AppleLocale fr_FR` force le français, quel que soit le réglage
  ///   région/langue du simulateur hôte — sans ça, ce simulateur résout l'anglais (traductions
  ///   ajoutées par la Phase G) et les assertions sur des libellés français échouent (« Ajouter
  ///   un joueur » introuvable, l'écran affichant « Add a player »).
  /// - `-uitesting-reset` (`resettingState: true`, le cas par défaut — premier lancement d'un
  ///   test) fait repartir `QuiMeneApp` sur un magasin vide, dédié aux tests d'interface (voir
  ///   `QuiMeneApp.isUITesting`) — sans ça, les joueurs créés par un parcours s'accumulent
  ///   d'une exécution de test à l'autre jusqu'à sortir de l'écran visible. `resettingState:
  ///   false` (`-uitesting` seul) rouvre ce même magasin sans l'effacer — nécessaire pour la
  ///   relance du parcours n°3, qui doit justement retrouver la partie laissée en cours.
  private func launchedApp(resettingState: Bool = true) -> XCUIApplication {
    let app = XCUIApplication()
    app.launchArguments += ["-AppleLanguages", "(fr)", "-AppleLocale", "fr_FR"]
    app.launchArguments.append(resettingState ? "-uitesting-reset" : "-uitesting")
    app.launch()
    return app
  }

  /// Doc utilisateur — le catalogue est trié alphabétiquement (`GamesTabView.games`) : Skyjo
  /// n'est pas visible sans défiler dans une liste à 16 jeux. La barre de recherche filtre
  /// directement dessus plutôt que de deviner combien de fois balayer l'écran.
  private func openSkyjoSetup(in app: XCUIApplication) {
    app.tabBars.buttons["Jeux"].tap()
    let searchField = app.searchFields["Rechercher un jeu"]
    XCTAssertTrue(searchField.waitForExistence(timeout: 5))
    searchField.tap()
    searchField.typeText("Skyjo")

    let skyjoRow = app.staticTexts["Skyjo"]
    XCTAssertTrue(skyjoRow.waitForExistence(timeout: 5))
    skyjoRow.tap()
  }

  /// Doc utilisateur — `MatchSetupModel.init` présélectionne automatiquement les joueurs les
  /// plus récemment créés, dans la limite du nombre maximum du jeu (`recentPlayersForThisGame`
  /// vide sur un magasin de test tout juste réinitialisé). Comme chaque test crée exactement
  /// les joueurs dont il a besoin, sur un magasin vide, ils sont déjà tous cochés en ouvrant
  /// cet écran — taper sur leurs lignes les décocherait au lieu de les sélectionner. On se
  /// contente donc de vérifier que la présélection a bien fait son travail.
  private func ensurePlayersSelected(_ names: [String], in app: XCUIApplication) {
    let startButton = app.buttons["Commencer"]
    XCTAssertTrue(startButton.waitForExistence(timeout: 5))
    XCTAssertTrue(
      startButton.isEnabled,
      "« Commencer » devrait déjà être actif : \(names.count) joueur(s) fraîchement créé(s) sur un magasin vide devraient être présélectionnés par MatchSetupModel."
    )
  }

  private func uniqueName(_ base: String) -> String {
    "\(base)\(Int.random(in: 10000...99999))"
  }

  /// Doc 10, parcours n°1 — créer un joueur avec un avatar (attribué automatiquement à partir
  /// du pseudo, doc 01) et le retrouver dans la liste. Réutilisée par les parcours 2 et 3, qui
  /// ont chacun besoin de joueurs frais pour ne pas dépendre de l'état laissé par un test
  /// précédent.
  private func createPlayer(named nickname: String, in app: XCUIApplication) {
    // Doc utilisateur — liste vide : le bouton de la barre d'outils et celui de l'EmptyState
    // portent le même libellé et coexistent tous les deux à l'écran, d'où `firstMatch` plutôt
    // qu'une correspondance unique.
    let addButton = app.buttons["Ajouter un joueur"].firstMatch
    XCTAssertTrue(addButton.waitForExistence(timeout: 10))
    addButton.tap()

    let nicknameField = app.textFields["Pseudo"]
    XCTAssertTrue(nicknameField.waitForExistence(timeout: 5))
    nicknameField.tap()
    nicknameField.typeText(nickname)

    app.buttons["Enregistrer"].tap()
    XCTAssertTrue(app.staticTexts[nickname].waitForExistence(timeout: 5))
  }
}
