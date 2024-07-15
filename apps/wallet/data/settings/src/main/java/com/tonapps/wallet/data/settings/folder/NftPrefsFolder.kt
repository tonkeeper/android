package com.tonapps.wallet.data.settings.folder

import android.content.Context
import android.util.Log
import com.tonapps.wallet.data.settings.entities.NftPrefsEntity

internal class NftPrefsFolder(context: Context): BaseSettingsFolder(context, "nft_prefs") {

    private companion object {
        private const val HIDDEN_PREFIX = "hidden_"
        private const val TRUST_PREFIX = "trust_"
    }

    fun get(walletId: String, nftAddress: String): NftPrefsEntity {
        return NftPrefsEntity(
            hidden = getBoolean(keyHidden(walletId, nftAddress), false),
            trust = getBoolean(keyTrust(walletId, nftAddress), false)
        )
    }

    fun setHidden(walletId: String, nftAddress: String, hidden: Boolean) {
        putBoolean(keyHidden(walletId, nftAddress), hidden)
    }

    fun setTrust(walletId: String, nftAddress: String, trust: Boolean) {
        putBoolean(keyTrust(walletId, nftAddress), trust)
    }

    private fun keyHidden(walletId: String, nftAddress: String): String {
        return key(HIDDEN_PREFIX, walletId, nftAddress)
    }

    private fun keyTrust(walletId: String, nftAddress: String): String {
        return key(TRUST_PREFIX, walletId, nftAddress)
    }

    private fun key(
        prefix: String,
        walletId: String,
        nftAddress: String
    ) = "$prefix$walletId:$nftAddress"
}