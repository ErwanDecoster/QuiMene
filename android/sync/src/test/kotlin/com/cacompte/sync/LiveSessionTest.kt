package com.cacompte.sync

import com.cacompte.domain.engine.MatchEvent
import com.cacompte.domain.engine.StampedEvent
import com.cacompte.domain.model.Participant
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreInput
import com.cacompte.domain.model.VariantSelection
import com.cacompte.domain.rules.Direction
import com.cacompte.domain.rules.End
import com.cacompte.domain.rules.EndCondition
import com.cacompte.domain.rules.EndConditionType
import com.cacompte.domain.rules.EntryKind
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules
import com.cacompte.domain.rules.LocalizedText
import com.cacompte.domain.rules.Players
import com.cacompte.domain.rules.ScoreEntrySpec
import com.cacompte.domain.rules.Scoring
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Comme les fixtures `DomainTests` : un [GameRules] minimal qui n'exerce que les défauts du
 * protocole, pour ne pas faire dépendre `:sync` de `:catalog`. */
private class DummyRules(
    override val engineID: String = ENGINE_ID,
) : GameRules {
    companion object {
        const val ENGINE_ID = "test.dummy.v1"
    }
}

private fun makeDefinition(max: Int? = null): GameDefinition =
    GameDefinition(
        id = "dummy",
        specVersion = 1,
        rulesVersion = 1,
        name = LocalizedText(fr = "Test"),
        symbol = "circle",
        players = Players(min = 2, max = 8),
        scoring =
            Scoring(
                direction = Direction.LowestWins,
                entry = ScoreEntrySpec(kind = EntryKind.Integer, max = max),
            ),
        engine = DummyRules.ENGINE_ID,
        end = End(conditions = listOf(EndCondition(type = EndConditionType.RoundLimit, value = 1000))),
    )

private fun makeCatalog(max: Int? = null): GameCatalog =
    GameCatalog(
        definitions = listOf(makeDefinition(max)),
        engineTable = mapOf(DummyRules.ENGINE_ID to { DummyRules() }),
    )

/** Un journal d'une seule manche `matchCreated`, ce que `MatchRepository.createMatch`
 * produirait côté hôte avant de partager la partie (doc 09). */
private fun makeInitialLog(participants: List<Participant>) =
    listOf(
        StampedEvent(
            lamport = 0uL,
            deviceID = "host-device",
            occurredAt = Instant.EPOCH,
            event = MatchEvent.MatchCreated("dummy", 1, VariantSelection(), participants),
        ),
    )

/** Miroir de `LiveSessionTests.swift` (« hôte autoritaire sur transport en mémoire », doc 09 /
 * ADR-0014). */
class LiveSessionTest {
    @Test
    fun `a round proposed by a contributor is validated, broadcast, and received by both peers`() =
        runTest {
            val participants =
                listOf(
                    Participant(displayName = "Marion", seatIndex = 0),
                    Participant(displayName = "Théo", seatIndex = 1),
                )
            val catalog = makeCatalog()
            val initialLog = makeInitialLog(participants)
            val sessionID = UUID.randomUUID()

            val host = LiveSession(deviceID = "host-device", catalog = catalog, scope = backgroundScope)
            host.startHosting(initialLog, sessionID, "042817")

            val (hostChannel, peerChannel) = InMemoryChannel.pair()
            host.acceptConnection(hostChannel)

            val peer = LiveSession(deviceID = "peer-device", catalog = catalog, scope = backgroundScope)
            peer.attachToHost(peerChannel, sessionID, "042817", Role.Contributor, "Théo", "1.0")

            // Le pair reçoit d'abord le `welcome` (le journal initial, un seul événement).
            val welcomeEvents = peer.events.take(1).toList()
            welcomeEvents.map { it.event } shouldBe listOf(initialLog[0].event)

            val draft =
                RoundDraft(index = 0, inputs = participants.map { ScoreInput(participantID = it.id, rawValue = 7) })
            val peerRoundEventsDeferred = async { peer.events.take(2).toList() }
            val hostRoundEventsDeferred = async { host.events.take(1).toList() }
            peer.propose(MatchEvent.RoundCommitted(draft))

            // Sur le pair : l'application optimiste, puis la confirmation de l'hôte (même id).
            val peerRoundEvents = peerRoundEventsDeferred.await()
            peerRoundEvents shouldHaveSize 2
            peerRoundEvents[0].id shouldBe peerRoundEvents[1].id
            peerRoundEvents[1].deviceID shouldBe "host-device"

            // Sur l'hôte : l'événement accepté, avant diffusion.
            val hostRoundEvents = hostRoundEventsDeferred.await()
            hostRoundEvents[0].id shouldBe peerRoundEvents[1].id
            hostRoundEvents[0].event shouldBe MatchEvent.RoundCommitted(draft)
        }

