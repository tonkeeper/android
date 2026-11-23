package com.tonapps.wallet.data.earn

import com.tonapps.wallet.data.earn.entities.EarnAssetOfferEntity
import com.tonapps.wallet.data.earn.entities.EarnPortfolioEntity
import com.tonapps.wallet.data.earn.entities.EarnPositionEntity
import com.tonapps.wallet.data.earn.entities.EarnVaultEntity
import io.earnapi.models.Balance
import io.earnapi.models.Position
import io.earnapi.models.Vault
import java.math.BigDecimal

internal const val EARN_USD = "USD"

internal fun Balance.toEntity(currency: String): EarnPortfolioEntity {
    val totals = totalFiat.amounts()
    val code = currency.uppercase().takeIf { it in totals } ?: EARN_USD
    return EarnPortfolioEntity(
        currency = code,
        totalFiat = totals[code],
        totalUsd = totals[EARN_USD] ?: BigDecimal.ZERO,
        positions = positions.map { it.toEntity(code) },
    )
}

private fun Position.toEntity(currency: String): EarnPositionEntity = EarnPositionEntity(
    network = vault.vaultsFyi.network,
    vaultId = vault.vaultsFyi.vaultId,
    assetSymbol = vault.asset.symbol,
    protocolName = vault.protocolName,
    balanceFiat = balanceFiat.amounts()[currency],
    earnedFiat = earnedFiat.amounts()[currency],
    assetIconUrl = vault.asset.image.ifEmpty { null },
)

private fun Map<String, String>.amounts(): Map<String, BigDecimal> = entries
    .mapNotNull { (code, amount) -> amount.toBigDecimalOrNull()?.let { code.uppercase() to it } }
    .toMap()

internal fun Vault.toEntity(): EarnVaultEntity = EarnVaultEntity(
    network = vaultsFyi.network,
    vaultId = vaultsFyi.vaultId,
    assetSymbol = asset.symbol,
    protocolName = protocolName,
    apy = apyPercent(),
    assetIconUrl = asset.image.ifEmpty { null },
)

internal fun List<Vault>.bestOffers(): Map<String, EarnAssetOfferEntity> =
    groupBy { it.asset.assetId.lowercase() }
        .mapValues { (_, vaults) -> vaults.maxBy(Vault::apy).toOffer() }

internal fun Vault.toOffer(): EarnAssetOfferEntity = EarnAssetOfferEntity(
    network = vaultsFyi.network,
    vaultId = vaultsFyi.vaultId,
    apy = apyPercent(),
)

private fun Vault.apyPercent(): BigDecimal = BigDecimal(apy.toString()).movePointRight(2)
