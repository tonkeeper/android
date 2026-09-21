package com.tonapps.wallet.features.events.components

import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.HistoryNft
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import io.walletapi.models.Chain
import java.time.OffsetDateTime

private const val PREVIEW_TON_ASSET_ID = "ton/mainnet/coin"
private const val PREVIEW_LUPA_ASSET_ID =
    "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
private const val PREVIEW_LP_ASSET_ID =
    "ton/mainnet/jetton/0:1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809"
private const val PREVIEW_SPAM_ASSET_ID =
    "ton/mainnet/jetton/0:9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a0"
private const val PREVIEW_NFT_ASSET_ID =
    "ton/mainnet/nft/0:5c4b3a2918f7e6d5c4b3a2918f7e6d5c4b3a2918f7e6d5c4b3a2918f7e6d5c4b"
private const val PREVIEW_FROM_ADDRESS = "UQAjKz9pQ8xVn2sT4bLmR7dYfH3kW6cX1uZaE5gJvNqT_ZxP"
private const val PREVIEW_TO_ADDRESS = "UQBm7HdC2vXq5NpR8sLtY4gKw1eZfA6bJ3nM9uVxD0iS_H1F"

private val PREVIEW_BLOCK_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-13T21:56:00Z")

private val PREVIEW_TON_ASSET = AssetEntity(
    id = PREVIEW_TON_ASSET_ID,
    name = "Toncoin",
    symbol = "TON",
    decimals = 9,
    imageUrl = "",
)

private val PREVIEW_LUPA_ASSET = AssetEntity(
    id = PREVIEW_LUPA_ASSET_ID,
    name = "Lupa",
    symbol = "LUPA",
    decimals = 9,
    imageUrl = "",
)

private val PREVIEW_LP_ASSET = AssetEntity(
    id = PREVIEW_LP_ASSET_ID,
    name = "Bolt Gram LP",
    symbol = "BOLT-GRAM LP",
    decimals = 9,
    imageUrl = "",
)

private val PREVIEW_SPAM_ASSET = AssetEntity(
    id = PREVIEW_SPAM_ASSET_ID,
    name = "Claim 5 000 USDT at ton-gift.org",
    symbol = "Claim 5 000 USDT at ton-gift.org",
    decimals = 9,
    imageUrl = "",
    verification = AssetEntity.Verification.blacklist,
)

private val PREVIEW_UNVERIFIED_ASSET = AssetEntity(
    id = PREVIEW_LUPA_ASSET_ID,
    name = "Gram Points",
    symbol = "GRAMP",
    decimals = 9,
    imageUrl = "",
    verification = AssetEntity.Verification.none,
)

internal val PREVIEW_NFT = HistoryNft(
    address = "0:5c4b3a2918f7e6d5c4b3a2918f7e6d5c4b3a2918f7e6d5c4b3a2918f7e6d5c4b",
    name = "Whale #1042",
    collectionName = "Whales Club",
    imageUrl = "",
    isVerified = true,
    isUnverified = false,
)

internal val PREVIEW_DOMAIN_NFT = HistoryNft(
    address = PREVIEW_TO_ADDRESS,
    name = "tonkeepertesttesttest.ton",
    collectionName = "TON DNS Domains",
    imageUrl = "",
    isVerified = true,
    isUnverified = false,
)

