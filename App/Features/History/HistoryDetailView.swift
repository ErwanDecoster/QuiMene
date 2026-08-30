import Catalog
import Domain
import Store
import SwiftData
import SwiftUI

/// Détail d'une partie de l'historique — lecture seule, aucune mutation possible. Rejoue le
/// journal d'événements (doc 04, source de vérité) puis réutilise `ResultsView` telle quelle :
/// podium, faits marquants, courbe et détail manche par manche sont les mêmes qu'à la sortie
/// d'une partie tout juste terminée.
///
/// Doc 14 « Profils partagés », phase 2 — une partie reçue de l'appareil d'un ami
/// (`MatchRecord.isImportedSummary`) n'a pas de journal d'événements exploitable : la rejouer
/// planterait (`MatchEngineError.missingMatchCreated`). `ReceivedMatchDetailView` s'appuie
/// uniquement sur les *snapshots* déjà matérialisés (`finalRank`/`finalScore`), sans rejeu.
struct HistoryDetailView: View {
    private let match: MatchRecord
    private let catalog: GameCatalog
    private let state: MatchState?
    private let definition: GameDefinition?
    private let standings: [Standing]
    private let participantRecords: [ParticipantRecord]
    private let playedAt: Date

    init(match: MatchRecord, context: ModelContext, catalog: GameCatalog) {
        self.match = match
        self.catalog = catalog
        playedAt = match.endedAt ?? match.startedAt

        guard !match.isImportedSummary else {
            state = nil
            definition = nil
            standings = []
            participantRecords = []
            return
        }

        let repository = MatchRepository(context: context)
        let rules = try! catalog.rules(for: match.gameID, version: match.rulesVersion)
        let loadedDefinition = try! catalog.definition(for: match.gameID, version: match.rulesVersion)
        let loadedState = try! repository.loadState(match, catalog: catalog)
        definition = loadedDefinition
        state = loadedState
        standings = rules.standings(loadedState, definition: loadedDefinition)
        participantRecords = match.participants.sorted { $0.seatIndex < $1.seatIndex }
    }

    var body: some View {
        if match.isImportedSummary {
            ReceivedMatchDetailView(match: match, catalog: catalog)
        } else if let state, let definition {
            ResultsView(
                state: state,
                definition: definition,
                standings: standings,
                participantRecords: participantRecords,
                playedAt: playedAt
            )
        }
    }
}
