package com.tonapps.wallet.api.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

@Parcelize
data class ExplorerEntity(
    val chain: String,
    val name: String,
    val url: String,
    val tokenUrl: String? = null,
    val accountUrl: String? = null,
) : Parcelable {

    constructor(json: JSONObject) : this(
        chain = json.getString("chain"),
        name = json.getString("name"),
        url = json.getString("url"),
        tokenUrl = json.optString("token_url").takeIf { !it.isNullOrBlank() },
        accountUrl = json.optString("account_url").takeIf { !it.isNullOrBlank() },
    )

    fun formatTx(hash: String): String = url.replace(TX_HASH, hash)

    fun formatToken(tokenAddress: String): String? =
        tokenUrl?.replace(TOKEN_ADDRESS, tokenAddress)

    fun formatAccount(accountAddress: String): String? =
        accountUrl?.replace(ACCOUNT_ADDRESS, accountAddress)

    companion object {
        private const val TX_HASH = "{tx_hash}"
        private const val TOKEN_ADDRESS = "{token_address}"
        private const val ACCOUNT_ADDRESS = "{account_address}"

        val defaults: List<ExplorerEntity> = listOf(
            ExplorerEntity(
                chain = "ton",
                name = "Tonviewer",
                url = "https://tonviewer.com/transaction/{tx_hash}",
                tokenUrl = "https://tonviewer.com/{token_address}",
                accountUrl = "https://tonviewer.com/{account_address}",
            ),
            ExplorerEntity(
                chain = "eth",
                name = "Blockscout",
                url = "https://eth.blockscout.com/tx/{tx_hash}",
                tokenUrl = "https://eth.blockscout.com/token/{token_address}",
                accountUrl = "https://eth.blockscout.com/address/{account_address}",
            ),
            ExplorerEntity(
                chain = "btc",
                name = "Mempool",
                url = "https://mempool.space/tx/{tx_hash}",
                accountUrl = "https://mempool.space/address/{account_address}",
            ),
            ExplorerEntity(
                chain = "bsc",
                name = "BscScan",
                url = "https://bscscan.com/tx/{tx_hash}",
                tokenUrl = "https://bscscan.com/token/{token_address}",
                accountUrl = "https://bscscan.com/address/{account_address}",
            ),
            ExplorerEntity(
                chain = "arb",
                name = "Blockscout",
                url = "https://arbitrum.blockscout.com/tx/{tx_hash}",
                tokenUrl = "https://arbitrum.blockscout.com/token/{token_address}",
                accountUrl = "https://arbitrum.blockscout.com/address/{account_address}",
            ),
            ExplorerEntity(
                chain = "base",
                name = "Blockscout",
                url = "https://base.blockscout.com/tx/{tx_hash}",
                tokenUrl = "https://base.blockscout.com/token/{token_address}",
                accountUrl = "https://base.blockscout.com/address/{account_address}",
            ),
            ExplorerEntity(
                chain = "tron",
                name = "Tronscan",
                url = "https://tronscan.org/#/transaction/{tx_hash}",
                tokenUrl = "https://tronscan.org/#/token20/{token_address}",
                accountUrl = "https://tronscan.org/#/address/{account_address}",
            ),
        )

        fun of(array: JSONArray?): List<ExplorerEntity> {
            if (array == null || array.length() == 0) return defaults
            return (0 until array.length()).map { ExplorerEntity(array.getJSONObject(it)) }
        }
    }
}
