package com.tonapps.tonkeeper.core

import com.tonapps.icu.Coins
import com.tonapps.icu.Coins.Companion.sumOf
import com.tonapps.legacy.enteties.AssetsEntity
import com.tonapps.legacy.enteties.AssetsEntity.Token
import com.tonapps.legacy.enteties.AssetsExtendedEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity

// TODO remove
suspend fun List<AssetsEntity>.sort(
    wallet: WalletEntity,
    settingsRepository: SettingsRepository
): List<AssetsEntity> {
    return map { asset ->
        val pref = if (asset is Token) {
            settingsRepository.getTokenPrefs(wallet.id, asset.token.address, asset.token.blacklist)
        } else {
            TokenPrefsEntity()
        }
        AssetsExtendedEntity(asset, pref, wallet.accountId)
    }.filter { !it.hidden }.sortedWith(AssetsExtendedEntity.comparator).map { it.raw }
}

fun List<AssetsEntity>.sumOfVerifiedFiat(): Coins {
    return sumOf {
        if (it !is Token || it.token.verified) {
            it.fiat
        } else {
            Coins.ZERO
        }
    }
}
