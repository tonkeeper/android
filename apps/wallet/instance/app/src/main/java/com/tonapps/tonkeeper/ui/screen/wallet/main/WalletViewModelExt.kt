package com.tonapps.tonkeeper.ui.screen.wallet.main

import com.tonapps.tonkeeper.core.entities.TokenExtendedEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

suspend fun List<AccountTokenEntity>.sortAndFilterTokens(
    wallet: WalletEntity,
    settingsRepository: SettingsRepository
) = map { token ->
    val pref = settingsRepository.getTokenPrefs(wallet.id, token.address, token.blacklist)
    TokenExtendedEntity(token, pref)
}.filter { !it.hidden }.sortedWith(TokenExtendedEntity.comparator).map { it.raw }

