package com.tonapps.tonkeeper.ui.base

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
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

    fun finish() {
        holder?.finish()
    }
}