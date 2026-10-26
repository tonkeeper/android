package com.tonapps.wallet.features.events.data

import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.toApiChain
import com.tonapps.wallet.data.multichain.account.toAssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.perpsMarketIndexOrNull
import io.JsonAny
import io.walletapi.models.Activity
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityFeeType
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import io.walletapi.models.ActivityTypeFilter
import io.walletapi.models.AssetInfo
import io.walletapi.models.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

class McEventsRepository(
    private val api: API,
    private val accountRepository: McAccountRepository,
    private val eventsRepository: EventsRepository,
) {

    private data class FirstPageKey(
        val walletId: String,
        val chain: Chain?,
        val activityType: ActivityTypeFilter?,
        val assetId: String?,
        val isSpam: Boolean?,
        val hideDust: Boolean,
        val showPerps: Boolean,
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
        activityType: ActivityTypeFilter?,
        assetId: String? = null,
        isSpam: Boolean? = null,
        hideDust: Boolean = false,
        showPerps: Boolean = false,
    ): WalletActivitiesPage = withContext(Dispatchers.IO) {
        val key = FirstPageKey(walletId, chain, activityType, assetId, isSpam, hideDust, showPerps)
        if (cursor == null) {
            firstPageCache[key]?.let { return@withContext it }
        }
        val market = perpsMarketIndexOrNull(assetId)
        val response = api.multichain.wallets.getWalletActivities(
            walletId = walletId,
            limit = limit,
            cursor = cursor,
            chain = chain,
            network = null,
            activityType = activityType,
            assetId = assetId.takeIf { market == null },
            isSpam = isSpam,
            hideDust = hideDust,
            showPerps = showPerps,
            market = market,
            xWalletId = walletId,
        )
        val activities = filterPrimaryTonActivities(
            walletId = walletId,
            activities = response.activities.mapNotNull { mapActivityToEntity(it, eventsRepository::getDecryptedComment) },
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
        val market = perpsMarketIndexOrNull(assetId)
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
                assetId = assetId.takeIf { market == null },
                isSpam = true,
                market = market,
                xWalletId = walletId,
            )
            val activities = filterPrimaryTonActivities(
                walletId = walletId,
                activities = response.activities.mapNotNull { mapActivityToEntity(it, eventsRepository::getDecryptedComment) },
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

    suspend fun getRecentMarketActivities(
        walletId: String,
        marketIndex: Int,
        limit: Int = RECENT_MARKET_ACTIVITIES_LIMIT,
    ): List<HistoryEventEntity> = withContext(Dispatchers.IO) {
        val response = api.multichain.wallets.getWalletActivities(
            walletId = walletId,
            limit = limit,
            cursor = null,
            chain = null,
            network = null,
            activityType = null,
            assetId = null,
            isSpam = false,
            showPerps = true,
            market = marketIndex,
            xWalletId = walletId,
        )
        response.activities
            .mapNotNull { mapActivityToEntity(it, eventsRepository::getDecryptedComment) }
            .filter { it.perps?.marketIndex == marketIndex }
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
        const val RECENT_MARKET_ACTIVITIES_LIMIT = 5
        const val SPAM_PROBE_PAGE_SIZE = 25
        const val MAX_PAGES_PER_RECENT_LOAD = 10
    }
}

private fun mapActivityToEntity(activity: Activity, decryptedComment: (String) -> String?): HistoryEventEntity? {
    val activityType = ActivityType.decode(activity.activityType) ?: return null
    if (activity.status == ActivityStatus.unknown || activity.direction == ActivityDirection.unknown) {
        return null
    }
    val plainComment = activity.commentOrNull()
    val encrypted = if (plainComment == null) {
        activity.encryptedCommentOrNull()
    } else {
        null
    }
    val decrypted = encrypted?.let { decryptedComment(it.cipherText) }
    val isBatteryFee = activity.isBatteryFee()
    return HistoryEventEntity(
        activityType = activityType,
        status = activity.status,
        blockTime = activity.blockTime,
        walletAddress = activity.walletAddress.tonRawToUserFriendly(),
        fromAddress = activity.fromAddress.tonRawToUserFriendly(),
        toAddress = activity.toAddress.tonRawToUserFriendly(),
        fromChain = knownChain(activity.fromChain),
        toChain = knownChain(activity.toChain),
        inAmount = activity.inAmount,
        outAmount = activity.outAmount,
        inAmountUsd = activity.inAmountUsd,
        outAmountUsd = activity.outAmountUsd,
        feeAmount = activity.feeAmount.takeUnless { isBatteryFee },
        feeAmountUsd = activity.feeAmountUsd.takeUnless { isBatteryFee },
        inAsset = activity.inToken.toActivityAssetOrNull(),
        outAsset = activity.outToken.toActivityAssetOrNull(),
        feeAsset = activity.feeToken.toActivityAssetOrNull(),
        txIds = activity.txIds,
        blockNumber = activity.blockNumber,
        protocol = activity.protocol,
        explorerUrl = activity.explorerUrl?.takeIf { it.isNotBlank() },
        comment = plainComment ?: decrypted,
        direction = activity.direction,
        isSpam = activity.isSpam == true,
        tronResource = activity.tronResourceOrNull(),
        batteryCharges = activity.batteryChargesOrNull(),
        perps = activity.perpsOrNull(),
        encryptedComment = encrypted?.takeIf { decrypted == null },
    )
}

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

private fun Activity.perpsOrNull(): PerpsMetaEntity? {
    val perps = meta?.get("perps")?.asObject() ?: return null
    return PerpsMetaEntity(
        symbol = perps["symbol"]?.asString()?.takeIf { it.isNotBlank() },
        side = when (perps["side"]?.asString()) {
            "long" -> PerpsSide.Long
            "short" -> PerpsSide.Short
            else -> null
        },
        reason = when (perps["reason"]?.asString()) {
            "manual" -> PerpsCloseReason.Manual
            "take_profit" -> PerpsCloseReason.TakeProfit
            "stop_loss" -> PerpsCloseReason.StopLoss
            "liquidation" -> PerpsCloseReason.Liquidation
            else -> null
        },
        marketIndex = perpsMarketIndexOrNull(perps["asset_id"]?.asString()),
        leverage = perps.decimalOrNull("leverage")
            ?.setScale(0, RoundingMode.HALF_UP)
            ?.toInt()
            ?.takeIf { it > 0 },
        entryPrice = perps.decimalOrNull("entry_price"),
        closePrice = perps.decimalOrNull("close_price"),
        size = perps.decimalOrNull("size"),
        sizeUsd = perps.decimalOrNull("size_usd"),
        resultPct = perps.decimalOrNull("result_pct"),
        marginUsd = perps.decimalOrNull("margin_usd"),
        fundingUsd = perps.decimalOrNull("funding_usd"),
    )
}

private fun Map<String, JsonAny>.decimalOrNull(key: String): BigDecimal? =
    get(key)?.asString()?.toBigDecimalOrNull()

private fun Activity.encryptedCommentOrNull(): EncryptedCommentEntity? {
    val encrypted = meta?.get("encrypted_comment")?.asObject() ?: return null
    val cipherText = encrypted["cipher_text"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
    val sender = fromAddress?.takeIf { it.isNotBlank() } ?: return null
    return EncryptedCommentEntity(cipherText = cipherText, senderAddress = sender)
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

private fun Activity.isBatteryFee(): Boolean = (fee?.type ?: feeType) == ActivityFeeType.battery

private fun Activity.batteryChargesOrNull(): Int? {
    if (!isBatteryFee()) {
        return null
    }
    val charges = (fee?.amount ?: feeAmount)?.toBigDecimalOrNull()?.stripTrailingZeros() ?: return null
    if (charges.signum() < 0 || charges.scale() > 0 || charges > MAX_BATTERY_CHARGES) {
        return null
    }
    return charges.toInt()
}

private fun AssetInfo?.toActivityAssetOrNull(): AssetEntity? =
    this?.takeIf { it.assetId.isNotBlank() }?.toAssetEntity()

private fun knownChain(value: String): Chain? = Chain.decode(value)?.takeIf { it != Chain.unknown }

private val MAX_BATTERY_CHARGES = BigDecimal(Int.MAX_VALUE)
