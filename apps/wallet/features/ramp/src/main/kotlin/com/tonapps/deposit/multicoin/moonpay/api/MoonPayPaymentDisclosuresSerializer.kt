package com.tonapps.deposit.multicoin.moonpay.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

// The quote's disclosures one by one: a malformed one (no id, an id that is not a string) is kept as undecodable, so the
// screen shows MoonPay's conservative text for it instead of losing every disclosure, or the whole quote, to it.
internal object MoonPayPaymentDisclosuresSerializer : KSerializer<List<MoonPayBuyQuote.PaymentDisclosure>> {

    private val list = ListSerializer(MoonPayBuyQuote.PaymentDisclosure.serializer())

    override val descriptor: SerialDescriptor = list.descriptor

    override fun deserialize(decoder: Decoder): List<MoonPayBuyQuote.PaymentDisclosure> {
        val json = decoder as? JsonDecoder ?: return list.deserialize(decoder)
        val element = json.decodeJsonElement()
        val items = element as? JsonArray ?: return listOf(undecodable(element.toString()))
        return items.map { item ->
            runCatching { json.json.decodeFromJsonElement(MoonPayBuyQuote.PaymentDisclosure.serializer(), item) }
                .getOrElse { undecodable(item.toString()) }
        }
    }

    override fun serialize(encoder: Encoder, value: List<MoonPayBuyQuote.PaymentDisclosure>) {
        // An undecodable one goes out with its raw text as the id: decoded again, it is still unknown.
        list.serialize(encoder, value)
    }

    private fun undecodable(raw: String): MoonPayBuyQuote.PaymentDisclosure {
        return MoonPayBuyQuote.PaymentDisclosure(id = raw, undecodable = true)
    }
}
