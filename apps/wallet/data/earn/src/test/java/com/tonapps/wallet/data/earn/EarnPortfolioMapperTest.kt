package com.tonapps.wallet.data.earn

import io.earnapi.models.AssetInfo
import io.earnapi.models.Balance
import io.earnapi.models.Position
import io.earnapi.models.Vault
import io.earnapi.models.VaultsFyiRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.OffsetDateTime

class EarnPortfolioMapperTest {

    @Test
    fun amountsComeInTheUserCurrency() {
        val portfolio = balance(
            total = mapOf("USD" to "10.00", "EUR" to "9.00"),
            position = mapOf("USD" to "10.00", "EUR" to "9.00"),
        ).toEntity("eur")

        assertEquals("EUR", portfolio.currency)
        assertEquals(BigDecimal("9.00"), portfolio.totalFiat)
        assertEquals(BigDecimal("9.00"), portfolio.positions.single().balanceFiat)
        assertEquals(BigDecimal("10.00"), portfolio.totalUsd)
    }

    @Test
    fun missingRateFallsBackToDollarsForEveryAmount() {
        val portfolio = balance(
            total = mapOf("USD" to "10.00"),
            position = mapOf("USD" to "10.00", "RUB" to "800.00"),
        ).toEntity("RUB")

        assertEquals("USD", portfolio.currency)
        assertEquals(BigDecimal("10.00"), portfolio.positions.single().balanceFiat)
    }

    @Test
    fun amountWithoutRateIsUnknownNotZero() {
        val portfolio = balance(
            total = mapOf("USD" to "10.00"),
            position = mapOf("EUR" to "9.00"),
        ).toEntity("USD")

        assertNull(portfolio.positions.single().balanceFiat)
    }

    @Test
    fun vaultApyComesAsPercent() {
        assertEquals(BigDecimal("6.74"), vault().toEntity().apy.stripTrailingZeros())
    }

    @Test
    fun offerIsTheHighestApyVaultOfItsAsset() {
        val offers = listOf(
            vault(underlying = "arb/mainnet/erc20/0xweth", vaultId = "0xlow", apy = 0.015),
            vault(underlying = "arb/mainnet/erc20/0xweth", vaultId = "0xhigh", apy = 0.0277),
            vault(underlying = "base/mainnet/erc20/0xusdc", vaultId = "0xusdc", apy = 0.05),
        ).bestOffers()

        assertEquals("0xhigh", offers.getValue("arb/mainnet/erc20/0xweth").vaultId)
        assertEquals("0xusdc", offers.getValue("base/mainnet/erc20/0xusdc").vaultId)
    }

    @Test
    fun offerKeepsTheFirstOfEqualApys() {
        val offers = listOf(
            vault(vaultId = "0xfirst", apy = 0.03),
            vault(vaultId = "0xsecond", apy = 0.03),
        ).bestOffers()

        assertEquals("0xfirst", offers.values.single().vaultId)
    }

    @Test
    fun offerMatchesTheAssetIgnoringCase() {
        val offers = listOf(
            vault(underlying = "BASE/mainnet/erc20/0xUSDC", vaultId = "0xupper", apy = 0.04),
            vault(underlying = "base/mainnet/erc20/0xusdc", vaultId = "0xlower", apy = 0.02),
        ).bestOffers()

        assertEquals("0xupper", offers.getValue("base/mainnet/erc20/0xusdc").vaultId)
    }

    private fun vault(
        underlying: String = "base/mainnet/erc20/0xusdc",
        vaultId: String = "0xvault",
        apy: Double = 0.0674,
    ): Vault = Vault(
        assetId = "base/mainnet/erc20/0xvault",
        symbol = "mwUSDC",
        name = "Moonwell Flagship USDC",
        asset = AssetInfo(
            assetId = underlying,
            name = "USD Coin",
            symbol = "USDC",
            decimals = 6,
            image = "",
        ),
        protocolName = "Moonwell",
        protocolLogo = "",
        apy = apy,
        tvlFiat = emptyMap(),
        score = 0.0,
        vaultsFyi = VaultsFyiRef(network = "base", vaultId = vaultId),
    )

    private fun balance(total: Map<String, String>, position: Map<String, String>): Balance = Balance(
        totalFiat = total,
        updatedAt = OffsetDateTime.parse("2026-10-28T12:00:00Z"),
        positions = listOf(
            Position(
                vault = vault(),
                balance = "10000000",
                balanceFiat = position,
                earnedFiat = position.mapValues { "0" },
            ),
        ),
    )
}
