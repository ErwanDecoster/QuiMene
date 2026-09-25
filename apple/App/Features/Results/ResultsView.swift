import Charts
import DesignSystem
import Domain
import Store
import SwiftUI

/// Doc 06 : « L'écran de résultats est la récompense de la soirée. » Podium toujours affiché,
/// faits marquants sélectionnés par `StatsEngine`, courbe d'évolution, badges.
struct ResultsView: View {
  let state: MatchState
  let definition: GameDefinition
  let standings: [Standing]
  let participantRecords: [ParticipantRecord]
  /// Doc 16 — ma place, quand les fiches ne le disent pas (partie suivie depuis un autre
  /// appareil : fiches en mémoire, sans lien vers mon profil).
  var myParticipantID: Participant.ID? = nil
  /// Doc utilisateur — date réelle de la partie pour la carte partagée, quand elle est connue
  /// (`HistoryDetailView`, rejouant une partie ancienne). `nil` juste après la fin d'une partie
  /// (`LiveMatchView` et consorts) : la carte retombe alors sur la date du jour, qui est déjà la
  /// bonne dans ce cas.
  var playedAt: Date? = nil

  private let statsEngine = StatsEngine()

  private func isMe(_ id: Participant.ID) -> Bool {
    id == myParticipantID || recordByID[id]?.player?.sharedProfileIsMine == true
  }

  private var recordByID: [Participant.ID: ParticipantRecord] {
    Dictionary(uniqueKeysWithValues: participantRecords.map { ($0.id, $0) })
  }

  private var badgeByParticipant: [Participant.ID: Badge] {
    Dictionary(
      uniqueKeysWithValues: statsEngine.badges(for: state, definition: definition).map {
        ($0.participantID, $0)
      })
  }

  private var insights: [Insight] {
    statsEngine.insights(for: state, definition: definition)
  }

  private var series: [ParticipantSeries] {
    statsEngine.series(for: state)
  }

  private var sortedStandings: [Standing] {
    standings.sorted { $0.rank < $1.rank }
  }

  @Environment(\.modelContext) private var modelContext

  var body: some View {
    content
      // Doc 16, phase E — une partie qui vient de se terminer part tout de suite chez les amis
      // liés qui y ont joué, sans attendre un retour au premier plan.
      .task { await SharedProfileSyncCoordinator.shared.sync(context: modelContext) }
  }

