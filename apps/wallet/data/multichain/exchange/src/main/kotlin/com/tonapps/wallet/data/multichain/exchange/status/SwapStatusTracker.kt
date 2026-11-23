package com.tonapps.wallet.data.multichain.exchange.status

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.extensions.AppLifecycleProvider
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.account.toApiChain
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.realtime.McWalletRealtimeProvider
import com.tonapps.wallet.data.settings.SettingsRepository
import io.walletapi.models.Activity
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import io.walletapi.models.Chain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

class SentSwap(
    val walletId: String,
    val txHash: String?,
    val sell: AssetEntity,
    val sendAmount: BigInteger,
    val buy: AssetEntity,
    val receiveAmount: BigInteger,
)

enum class SwapStatus { Pending, Done, Failed }

data class SwapStatusToast(val swap: SentSwap, val status: SwapStatus, val amount: String)

class SwapStatusTracker(
    private val api: API,
    realtime: McWalletRealtimeProvider,
    appLifecycle: AppLifecycleProvider,
    settingsRepository: SettingsRepository,
) {

    private data class Tracked(
        val swap: SentSwap,
        val sentAt: Long = System.currentTimeMillis(),
        val activity: Activity? = null,
        val pinned: Boolean = true,
    ) {
        val status: SwapStatus
            get() = when (activity?.status) {
                ActivityStatus.confirmed -> SwapStatus.Done
                ActivityStatus.failed, ActivityStatus.dropped -> SwapStatus.Failed
                else -> SwapStatus.Pending
            }
    }

    private val scope = Async.mainScope()
    private val state = MutableStateFlow<Tracked?>(null)
    private val opened = MutableStateFlow<Tracked?>(null)
    private val earlierTxIds = mutableSetOf<String>()
    private var refreshJob: Job? = null
    private var foreground = false

    val toast: StateFlow<SwapStatusToast?> = combine(state, opened, settingsRepository.hiddenBalancesFlow) { tracked, opened, hidden ->
        val pinned = tracked?.takeIf { it.pinned && opened == null } ?: return@combine null
        SwapStatusToast(pinned.swap, pinned.status, formatSwapAmount(pinned.swap.sell, pinned.swap.sendAmount, hidden))
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val details: StateFlow<SwapDetails?> = combine(opened, settingsRepository.hiddenBalancesFlow) { tracked, hidden ->
        tracked?.let { swapDetails(it.swap, it.status, it.activity, it.sentAt, hidden) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val Activity.isExchange: Boolean
        get() = activityType == ActivityType.swap.value || activityType == ActivityType.bridge.value

    init {
        scope.launch {
            merge(
                appLifecycle.observeWithoutGrace.onEach { foreground = it },
                realtime.activityHints.filter { it == state.value?.swap?.walletId },
            ).collect { restart(0) }
        }
    }

    fun track(swap: SentSwap) {
        state.value?.activity?.let { earlierTxIds += it.txIds }
        state.value = Tracked(swap)
        restart(POLL_INTERVALS[0])
    }

    fun hide(toast: SwapStatusToast) {
        val tracked = state.value
        if (tracked != null && tracked.swap === toast.swap && tracked.status == toast.status) {
            state.value = tracked.copy(pinned = false)
        }
    }

    fun openDetails() {
        opened.value = state.value
    }

    fun closeDetails() {
        val swap = opened.value?.swap
        opened.value = null
        val tracked = state.value
        if (tracked != null && tracked.swap === swap && tracked.status != SwapStatus.Pending) {
            state.value = tracked.copy(pinned = false)
        }
    }

    private fun restart(delayMs: Long) {
        refreshJob?.cancel()
        val swap = state.value?.takeIf { foreground && it.status == SwapStatus.Pending }?.swap ?: return
        refreshJob = scope.launch {
            delay(delayMs)
            var attempt = 0
            while (refresh(swap)) {
                attempt++
                delay(POLL_INTERVALS[min(attempt, POLL_INTERVALS.lastIndex)])
            }
        }
    }

    private suspend fun refresh(swap: SentSwap): Boolean {
        val activities = try {
            withContext(Async.Io) {
                api.multichain.wallets.getWalletActivities(
                    walletId = swap.walletId,
                    limit = HISTORY_LIMIT,
                    network = null,
                    xWalletId = swap.walletId,
                ).activities
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.w(e, "SwapStatus history request failed")
            emptyList()
        }
        val tracked = state.value?.takeIf { it.swap === swap } ?: return false
        val updated = tracked.copy(activity = match(tracked, activities) ?: tracked.activity)
        state.value = updated.copy(pinned = tracked.pinned || updated.status != tracked.status)
        if (opened.value?.swap === swap) {
            opened.value = updated
        }
        if (updated.status != SwapStatus.Pending) {
            return false
        }
        val limit = if (updated.activity == null) {
            UNSEEN_LIMIT_MS
        } else {
            PENDING_LIMIT_MS
        }
        if (System.currentTimeMillis() - updated.sentAt > limit) {
            L.w("SwapStatus gave up on hash=${swap.txHash} seen=${updated.activity != null}")
            state.value = null
            return false
        }
        return true
    }

    private fun match(tracked: Tracked, activities: List<Activity>): Activity? {
        val swap = tracked.swap
        val hash = swap.txHash
        if (hash != null) {
            val target = normalizedTxId(hash, swap.sell.valueOrNull?.chain?.toApiChain()?.value)
            val matches = activities.filter { activity ->
                activity.txIds.any { normalizedTxId(it, activity.fromChain.ifBlank { activity.toChain }) == target }
            }
            val candidates = matches.filter { it.isExchange }.ifEmpty { matches }
            return candidates.firstOrNull { it.status != ActivityStatus.pending } ?: candidates.firstOrNull()
        }
        val amount = swap.sendAmount.toString().toBigInteger()
        return activities
            .filter { activity ->
                activity.isExchange &&
                    activity.outToken?.assetId == swap.sell.id &&
                    activity.outAmount?.unsigned() == amount &&
                    activity.blockTime.toInstant().toEpochMilli() >= tracked.sentAt - GASLESS_WINDOW_MS &&
                    activity.txIds.none { it in earlierTxIds }
            }
            .minByOrNull { it.blockTime }
    }

    private fun normalizedTxId(raw: String, chain: String?): String {
        val value = raw.trim().lowercase()
        val colon = value.indexOf(':')
        if (colon > 0 && Chain.decode(value.substring(0, colon)).let { it != null && it != Chain.unknown }) {
            return value
        }
        return "${chain ?: "multichain"}:$value"
    }

    private companion object {
        val POLL_INTERVALS = listOf(5_000L, 10_000L, 20_000L, 30_000L)
        const val UNSEEN_LIMIT_MS = 10 * 60_000L
        const val PENDING_LIMIT_MS = 60 * 60_000L
        const val GASLESS_WINDOW_MS = 60_000L
        const val HISTORY_LIMIT = 50
    }
}
