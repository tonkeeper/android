package com.tonkeeper.api

import com.tonkeeper.core.Coin
import io.tonapi.infrastructure.Serializer
import io.tonapi.models.AccountAddress
import io.tonapi.models.ImagePreview
import io.tonapi.models.JettonBalance
import io.tonapi.models.JettonPreview
import io.tonapi.models.JettonSwapAction
import io.tonapi.models.NftItem
import io.tonapi.models.TonTransferAction
import kotlinx.coroutines.delay
import org.ton.block.AddrStd

private val nftItemPreviewSizes = arrayOf(
    "250x250", "500x500", "100x100"
)

suspend fun <R> withRetry(
    times: Int = 5,
    delay: Long = 1000,
    block: () -> R
): R {
    var lastError: Throwable? = null
    for (i in 0 until times) {
        try {
            return block()
        } catch (e: Throwable) {
            lastError = e
        }
        delay(delay)
    }
    throw lastError!!
}

inline fun <reified T> toJSON(obj: T): String {
    return Serializer.moshi.adapter(T::class.java).toJson(obj)
}

inline fun <reified T> fromJSON(json: String): T {
    return Serializer.moshi.adapter(T::class.java).fromJson(json)!!
}

val JettonSwapAction.jettonPreview: JettonPreview?
    get() {
        return jettonMasterIn ?: jettonMasterOut
    }

val JettonSwapAction.amount: String
    get() {
        if (amountIn.isEmpty()) {
            return amountOut
        }
        return amountIn
    }

val JettonSwapAction.ton: Long
    get() {
        return tonIn ?: tonOut ?: 0
    }

val AccountAddress.nameOrAddress: String
    get() {
        if (!name.isNullOrBlank()) {
            return name!!
        }
        return address.userLikeAddress.shortAddress
    }

val AccountAddress.iconURL: String?
    get() = icon

val String.shortAddress: String
    get() {
        if (length < 8) return this
        return substring(0, 4) + "…" + substring(length - 4, length)
    }

val String.userLikeAddress: String
    get() {
        return try {
            AddrStd.toString(AddrStd.parse(this))
        } catch (e: Throwable) {
            this
        }
    }

val JettonBalance.address: String
    get() = jetton.address

val JettonBalance.symbol: String
    get() = jetton.symbol

val JettonBalance.parsedBalance: Float
    get() = Coin.parseFloat(balance, jetton.decimals)

fun NftItem.imageBySize(size: String): ImagePreview? {
    return previews?.firstOrNull { it.resolution == size }
}

val NftItem.imageURL: String
    get() {
        for (size in nftItemPreviewSizes) {
            val preview = imageBySize(size)
            if (preview != null) {
                return preview.url
            }
        }
        return previews?.lastOrNull()?.url ?: ""
    }

val NftItem.title: String
    get() {
        val metadataName = metadata["name"] as? String
        if (metadataName != null) {
            return metadataName
        }
        return collection?.name ?: ""
    }

val NftItem.description: String?
    get() {
        val metadataName = metadata["description"] as? String
        if (metadataName != null) {
            return metadataName
        }
        return null
    }

val NftItem.collectionName: String
    get() {
        return collection?.name ?: ""
    }

val NftItem.collectionDescription: String?
    get() {
        return collection?.description
    }

val NftItem.ownerAddress: String?
    get() {
        return owner?.address?.userLikeAddress
    }
