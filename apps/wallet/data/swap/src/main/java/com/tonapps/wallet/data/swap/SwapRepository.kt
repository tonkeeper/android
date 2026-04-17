package com.tonapps.wallet.data.swap

import android.content.Context
import com.tonapps.extensions.map
import com.tonapps.extensions.mapList
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toListParcel
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.swap.entity.SwapAssetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SwapRepository(
    private val context: Context,
    private val api: API,
    private val scope: CoroutineScope,
) : BlobDataSource<List<SwapAssetEntity>>(
    context = context,
    path = "swap",
    timeout = TimeUnit.DAYS.toMillis(1)
) {

    private companion object {
        private const val ASSETS_KEY = "assets"
    }

    val assetsFlow = flow {
        emit(getAssets())
        emit(getAssets(true))
    }.mapList { it.currency }.stateIn(scope, SharingStarted.Lazily, null).filterNotNull()

    suspend fun getAssets(ignoreCache: Boolean = false): List<SwapAssetEntity> = withContext(Dispatchers.IO) {
        val cached = if (ignoreCache) {
            null
        } else {
            getCache(ASSETS_KEY)
        }
        cached ?: loadAssets()
    }

    private fun loadAssets(): List<SwapAssetEntity> {
        val list = api.getSwapAssets().map(::SwapAssetEntity)
        if (list.isEmpty()) {
            return emptyList()
        }
        setCache(ASSETS_KEY, list)
        return list
    }

    override fun onMarshall(data: List<SwapAssetEntity>) = data.toByteArray()

    override fun onUnmarshall(bytes: ByteArray) = bytes.toListParcel<SwapAssetEntity>()
}
