package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoonPayAssetResolverTest {

    private val ethereumNative = asset("eth", "ethereum", contract = "0x0000000000000000000000000000000000000000")
    private val usdtEthereum = asset("usdt", "ethereum", contract = "0xdAC17F958D2ee523a2206206994597C13D831ec7")
    private val usdtBsc = asset("usdt_bsc", "binance_smart_chain", contract = "0x55d398326f99059fF775485246999027B3197955")
    private val bnbNative = asset("bnb_bsc", "binance_smart_chain")
    private val ton = asset("ton", "ton")
    private val usdtTon = asset("usdt_ton", "ton", contract = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs")

    private val catalog = listOf(ethereumNative, usdtEthereum, usdtBsc, bnbNative, ton, usdtTon)

    @Test
    fun `a native coin is matched by its ticker on its network`() {
        assertEquals("eth", match("eth/mainnet/coin", "ETH")?.code)
    }

    @Test
    fun `a native coin under another code is the only native of its network`() {
        assertEquals("bnb_bsc", match("bsc/mainnet/coin", "BNB")?.code)
        assertEquals("ton", match("ton/mainnet/coin", "GRAM")?.code)
    }

    @Test
    fun `a token is matched by its contract, whatever the case`() {
        assertEquals("usdt", match("eth/mainnet/erc20/0xdac17f958d2ee523a2206206994597c13d831ec7", "USDT")?.code)
    }

    @Test
    fun `a token never takes the same address on another network`() {
        val match = MoonPayAssetResolver.match("bsc/mainnet/erc20/0xdac17f958d2ee523a2206206994597c13d831ec7", "USDT", listOf(usdtEthereum))

        assertNull(match)
    }

    @Test
    fun `a jetton's raw address matches MoonPay's friendly one`() {
        assertEquals("usdt_ton", match("ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe", "USDT")?.code)
    }

    @Test
    fun `a suspended or disabled asset is not sold`() {
        val suspended = ethereumNative.copy(status = MoonPayPlatformAsset.Status(suspended = true))
        val disabled = ethereumNative.copy(status = MoonPayPlatformAsset.Status(disabled = true))

        assertNull(MoonPayAssetResolver.match("eth/mainnet/coin", "ETH", listOf(suspended, disabled)))
    }

    @Test
    fun `an asset id the app cannot read has no match`() {
        assertNull(match("nonsense", "X"))
    }

    private fun match(assetId: String, symbol: String): MoonPayPlatformAsset? {
        return MoonPayAssetResolver.match(assetId, symbol, catalog)
    }

    private fun asset(code: String, network: String, contract: String? = null): MoonPayPlatformAsset {
        return MoonPayPlatformAsset(code = code, network = network, contractAddress = contract)
    }
}
