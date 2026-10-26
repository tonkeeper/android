package com.tonapps.portfolio.screens.wallet

import androidx.annotation.DrawableRes
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.earn.entities.EarnPositionEntity
import com.tonapps.wallet.data.earn.entities.EarnVaultEntity
import java.math.BigDecimal

internal object EarnUiMapper {

    private const val USD = "USD"

    fun mapVault(vault: EarnVaultEntity): EarnVaultUi = EarnVaultUi(
        network = vault.network,
        vaultId = vault.vaultId,
        assetSymbol = vault.assetSymbol,
        assetIconUrl = vault.assetIconUrl,
        networkIcon = network(vault.network)?.icon,
        apyFormat = CurrencyFormatter.formatPercent(vault.apy).toString(),
        protocolName = vault.protocolName,
    )

    fun mapPosition(position: EarnPositionEntity, hiddenBalance: Boolean): EarnPositionUi {
        val earnNetwork = network(position.network)
        return EarnPositionUi(
            network = position.network,
            vaultId = position.vaultId,
            assetSymbol = position.assetSymbol,
            assetIconUrl = position.assetIconUrl,
            networkIcon = earnNetwork?.icon,
            networkName = earnNetwork?.name ?: position.network,
            protocolName = position.protocolName,
            balanceFormat = maskIf(hiddenBalance) { formatUsd(position.balanceUsd) },
            earnedFormat = maskIf(hiddenBalance) { formatEarned(position.earnedUsd) },
        )
    }

    fun formatTotal(totalUsd: BigDecimal, hiddenBalance: Boolean): String =
        maskIf(hiddenBalance) { formatUsd(totalUsd) }

    // The wallet's own formatter, so the block reads like the balance and assets above it.
    // TODO format in the user's currency with TK-4257: the API returns every amount per requested
    //  currency, and the Earn flow then has to follow currency changes too
    private fun formatUsd(value: BigDecimal): String = CurrencyFormatter.formatFiat(USD, value).toString()

    private inline fun maskIf(hidden: Boolean, format: () -> String): String = if (hidden) {
        HIDDEN_BALANCE
    } else {
        format()
    }

    // The design only draws a gain, and the formatter writes the minus itself, so a loss is left as it
    // comes until TK-3787 says whether the API can even report one.
    private fun formatEarned(earned: BigDecimal): String {
        val formatted = formatUsd(earned)
        return if (earned.signum() > 0) {
            "+ $formatted"
        } else {
            formatted
        }
    }

    // One table, so an icon and a label can never drift apart. These are the names vaults.fyi uses,
    // and "mainnet" is its word for Ethereum — elsewhere in the app that word means "not testnet".
    // TODO match the networks TK-3786 settles on, once the backend contract names them
    private fun network(network: String): EarnNetwork? = when (network.lowercase()) {
        "mainnet", "ethereum" -> EarnNetwork(UIKitIcon.ic_eth, "Ethereum")
        "base" -> EarnNetwork(UIKitIcon.ic_base, "Base")
        "arbitrum" -> EarnNetwork(UIKitIcon.ic_arb, "Arbitrum")
        "bsc" -> EarnNetwork(UIKitIcon.ic_bsc, "BSC")
        else -> null
    }

    private data class EarnNetwork(
        @get:DrawableRes val icon: Int,
        val name: String,
    )
}