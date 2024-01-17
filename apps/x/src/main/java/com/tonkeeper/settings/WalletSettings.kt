package com.tonkeeper.settings

import android.content.Context
import com.tonkeeper.event.WalletSettingsEvent
import core.EventBus

class WalletSettings(context: Context) {

    private companion object {
        private const val RECOVERY_PHRASE_BACKUP_KEY = "recovery_phrase_backup"
    }

    private val prefs = context.getSharedPreferences("wallet_settings", Context.MODE_PRIVATE)

    fun isRecoveryPhraseBackup(accountId: String): Boolean {
        return prefs.getBoolean(key(accountId, RECOVERY_PHRASE_BACKUP_KEY), false)
    }

    fun setRecoveryPhraseBackup(accountId: String, isBackup: Boolean) {
        prefs.edit().putBoolean(key(accountId, RECOVERY_PHRASE_BACKUP_KEY), isBackup).apply()

        EventBus.post(WalletSettingsEvent)
    }

    private fun key(accountId: String, key: String): String {
        return "$accountId:$key"
    }
}