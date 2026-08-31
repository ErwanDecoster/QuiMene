import Foundation
import SwiftData

/// Modèle de données §« PlayerRecord » — la fiche joueur, réutilisée d'une partie à l'autre.
/// Toute propriété a une valeur par défaut : contrainte CloudKit, pas une préférence de style.
@Model
public final class PlayerRecord {
    public var id: UUID = UUID()
    public var nickname: String = ""
    /// `"symbol"` | `"emoji"` | `"photo"`
    public var avatarKind: String = "symbol"
    /// Nom SF Symbol, ou emoji, ou `""` si `avatarKind == "photo"`.
    public var avatarValue: String = ""
    @Attribute(.externalStorage) public var avatarPhoto: Data?
    /// Identifiant de palette joueur, `"1"`…`"10"` (charte §1.5).
    public var paletteID: String = "1"
    public var createdAt: Date = Date()
    public var isArchived: Bool = false
    public var sortIndex: Int = 0
    /// Doc 14 « Profils partagés » — identifiant permanent, partagé avec la fiche d'un ami sur
    /// son propre appareil (jamais régénéré une fois posé : un lien déjà distribué en QR doit
    /// rester valable). `nil` tant que cette fiche n'a jamais été partagée ni liée.
    public var sharedProfileID: UUID?
    /// Doc 14, phase 4 — `true` uniquement pour la fiche qu'on a soi-même partagée (« Partager ce
    /// profil » l'a générée) : par construction, une seule fiche par appareil peut l'être
    /// (`PlayerRepository.sharedProfileID(for:)` refuse d'en désigner une seconde) — c'est donc
    /// *la* fiche qui représente l'utilisateur de cet appareil, jamais une fiche qui suit un ami
    /// (liée en scannant *son* code, `sharedProfileIsMine` reste `false`). Distingue « tout ce qui
    /// m'est poussé m'intéresse » de « seulement ce qui me concerne, moi » côté synchronisation
    /// (`SharedProfileSyncCoordinator`).
    public var sharedProfileIsMine: Bool = false
    /// Doc 14, phase 3 — pseudo tel que scanné au moment de la liaison (jamais mis à jour
    /// ensuite) : cet appareil n'a aucun moyen de savoir si l'ami a changé de pseudo depuis.
    /// `nil` pour une fiche jamais liée par scan (y compris une fiche seulement *partagée*, dont
    /// l'appareil d'origine ne sait jamais qui l'a réclamée — doc 14 « Limites de confiance »).
    public var sharedProfileLinkedName: String?
    public var sharedProfileLinkedAt: Date?

    /// Inverse de `ParticipantRecord.player` — indispensable dès que CloudKit est actif : une
    /// relation sans inverse déclarée des deux côtés fait planter l'ouverture du container
    /// (contrainte CloudKit, doc 03 n°3), alors qu'elle passait silencieusement en local seul.
    /// Stockage optionnel : CloudKit exige que les relations vers plusieurs le soient (au-delà
    /// d'avoir une valeur par défaut) — masqué derrière `participations` ci-dessous.
    @Relationship(deleteRule: .nullify, inverse: \ParticipantRecord.player)
    public var participationsStorage: [ParticipantRecord]? = []

    public var participations: [ParticipantRecord] {
        get { participationsStorage ?? [] }
        set { participationsStorage = newValue }
    }

    public init(
        id: UUID = UUID(),
        nickname: String,
        avatarKind: String,
        avatarValue: String,
        avatarPhoto: Data? = nil,
        paletteID: String,
        createdAt: Date = Date(),
        isArchived: Bool = false,
        sortIndex: Int = 0
    ) {
        self.id = id
        self.nickname = nickname
        self.avatarKind = avatarKind
        self.avatarValue = avatarValue
        self.avatarPhoto = avatarPhoto
        self.paletteID = paletteID
        self.createdAt = createdAt
        self.isArchived = isArchived
        self.sortIndex = sortIndex
    }
}
