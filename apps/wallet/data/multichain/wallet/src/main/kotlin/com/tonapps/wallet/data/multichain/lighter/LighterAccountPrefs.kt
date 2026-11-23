package com.tonapps.wallet.data.multichain.lighter

import android.content.Context
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import androidx.core.content.edit

/**
 * The venue account each wallet's L2 key is registered against, and its integrator approval record.
 * Not a secret — the venue answers it for the wallet's L1 address — so it lives in plain prefs,
 * readable while the session key is locked.
 */
internal class LighterAccountPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(walletId: String): StoredLighterAccount? {
        val prefix = prefix(walletId)
        if (!prefs.contains(prefix + ACCOUNT_INDEX)) {
            return null
        }
        return StoredLighterAccount(
            accountIndex = prefs.getLong(prefix + ACCOUNT_INDEX, 0L),
            apiKeyIndex = prefs.getInt(prefix + API_KEY_INDEX, 0),
        )
    }

    // Committed, not applied: the activation is on disk before anything is signed with it, and a
    // failed write fails the activation instead of being lost with the process.
    fun save(walletId: String, account: StoredLighterAccount) {
        val prefix = prefix(walletId)
        val saved = prefs.edit()
            .putLong(prefix + ACCOUNT_INDEX, account.accountIndex)
            .putInt(prefix + API_KEY_INDEX, account.apiKeyIndex)
            .commit()
        check(saved) { "lighter: account of $walletId was not saved" }
    }

    fun remove(walletId: String) {
        val prefix = prefix(walletId)
        prefs.edit {
            remove(prefix + ACCOUNT_INDEX)
            remove(prefix + API_KEY_INDEX)
            INTEGRATOR_APPROVAL_KEYS.forEach { remove(prefix + it) }
        }
    }

    fun loadIntegratorApproval(walletId: String): StoredIntegratorApproval? {
        val prefix = prefix(walletId)
        if (!prefs.contains(prefix + APPROVAL_EXPIRY_MS)) {
            return null
        }
        return StoredIntegratorApproval(
            key = IntegratorApprovalKey(
                accountIndex = prefs.getLong(prefix + APPROVAL_ACCOUNT_INDEX, 0L),
                integratorAccountIndex = prefs.getLong(prefix + APPROVAL_INTEGRATOR_INDEX, 0L),
                maxPerpsTakerFee = prefs.getInt(prefix + APPROVAL_MAX_PERPS_TAKER_FEE, 0),
                maxPerpsMakerFee = prefs.getInt(prefix + APPROVAL_MAX_PERPS_MAKER_FEE, 0),
                maxSpotTakerFee = prefs.getInt(prefix + APPROVAL_MAX_SPOT_TAKER_FEE, 0),
                maxSpotMakerFee = prefs.getInt(prefix + APPROVAL_MAX_SPOT_MAKER_FEE, 0),
            ),
            expiryMs = prefs.getLong(prefix + APPROVAL_EXPIRY_MS, 0L),
        )
    }

    fun saveIntegratorApproval(walletId: String, approval: StoredIntegratorApproval) {
        val prefix = prefix(walletId)
        val key = approval.key
        prefs.edit {
            putLong(prefix + APPROVAL_ACCOUNT_INDEX, key.accountIndex)
            putLong(prefix + APPROVAL_INTEGRATOR_INDEX, key.integratorAccountIndex)
            putInt(prefix + APPROVAL_MAX_PERPS_TAKER_FEE, key.maxPerpsTakerFee)
            putInt(prefix + APPROVAL_MAX_PERPS_MAKER_FEE, key.maxPerpsMakerFee)
            putInt(prefix + APPROVAL_MAX_SPOT_TAKER_FEE, key.maxSpotTakerFee)
            putInt(prefix + APPROVAL_MAX_SPOT_MAKER_FEE, key.maxSpotMakerFee)
            putLong(prefix + APPROVAL_EXPIRY_MS, approval.expiryMs)
        }
    }

    fun removeIntegratorApproval(walletId: String) {
        val prefix = prefix(walletId)
        prefs.edit {
            INTEGRATOR_APPROVAL_KEYS.forEach { remove(prefix + it) }
        }
    }

    private fun prefix(walletId: String) = "$walletId:${CredentialIds.LIGHTER_MAINNET}:"

    private companion object {
        const val NAME = "lighter_accounts"
        const val ACCOUNT_INDEX = "account_index"
        const val API_KEY_INDEX = "api_key_index"
        const val APPROVAL_ACCOUNT_INDEX = "integrator_approval_account_index"
        const val APPROVAL_INTEGRATOR_INDEX = "integrator_approval_integrator_index"
        const val APPROVAL_MAX_PERPS_TAKER_FEE = "integrator_approval_max_perps_taker_fee"
        const val APPROVAL_MAX_PERPS_MAKER_FEE = "integrator_approval_max_perps_maker_fee"
        const val APPROVAL_MAX_SPOT_TAKER_FEE = "integrator_approval_max_spot_taker_fee"
        const val APPROVAL_MAX_SPOT_MAKER_FEE = "integrator_approval_max_spot_maker_fee"
        const val APPROVAL_EXPIRY_MS = "integrator_approval_expiry_ms"
        val INTEGRATOR_APPROVAL_KEYS = listOf(
            APPROVAL_ACCOUNT_INDEX,
            APPROVAL_INTEGRATOR_INDEX,
            APPROVAL_MAX_PERPS_TAKER_FEE,
            APPROVAL_MAX_PERPS_MAKER_FEE,
            APPROVAL_MAX_SPOT_TAKER_FEE,
            APPROVAL_MAX_SPOT_MAKER_FEE,
            APPROVAL_EXPIRY_MS,
        )
    }
}
