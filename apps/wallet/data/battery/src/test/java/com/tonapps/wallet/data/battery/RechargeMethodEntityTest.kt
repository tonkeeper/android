package com.tonapps.wallet.data.battery

import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodType
import io.batteryapi.models.RechargeMethods
import io.batteryapi.models.RechargeMethodsMethodsInner
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RechargeMethodEntityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aMultichainWalletGetsTheOtherChainsMethods() {
        val methods = parse(RESPONSE).methods

        val mapped = methods.mapNotNull { RechargeMethodEntity.of(it, multichain = true) }

        assertEquals(
            listOf(RechargeMethodType.TON, RechargeMethodType.JETTON, RechargeMethodType.ERC20, RechargeMethodType.TRC20, RechargeMethodType.SPL, RechargeMethodType.COIN),
            mapped.map { it.type },
        )
        val usdc = mapped.single { it.type == RechargeMethodType.ERC20 }
        assertEquals("base/mainnet", usdc.chain)
        assertEquals("base/mainnet/erc20/0x833589fcd6edb6e08f4c7c32d4f71b54bda02913", usdc.assetId)
        assertEquals("0.80957092", usdc.rate)
        assertEquals(6, usdc.decimals)
        assertEquals("5", usdc.minBootstrapValue)
    }

    @Test
    fun aTonWalletKeepsOnlyTonsMethods() {
        val methods = parse(RESPONSE).methods

        val mapped = methods.mapNotNull { RechargeMethodEntity.of(it) }

        assertEquals(listOf(RechargeMethodType.TON, RechargeMethodType.JETTON), mapped.map { it.type })
    }

    @Test
    fun anUnknownTypeIsDroppedWithoutBreakingTheList() {
        val methods = parse(RESPONSE_WITH_UNKNOWN).methods

        assertEquals(RechargeMethodsMethodsInner.Type.unknown, methods[1].type)
        assertNull(RechargeMethodEntity.of(methods[1], multichain = true))
        assertNotNull(RechargeMethodEntity.of(methods[0], multichain = true))
    }

    @Test
    fun onlyTonMethodsAreBoughtAsBefore() {
        assertEquals(
            listOf(RechargeMethodType.TON, RechargeMethodType.JETTON),
            RechargeMethodType.entries.filter { it.isTon },
        )
    }

    private fun parse(body: String): RechargeMethods = json.decodeFromString(RechargeMethods.serializer(), body)

    private companion object {

        val RESPONSE = """
            {"methods":[
              ${method("ton/mainnet", "ton/mainnet/coin", "ton", "TON", "1", 9)},
              ${method("ton/mainnet", "ton/mainnet/jetton/0:b113", "jetton", "USD₮", "0.8", 6)},
              ${method("base/mainnet", "base/mainnet/erc20/0x833589fcd6edb6e08f4c7c32d4f71b54bda02913", "erc20", "USDC", "0.80957092", 6)},
              ${method("tron/mainnet", "tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", "trc20", "USDT", "0.8", 6)},
              ${method("sol/mainnet", "sol/mainnet/spl/EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", "spl", "USDC", "0.8", 6)},
              ${method("eth/mainnet", "eth/mainnet/coin", "coin", "ETH", "1300", 18)}
            ]}
        """.trimIndent()

        val RESPONSE_WITH_UNKNOWN = """
            {"methods":[
              ${method("ton/mainnet", "ton/mainnet/coin", "ton", "TON", "1", 9)},
              ${method("btc/mainnet", "btc/mainnet/coin", "bitcoin", "BTC", "50000", 8)}
            ]}
        """.trimIndent()

        fun method(chain: String, assetId: String, type: String, symbol: String, rate: String, decimals: Int) = """
            {"chain":"$chain","asset_id":"$assetId","type":"$type","symbol":"$symbol","decimals":$decimals,
             "rate":"$rate","min_bootstrap_value":"5","support_recharge":true,"support_gasless":true}
        """.trimIndent()
    }
}
