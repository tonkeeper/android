package com.tonapps.core

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.tonapps.apps.wallet.features.core.R
import com.tonapps.wallet.data.settings.SettingsRepository
import org.koin.android.ext.android.inject
import ui.theme.AppColorScheme
import ui.theme.MoonTheme
import ui.theme.appColorSchemeBlue
import ui.theme.appColorSchemeDark
import ui.theme.appColorSchemeLight
import uikit.base.BaseFragment

// True while this fragment is the top one; screens use it to react to being covered by (and returning
// from) another fragment, which the fragment lifecycle can't tell them because navigation only adds.
val LocalIsTopFragment = staticCompositionLocalOf { true }

abstract class ComposableFragment : BaseFragment(R.layout.fragment_compose_host) {

    val settings: SettingsRepository by inject()

    private val Context.uiMode: Int
        get() = resources.configuration.uiMode

    private val Context.isDarkMode: Boolean
        get() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    @get:Composable
    private val theme: AppColorScheme
        get() {
            return when(settings.theme.key) {
                "blue" -> appColorSchemeBlue()
                "dark" -> appColorSchemeDark()
                "light" -> appColorSchemeLight()
                else -> if (requireContext().isDarkMode) appColorSchemeBlue() else appColorSchemeLight()
            }
        }

    private var isTopFragment by mutableStateOf(true)

    private var fragmentLifecycleCallbacksHost: FragmentManager? = null

    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) {
            updateTopFragmentState()
        }

        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            updateTopFragmentState()
        }

        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
            updateTopFragmentState()
        }

        override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
            updateTopFragmentState()
        }
    }

    private fun updateTopFragmentState() {
        if (!isAdded) return
        isTopFragment = parentFragmentManager.fragments.lastOrNull() == this
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fm = parentFragmentManager
        fm.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, false)
        fragmentLifecycleCallbacksHost = fm
        updateTopFragmentState()
    }

    override fun onDestroyView() {
        fragmentLifecycleCallbacksHost?.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks)
        fragmentLifecycleCallbacksHost = null
        super.onDestroyView()
    }

    fun setContent(content: @Composable () -> Unit) {
        view?.findViewById<ComposeView>(R.id.compose_view)?.setContent {
            MoonTheme(colorScheme = theme) {
                val parent = LocalNavigationEventDispatcherOwner.current
                if (parent == null) {
                    CompositionLocalProvider(LocalIsTopFragment provides isTopFragment) {
                        content()
                    }
                } else {
                    val localDispatcherOwner = rememberNavigationEventDispatcherOwner(
                        enabled = isTopFragment,
                        parent = parent,
                    )
                    CompositionLocalProvider(
                        LocalIsTopFragment provides isTopFragment,
                        LocalNavigationEventDispatcherOwner provides localDispatcherOwner,
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
