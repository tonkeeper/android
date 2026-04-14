package com.tonapps.tonkeeper.ui.base

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.async.Async
import com.tonapps.log.L
import com.tonapps.tonkeeper.extensions.loading
import com.tonapps.tonkeeper.extensions.showToast
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import uikit.base.BaseFragment
import uikit.navigation.Navigation
import java.lang.ref.WeakReference

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

    protected val navigation: Navigation?
        get() = Navigation.from(context)

    @Suppress("MemberVisibilityCanBePrivate")
    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        L.e(throwable, "Coroutine Exception Handler")
    }

    private val job = SupervisorJob()

    @Suppress("MemberVisibilityCanBePrivate")
    protected val commonScope = job + exceptionHandler

    // Bg
    protected val bgDispatcher = Async.Io
    protected val bgScope = CoroutineScope(commonScope + bgDispatcher)

    open fun attachHolder(holder: Holder) {
        holderRef = WeakReference(holder)
    }

    fun <T> Flow<T>.launch() = this.launchIn(viewModelScope)

    fun <T> Flow<T>.collectFlow(action: suspend (T) -> Unit) = this.onEach { action(it) }.launch()

    fun detachHolder() {
        holderRef?.clear()
        holderRef = null
    }

    fun getString(resId: Int) = context.getString(resId)

    fun getString(resId: Int, vararg formatArgs: Any) = context.getString(resId, *formatArgs)

    override fun onCleared() {
        super.onCleared()
        detachHolder()
        job.cancel()
    }

    @UiThread
    suspend fun finish() = withContext(Dispatchers.Main) {
        holder?.finish()
    }

    suspend fun toast(@StringRes resId: Int) = withContext(Dispatchers.Main) {
        context.showToast(resId)
    }

    suspend fun toast(text: String) = withContext(Dispatchers.Main) {
        context.showToast(text)
    }

    suspend fun loading(loading: Boolean) = withContext(Dispatchers.Main) {
        context.loading(loading)
    }

    suspend fun openScreen(screen: BaseFragment) = withContext(Dispatchers.Main) {
        try {
            navigation?.add(screen)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}