  private var content: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: Space.xxl) {
        podiumSection
        if !insights.isEmpty {
          insightsSection
        }
        chartSection
        roundByRoundSection
        ShareLink(
          item: ResultsShareCard(
            gameName: definition.name.localized,
            standings: sortedStandings,
            recordByID: recordByID,
            badgeByParticipant: badgeByParticipant,
            roundCount: state.rounds.count,
            playedAt: playedAt ?? Date()
          ).renderedImage(),
          preview: SharePreview("Résultats — \(definition.name.localized)")
        ) {
          HStack(spacing: Space.sm) {
            Image(systemName: "square.and.arrow.up")
              .font(.system(size: IconSize.sm))
            Text("Partager le résumé")
          }
        }
        .buttonStyle(.secondary(size: .medium))
        .frame(maxWidth: .infinity)
      }
      .padding(Space.lg)
    }
    .background(.neutralBg)
    .navigationTitle("Résultats")
    .navigationBarTitleDisplayMode(.inline)
  }

  private var podiumSection: some View {
    VStack(alignment: .leading, spacing: Space.md) {
      Text(definition.name.localized).font(.h2).foregroundStyle(.textPrimary)
      ForEach(sortedStandings, id: \.participantID) { standing in
        if let record = recordByID[standing.participantID] {
          HStack(spacing: Space.md) {
            Text("\(standing.rank)")
              .font(.h3)
              .foregroundStyle(standing.rank == 1 ? .brandBrass : .textSecondary)
              .frame(width: 32)
            AvatarView(avatar: record.avatar, size: .medium)
            VStack(alignment: .leading, spacing: Space.xxs) {
              HStack(spacing: Space.sm) {
                Text(record.nicknameSnapshot).font(.h5).foregroundStyle(.textPrimary)
                if isMe(standing.participantID) { MeBadge() }
              }
              if let badge = badgeByParticipant[standing.participantID] {
                Text(badge.kind.label).font(.label).foregroundStyle(.brandBrass)
              }
            }
            Spacer()
            Text(standing.score.formatted())
              .font(.scoreXL)
              .foregroundStyle(standing.rank == 1 ? .brandBrass : .textSecondary)
          }
          .padding(Space.md)
          .background(
            standing.rank == 1 ? Color.brandBrass.opacity(0.08) : Color.neutralSurface,
            in: .rect(cornerRadius: Radius.md)
          )
          // Doc 08 « Accessibilité » — même regroupement de ligne que `ScoreBoardView` ; le
          // podium n'anime rien aujourd'hui, rien à gérer côté Reduce Motion pour l'instant.
          .accessibleScoreRow(
            name: record.nicknameSnapshot, rank: standing.rank, score: standing.score)
        }
      }
    }
  }

  private var insightsSection: some View {
    VStack(alignment: .leading, spacing: Space.md) {
      Text("Faits marquants").font(.h4).foregroundStyle(.textPrimary)
      ForEach(insights) { insight in
        Card {
          HStack(spacing: Space.md) {
            Image(systemName: insight.symbol)
              .font(.system(size: IconSize.lg))
              .frame(width: IconSize.lg, height: IconSize.lg)
              .foregroundStyle(.brandInk)
            VStack(alignment: .leading, spacing: Space.xxs) {
              Text(insight.headline).font(.h6).foregroundStyle(.textPrimary)
              Text(insight.detail).font(.bodySmall).foregroundStyle(.textSecondary)
            }
            Spacer(minLength: 0)
          }
        }
      }
    }
  }

  private var chartSection: some View {
    let direction = definition.scoring.direction
    let threshold = definition.end.conditions
      .first { $0.type == .scoreThreshold }
      .map { $0.resolvedValue(variants: state.variants) }

    func displayValue(_ total: Int) -> Double {
      direction == .lowestWins ? Double(-total) : Double(total)
    }

    return VStack(alignment: .leading, spacing: Space.md) {
      Text("Évolution").font(.h4).foregroundStyle(.textPrimary)
      Chart {
        ForEach(series) { entry in
          ForEach(entry.points, id: \.round) { point in
            LineMark(
              x: .value("Manche", point.round + 1),
              y: .value("Total", displayValue(point.total))
            )
            .foregroundStyle(by: .value("Joueur", entry.name))
            .interpolationMethod(.monotone)
          }
        }
        if let threshold {
          RuleMark(y: .value("Seuil", displayValue(threshold)))
            .foregroundStyle(.semanticWarning)
            .lineStyle(StrokeStyle(dash: [4, 4]))
        }
      }
      .chartYAxis {
        AxisMarks { value in
          AxisGridLine()
          if let raw = value.as(Double.self) {
            AxisValueLabel {
              Text(Int(direction == .lowestWins ? -raw : raw).formatted())
            }
          }
        }
      }
      .frame(height: 220)
      // Doc 08 « Accessibilité » — Swift Charts ne donne aucun libellé VoiceOver aux `LineMark`
      // par défaut. Plutôt qu'une description point par point (peu exploitable au doigt sur une
      // dizaine de manches), un résumé composé du classement final, dans l'ordre du podium.
      .accessibilityElement(children: .ignore)
      .accessibilityLabel("Évolution des scores")
      .accessibilityValue(chartAccessibilitySummary)
    }
  }

  private var chartAccessibilitySummary: String {
    sortedStandings
      .compactMap { standing -> String? in
        guard let record = recordByID[standing.participantID] else { return nil }
        return "\(record.nicknameSnapshot), \(standing.score.formatted()) points"
      }
      .joined(separator: " · ")
  }

  /// Doc 01 « détail manche par manche » — le journal d'événements est la source de vérité
  /// (doc 04), donc ce tableau se contente de le relire ; rien n'est recalculé ici.
  @ViewBuilder
  private var roundByRoundSection: some View {
    let rounds = state.rounds.sorted { $0.index < $1.index }
    if !rounds.isEmpty {
      VStack(alignment: .leading, spacing: Space.md) {
        Text("Manche par manche").font(.h4).foregroundStyle(.textPrimary)
        Card {
          ScrollView(.horizontal, showsIndicators: false) {
            Grid(alignment: .leading, horizontalSpacing: Space.lg, verticalSpacing: Space.xs) {
              GridRow {
                Text("").frame(width: 24, alignment: .leading)
                ForEach(sortedStandings, id: \.participantID) { standing in
                  if let record = recordByID[standing.participantID] {
                    HStack(spacing: Space.xxs) {
                      Text(record.nicknameSnapshot)
                        .font(.label)
                        .foregroundStyle(.textSecondary)
                      if isMe(standing.participantID) { MeBadge() }
                    }
                  }
                }
              }
              ForEach(rounds, id: \.index) { round in
                GridRow {
                  Text("\(round.index + 1)")
                    .font(.label)
                    .foregroundStyle(.textTertiary)
                    .frame(width: 24, alignment: .leading)
                  ForEach(sortedStandings, id: \.participantID) { standing in
                    let name = recordByID[standing.participantID]?.nicknameSnapshot ?? ""
                    if let entry = round.entries.first(where: {
                      $0.participantID == standing.participantID
                    }) {
                      roundEntryText(entry)
                        .accessibilityLabel(
                          "\(name), manche \(round.index + 1) : \(entry.computedValue)")
                    } else {
                      Text("—").font(.bodySmall).foregroundStyle(.textTertiary)
                        .accessibilityLabel("\(name), manche \(round.index + 1), sans saisie")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  /// `12 → 24` quand une règle a modifié la valeur saisie (doublement Skyjo, bonus…) — doc 03 :
  /// conserver la saisie brute *et* la valeur calculée est ce qui rend ce genre d'affichage
  /// possible sans perdre l'intention initiale du joueur.
  private func roundEntryText(_ entry: ScoreEntry) -> some View {
    Text(
      entry.rawValue == entry.computedValue
        ? "\(entry.computedValue)" : "\(entry.rawValue) → \(entry.computedValue)"
    )
    .font(.bodySmall)
    .foregroundStyle(.textPrimary)
  }
}

extension Badge.Kind {
  var label: String {
    switch self {
    case .winner: "Vainqueur"
    case .metronome: "Le Métronome"
    case .rollercoaster: "Les montagnes russes"
    case .comeback: "La remontada"
    case .kamikaze: "Le kamikaze"
    case .unshakeable: "Imperturbable"
    case .photoFinish: "Photo finish"
    case .sniper: "Le Sniper"
    case .boulet: "Le Boulet"
    case .landslide: "Le Fossé"
    }
  }
}
