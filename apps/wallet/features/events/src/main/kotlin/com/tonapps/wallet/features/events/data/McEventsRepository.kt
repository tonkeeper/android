package com.tonapps.wallet.features.events.data

import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.toApiChain
import com.tonapps.wallet.data.multichain.account.toAssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import io.walletapi.models.Activity
import io.walletapi.models.ActivityType
import io.walletapi.models.AssetInfo
import io.walletapi.models.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class McEventsRepository(
    private val api: API,
    private val accountRepository: McAccountRepository,
) {

    private data class FirstPageKey(
        val walletId: String,
        val chain: Chain?,
        val activityType: ActivityType?,
        val assetId: String?,
        val isSpam: Boolean?,
        val hideDust: Boolean,
    )

    private val firstPageCache = ConcurrentHashMap<FirstPageKey, WalletActivitiesPage>()

    fun invalidateFirstPageCache() {
        firstPageCache.clear()
    }

    suspend fun getWalletActivities(
        walletId: String,
        limit: Int,
        cursor: String?,
        chain: Chain?,
        activityType: ActivityType?,
        assetId: String? = null,
        isSpam: Boolean? = null,
        hideDust: Boolean = false,
    ): WalletActivitiesPage = withContext(Dispatchers.IO) {
        val key = FirstPageKey(walletId, chain, activityType, assetId, isSpam, hideDust)
        if (cursor == null) {
            firstPageCache[key]?.let { return@withContext it }
        }
        val response = api.multichain.wallets.getWalletActivities(
            walletId = walletId,
            limit = limit,
            cursor = cursor,
            chain = chain,
            network = null,
            activityType = activityType,
            assetId = assetId,
            isSpam = isSpam,
            hideDust = hideDust,
            xWalletId = walletId,
        )
        val activities = filterPrimaryTonActivities(
            walletId = walletId,
            activities = response.activities.map(::mapActivityToEntity),
        )
        val page = WalletActivitiesPage(
            activities = activities,
            cursor = response.nextCursor?.takeIf { it.isNotBlank() },
        )
        if (cursor == null && activities.isNotEmpty()) {
            firstPageCache[key] = page
        }
        page
    }

    suspend fun hasSpamActivities(
        walletId: String,
        chain: Chain?,
        assetId: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        var cursor: String? = null
        var pagesConsumed = 0
        do {
            val requestCursor = cursor
            val response = api.multichain.wallets.getWalletActivities(
                walletId = walletId,
                limit = SPAM_PROBE_PAGE_SIZE,
                cursor = requestCursor,
                chain = chain,
                network = null,
                activityType = null,
                assetId = assetId,
                isSpam = true,
                xWalletId = walletId,
            )
            val activities = filterPrimaryTonActivities(
                walletId = walletId,
                activities = response.activities.map(::mapActivityToEntity),
            )
            if (activities.isNotEmpty()) {
                return@withContext true
            }
            cursor = response.nextCursor?.takeIf { it.isNotBlank() && it != requestCursor }
            pagesConsumed++
        } while (cursor != null && pagesConsumed < MAX_PAGES_PER_RECENT_LOAD)
        false
    }

    suspend fun getRecentAssetActivities(
        walletId: String,
        assetId: String,
        limit: Int = RECENT_ASSET_ACTIVITIES_LIMIT,
    ): List<HistoryEventEntity> = withContext(Dispatchers.IO) {
        val chain = Asset.coinFromString(assetId)?.chain?.toApiChain()
        val items = mutableListOf<HistoryEventEntity>()
        var cursor: String? = null
        var pagesConsumed = 0
        do {
            val requestCursor = cursor
            val page = getWalletActivities(
                walletId = walletId,
                limit = limit,
                cursor = requestCursor,
                chain = chain,
                activityType = null,
                assetId = assetId,
                isSpam = false,
            )
            items += page.activities
            cursor = page.cursor?.takeIf { it.isNotBlank() && it != requestCursor }
            pagesConsumed++
        } while (items.size < limit && cursor != null && pagesConsumed < MAX_PAGES_PER_RECENT_LOAD)
        items.take(limit)
    }

    /**
     * One seed registers multiple TON variants (e.g. v4R2 + W5) under the same wallet_id.
     * History is wallet-scoped, so drop TON activities that belong to a non-primary account —
     * same selection as [McAccountRepository.getCoinAccounts] / balances.
     */
    private suspend fun filterPrimaryTonActivities(
        walletId: String,
        activities: List<HistoryEventEntity>,
    ): List<HistoryEventEntity> {
        val primaryTonAddress = accountRepository.getTonAccount(walletId)?.displayAddress
            ?: return activities
        return activities.filter { it.isPrimaryTonActivity(primaryTonAddress) }
    }

    private fun HistoryEventEntity.isPrimaryTonActivity(primaryTonAddress: String): Boolean {
        val address = walletAddress ?: return true
        if (!address.isValidTonAddress()) {
            return true
        }
        return address.equalsAddress(primaryTonAddress)
    }

    private companion object {
        const val RECENT_ASSET_ACTIVITIES_LIMIT = 25
        const val SPAM_PROBE_PAGE_SIZE = 25
        const val MAX_PAGES_PER_RECENT_LOAD = 10
    }
}

private fun mapActivityToEntity(activity: Activity): HistoryEventEntity =
    HistoryEventEntity(
        activityType = activity.activityType,
        status = activity.status,
        blockTime = activity.blockTime,
        walletAddress = activity.walletAddress.tonRawToUserFriendly(),
        fromAddress = activity.fromAddress.tonRawToUserFriendly(),
        toAddress = activity.toAddress.tonRawToUserFriendly(),
        fromChain = activity.fromChain,
        toChain = activity.toChain,
        inAmount = activity.inAmount,
        outAmount = activity.outAmount,
        inAmountUsd = activity.inAmountUsd,
        outAmountUsd = activity.outAmountUsd,
        feeAmount = activity.feeAmount,
        feeAmountUsd = activity.feeAmountUsd,
        inAsset = activity.inToken.toActivityAssetOrNull(),
        outAsset = activity.outToken.toActivityAssetOrNull(),
        feeAsset = activity.feeToken.toActivityAssetOrNull(),
        txIds = activity.txIds,
        blockNumber = activity.blockNumber,
        protocol = activity.protocol,
        explorerUrl = activity.explorerUrl?.takeIf { it.isNotBlank() },
        comment = activity.commentOrNull(),
        direction = activity.direction,
        isSpam = activity.isSpam == true,
        tronResource = activity.tronResourceOrNull(),
    )

private fun String?.tonRawToUserFriendly(): String? =
    if (this != null && contains(':')) {
        toUserFriendly(wallet = true, testnet = false)
    } else {
        this
    }

private fun Activity.commentOrNull(): String? {
    val value = meta?.get("memo")?.asPrimitive() ?: meta?.get("comment")?.asPrimitive()
    return when (value) {
        is String -> value.takeIf { it.isNotBlank() }
        is Number -> value.toString()
        else -> null
    }
}

private fun Activity.tronResourceOrNull(): TronResourceEntity? {
    val resource = meta?.get("tron_resource")?.asObject() ?: return null
    val energy = resource["energy"]?.asLong() ?: 0L
    val bandwidth = resource["bandwidth"]?.asLong() ?: 0L
    if (energy <= 0L && bandwidth <= 0L) {
        return null
    }
    return TronResourceEntity(energy = energy, bandwidth = bandwidth)
}

private fun AssetInfo?.toActivityAssetOrNull(): AssetEntity? =
    this?.takeIf { it.assetId.isNotBlank() }?.toAssetEntity()
