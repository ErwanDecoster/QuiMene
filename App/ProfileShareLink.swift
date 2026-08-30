import Foundation

/// Doc 14 « Profils partagés » — lien qui transporte l'identifiant permanent liant deux fiches
/// joueur sur deux appareils différents. Contrairement à `JoinLink` (un code d'appairage
/// éphémère, valable une soirée), l'identifiant transporté ici ne périme jamais : une fois liée,
/// une fiche reste liée jusqu'à délier explicitement.
enum ProfileShareLink {
    private static let scheme = "cacompte"
    private static let host = "claim-profile"

    struct Payload: Equatable {
        let id: UUID
        let name: String
    }

    static func url(id: UUID, name: String) -> URL? {
        var components = URLComponents()
        components.scheme = scheme
        components.host = host
        components.queryItems = [
            URLQueryItem(name: "id", value: id.uuidString),
            URLQueryItem(name: "name", value: name),
        ]
        return components.url
    }

    static func parse(_ url: URL) -> Payload? {
        guard url.scheme == scheme, url.host == host,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let items = components.queryItems,
              let idString = items.first(where: { $0.name == "id" })?.value,
              let id = UUID(uuidString: idString) else { return nil }
        let name = items.first(where: { $0.name == "name" })?.value ?? ""
        return Payload(id: id, name: name)
    }
}
