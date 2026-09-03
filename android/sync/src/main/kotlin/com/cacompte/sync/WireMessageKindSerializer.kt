package com.cacompte.sync

import com.cacompte.domain.engine.StampedEvent
import com.cacompte.domain.model.UUIDSerializer
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Miroir du `Codable` synthétisé par Swift pour `WireMessage.Kind` — même convention que
 * [com.cacompte.domain.engine.MatchEventSerializer] (`{"<cas>": {…}}`, `_0` pour un unique
 * paramètre non nommé, objet vide pour un cas sans paramètre) : `events`/`proposal` portent un
 * seul `[StampedEvent]` non nommé, `goodbye` n'a aucun paramètre. Vérifié bit-à-bit contre les 8
 * golden files du dossier `spec/wire` (doc 09, étape F).
 */
object WireMessageKindSerializer : KSerializer<WireMessage.Kind> {
    private val stampedEventListSerializer = ListSerializer(StampedEvent.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("WireMessage.Kind")

    override fun serialize(
        encoder: Encoder,
        value: WireMessage.Kind,
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("WireMessage.Kind ne se sérialise qu'en JSON.")
        val json = jsonEncoder.json
        val (caseName, payload) =
            when (value) {
                is WireMessage.Kind.Hello ->
                    "hello" to
                        buildJsonObject {
                            put("deviceName", value.deviceName)
                            put("appVersion", value.appVersion)
                            put("platform", json.encodeToJsonElement(WireMessage.Platform.serializer(), value.platform))
                            put("role", json.encodeToJsonElement(Role.serializer(), value.role))
                            put("deviceID", value.deviceID)
                        }

                is WireMessage.Kind.Welcome ->
                    "welcome" to
                        buildJsonObject {
                            put("log", json.encodeToJsonElement(stampedEventListSerializer, value.log))
                            put("role", json.encodeToJsonElement(Role.serializer(), value.role))
                        }

                is WireMessage.Kind.Events ->
                    "events" to
                        buildJsonObject {
                            put("_0", json.encodeToJsonElement(stampedEventListSerializer, value.events))
                        }

                is WireMessage.Kind.MatchChanged ->
                    "matchChanged" to
                        buildJsonObject {
                            put("log", json.encodeToJsonElement(stampedEventListSerializer, value.log))
                        }

                is WireMessage.Kind.Proposal ->
                    "proposal" to
                        buildJsonObject {
                            put("_0", json.encodeToJsonElement(stampedEventListSerializer, value.events))
                        }

                is WireMessage.Kind.Rejection ->
                    "rejection" to
                        buildJsonObject {
                            put("eventID", json.encodeToJsonElement(UUIDSerializer, value.eventID))
                            put("reason", value.reason)
                        }

                is WireMessage.Kind.Heartbeat ->
                    "heartbeat" to buildJsonObject { put("lamport", value.lamport.toLong()) }

                WireMessage.Kind.Goodbye -> "goodbye" to JsonObject(emptyMap())
            }
        jsonEncoder.encodeJsonElement(buildJsonObject { put(caseName, payload) })
    }

    override fun deserialize(decoder: Decoder): WireMessage.Kind {
        val jsonDecoder = decoder as? JsonDecoder ?: error("WireMessage.Kind ne se désérialise qu'en JSON.")
        val json = jsonDecoder.json
        val root = jsonDecoder.decodeJsonElement().jsonObject
        val (caseName, payloadElement) =
            root.entries.singleOrNull()
                ?: error("WireMessage.Kind attend un objet à une seule clé (le nom du cas).")
        val obj = payloadElement.jsonObject
        return when (caseName) {
            "hello" ->
                WireMessage.Kind.Hello(
                    deviceName = obj.getValue("deviceName").jsonPrimitive.content,
                    appVersion = obj.getValue("appVersion").jsonPrimitive.content,
                    platform = json.decodeFromJsonElement(WireMessage.Platform.serializer(), obj.getValue("platform")),
                    role = json.decodeFromJsonElement(Role.serializer(), obj.getValue("role")),
                    deviceID = obj.getValue("deviceID").jsonPrimitive.content,
                )

            "welcome" ->
                WireMessage.Kind.Welcome(
                    log = json.decodeFromJsonElement(stampedEventListSerializer, obj.getValue("log")),
                    role = json.decodeFromJsonElement(Role.serializer(), obj.getValue("role")),
                )

            "events" ->
                WireMessage.Kind.Events(
                    events = json.decodeFromJsonElement(stampedEventListSerializer, obj.getValue("_0")),
                )

            "matchChanged" ->
                WireMessage.Kind.MatchChanged(
                    log = json.decodeFromJsonElement(stampedEventListSerializer, obj.getValue("log")),
                )

            "proposal" ->
                WireMessage.Kind.Proposal(
                    events = json.decodeFromJsonElement(stampedEventListSerializer, obj.getValue("_0")),
                )

            "rejection" ->
                WireMessage.Kind.Rejection(
                    eventID = json.decodeFromJsonElement(UUIDSerializer, obj.getValue("eventID")),
                    reason = obj.getValue("reason").jsonPrimitive.content,
                )

            "heartbeat" ->
                WireMessage.Kind.Heartbeat(
                    lamport =
                        obj
                            .getValue("lamport")
                            .jsonPrimitive.long
                            .toULong(),
                )

            "goodbye" -> WireMessage.Kind.Goodbye

            else -> error("Cas WireMessage.Kind inconnu : $caseName")
        }
    }
}
