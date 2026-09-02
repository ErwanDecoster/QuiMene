import DesignSystem
import Store
import SwiftData
import Testing

@testable import CaCompte

/// Doc 15 « Reste au plan — Phase C » : `PlayerEditorModel` était le dernier des trois flux
/// `@Observable` (doc 02) sans aucun test. Couvre la validation de saisie (`canSave`), la
/// régénération d'avatar par pseudo tant qu'aucun choix manuel n'a eu lieu (charte §1.5), et la
/// persistance via `PlayerRepository`.
@MainActor
@Suite("PlayerEditorModel", .serialized)
struct PlayerEditorModelTests {
  /// Doc utilisateur — voir `LiveMatchModelTests.makeModel` : renvoie le `ModelContainer` lui-
  /// même, pas seulement son contexte. SwiftData invalide un `ModelContext` dès que le
  /// conteneur qui le possède est désalloué ; l'appelant doit donc le garder en vie
  /// (`withExtendedLifetime`) pour toute la durée du test. `cloudKitDatabase: .none` explicite,
  /// nécessaire en hébergé (`TEST_HOST`) pour ne pas tenter CloudKit malgré l'entitlement réel
  /// de `CaCompte.app`.
  private func makeContainer() throws -> ModelContainer {
    let schema = Schema(CaCompteSchemaV1.models)
    let config = ModelConfiguration(
      schema: schema, isStoredInMemoryOnly: true, cloudKitDatabase: .none)
    return try ModelContainer(for: schema, configurations: [config])
  }

  @Test("canSave rejette un pseudo vide ou trop long, accepte le reste")
  func canSaveValidatesNicknameLength() throws {
    let container = try makeContainer()
    withExtendedLifetime(container) {
      let model = PlayerEditorModel(mode: .create, context: container.mainContext)

      model.nickname = ""
      #expect(!model.canSave)

      model.nickname = String(repeating: "a", count: 25)
      #expect(!model.canSave, "24 caractères maximum")

      model.nickname = "Alice"
      #expect(model.canSave)
    }
  }

  @Test("L'avatar se régénère à partir du pseudo tant qu'aucun choix manuel n'a eu lieu")
  func avatarRegeneratesFromNicknameUntilManualOverride() throws {
    let container = try makeContainer()
    withExtendedLifetime(container) {
      let model = PlayerEditorModel(mode: .create, context: container.mainContext)

      model.nickname = "Alice"
      let generatedForAlice = Avatar.generated(for: "Alice")
      guard case .emoji(let expectedEmoji) = generatedForAlice.kind else {
        Issue.record("Avatar.generated devrait produire un emoji pour ce hachage")
        return
      }
      #expect(model.emojiValue == expectedEmoji)
      #expect(!model.hasManualAvatarOverride)

      model.selectEmoji("🦊")
      #expect(model.hasManualAvatarOverride)

      model.nickname = "Alicia"
      #expect(
        model.emojiValue == "🦊", "un choix manuel n'est plus écrasé par un changement de pseudo")
    }
  }

  @Test("save() en création persiste une fiche joueur retrouvable")
  func saveInCreateModePersistsPlayer() throws {
    let container = try makeContainer()
    try withExtendedLifetime(container) {
      let context = container.mainContext
      let model = PlayerEditorModel(mode: .create, context: context)
      model.nickname = "Chloé"

      try model.save()

      let players = try context.fetch(FetchDescriptor<PlayerRecord>())
      #expect(players.map(\.nickname) == ["Chloé"])
    }
  }

  @Test("archive() puis delete() sur une fiche existante")
  func archiveThenDeleteExistingPlayer() throws {
    let container = try makeContainer()
    try withExtendedLifetime(container) {
      let context = container.mainContext
      let repository = PlayerRepository(context: context)
      let player = try repository.create(
        nickname: "David", avatarKind: "symbol", avatarValue: "hare.fill")
      let model = PlayerEditorModel(mode: .edit(player), context: context)

      #expect(model.isEditing)
      #expect(!model.isArchivedPlayer)

      try model.archive()
      #expect(player.isArchived)

      try model.delete()
      #expect(try context.fetch(FetchDescriptor<PlayerRecord>()).isEmpty)
    }
  }
}
