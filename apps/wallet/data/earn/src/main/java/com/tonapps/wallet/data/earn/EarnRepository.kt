package com.tonapps.wallet.data.earn

import com.tonapps.wallet.data.earn.entities.EarnAssetOfferEntity
import com.tonapps.wallet.data.earn.entities.EarnPortfolioEntity
import com.tonapps.wallet.data.earn.entities.EarnPositionEntity
import com.tonapps.wallet.data.earn.entities.EarnVaultEntity
import java.math.BigDecimal

// The same vaults iOS ships, so both apps open the same pages until TK-3786 serves the real ones.
class EarnRepository {

    fun featuredVaults(): List<EarnVaultEntity> = VAULTS

    fun portfolio(): EarnPortfolioEntity = PORTFOLIO

    // Asset ids come lowercased from the wallet, but the mocks were copied from iOS as written.
    fun assetOffer(assetId: String): EarnAssetOfferEntity? =
        ASSET_OFFERS.firstOrNull { it.assetId.equals(assetId, ignoreCase = true) }

    private companion object {

        private val ASSET_OFFERS = listOf(
            EarnAssetOfferEntity(
                assetId = "eth/mainnet/erc20/0xdAC17F958D2ee523a2206206994597C13D831ec7",
                network = "mainnet",
                vaultId = "0x23878914EFE38d27C4D67Ab83ed1b93A74D4086a",
                apy = BigDecimal("2.58"),
            ),
            EarnAssetOfferEntity(
                assetId = "eth/mainnet/erc20/0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                network = "mainnet",
                vaultId = "0x98C23E9d8f34FEFb1B7BD6a91B7FF122F4e16F5c",
                apy = BigDecimal("3.73"),
            ),
        )

        private val PORTFOLIO = EarnPortfolioEntity(
            totalUsd = BigDecimal("14.32"),
            positions = listOf(
                EarnPositionEntity(
                    network = "mainnet",
                    vaultId = "0x98C23E9d8f34FEFb1B7BD6a91B7FF122F4e16F5c",
                    assetSymbol = "USDC",
                    protocolName = "Aave",
                    balanceUsd = BigDecimal("1.92"),
                    earnedUsd = BigDecimal("0.02"),
                    assetIconUrl = null,
                ),
                EarnPositionEntity(
                    network = "base",
                    vaultId = "0xc1256Ae5FF1cf2719D4937adb3bbCCab2E00A2Ca",
                    assetSymbol = "USDC",
                    protocolName = "Moonwell",
                    balanceUsd = BigDecimal("12.40"),
                    earnedUsd = BigDecimal("0.31"),
                    assetIconUrl = null,
                ),
            ),
        )

        private val VAULTS = listOf(
            EarnVaultEntity(
                network = "mainnet",
                vaultId = "0x23878914EFE38d27C4D67Ab83ed1b93A74D4086a",
                assetSymbol = "USDT",
                protocolName = "Aave",
                apy = BigDecimal("2.58"),
                assetIconUrl = null,
            ),
            EarnVaultEntity(
                network = "mainnet",
                vaultId = "0x98C23E9d8f34FEFb1B7BD6a91B7FF122F4e16F5c",
                assetSymbol = "USDC",
                protocolName = "Aave",
                apy = BigDecimal("3.73"),
                assetIconUrl = null,
            ),
            EarnVaultEntity(
                network = "mainnet",
                vaultId = "0x4d5F47FA6A74757f35C14fD3a6Ef8E3C9BC514E8",
                assetSymbol = "WETH",
                protocolName = "Aave",
                apy = BigDecimal("1.45"),
                assetIconUrl = null,
            ),
            EarnVaultEntity(
                network = "base",
                vaultId = "0xc1256Ae5FF1cf2719D4937adb3bbCCab2E00A2Ca",
                assetSymbol = "USDC",
                protocolName = "Moonwell",
                apy = BigDecimal("6.74"),
                assetIconUrl = null,
            ),
            EarnVaultEntity(
                network = "arbitrum",
                vaultId = "0x724dc807b04555b71ed48a6896b6F41593b8C637",
                assetSymbol = "USDC",
                protocolName = "Aave",
                apy = BigDecimal("3.73"),
                assetIconUrl = null,
            ),
            EarnVaultEntity(
                network = "bsc",
                vaultId = "0xfD5840Cd36d94D7229439859C0112a4185BC0255",
                assetSymbol = "USDT",
                protocolName = "Venus",
                apy = BigDecimal("3.56"),
                assetIconUrl = null,
            ),
        )
    }
}