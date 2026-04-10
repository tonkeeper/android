package io.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("This serializer can be used only with Json")
        val jsonElement = serializeAny(value)
        jsonEncoder.encodeJsonElement(jsonElement)
    }

    private fun serializeAny(value: Any): JsonElement = when (value) {
        is Map<*, *> -> {
            val map = value as Map<String, Any>
            JsonObject(map.mapValues { serializeAny(it.value) })
        }
        is List<*> -> {
            val list = value as List<Any>
            JsonArray(list.map { serializeAny(it) })
        }
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> JsonNull
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as? JsonDecoder ?: error("This serializer can be used only with Json")
        val element = jsonDecoder.decodeJsonElement()
        return deserializeJsonElement(element)
    }

    private fun deserializeJsonElement(element: JsonElement): Any = when (element) {
        is JsonObject -> element.mapValues { deserializeJsonElement(it.value) }
        is JsonArray -> element.map { deserializeJsonElement(it) }
        is JsonPrimitive -> {
            if (element.isString) {
                element.content
            } else {
                element.content.toBooleanStrictOrNull() ?: element.content.toDoubleOrNull() ?: element.content
            }
        }
    }
}
