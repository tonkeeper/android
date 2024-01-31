package uikit.base

import android.app.Application
import android.app.Dialog
import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import uikit.extensions.activity

open class BaseDialog(
    context: Context
): Dialog(context, uikit.R.style.Widget_Dialog), LifecycleOwner, ViewModelStoreOwner, ComponentCallbacks {

    private val lifecycleRegistry: LifecycleRegistry by lazy {
        LifecycleRegistry(this)
    }

    override val viewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    init {
        window?.let { applyWindow(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    private fun applyWindow(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyAttributes(window)
    }

    private fun applyAttributes(window: Window) {
        val attributes = window.attributes
        attributes.width = ViewGroup.LayoutParams.MATCH_PARENT
        attributes.height = ViewGroup.LayoutParams.MATCH_PARENT
        attributes.gravity = Gravity.TOP or Gravity.START
        attributes.dimAmount = 0f
        attributes.flags = attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
        window.attributes = attributes
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onPause()
    }

    fun getDefaultViewModelCreationExtras(): CreationExtras {
        var application: Application? = null
        var appContext: Context? = context.applicationContext
        while (appContext is ContextWrapper) {
            if (appContext is Application) {
                application = appContext
                break
            }
            appContext = appContext.baseContext
        }
        val extras = MutableCreationExtras()
        if (application != null) {
            extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] = application
        }
        extras[VIEW_MODEL_STORE_OWNER_KEY] = this
        return extras
    }

    override fun onConfigurationChanged(newConfig: Configuration) {

    }

    override fun onLowMemory() {

    }

    fun dismissAndDestroy() {
        super.dismiss()
        destroy()
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    open fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    open fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onStop() {
        super.onStop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    open fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }

    fun destroy() {
        if (isShowing) {
            super.dismiss()
        }
        onDestroy()
    }
}