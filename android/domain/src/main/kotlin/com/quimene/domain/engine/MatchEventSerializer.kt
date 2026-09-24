package com.quimene.domain.engine

import com.quimene.domain.model.InstantSerializer
import com.quimene.domain.model.Participant
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.VariantSelection
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Miroir du `Codable` synthétisé par Swift pour `MatchEvent` (enum à cas associés) — **pas** le
 * polymorphisme à discriminant par défaut de kotlinx.serialization (`{"type": "roundCommitted",
 * ...}`), jamais vérifié bit-à-bit avant l'étape F (voir golden files du dossier `spec/wire`, doc
 * 09).
 * Swift encode chaque cas comme `{"<nomDuCas>": {<champs>}}` ; un unique paramètre **non nommé**
 * (`roundCommitted(RoundDraft)`) reçoit la clé synthétique `_0` ; un cas sans paramètre
 * (`matchEndedManually`) encode un objet vide `{}`, jamais `null` ni une chaîne nue.
 */
object MatchEventSerializer : KSerializer<MatchEvent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MatchEvent")

    override fun serialize(
        encoder: Encoder,
        value: MatchEvent,
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("MatchEvent ne se sérialise qu'en JSON.")
        val json = jsonEncoder.json
        val (caseName, payload) =
            when (value) {
                is MatchEvent.MatchCreated ->
                    "matchCreated" to
                        buildJsonObject {
                            put("gameID", value.gameID)
                            put("rulesVersion", value.rulesVersion)
                            put("variants", json.encodeToJsonElement(VariantSelection.serializer(), value.variants))
                            put(
                                "participants",
                                json.encodeToJsonElement(ListSerializer(Participant.serializer()), value.participants),
                            )
                        }

                is MatchEvent.RoundCommitted ->
                    "roundCommitted" to
                        buildJsonObject {
                            put("_0", json.encodeToJsonElement(RoundDraft.serializer(), value.draft))
                        }

                is MatchEvent.RoundAmended ->
                    "roundAmended" to
                        buildJsonObject {
                            put("index", value.index)
                            put("draft", json.encodeToJsonElement(RoundDraft.serializer(), value.draft))
                        }

                is MatchEvent.RoundRemoved ->
                    "roundRemoved" to buildJsonObject { put("index", value.index) }

                is MatchEvent.MatchAbandoned ->
                    "matchAbandoned" to
                        buildJsonObject {
                            put("at", json.encodeToJsonElement(InstantSerializer, value.at))
                        }

                MatchEvent.MatchEndedManually -> "matchEndedManually" to JsonObject(emptyMap())

                is MatchEvent.NoteAdded ->
                    "noteAdded" to
                        buildJsonObject {
                            put("roundIndex", value.roundIndex)
                            put("text", value.text)
                        }
            }
        jsonEncoder.encodeJsonElement(buildJsonObject { put(caseName, payload) })
    }

    override fun deserialize(decoder: Decoder): MatchEvent {
        val jsonDecoder = decoder as? JsonDecoder ?: error("MatchEvent ne se désérialise qu'en JSON.")
        val json = jsonDecoder.json
        val root = jsonDecoder.decodeJsonElement().jsonObject
        val (caseName, payloadElement) =
            root.entries.singleOrNull() ?: error("MatchEvent attend un objet à une seule clé (le nom du cas).")
        val obj = payloadElement.jsonObject
        return when (caseName) {
            "matchCreated" ->
                MatchEvent.MatchCreated(
                    gameID = obj.getValue("gameID").jsonPrimitive.content,
                    rulesVersion = obj.getValue("rulesVersion").jsonPrimitive.int,
                    variants = json.decodeFromJsonElement(VariantSelection.serializer(), obj.getValue("variants")),
                    participants =
                        json.decodeFromJsonElement(
                            ListSerializer(Participant.serializer()),
                            obj.getValue("participants"),
                        ),
                )

            "roundCommitted" ->
                MatchEvent.RoundCommitted(
                    draft = json.decodeFromJsonElement(RoundDraft.serializer(), obj.getValue("_0")),
                )

            "roundAmended" ->
                MatchEvent.RoundAmended(
                    index = obj.getValue("index").jsonPrimitive.int,
                    draft = json.decodeFromJsonElement(RoundDraft.serializer(), obj.getValue("draft")),
                )

            "roundRemoved" -> MatchEvent.RoundRemoved(index = obj.getValue("index").jsonPrimitive.int)

            "matchAbandoned" ->
                MatchEvent.MatchAbandoned(
                    at = json.decodeFromJsonElement(InstantSerializer, obj.getValue("at")),
                )

            "matchEndedManually" -> MatchEvent.MatchEndedManually

            "noteAdded" ->
                MatchEvent.NoteAdded(
                    roundIndex = obj.getValue("roundIndex").jsonPrimitive.int,
                    text = obj.getValue("text").jsonPrimitive.content,
                )

            else -> error("Cas MatchEvent inconnu : $caseName")
        }
    }
}
