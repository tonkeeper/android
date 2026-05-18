package com.tonapps.wallet.data.purchase

import android.content.Context
import com.tonapps.extensions.CacheKey
import com.tonapps.extensions.TimedCacheMemory
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.wallet.data.purchase.entity.MerchantEntity
import com.tonapps.wallet.data.purchase.entity.OnRamp
import com.tonapps.wallet.data.purchase.entity.PurchaseCategoryEntity
import com.tonapps.wallet.data.purchase.entity.PurchaseDataEntity
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import io.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class PurchaseRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val api: API
) : BlobDataSource<PurchaseDataEntity>(
    context = context,
    path = "purchase",
    timeout = TimeUnit.DAYS.toMillis(1)
) {

    sealed interface Keys : CacheKey {
        data object Merchants : Keys
    }

    private val timedCache = TimedCacheMemory<Keys.Merchants>()

    private val onRampCache = simple<OnRamp.Data>(context, "onRamp", TimeUnit.DAYS.toMillis(14))
    private val merchantsCache = simpleJSON<List<MerchantEntity>>(context,"merchants", TimeUnit.DAYS.toMillis(1))

    fun onRampDataFlow() = flow {
        getOnRampDataCache()?.let { emit(it) }
        fetchOnRampDataCache()?.let { emit(it) }
    }.flowOn(Dispatchers.IO)

    private fun loadOnRampMerchants(): List<MerchantEntity> {
        return try {
            val data = api.getOnRampMerchants() ?: throw Exception("No merchants found")
            Serializer.JSON.decodeFromString<List<MerchantEntity>>(data)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun getMerchants(): List<MerchantEntity> {
        var list = merchantsCache.getCache("main") ?: emptyList()
        if (list.isEmpty()) {
            list = loadOnRampMerchants()
            merchantsCache.setCache("main", list)
        }
        return list
    }

    suspend fun getPaymentMethods(currency: String): List<OnRamp.PaymentMethodMerchant> = withContext(Dispatchers.IO) {
        try {
            val data = api.getOnRampPaymentMethods(currency) ?: throw Exception("No payment methods found for country: ${api.country}")
            L.d("PurchaseRepositoryLog", "getPaymentMethods: $data")
            Serializer.JSON.decodeFromString<List<OnRamp.PaymentMethodMerchant>>(data)
        } catch (e: Throwable) {
            L.e("PurchaseRepositoryLog", "error", e)
            emptyList()
        }
    }

    fun getOnRampDataCache(): OnRamp.Data? {
        val cacheKey = "data_${api.country}"
        return onRampCache.getCache(cacheKey)
    }

    suspend fun fetchOnRampDataCache(): OnRamp.Data? {
        return runCatching {
            timedCache.getOrLoad(Keys.Merchants) {
                Serializer.fromJSON<OnRamp.Data>(
                    string = api.getOnRampData()
                        .orEmpty()
                )
            }
        }.getOrNull()
    }

    fun get(
        network: TonNetwork,
        country: String,
        locale: Locale,
    ): Pair<List<PurchaseCategoryEntity>, List<PurchaseCategoryEntity>>? {
        val data = get(network, locale) ?: return null
        val methods = data.getCountry(country).methods
        return filterMethods(data.buy, methods) to filterMethods(data.sell, methods)
    }

    private fun filterMethods(
        categories: List<PurchaseCategoryEntity>,
        methods: List<String>
    ): List<PurchaseCategoryEntity> {
        val list = mutableListOf<PurchaseCategoryEntity>()
        for (category in categories) {
            if (category.type == "swap") {
                list.add(category.copy(items = category.items))
            } else {
                val items = category.items.filter {
                    methods.contains(it.id)
                }
                if (items.isNotEmpty()) {
                    // Sort by methods
                    val sortedItems = items.sortedBy {
                        methods.indexOf(it.id)
                    }
                    val categoryEntity = category.copy(
                        items = sortedItems
                    )
                    list.add(categoryEntity)
                }
            }
        }
        return list
    }

    fun getMethod(id: String, network: TonNetwork, locale: Locale): PurchaseMethodEntity? {
        val data = get(network, locale) ?: return null
        val methods = (data.buy + data.sell).map { it.items }.flatten()
        return methods.find { it.id == id }
    }

    private fun get(network: TonNetwork, locale: Locale): PurchaseDataEntity? {
        val key = cacheKey(network, locale)
        var data = getCache(key)
        if (data == null) {
            data = load(network, locale) ?: return null
            setCache(key, data)
        }
        return data
    }

    private fun load(network: TonNetwork, locale: Locale): PurchaseDataEntity? {
        val json = api.getFiatMethods(network, locale) ?: return null
        return PurchaseDataEntity(json)
    }

    private fun cacheKey(network: TonNetwork, locale: Locale): String {
        return "${network.name.lowercase()}-${locale.language}"
    }

    override fun onMarshall(data: PurchaseDataEntity) = data.toByteArray()

    override fun onUnmarshall(bytes: ByteArray) = bytes.toParcel<PurchaseDataEntity>()
}