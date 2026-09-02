package com.cacompte.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Miroir de `VariantSelection.Value.swift` — union non taguée Int/Bool/String, encodée comme une
 * valeur JSON brute (`true`, `100`, `"foo"`), jamais `{"int": 100}`. Ordre de décodage identique
 * à Swift : une valeur JSON **non guillemettée** est essayée en Bool puis en Int avant de
 * retomber en String ; une valeur déjà guillemettée reste toujours une String (pas de coercion —
 * `"true"` guillemetée ne devient jamais un [VariantValue.BoolValue]).
 */
@Serializable(with = VariantValueSerializer::class)
sealed interface VariantValue {
    @JvmInline
    value class IntValue(
        val value: Int,
    ) : VariantValue

    @JvmInline
    value class BoolValue(
        val value: Boolean,
    ) : VariantValue

    @JvmInline
    value class StringValue(
        val value: String,
    ) : VariantValue
}

object VariantValueSerializer : KSerializer<VariantValue> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: VariantValue,
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("VariantValue ne se sérialise qu'en JSON")
        val element =
            when (value) {
                is VariantValue.BoolValue -> JsonPrimitive(value.value)
                is VariantValue.IntValue -> JsonPrimitive(value.value)
                is VariantValue.StringValue -> JsonPrimitive(value.value)
            }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): VariantValue {
        val jsonDecoder = decoder as? JsonDecoder ?: error("VariantValue ne se désérialise qu'en JSON")
        val primitive =
            jsonDecoder.decodeJsonElement() as? JsonPrimitive
                ?: error("VariantValue doit être une primitive JSON")
        if (!primitive.isString) {
            primitive.booleanOrNull?.let { return VariantValue.BoolValue(it) }
            primitive.intOrNull?.let { return VariantValue.IntValue(it) }
        }
        return VariantValue.StringValue(primitive.content)
    }
}

/**
 * Miroir de `VariantSelection.swift` — sérialisée comme un objet JSON brut (`{"clé": valeur}`),
 * pas `{"values": {...}}` (même conteneur "valeur unique" que la source Swift). Les accesseurs
 * ne coercent jamais un type vers un autre : `int("x", default: 0)` renvoie le défaut si la
 * valeur stockée est un `Bool`, elle ne le convertit pas. `values` est `internal` (pas `private`)
 * uniquement pour que [VariantSelectionSerializer], dans le même module, puisse le lire — la
 * classe reste sans accesseur public dessus, même encapsulation qu'en Swift (`private`).
 */
@Serializable(with = VariantSelectionSerializer::class)
data class VariantSelection(
    internal val values: Map<String, VariantValue> = emptyMap(),
) {
    fun bool(
        key: String,
        default: Boolean,
    ): Boolean = (values[key] as? VariantValue.BoolValue)?.value ?: default

    fun int(
        key: String,
        default: Int,
    ): Int = (values[key] as? VariantValue.IntValue)?.value ?: default

    fun string(
        key: String,
        default: String,
    ): String = (values[key] as? VariantValue.StringValue)?.value ?: default

    companion object {
        fun of(vararg pairs: Pair<String, VariantValue>): VariantSelection = VariantSelection(pairs.toMap())
    }
}

object VariantSelectionSerializer : KSerializer<VariantSelection> {
    private val mapSerializer = MapSerializer(String.serializer(), VariantValueSerializer)

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: VariantSelection,
    ) {
        encoder.encodeSerializableValue(mapSerializer, value.values)
    }

    override fun deserialize(decoder: Decoder): VariantSelection =
        VariantSelection(decoder.decodeSerializableValue(mapSerializer))
}
