package com.tonapps.wallet.features.events.data

import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class HistoryPerpsMarketResolver(
    private val perpsRepository: PerpsRepository,
) {
    private val mutex = Mutex()
    private val requestedIndices = mutableSetOf<Int>()
    private val marketsByIndex = MutableStateFlow<Map<Int, PerpMarket>>(emptyMap())

    val markets: StateFlow<Map<Int, PerpMarket>> = marketsByIndex.asStateFlow()

    suspend fun resolve(activities: List<HistoryEventEntity>) {
        val indices = mutex.withLock {
            activities.mapNotNull { it.perps?.marketIndex }
                .distinct()
                .filter { it !in requestedIndices }
                .also { requestedIndices += it }
        }
        if (indices.isEmpty()) {
            return
        }

        val markets = try {
            perpsRepository.getMarketsByIndices(indices).getOrNull()
        } catch (e: CancellationException) {
            releaseClaims(indices)
            throw e
        }
        val resolved = markets.orEmpty().associateBy { it.marketIndex }
        if (resolved.isNotEmpty()) {
            marketsByIndex.update { known -> known + resolved }
        }

        val unresolved = indices.filterNot(resolved::containsKey)
        if (unresolved.isNotEmpty()) {
            releaseClaims(unresolved)
        }
    }

    private suspend fun releaseClaims(indices: List<Int>) {
        withContext(NonCancellable) {
            mutex.withLock { requestedIndices -= indices }
        }
    }
}
