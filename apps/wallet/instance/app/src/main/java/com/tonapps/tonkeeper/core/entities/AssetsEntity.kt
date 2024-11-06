package com.tonapps.tonkeeper.core.entities

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

sealed class AssetsEntity(
    val fiat: Coins,
) {

    companion object {

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
    }

    data class Staked(val staked: StakedEntity): AssetsEntity(staked.fiatBalance) {

        val isTonstakers: Boolean
            get() = staked.isTonstakers

        val liquidToken: BalanceEntity?
            get() = staked.liquidToken

        val readyWithdraw: Coins
            get() = staked.readyWithdraw
    }

    data class Token(val token: AccountTokenEntity): AssetsEntity(token.fiat) {

        val address: String
            get() = token.address
    }
}