private fun previewActivity(
    activityType: ActivityType,
    inAsset: AssetEntity? = null,
    inAmount: String? = null,
    outAsset: AssetEntity? = null,
    outAmount: String? = null,
    status: ActivityStatus = ActivityStatus.confirmed,
    comment: String? = null,
    isSpam: Boolean = false,
    feeAsset: AssetEntity? = null,
    feeAmount: String? = null,
    protocol: String? = null,
): HistoryEventEntity {
    val direction = when (activityType) {
        ActivityType.receive, ActivityType.mint -> ActivityDirection.`in`
        ActivityType.send -> ActivityDirection.out
        else -> ActivityDirection.self
    }
    return HistoryEventEntity(
        activityType = activityType,
        status = status,
        blockTime = PREVIEW_BLOCK_TIME,
        walletAddress = PREVIEW_TO_ADDRESS,
        fromAddress = PREVIEW_FROM_ADDRESS,
        toAddress = PREVIEW_TO_ADDRESS,
        fromChain = Chain.ton,
        toChain = Chain.ton,
        inAmount = inAmount,
        outAmount = outAmount,
        inAmountUsd = null,
        outAmountUsd = null,
        feeAmount = feeAmount,
        feeAmountUsd = null,
        inAsset = inAsset,
        outAsset = outAsset,
        feeAsset = feeAsset,
        txIds = emptyList(),
        blockNumber = null,
        protocol = protocol,
        explorerUrl = null,
        comment = comment,
        direction = direction,
        isSpam = isSpam,
    )
}

internal fun previewShortReceive() = previewActivity(
    activityType = ActivityType.receive,
    inAsset = PREVIEW_LUPA_ASSET,
    inAmount = "24150000000",
)

internal fun previewLongAmountSend() = previewActivity(
    activityType = ActivityType.send,
    outAsset = PREVIEW_LP_ASSET,
    outAmount = "10",
)

internal fun previewSpamReceive() = previewActivity(
    activityType = ActivityType.receive,
    inAsset = PREVIEW_SPAM_ASSET,
    inAmount = "5000000000000",
    isSpam = true,
)

internal fun previewFailedSend() = previewActivity(
    activityType = ActivityType.send,
    outAsset = PREVIEW_TON_ASSET,
    outAmount = "1500000000",
    status = ActivityStatus.failed,
)

internal fun previewPendingSend() = previewActivity(
    activityType = ActivityType.send,
    outAsset = PREVIEW_TON_ASSET,
    outAmount = "1500000000",
    status = ActivityStatus.pending,
)

internal fun previewPendingReceive() = previewActivity(
    activityType = ActivityType.receive,
    inAsset = PREVIEW_TON_ASSET,
    inAmount = "24150000000",
    status = ActivityStatus.pending,
)

internal fun previewPendingSwap() = previewActivity(
    activityType = ActivityType.swap,
    inAsset = PREVIEW_LUPA_ASSET,
    inAmount = "1284500000000",
    outAsset = PREVIEW_TON_ASSET,
    outAmount = "42000000000",
    status = ActivityStatus.pending,
)

internal fun previewSwap() = previewActivity(
    activityType = ActivityType.swap,
    inAsset = PREVIEW_LUPA_ASSET,
    inAmount = "1284500000000",
    outAsset = PREVIEW_TON_ASSET,
    outAmount = "42000000000",
)

internal fun previewUnverifiedReceive() = previewActivity(
    activityType = ActivityType.receive,
    inAsset = PREVIEW_UNVERIFIED_ASSET,
    inAmount = "125000000000",
)

internal fun previewCommentSend() = previewActivity(
    activityType = ActivityType.send,
    outAsset = PREVIEW_TON_ASSET,
    outAmount = "3200000000",
    comment = "Call: 0x2e3af4a91c7b5d08e6f4c2a1b9d8e7f6a5c4b3d2e1f0a9b8c7d6e5f4a3b2c1d0",
)

internal fun previewNftReceive() = previewActivity(
    activityType = ActivityType.receive,
    inAsset = AssetEntity(
        id = PREVIEW_NFT_ASSET_ID,
        name = "Whale #1042",
        symbol = "NFT",
        decimals = 0,
        imageUrl = "",
    ),
)

internal fun previewDomainRenew() = previewActivity(
    activityType = ActivityType.dns_renew,
    feeAsset = PREVIEW_TON_ASSET,
    feeAmount = "52000000",
    protocol = "tonkeepertesttesttest.ton",
)
