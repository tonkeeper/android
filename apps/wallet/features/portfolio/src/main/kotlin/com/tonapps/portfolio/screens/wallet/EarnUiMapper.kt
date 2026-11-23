package com.tonapps.portfolio.screens.wallet

import androidx.annotation.DrawableRes
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.SignMode
import com.tonapps.core.extensions.fiatRate
import com.tonapps.core.extensions.formatFiat
import com.tonapps.core.extensions.toDisplayUnit
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.earn.entities.EarnPositionEntity
import com.tonapps.wallet.data.earn.entities.EarnVaultEntity
import java.math.BigDecimal

internal object EarnUiMapper {

    private const val UNKNOWN_AMOUNT = "-"

    fun mapVault(vault: EarnVaultEntity): EarnVaultUi = EarnVaultUi(
        network = vault.network,
        vaultId = vault.vaultId,
        assetSymbol = vault.assetSymbol,
        assetIconUrl = vault.assetIconUrl,
        networkIcon = network(vault.network)?.icon,
        apyFormat = CurrencyFormatter.formatPercent(vault.apy).toString(),
        protocolName = vault.protocolName,
    )

    fun mapPosition(position: EarnPositionEntity, currency: String, hiddenBalance: Boolean): EarnPositionUi {
        val earnNetwork = network(position.network)
        return EarnPositionUi(
            network = position.network,
            vaultId = position.vaultId,
            assetSymbol = position.assetSymbol,
            assetIconUrl = position.assetIconUrl,
            networkIcon = earnNetwork?.icon,
            networkName = earnNetwork?.name ?: position.network,
            protocolName = position.protocolName,
            balanceFormat = formatFiat(currency, position.balanceFiat, hiddenBalance),
            earnedFormat = position.earnedFiat?.takeUnless { hiddenBalance }?.let { formatEarned(currency, it) },
            isProfit = (position.earnedFiat?.signum() ?: 0) >= 0,
        )
    }

    fun formatFiat(currency: String, value: BigDecimal?, hiddenBalance: Boolean): String = maskIf(hiddenBalance) {
        value?.let { Coins.of(it).formatFiat(currency) } ?: UNKNOWN_AMOUNT
    }

    private inline fun maskIf(hidden: Boolean, format: () -> String): String = if (hidden) {
        HIDDEN_BALANCE
    } else {
        format()
    }

    private fun formatEarned(currency: String, earned: BigDecimal): String? {
        if (earned.signum() == 0) {
            return null
        }
        return Formatter.formatFiat(
            value = Coins.of(earned).toDisplayUnit(),
            rate = fiatRate(currency, Coins.ONE),
            sign = SignMode.All,
        )
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