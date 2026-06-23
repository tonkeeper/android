package com.tonapps.blockchain.model.legacy

import android.os.Parcelable
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.ton.TonNetwork
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class BlockchainAddress(
    val value: String,
    val network: TonNetwork,
    val blockchain: Blockchain,
): Parcelable {

    @IgnoredOnParcel
    val key: String by lazy {
        when (network) {
            TonNetwork.TESTNET -> "${blockchain.id}:$value:testnet"
            TonNetwork.TETRA -> "${blockchain.id}:$value:tetra"
            TonNetwork.MAINNET -> "${blockchain.id}:$value"
        }
    }

    companion object {

        fun valueOf(value: String): BlockchainAddress {
            val split = value.split(":")
            return if (split.size == 2) {
                BlockchainAddress(
                    value = split[1],
                    network = TonNetwork.MAINNET,
                    blockchain = Blockchain.valueOf(split[0])
                )
            } else {
                BlockchainAddress(
                    value = split[2],
                    network = when (split[1]) {
                        "testnet" -> TonNetwork.TESTNET
                        "tetra" -> TonNetwork.TETRA
                        else -> TonNetwork.MAINNET
                    },
                    blockchain = Blockchain.valueOf(split[0])
                )
            }
        }
    }
}