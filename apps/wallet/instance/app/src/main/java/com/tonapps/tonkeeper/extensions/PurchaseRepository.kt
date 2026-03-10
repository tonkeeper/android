package com.tonapps.tonkeeper.extensions

import com.tonapps.tonkeeper.Environment
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.purchase.entity.OnRamp
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

data class OnRampResult(
    val data: OnRamp.Data,
    val country: String
)


suspend fun PurchaseRepository.getProvidersByCountry(
    wallet: WalletEntity,
    settingsRepository: SettingsRepository,
    country: String
): List<PurchaseMethodEntity> = withContext(Dispatchers.IO) {
    val methods = get(wallet.network, country, settingsRepository.getLocale()) ?: return@withContext emptyList()
    val all = methods.first + methods.second
    all.map { it.items }.flatten().distinctBy { it.title }
}