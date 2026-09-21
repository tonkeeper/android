package com.tonapps.wallet.data.raffle.debug

import android.content.Context
import androidx.core.content.edit
import com.tonapps.lib.log.BuildConfig
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.raffle.RaffleClock
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.data.raffle.toEntity
import io.walletapi.models.ForcePickRaffleWinnersRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

class RaffleDebugStore(context: Context, private val api: API) {

    private val prefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.getSharedPreferences("raffle_debug", Context.MODE_PRIVATE)
    }

    val debugNowFlow: StateFlow<OffsetDateTime?> field = MutableStateFlow(read())

    val storyDedupeResetCount: StateFlow<Int> field = MutableStateFlow(0)

    init {
        RaffleClock.debugNow = debugNowFlow.value
    }

    var debugNow: OffsetDateTime?
        get() = debugNowFlow.value
        set(value) {
            if (!BuildConfig.DEBUG) {
                return
            }
            prefs.edit { putString(KEY, value?.toString()) }
            debugNowFlow.value = value
            RaffleClock.debugNow = value
        }

    fun resetStoryDedupe() {
        if (!BuildConfig.DEBUG) {
            return
        }
        storyDedupeResetCount.value++
    }

    suspend fun raffleAt(walletId: String, lang: String, at: OffsetDateTime?): RaffleEntity? =
        withContext(Dispatchers.IO) {
            api.multichain.raffles.getWalletRaffles(walletId, lang, null, at, xWalletId = walletId)
                .raffles
                .firstOrNull()
                ?.toEntity()
        }

    suspend fun pickWinner(walletId: String, raffleId: String, prizeId: String?) {
        withContext(Dispatchers.IO) {
            api.multichain.raffles.forcePickRaffleWinners(
                raffleId,
                ForcePickRaffleWinnersRequest(walletId = walletId, prizeId = prizeId),
            )
        }
    }

    private fun read(): OffsetDateTime? {
        if (!BuildConfig.DEBUG) {
            return null
        }
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            OffsetDateTime.parse(raw)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        private const val KEY = "debug_now"
    }
}
