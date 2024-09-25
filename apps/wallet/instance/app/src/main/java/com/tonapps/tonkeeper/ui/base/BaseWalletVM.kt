package com.tonapps.tonkeeper.ui.base

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.isUIThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.observeOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.navigation.Navigation
import uikit.navigation.Navigation.Companion.navigation
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

abstract class BaseWalletVM(
    app: Application
): AndroidViewModel(app) {

    interface Holder {
        val uiContext: Context?
        fun finish()
    }

    class EmptyViewViewModel(app: Application): BaseWalletVM(app)

    private var holderRef: WeakReference<Holder>? = null

    private val holder: Holder?
        get() = holderRef?.get()

    val context: Context
        get() = holder?.uiContext ?: getApplication()

    fun attachHolder(holder: Holder) {
        holderRef = WeakReference(holder)
    }

    fun <T> Flow<T>.launch() {
        this.launchIn(viewModelScope)
    }

    fun <T> Flow<T>.collectFlow(action: suspend (T) -> Unit) {
        this.onEach { action(it) }.launch()
    }

    fun detachHolder() {
        holderRef?.clear()
        holderRef = null
    }

    fun getString(resId: Int) = context.getString(resId)

    fun getString(resId: Int, vararg formatArgs: Any) = context.getString(resId, *formatArgs)

    override fun onCleared() {
        super.onCleared()
        detachHolder()
    }

    @UiThread
    fun finish() {
        if (isUIThread) {
            holder?.finish()
        } else {
            throw IllegalStateException("finish() must be called from UI thread")
        }
    }
}