package com.tonapps.wallet.data.ledger.discovery

import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.data.multichain.hardware.HardwareAccountDraft
import com.tonapps.wallet.data.multichain.hardware.HardwareWalletDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class LedgerImportCandidate(
    val descriptor: HardwareWalletDescriptor,
    val accounts: List<LedgerImportAccount>,
) {

    val drafts: List<HardwareAccountDraft>
        get() = accounts.mapNotNull { it.toDraft() }

    val tonPublicKeyHex: String?
        get() = drafts.firstOrNull { it.chain == Chain.Ton.Mainnet }?.publicKeyHex?.lowercase()

    companion object {

        private val json = Json { ignoreUnknownKeys = true }
        private val listSerializer = ListSerializer(serializer())

        fun from(device: LedgerDevice, account: LedgerDiscoveredAccount): LedgerImportCandidate = LedgerImportCandidate(
            descriptor = account.descriptor(device),
            accounts = account.accounts.map(LedgerImportAccount::from),
        )

        fun encode(candidates: List<LedgerImportCandidate>): String = json.encodeToString(listSerializer, candidates)

        fun decode(value: String): List<LedgerImportCandidate> = json.decodeFromString(listSerializer, value)
    }
}

@Serializable
data class LedgerImportAccount(
    val network: String,
    val mode: String,
    val addressType: String,
    val displayAddress: String,
    val publicKeyHex: String,
    val extendedPublicKey: String,
) {

    fun toDraft(): HardwareAccountDraft? {
        val chain = Chain.find(network, mode) ?: return null
        val type = Address.Type.entries.firstOrNull { it.name == addressType } ?: return null
        return HardwareAccountDraft(chain, type, displayAddress, publicKeyHex, extendedPublicKey)
    }

    companion object {
        fun from(draft: HardwareAccountDraft) = LedgerImportAccount(
            network = draft.chain.network.type.id,
            mode = draft.chain.network.mode.id,
            addressType = draft.addressType.name,
            displayAddress = draft.displayAddress,
            publicKeyHex = draft.publicKeyHex,
            extendedPublicKey = draft.extendedPublicKey,
        )
    }
}
