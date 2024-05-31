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

    fun getBoolean(key: String, defValue: Boolean = false) = prefs.getBoolean(key, defValue)

    fun getInt(key: String, defValue: Int = 0) = prefs.getInt(key, defValue)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        notifyChanged()
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        notifyChanged()
    }

    fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        notifyChanged()
    }
}