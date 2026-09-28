import UIKit
import XCTest

/// Doc 10 « Captures des stores » — lancé seulement par `Scripts/store-screenshots.sh`, qui pose
/// `QUIMENE_SCREENSHOT_LOCALES` (`TEST_RUNNER_QUIMENE_SCREENSHOT_LOCALES` côté `xcodebuild`),
/// des codes de langue App Store Connect (`fr-FR`, `it`…) : un `xcodebuild test` ordinaire le
/// saute. Chaque capture est une pièce jointe nommée `<code>_<fichier>` que le script extrait du
/// `.xcresult` — le lanceur de tests du simulateur ne peut pas écrire sous `~/Documents` lui-même.
///
/// Les onglets sont désignés par leur symbole (l'identifiant d'accessibilité que SwiftUI leur
/// donne), pas par leur libellé, pour que le même parcours serve dans toutes les langues — et sur
/// iPad, où ils ne sont pas dans une `tabBar` mais en haut de l'écran.
@MainActor
final class StoreScreenshotTests: XCTestCase {
  private enum Tab: String {
    case players = "person.2.fill"
    case games = "die.face.5.fill"
    case history = "clock.arrow.circlepath"
    case profile = "person.crop.circle"
  }

  override func setUpWithError() throws {
    continueAfterFailure = false
  }

  func testCaptureStoreScreenshots() throws {
    let environment = ProcessInfo.processInfo.environment
    guard let locales = environment["QUIMENE_SCREENSHOT_LOCALES"], !locales.isEmpty else {
      throw XCTSkip("Réservé à Scripts/store-screenshots.sh.")
    }
    let demoURL = try XCTUnwrap(
      Bundle(for: Self.self).url(forResource: "demo-data", withExtension: "json"))
    let demoData = try String(contentsOf: demoURL, encoding: .utf8)

    for locale in locales.split(separator: ",").map(String.init) {
      captureScreens(locale: locale, demoData: demoData)
    }
  }

  private func captureScreens(locale: String, demoData: String) {
    let parts = locale.split(separator: "-")
    let language = String(parts[0])
    let region = parts.count > 1 ? String(parts[1]) : language.uppercased()
    let app = XCUIApplication()
    app.launchArguments = [
      "-AppleLanguages", "(\(language))", "-AppleLocale", "\(language)_\(region)",
      "-uitesting-reset", "-screenshots",
    ]
    app.launchEnvironment["QUIMENE_DEMO_DATA"] = demoData
    app.launch()

    // Joueurs — premier onglet, affiché au lancement.
    XCTAssertTrue(tabButton(.players, in: app).waitForExistence(timeout: 15))
    snapshot("06-joueurs", locale: locale)

    open(.games, in: app)
    let resume = app.buttons["resume-match"]
    XCTAssertTrue(resume.waitForExistence(timeout: 5))
    snapshot("03-jeux", locale: locale)

    resume.tap()
    XCTAssertTrue(app.textFields.firstMatch.waitForExistence(timeout: 5))
    hideKeyboardOnPad(in: app)
    snapshot("01-partie", locale: locale)
    goBack(in: app)

    open(.history, in: app)
    let latestMatch = app.buttons.matching(identifier: "history-match").firstMatch
    XCTAssertTrue(latestMatch.waitForExistence(timeout: 5))
    snapshot("05-historique", locale: locale)

    latestMatch.tap()
    snapshot("02-resultats", locale: locale)
    goBack(in: app)

    open(.profile, in: app)
    snapshot("04-profil", locale: locale)

    app.terminate()
  }

  private func tabButton(_ tab: Tab, in app: XCUIApplication) -> XCUIElement {
    app.buttons.matching(identifier: tab.rawValue).firstMatch
  }

  private func open(_ tab: Tab, in app: XCUIApplication) {
    let button = tabButton(tab, in: app)
    XCTAssertTrue(button.waitForExistence(timeout: 5))
    button.tap()
  }

  /// Sur iPad, le clavier plein format couvrirait la moitié de l'écran ; sur iPhone, le pavé
  /// numérique montre justement la saisie. « Masquer le clavier » est le dernier bouton du clavier
  /// iPad (son libellé suit la langue du système), touchable seulement une fois le clavier monté.
  private func hideKeyboardOnPad(in app: XCUIApplication) {
    guard UIDevice.current.userInterfaceIdiom == .pad else { return }
    let keyboard = app.keyboards.firstMatch
    let deadline = Date().addingTimeInterval(5)
    while Date() < deadline {
      if let hideKey = keyboard.buttons.allElementsBoundByIndex.last, hideKey.isHittable {
        hideKey.tap()
        XCTAssertTrue(keyboard.waitForNonExistence(timeout: 5))
        return
      }
      Thread.sleep(forTimeInterval: 0.25)
    }
  }

  private func goBack(in app: XCUIApplication) {
    app.navigationBars.buttons.element(boundBy: 0).tap()
  }

  /// Laisse finir les animations (poussée de navigation, apparition du clavier, graphiques) : une
  /// capture prise en plein mouvement serait floue ou incomplète.
  private func snapshot(_ name: String, locale: String) {
    Thread.sleep(forTimeInterval: 1.5)
    let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
    attachment.name = "\(locale)_\(name)"
    attachment.lifetime = .keepAlways
    add(attachment)
  }
}
