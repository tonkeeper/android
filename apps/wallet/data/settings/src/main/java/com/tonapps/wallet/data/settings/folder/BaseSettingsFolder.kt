package com.tonapps.wallet.data.settings.folder

import android.content.Context
import android.content.SharedPreferences
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.getByteArray
import kotlinx.coroutines.flow.asSharedFlow

internal abstract class BaseSettingsFolder(
    private val context: Context,
    private val name: String
) {

    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val _changedFlow = MutableEffectFlow<Unit>()
    val changedFlow = _changedFlow.asSharedFlow()

    init {
        notifyChanged()
    }

    fun notifyChanged() {
        _changedFlow.tryEmit(Unit)
    }

    fun contains(key: String) = prefs.contains(key)

    fun getBoolean(key: String, defValue: Boolean = false) = prefs.getBoolean(key, defValue)

    fun getInt(key: String, defValue: Int = 0) = prefs.getInt(key, defValue)

    fun getLong(key: String, defValue: Long = 0) = prefs.getLong(key, defValue)

    fun putLong(key: String, value: Long, notify: Boolean = true) {
        prefs.edit().putLong(key, value).apply()
        if (notify) {
            notifyChanged()
        }
    }

    fun putBoolean(key: String, value: Boolean, notify: Boolean = true) {
        prefs.edit().putBoolean(key, value).apply()
        if (notify) {
            notifyChanged()
        }
    }

    fun putInt(key: String, value: Int, notify: Boolean = true) {
        prefs.edit().putInt(key, value).apply()
        if (notify) {
            notifyChanged()
        }
    }

    fun edit(notify: Boolean = true, block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        if (notify) {
            notifyChanged()
        }
    }
}