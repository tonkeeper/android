package com.tonapps.tonkeeper.manager.push

private const val BODY_TEMPLATE_KEY = "body_template"
private const val WALLET_ID_KEY = "wallet_id"
private const val ADDRESS_KEY = "address"
private const val WALLET_NAME_PLACEHOLDER = "{wallet_name}"

internal object PushBodyTemplate {

    suspend fun resolve(
        data: (String) -> String?,
        getWalletsCount: suspend () -> Int,
        findWalletNameById: suspend (String) -> String?,
        findWalletNameByAddress: suspend (String) -> String?,
    ): String? {
        val template = data(BODY_TEMPLATE_KEY)?.takeIf { it.isNotBlank() } ?: return null
        if (getWalletsCount() <= 1) {
            return null
        }
        val walletName = data(WALLET_ID_KEY)?.let { findWalletNameById(it) }
            ?: data(ADDRESS_KEY)?.let { findWalletNameByAddress(it) }
        if (walletName.isNullOrBlank()) {
            return null
        }
        return template.replace(WALLET_NAME_PLACEHOLDER, walletName)
    }
}
