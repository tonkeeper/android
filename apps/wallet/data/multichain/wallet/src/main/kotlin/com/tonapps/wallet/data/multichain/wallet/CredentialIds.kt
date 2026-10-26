package com.tonapps.wallet.data.multichain.wallet

/** Mnemonic rows keep their UUID ids; every other row is prefixed here (prefixes contain `:`, UUIDs never do). */
object CredentialIds {

    const val LIGHTER_MAINNET = "mainnet"

    private const val APP_KEY = "app_key:"
    private const val LIGHTER_L2_KEY = "lighter_l2_key:"

    // TODO remove before perps release: rows only earlier builds wrote, see lighterAccount and lighterRoToken.
    private const val LIGHTER_ACCOUNT = "lighter_account:"
    private const val LIGHTER_RO_TOKEN = "lighter_ro_token:"

    private val lighterEnvironments = listOf(LIGHTER_MAINNET)

    fun appKey(walletId: String): String = APP_KEY + walletId

    fun lighterL2Key(walletId: String, environment: String): String = "$LIGHTER_L2_KEY$walletId:$environment"

    /**
     * Where the account was sealed before it moved to plain prefs: read once to migrate it, then dropped.
     * TODO remove before perps release, with LighterCredentialsStore.migrateLegacyAccount.
     */
    fun lighterAccount(walletId: String, environment: String): String = "$LIGHTER_ACCOUNT$walletId:$environment"

    // TODO remove before perps release: nothing writes it any more, it is only deleted with the wallet.
    fun lighterRoToken(walletId: String, environment: String): String = "$LIGHTER_RO_TOKEN$walletId:$environment"

    fun derivedIds(walletId: String): List<String> {
        return listOf(appKey(walletId)) + lighterEnvironments.flatMap { environment ->
            listOf(
                lighterL2Key(walletId, environment),
                // TODO remove before perps release, with lighterAccount and lighterRoToken.
                lighterAccount(walletId, environment),
                lighterRoToken(walletId, environment),
            )
        }
    }
}
