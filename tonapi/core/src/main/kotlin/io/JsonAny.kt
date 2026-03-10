package io

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Serializable(with = JsonAnySerializer::class)
sealed class JsonAny {
    data class Object(val value: Map<String, JsonAny>) : JsonAny()
    data class Array(val value: List<JsonAny>) : JsonAny()
    data class Primitive(val value: Any?) : JsonAny()

    fun asObject(): Map<String, JsonAny>? = (this as? Object)?.value
    fun asArray(): List<JsonAny>? = (this as? Array)?.value
    fun asPrimitive(): Any? = (this as? Primitive)?.value

    fun asString(): String? = asPrimitive()?.toString()
    fun asLong(): Long? = (asPrimitive() as? Number)?.toLong()
    fun asDouble(): Double? = (asPrimitive() as? Number)?.toDouble()
    fun asBoolean(): Boolean? = asPrimitive() as? Boolean
}

object JsonAnySerializer : KSerializer<JsonAny> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: JsonAny) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        val jsonElement = serializeJsonAny(value)
        jsonEncoder.encodeJsonElement(jsonElement)
    }

    private fun serializeJsonAny(value: JsonAny): JsonElement = when (value) {
        is JsonAny.Object -> JsonObject(value.value.mapValues { serializeJsonAny(it.value) })
        is JsonAny.Array -> JsonArray(value.value.map { serializeJsonAny(it) })
        is JsonAny.Primitive -> when (val primitive = value.value) {
            is String -> JsonPrimitive(primitive)
            is Number -> JsonPrimitive(primitive)
            is Boolean -> JsonPrimitive(primitive)
            else -> JsonNull
        }
    }

    override fun deserialize(decoder: Decoder): JsonAny {
        val jsonDecoder = decoder as? JsonDecoder ?: return JsonAny.Primitive(null)
        val jsonElement = jsonDecoder.decodeJsonElement()
        return deserializeJsonElement(jsonElement)
    }

    private fun deserializeJsonElement(element: JsonElement): JsonAny = when (element) {
        is JsonObject -> JsonAny.Object(element.mapValues { deserializeJsonElement(it.value) })
        is JsonArray -> JsonAny.Array(element.map { deserializeJsonElement(it) })
        is JsonPrimitive -> {
            val value = if (element.isString) {
                element.content
            } else {
                element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.content
            }
            JsonAny.Primitive(value)
        }
    }
}
