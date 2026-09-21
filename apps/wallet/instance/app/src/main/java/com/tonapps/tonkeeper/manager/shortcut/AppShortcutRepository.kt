package com.tonapps.tonkeeper.manager.shortcut

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.tonapps.extensions.putBoolean
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import com.tonapps.tonkeeper.ui.screen.root.ShortcutDeeplinkActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppShortcutRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("app_shortcut", Context.MODE_PRIVATE)

    private val mutex = Mutex()

    // URLs seen on our own legacy pins while migrating them (with the elapsedRealtime of the
    // sighting), kept so a pin tap validates even when the migration already re-targeted the pin
    // away from RootActivity. Only the tap racing that migration is legitimate, so entries expire
    // after [LEGACY_PIN_TTL_MS] — a forged intent cannot replay a recorded URL for the rest of the
    // process lifetime. Guarded by [mutex].
    private val legacyPinUrls = mutableMapOf<String, Long>()

    // In-memory hand-off from ShortcutDeeplinkActivity to RootActivity. Buffered so a URL
    // submitted before RootActivity exists (cold start) or while it is stopped (warm start) is
    // not lost; a Channel delivers exactly once, so a consumed URL is never replayed to a
    // recreated collector.
    private val pendingDeeplink = Channel<Uri>(Channel.BUFFERED)
    val pendingDeeplinkFlow: Flow<Uri> = pendingDeeplink.receiveAsFlow()

    fun submitDeeplink(uri: Uri) {
        pendingDeeplink.trySend(uri)
    }

    suspend fun migrateLegacyPinnedShortcuts() {
        mutex.withLock { migrateLocked(force = false) }
    }

    // Validation for legacy pins that still deliver "dapp_deeplink" to the exported RootActivity:
    // requestPinShortcut binds a pin to the creating package, so a URL found on one of our own
    // pinned shortcuts cannot have been forged by another app. Force-migrates the legacy pins as
    // part of the same critical section, so validation cannot race the migration.
    suspend fun migrateAndResolveLegacyPin(url: String): Boolean = mutex.withLock {
        migrateLocked(force = true)
        val recordedAt = legacyPinUrls[url] ?: return@withLock false
        SystemClock.elapsedRealtime() - recordedAt <= LEGACY_PIN_TTL_MS
    }

    private fun migrateLocked(force: Boolean) {
        if (!force && prefs.getBoolean(MIGRATION_DONE_KEY, false)) {
            return
        }
        try {
            val pinned = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_PINNED
            )
            var droppedLegacyPin = false
            val updated = pinned.mapNotNull { old ->
                val oldIntent = old.intent ?: return@mapNotNull null
                if (oldIntent.component?.className != RootActivity::class.java.name) {
                    return@mapNotNull null
                }
                val url = oldIntent.getStringExtra(ShortcutDeeplinkActivity.EXTRA_DAPP_DEEPLINK)
                    ?: return@mapNotNull null
                // Recorded before the label-dependent rebuild below, so a pin the platform reports
                // without a label still validates on tap instead of going dead.
                legacyPinUrls[url] = SystemClock.elapsedRealtime()
                val label = old.shortLabel
                if (label == null) {
                    // A legacy pin we cannot rebuild (Builder requires a label) stays on the
                    // exported path, so the migration must not be marked done.
                    droppedLegacyPin = true
                    return@mapNotNull null
                }
                // No icon set: updateShortcuts merges via copyNonNullFieldsFrom, so the existing
                // pinned icon is preserved. id + label kept, intent re-targeted.
                ShortcutInfoCompat.Builder(context, old.id)
                    .setShortLabel(label)
                    .setIntent(ShortcutDeeplinkActivity.intent(context, url))
                    .build()
            }
            val migrated = updated.isEmpty() || ShortcutManagerCompat.updateShortcuts(context, updated)
            if (migrated && !droppedLegacyPin) {
                prefs.putBoolean(MIGRATION_DONE_KEY, true)
            }
        } catch (e: Throwable) {
            // Flag not set on failure, so the migration is retried on the next launch.
            L.e(e)
        }
    }

    private companion object {
        private const val MIGRATION_DONE_KEY = "legacy_dapp_pins_migrated"
        private const val LEGACY_PIN_TTL_MS = 15_000L
    }
}
