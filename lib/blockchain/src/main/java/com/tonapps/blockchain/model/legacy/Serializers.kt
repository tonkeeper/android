package com.tonapps.blockchain.model.legacy

import android.net.Uri
import com.tonapps.blockchain.contract.Blockchain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal

object BlockchainEnumSerializer : KSerializer<Blockchain> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Blockchain", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Blockchain) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): Blockchain {
        return Blockchain.valueOf(decoder.decodeString())
    }
}

object UriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("android.net.Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri {
        return Uri.parse(decoder.decodeString())
    }
}

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.math.BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        return BigDecimal(decoder.decodeString())
    }
}
