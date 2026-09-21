package com.tonapps.wallet.features.events.data

import androidx.compose.runtime.Immutable
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.wallet.api.API
import io.tonapi.models.NftItem
import io.tonapi.models.TrustType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
data class HistoryNft(
    val address: String,
    val name: String,
    val collectionName: String,
    val imageUrl: String,
    val isVerified: Boolean,
    val isUnverified: Boolean,
)

class HistoryNftResolver(
    private val api: API,
) {
    private val mutex = Mutex()
    private val requestedAddresses = mutableSetOf<String>()
    private val nftsByAddress = MutableStateFlow<Map<String, HistoryNft>>(emptyMap())

    val nfts: StateFlow<Map<String, HistoryNft>> = nftsByAddress.asStateFlow()

    suspend fun resolve(
        activities: List<HistoryEventEntity>,
        network: TonNetwork,
    ) {
        val addresses = mutex.withLock {
            val cached = nftsByAddress.value
            activities.mapNotNull { it.nftAddress }
                .distinct()
                .filter { it !in requestedAddresses && it !in cached }
                .also { requestedAddresses += it }
        }
        if (addresses.isEmpty()) {
            return
        }

        val items = try {
            withContext(Dispatchers.IO) {
                api.getNftsByAddresses(
                    addresses = addresses,
                    network = network,
                )
            }
        } catch (e: CancellationException) {
            releaseClaims(addresses)
            throw e
        } catch (_: Exception) {
            null
        }
        if (items == null) {
            releaseClaims(addresses)
            return
        }

        val resolved = addresses.mapNotNull { address ->
            items.firstOrNull { it.address.equalsAddress(address) }
                ?.toHistoryNft()
                ?.let { address to it }
        }.toMap()

        if (resolved.isNotEmpty()) {
            nftsByAddress.update { it + resolved }
        }
    }

    private suspend fun releaseClaims(addresses: List<String>) {
        withContext(NonCancellable) {
            mutex.withLock { requestedAddresses -= addresses }
        }
    }
}

fun Map<String, HistoryNft>.nftFor(activity: HistoryEventEntity): HistoryNft? =
    activity.nftAddress?.let(::get)

private fun NftItem.toHistoryNft(): HistoryNft? {
    val isUnverified = trust == TrustType.none
    return when (trust) {
        TrustType.blacklist -> null
        TrustType.whitelist, TrustType.graylist, TrustType.none -> HistoryNft(
            address = address,
            name = nftDisplayName(),
            collectionName = if (isUnverified) {
                ""
            } else {
                collection?.name.orEmpty()
            },
            imageUrl = nftImageUrl(),
            isVerified = trust == TrustType.whitelist,
            isUnverified = isUnverified,
        )
    }
}

private fun NftItem.nftDisplayName(): String {
    val metadataName = metadata["name"]?.asString()
    if (!metadataName.isNullOrBlank()) {
        return metadataName
    }
    val dnsName = dns
    if (!dnsName.isNullOrBlank()) {
        return dnsName
    }
    return "NFT"
}

private fun NftItem.nftImageUrl(): String {
    val previews = previews.orEmpty()
    if (previews.isEmpty()) {
        return ""
    }
    val preferred = previews.firstOrNull { preview ->
        val size = preview.resolution.substringBefore('x').toIntOrNull() ?: 0
        size in 256..1024
    }
    return (preferred ?: previews.last()).url
}
