package com.tonapps.wallet.data.banner

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BannerEntity
import com.tonapps.wallet.data.banner.entities.BannerDataEntity
import com.tonapps.wallet.data.banner.source.HiddenBannersDataSource
import com.tonapps.wallet.data.banner.source.LocalDataSource
import com.tonapps.wallet.data.banner.source.RemoteDataSource
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class BannerRepository(
    context: Context,
    api: API,
    private val settingsRepository: SettingsRepository,
) {

    private val localDataSource: LocalDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDataSource(context)
    }

    private val hiddenDataSource: HiddenBannersDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HiddenBannersDataSource(context)
    }

    private val remoteDataSource = RemoteDataSource(api)

    suspend fun getBanners(
        walletId: String,
        isMultichain: Boolean,
        network: TonNetwork = TonNetwork.MAINNET,
        refresh: Boolean = false,
    ): List<BannerEntity> = withContext(Dispatchers.IO) {
        val queryWalletId = walletId.takeIf { isMultichain }
        val data = if (refresh) {
            loadRemote(network, queryWalletId) ?: loadLocal(queryWalletId)
        } else {
            loadLocal(queryWalletId)
        } ?: return@withContext emptyList()
        filterHidden(walletId, data.banners)
    }

    /**
     * Emits cached banners first (if any) and then the freshly loaded remote banners.
     */
    fun getBannersFlow(
        walletId: String,
        isMultichain: Boolean,
        network: TonNetwork = TonNetwork.MAINNET,
    ): Flow<List<BannerEntity>> = flow {
        val queryWalletId = walletId.takeIf { isMultichain }
        loadLocal(queryWalletId)?.let { emit(filterHidden(walletId, it.banners)) }
        loadRemote(network, queryWalletId)?.let { emit(filterHidden(walletId, it.banners)) }
    }.flowOn(Dispatchers.IO)

    fun hideBanner(walletId: String, bannerId: String) {
        hiddenDataSource.hide(walletId, bannerId)
    }

    fun filterHidden(walletId: String, banners: List<BannerEntity>): List<BannerEntity> {
        val hidden = hiddenDataSource.getHidden(walletId)
        if (hidden.isEmpty()) {
            return banners
        }
        return banners.filter { it.id !in hidden }
    }

    private fun loadLocal(walletId: String?): BannerDataEntity? {
        return localDataSource.getCache(cacheKey(walletId))
    }

    private suspend fun loadRemote(network: TonNetwork, walletId: String?): BannerDataEntity? {
        val isNew = !settingsRepository.hadWalletsOnMultichainRelease
        val data = remoteDataSource.load(network, walletId, isNew) ?: return null
        localDataSource.setCache(cacheKey(walletId), data)
        return data
    }

    private fun cacheKey(walletId: String?): String {
        return walletId?.let { "${CACHE_KEY}_$it" } ?: CACHE_KEY
    }

    private companion object {
        private const val CACHE_KEY = "banner_data"
    }
}
