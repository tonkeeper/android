package com.tonapps.tonkeeper.ui.base

import android.app.Application
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import uikit.navigation.Navigation
import uikit.navigation.Navigation.Companion.navigation
import java.lang.ref.WeakReference

abstract class BaseWalletVM(
    app: Application
): AndroidViewModel(app) {

    private var activityRef: WeakReference<FragmentActivity>? = null

    val activity: FragmentActivity?
        get() = activityRef?.get()

    val navigation: Navigation?
        get() = activity?.navigation

    val context: Context
        get() = activity ?: getApplication()

    fun attachActivity(activity: FragmentActivity) {
        activityRef = WeakReference(activity)
    }

    fun detachActivity() {
        activityRef?.clear()
        activityRef = null
    }

    fun getString(resId: Int) = context.getString(resId)

    fun getString(resId: Int, vararg formatArgs: Any) = context.getString(resId, *formatArgs)

    override fun onCleared() {
        super.onCleared()
        detachActivity()
    }
}