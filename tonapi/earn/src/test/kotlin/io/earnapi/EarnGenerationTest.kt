package io.earnapi

import io.earnapi.apis.VaultsApi
import io.earnapi.apis.WalletsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnGenerationTest {
    @Test
    fun balanceRequestKeepsInterceptorAuthentication() {
        val request = WalletsApi().getWalletBalanceRequestConfig(
            walletId = "wallet-id",
            currencies = listOf("usd"),
            refresh = null,
            xWalletID = "wallet-id",
            xWalletAuthorization = null,
        )

        assertTrue(request.requiresAuthentication)
        assertTrue(request.requiresWalletAuthentication)
        assertEquals("wallet-id", request.walletId)
    }

    @Test
    fun vaultsRequestNeedsNoCredentials() {
        val request = VaultsApi().getVaultsRequestConfig(currencies = listOf("usd"), assetIds = null, underlyingAssetIds = null)

        assertFalse(request.requiresAuthentication)
        assertFalse(request.requiresWalletAuthentication)
    }
}