    @Test
    fun `an observer cannot propose a round`() =
        runTest {
            val participants = listOf(Participant(displayName = "Marion", seatIndex = 0))
            val catalog = makeCatalog()
            val initialLog = makeInitialLog(participants)
            val sessionID = UUID.randomUUID()

            val host = LiveSession(deviceID = "host-device", catalog = catalog, scope = backgroundScope)
            host.startHosting(initialLog, sessionID, "042817")

            val (hostChannel, peerChannel) = InMemoryChannel.pair()
            host.acceptConnection(hostChannel)

            val observer = LiveSession(deviceID = "observer-device", catalog = catalog, scope = backgroundScope)
            observer.attachToHost(peerChannel, sessionID, "042817", Role.Observer, "David", "1.0")
            observer.events.first() // laisse le temps au `welcome` d'arriver.

            val draft =
                RoundDraft(index = 0, inputs = listOf(ScoreInput(participantID = participants[0].id, rawValue = 7)))
            shouldThrow<LiveSession.SessionError.NotAuthorized> {
                observer.propose(MatchEvent.RoundCommitted(draft))
            }
        }

    @Test
    fun `the host rejects an out-of-bounds round and the contributor is told`() =
        runTest {
            val participants = listOf(Participant(displayName = "Marion", seatIndex = 0))
            // `ScoreEntrySpec.max`, vérifié par `validate` par défaut.
            val catalog = makeCatalog(max = 10)
            val initialLog = makeInitialLog(participants)
            val sessionID = UUID.randomUUID()

            val host = LiveSession(deviceID = "host-device", catalog = catalog, scope = backgroundScope)
            host.startHosting(initialLog, sessionID, "042817")

            val (hostChannel, peerChannel) = InMemoryChannel.pair()
            host.acceptConnection(hostChannel)

            val contributor = LiveSession(deviceID = "peer-device", catalog = catalog, scope = backgroundScope)
            contributor.attachToHost(peerChannel, sessionID, "042817", Role.Contributor, "Théo", "1.0")
            contributor.events.first() // welcome.

            val tooHigh =
                RoundDraft(index = 0, inputs = listOf(ScoreInput(participantID = participants[0].id, rawValue = 999)))
            val rejectionDeferred = async { contributor.rejections.take(1).toList() }
            contributor.propose(MatchEvent.RoundCommitted(tooHigh))

            rejectionDeferred.await() shouldHaveSize 1
        }

    @Test
    fun `the host can switch matches without breaking the connection or changing the encryption key`() =
        runTest {
            val participantsA =
                listOf(
                    Participant(displayName = "Marion", seatIndex = 0),
                    Participant(displayName = "Théo", seatIndex = 1),
                )
            val catalog = makeCatalog()
            val initialLogA = makeInitialLog(participantsA)
            val sessionID = UUID.randomUUID()

            val host = LiveSession(deviceID = "host-device", catalog = catalog, scope = backgroundScope)
            host.startHosting(initialLogA, sessionID, "042817")

            val (hostChannel, peerChannel) = InMemoryChannel.pair()
            host.acceptConnection(hostChannel)

            val peer = LiveSession(deviceID = "peer-device", catalog = catalog, scope = backgroundScope)
            peer.attachToHost(peerChannel, sessionID, "042817", Role.Contributor, "Théo", "1.0")
            peer.events.first() // welcome de la partie A.

            // Doc 09 « Fin de partie » — l'hôte enchaîne une nouvelle partie sans jamais rouvrir
            // la connexion ni le canal : même sessionID, même code, mêmes pairs.
            val participantsB =
                listOf(
                    Participant(displayName = "Marion", seatIndex = 0),
                    Participant(displayName = "Théo", seatIndex = 1),
                )
            val initialLogB = makeInitialLog(participantsB)
            val changedLogsDeferred = async { peer.matchChanged.take(1).toList() }
            host.switchMatch(initialLogB)

            // Le pair reçoit le nouveau journal via `matchChanged`, pas `events`.
            val changedLogs = changedLogsDeferred.await()
            changedLogs.first().map { it.event } shouldBe initialLogB.map { it.event }

            // La clé de chiffrement n'a pas changé : le pair peut continuer à proposer des
            // manches sur la nouvelle partie sans se réappairer.
            val draft =
                RoundDraft(index = 0, inputs = participantsB.map { ScoreInput(participantID = it.id, rawValue = 5) })
            val hostRoundEventsDeferred = async { host.events.take(1).toList() }
            peer.propose(MatchEvent.RoundCommitted(draft))

            hostRoundEventsDeferred.await()[0].event shouldBe MatchEvent.RoundCommitted(draft)
        }
}
