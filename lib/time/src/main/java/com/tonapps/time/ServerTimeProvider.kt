package com.tonapps.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import kotlin.math.abs

/**
 * The app's server UTC time (see [ServerClock]), learned from the `Date` header of server
 * responses (see `com.tonapps.network.interceptor.ServerTimeInterceptor`).
 *
 * Between launches in one boot it is kept as the server time at boot, which no change of the
 * device clock moves; after a reboot it starts from the device time. Until a response of this
 * launch confirms it, [isSynced] is false.
 */
class ServerTimeProvider(context: Context) : TimeProvider {

    private val clock = ServerClock(SystemClock::elapsedRealtime, System::currentTimeMillis)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN_BOOT)
    private val lock = Any()

    private var storedBootMillis: Long? = null

    /** Whether a response in this launch set the time; before that it is the restored or the device time. */
    val isSynced: Boolean
        get() = clock.isSynced

    init {
        // elapsedRealtime starts over at boot, so a time kept in another boot does not apply.
        val sameBoot = bootCount != UNKNOWN_BOOT && prefs.getInt(BOOT_COUNT_KEY, UNKNOWN_BOOT) == bootCount
        if (sameBoot && prefs.contains(BOOT_MILLIS_KEY)) {
            val bootMillis = prefs.getLong(BOOT_MILLIS_KEY, 0)
            storedBootMillis = bootMillis
            clock.restore(bootMillis)
        }
    }

    override fun currentTimeMillis(): Long = clock.currentTimeMillis()

    /**
     * Takes the `Date` of a response to a request sent at [sentAt] and answered at [receivedAt],
     * both [SystemClock.elapsedRealtime].
     */
    fun onServerDate(dateMillis: Long, sentAt: Long, receivedAt: Long) {
        clock.onServerDate(dateMillis, sentAt, receivedAt)
        storeBootMillis()
    }

    private fun storeBootMillis() {
        if (bootCount == UNKNOWN_BOOT) {
            return
        }

        val bootMillis = clock.bootMillis() ?: return
        synchronized(lock) {
            val stored = storedBootMillis
            if (stored != null && abs(bootMillis - stored) < BOOT_MILLIS_TOLERANCE_MILLIS) {
                return
            }

            storedBootMillis = bootMillis
            prefs.edit {
                putLong(BOOT_MILLIS_KEY, bootMillis)
                putInt(BOOT_COUNT_KEY, bootCount)
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "server_time"
        const val BOOT_MILLIS_KEY = "boot_millis"
        const val BOOT_COUNT_KEY = "boot_count"
        const val UNKNOWN_BOOT = -1

        // Skips writes for the sub-second moves of each date narrowing the range.
        const val BOOT_MILLIS_TOLERANCE_MILLIS = 100L
    }
}
