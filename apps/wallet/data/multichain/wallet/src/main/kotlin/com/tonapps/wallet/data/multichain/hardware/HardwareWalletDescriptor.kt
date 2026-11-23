package com.tonapps.wallet.data.multichain.hardware

import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HardwareWalletDescriptor(
    val vendor: String = VENDOR_LEDGER,
    val deviceId: String,
    val deviceName: String,
    val deviceModel: String? = null,
    val accountIndex: Int,
    val derivationPaths: Map<String, String>,
    val bitcoinMasterFingerprint: String? = null,
    val bitcoinAccountXpub: String? = null,
) {

    fun derivationPath(chain: Chain): String? = derivationPaths[chain.network.type.id]

    fun encode(): ByteArray = json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {

        const val VENDOR_LEDGER = "ledger"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun decode(data: ByteArray): HardwareWalletDescriptor =
            json.decodeFromString(serializer(), data.toString(Charsets.UTF_8))
    }
}

data class HardwareAccountDraft(
    val chain: Chain,
    val addressType: Address.Type,
    val displayAddress: String,
    val publicKeyHex: String,
    val extendedPublicKey: String = "",
)
