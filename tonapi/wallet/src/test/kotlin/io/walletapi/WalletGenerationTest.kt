package io.walletapi

import io.Serializer
import io.walletapi.apis.WalletsApi
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityStatus
import io.walletapi.models.SyncStatus
import io.walletapi.models.WalletRegisterResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletGenerationTest {
    @Test
    fun unknownTopLevelAndNestedEnumsDoNotDiscardTheResponse() {
        assertEquals(ActivityStatus.unknown, Json.decodeFromString<ActivityStatus>("\"future-status\""))
        assertEquals(ActivityDirection.unknown, Json.decodeFromString<ActivityDirection>("\"future-direction\""))
        val result = Json.decodeFromString<WalletRegisterResult>("""{"index":0,"status":"future-status","wallet_id":"wallet"}""")
        assertEquals(WalletRegisterResult.Status.unknown, result.status)
        assertEquals("wallet", result.walletId)
        val sync = Serializer.fromJSON<SyncStatus>("""{"status":"future-status","chains":{"ton":{"status":"ready","backfill":"complete"}}}""")
        assertEquals(SyncStatus.Status.unknown, sync.status)
        assertTrue(sync.chains.containsKey("ton"))
    }

    @Test
    fun walletRequestKeepsItsAuthenticationTagsAndWireHeaders() {
        val request = WalletsApi().getWalletRequestConfig(walletId = "wallet", network = null, xWalletId = "wallet")
        assertTrue(request.requiresAuthentication)
        assertTrue(request.requiresWalletAuthentication)
        assertEquals("wallet", request.walletId)
        assertEquals("wallet", request.headers["X-Wallet-ID"])
    }
}
