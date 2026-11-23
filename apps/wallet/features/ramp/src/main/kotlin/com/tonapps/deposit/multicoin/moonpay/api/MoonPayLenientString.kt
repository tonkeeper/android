package com.tonapps.deposit.multicoin.moonpay.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

// MoonPay documents amounts and rates as strings, but some of them arrive as numbers.
internal object MoonPayLenientString : KSerializer<String> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MoonPayLenientString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeString()
        return (element as? JsonPrimitive)?.content
            ?: throw SerializationException("Expected a string-like value, got $element")
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}
