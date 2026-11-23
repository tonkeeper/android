package com.tonapps.tonkeeper.ui.screen.battery

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.tonkeeper.ui.screen.battery.recharge.asset.BatteryRechargeAssets
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodType
import com.tonapps.wallet.data.multichain.account.AccountBalanceEntity
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryRechargeAssetsTest {

    @Test
    fun onlyPositionsWithABalanceOfAListedAssetArePayable() {
        val accounts = listOf(
            account(USDC_BASE, balance = "10000000"),
            account(USDT_TRON, balance = "0"),
            account(SOL, balance = "500000000"),
            account(UNLISTED, balance = "10000000"),
        )

        val payable = BatteryRechargeAssets.payable(listOf(method(USDC_BASE), method(USDT_TRON), method(SOL)), accounts)

        assertEquals(setOf(USDC_BASE, SOL), payable.map { it.asset.id }.toSet())
    }

    @Test
    fun aMethodThatDoesNotRechargeOrIsOnTonIsLeftOut() {
        val accounts = listOf(account(USDC_BASE, balance = "1"), account(TON, balance = "1"))
        val methods = listOf(
            method(USDC_BASE, supportRecharge = false),
            method(TON, type = RechargeMethodType.TON),
        )

        assertEquals(emptyList<AccountWithDetails>(), BatteryRechargeAssets.payable(methods, accounts))
    }

    @Test
    fun theLargestByFiatValueComesFirst() {
        val accounts = listOf(
            account(USDC_BASE, balance = "1000000", price = "1"),
            account(SOL, balance = "1000000000", price = "150"),
        )

        val payable = BatteryRechargeAssets.payable(listOf(method(USDC_BASE), method(SOL)), accounts)

        assertEquals(listOf(SOL, USDC_BASE), payable.map { it.asset.id })
    }

    @Test
    fun anEvmAddressMatchesInAnyCase() {
        val checksummed = "base/mainnet/erc20/0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"

        assertNotNull(BatteryRechargeAssets.method(listOf(method(USDC_BASE)), checksummed))
    }

    @Test
    fun theTokenStandardIsSpelledEitherWay() {
        val battery = "bsc/mainnet/erc20/0x55d398326f99059ff775485246999027b3197955"
        val balance = "bsc/mainnet/bep20/0x55d398326f99059ff775485246999027b3197955"

        assertNotNull(BatteryRechargeAssets.method(listOf(method(battery)), balance))
    }

    @Test
    fun anotherChainOrContractIsAnotherAsset() {
        val usdcArb = "arb/mainnet/erc20/0x833589fcd6edb6e08f4c7c32d4f71b54bda02913"
        val usdtBase = "base/mainnet/erc20/0xfde4c96c8593536e31f229ea8f37b2ada2699bb2"

        assertNull(BatteryRechargeAssets.method(listOf(method(USDC_BASE)), usdcArb))
        assertNull(BatteryRechargeAssets.method(listOf(method(USDC_BASE)), usdtBase))
        assertNull(BatteryRechargeAssets.method(listOf(method(SOL)), "eth/mainnet/coin"))
        assertNull(BatteryRechargeAssets.method(listOf(method(SOL)), "sol/mainnet/spl/So11111111111111111111111111111111111111112"))
    }

    @Test
    fun aBase58AddressMatchesOnlyAsItIs() {
        assertNotNull(BatteryRechargeAssets.method(listOf(method(USDT_TRON)), USDT_TRON))
        assertNull(BatteryRechargeAssets.method(listOf(method(USDT_TRON)), USDT_TRON.lowercase()))
    }

    @Test
    fun aTokenIsBadgedWithItsChain() {
        val usdtTon = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"

        assertEquals(Chain.Base.Mainnet, BatteryRechargeAssets.badgeChain(method(USDC_BASE)))
        assertEquals(Chain.Ton.Mainnet, BatteryRechargeAssets.badgeChain(method(usdtTon, type = RechargeMethodType.JETTON)))
    }

    @Test
    fun aChainsOwnCoinHasNoBadge() {
        assertNull(BatteryRechargeAssets.badgeChain(method(TON, type = RechargeMethodType.TON)))
        assertNull(BatteryRechargeAssets.badgeChain(method(SOL, type = RechargeMethodType.COIN)))
    }

    @Test
    fun aCoinBorrowedFromAnotherChainIsBadgedWithItsChain() {
        assertEquals(Chain.Base.Mainnet, BatteryRechargeAssets.badgeChain(method("base/mainnet/coin", type = RechargeMethodType.COIN)))
    }

    @Test
    fun aMethodWithoutAKnownChainHasNoBadge() {
        assertNull(BatteryRechargeAssets.badgeChain(method(USDC_BASE).copy(chain = "")))
        assertNull(BatteryRechargeAssets.badgeChain(method(USDC_BASE).copy(chain = "unknown/mainnet")))
    }

    private fun method(
        assetId: String,
        type: RechargeMethodType = RechargeMethodType.ERC20,
        supportRecharge: Boolean = true,
    ) = RechargeMethodEntity(
        type = type,
        rate = "0.8",
        symbol = "X",
        decimals = 6,
        supportGasless = true,
        supportRecharge = supportRecharge,
        chain = assetId.split('/').take(2).joinToString("/"),
        assetId = assetId,
    )

    private fun account(assetId: String, balance: String, price: String? = "1"): AccountWithDetails {
        val parts = assetId.split('/')
        val decimals = if (assetId == SOL) {
            9
        } else {
            6
        }
        return AccountWithDetails(
            data = AccountEntity(
                walletId = "wallet",
                network = parts[0],
                mode = parts[1],
                displayAddress = "address",
                publicKey = "",
                segwitPublicKey = "",
            ),
            balance = AccountBalanceEntity(available = balance),
            asset = AssetEntity(id = assetId, name = "X", symbol = "X", decimals = decimals, imageUrl = ""),
            rate = price?.let { AssetRateEntity(price = it, percentChange24h = "0", currencyCode = "USD") },
        )
    }

    private companion object {
        const val USDC_BASE = "base/mainnet/erc20/0x833589fcd6edb6e08f4c7c32d4f71b54bda02913"
        const val USDT_TRON = "tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val SOL = "sol/mainnet/coin"
        const val TON = "ton/mainnet/coin"
        const val UNLISTED = "eth/mainnet/erc20/0xdac17f958d2ee523a2206206994597c13d831ec7"
    }
}
