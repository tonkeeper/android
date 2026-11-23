package com.tonapps.tonkeeper.manager.push

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushBodyTemplateTest {

    private val template = "Swap completed on {wallet_name}: +32.2 Utya"

    private val walletNamesById = mapOf("mc-wallet" to "Main")
    private val walletNamesByAddress = mapOf("0:abc" to "Savings")

    private suspend fun resolve(
        data: Map<String, String>,
        walletsCount: Int = 2,
    ): String? = PushBodyTemplate.resolve(
        data = data::get,
        getWalletsCount = { walletsCount },
        findWalletNameById = { walletNamesById[it] },
        findWalletNameByAddress = { walletNamesByAddress[it] },
    )

    @Test
    fun walletFoundByIdReplacesPlaceholder() = runTest {
        val data = mapOf("body_template" to template, "wallet_id" to "mc-wallet", "address" to "0:abc")
        assertEquals("Swap completed on Main: +32.2 Utya", resolve(data))
    }

    @Test
    fun unknownWalletIdFallsBackToAddress() = runTest {
        val data = mapOf("body_template" to template, "wallet_id" to "other", "address" to "0:abc")
        assertEquals("Swap completed on Savings: +32.2 Utya", resolve(data))
    }

    @Test
    fun missingWalletIdFallsBackToAddress() = runTest {
        val data = mapOf("body_template" to template, "address" to "0:abc")
        assertEquals("Swap completed on Savings: +32.2 Utya", resolve(data))
    }

    @Test
    fun singleWalletKeepsServerBody() = runTest {
        val data = mapOf("body_template" to template, "wallet_id" to "mc-wallet", "address" to "0:abc")
        assertNull(resolve(data, walletsCount = 1))
    }

    @Test
    fun noWalletsKeepsServerBody() = runTest {
        val data = mapOf("body_template" to template, "wallet_id" to "mc-wallet", "address" to "0:abc")
        assertNull(resolve(data, walletsCount = 0))
    }

    @Test
    fun unknownWalletKeepsServerBody() = runTest {
        val data = mapOf("body_template" to template, "wallet_id" to "other", "address" to "0:other")
        assertNull(resolve(data))
    }

    @Test
    fun blankWalletNameKeepsServerBody() = runTest {
        val data = mapOf("body_template" to template, "wallet_id" to "mc-wallet")
        val body = PushBodyTemplate.resolve(
            data = data::get,
            getWalletsCount = { 2 },
            findWalletNameById = { " " },
            findWalletNameByAddress = { null },
        )
        assertNull(body)
    }

    @Test
    fun blankTemplateKeepsServerBody() = runTest {
        val data = mapOf("body_template" to " ", "wallet_id" to "mc-wallet")
        assertNull(resolve(data))
    }

    @Test
    fun missingTemplateKeepsServerBody() = runTest {
        val data = mapOf("wallet_id" to "mc-wallet", "address" to "0:abc")
        assertNull(resolve(data))
    }
}
