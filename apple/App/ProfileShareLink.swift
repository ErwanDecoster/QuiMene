import Foundation

/// Doc 14 « Profils partagés » — lien qui transporte l'identifiant permanent liant deux fiches
/// joueur sur deux appareils différents. Contrairement à `JoinLink` (un code d'appairage
/// éphémère, valable une soirée), l'identifiant transporté ici ne périme jamais : une fois liée,
/// une fiche reste liée jusqu'à délier explicitement.
///
/// Doc 14, phase 3 — transporte aussi l'avatar (pas seulement le pseudo, jusqu'ici jamais montré
/// à qui scannait) : celui qui lie peut choisir d'adopter le pseudo et l'avatar de la personne
/// représentée plutôt que de garder ceux, potentiellement approximatifs, qu'il avait choisis à
/// la création de sa propre fiche. Une photo ne peut pas transiter par un QR (poids, densité de
/// scan) — `avatarKind` reste transporté tel quel, mais l'adoption d'avatar n'est proposée que
/// si ce n'est pas `"photo"` (voir `PlayerEditorView`).
enum ProfileShareLink {
  private static let scheme = "quimene"
  private static let host = "claim-profile"

  struct Payload: Equatable, Identifiable {
    let id: UUID
    let name: String
    let avatarKind: String
    let avatarValue: String
    let paletteID: String
  }

  static func url(
    id: UUID, name: String, avatarKind: String, avatarValue: String, paletteID: String
  ) -> URL? {
    var components = URLComponents()
    components.scheme = scheme
    components.host = host
    components.queryItems = [
      URLQueryItem(name: "id", value: id.uuidString),
      URLQueryItem(name: "name", value: name),
      URLQueryItem(name: "avatarKind", value: avatarKind),
      URLQueryItem(name: "avatarValue", value: avatarValue),
      URLQueryItem(name: "paletteID", value: paletteID),
    ]
    return components.url
  }

  static func parse(_ url: URL) -> Payload? {
    guard url.scheme == scheme, url.host == host,
      let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
      let items = components.queryItems,
      let idString = items.first(where: { $0.name == "id" })?.value,
      let id = UUID(uuidString: idString)
    else { return nil }
    let name = items.first(where: { $0.name == "name" })?.value ?? ""
    let avatarKind = items.first(where: { $0.name == "avatarKind" })?.value ?? "symbol"
    let avatarValue = items.first(where: { $0.name == "avatarValue" })?.value ?? ""
    let paletteID = items.first(where: { $0.name == "paletteID" })?.value ?? "1"
    return Payload(
      id: id, name: name, avatarKind: avatarKind, avatarValue: avatarValue, paletteID: paletteID)
  }
}
