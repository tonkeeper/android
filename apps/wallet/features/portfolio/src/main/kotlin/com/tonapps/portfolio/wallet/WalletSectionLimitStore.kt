package com.tonapps.portfolio.wallet

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WalletSectionLimit(val count: Int) {
    Top3(3),
    Top10(10),
    Top25(25),
}

enum class WalletLimitedSection(internal val key: String) {
    Assets("assets"),
    Earn("earn"),
}

class WalletSectionLimitStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val limits = WalletLimitedSection.entries.associateWith { section ->
        MutableStateFlow(read(section))
    }

    fun limit(section: WalletLimitedSection): StateFlow<WalletSectionLimit> = limits.getValue(section).asStateFlow()

    fun setLimit(section: WalletLimitedSection, limit: WalletSectionLimit) {
        prefs.edit { putInt(section.key, limit.count) }
        limits.getValue(section).value = limit
    }

    private fun read(section: WalletLimitedSection): WalletSectionLimit {
        val count = prefs.getInt(section.key, DEFAULT_LIMIT.count)
        return WalletSectionLimit.entries.firstOrNull { it.count == count } ?: DEFAULT_LIMIT
    }

    private companion object {
        const val PREFERENCES_NAME = "wallet_section_limits"
        val DEFAULT_LIMIT = WalletSectionLimit.Top3
    }
}
