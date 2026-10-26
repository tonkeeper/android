package com.tonapps.core

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.tonapps.apps.wallet.features.core.R
import com.tonapps.wallet.data.settings.SettingsRepository
import org.koin.android.ext.android.inject
import ui.platformNestedScrollInterop
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

    // Bridges swipes on non-scrollable areas of a Compose bottom sheet to the classic
    // BottomSheetBehavior around it. The behavior refuses raw drags that start over the
    // ComposeView (it treats it as a nested-scrolling child), so without this a sheet whose
    // content isn't scrollable under the finger (headers, empty states) can't be swiped away.
    // The zero-consuming scrollable turns such drags into nested-scroll deltas and the interop
    // connection forwards them to the behavior.
    @Composable
    private fun SheetDragBridge(fillSheet: Boolean, content: @Composable () -> Unit) {
        // BottomSheet containers are full-height, so the bridge must cover the whole sheet
        // (drags on the empty area below short content still dismiss). Modal sheets are
        // measured wrap_content — forcing full size there would blow the sheet up to the
        // whole screen, so the Box wraps the real content size instead.
        Box(
            modifier = (if (fillSheet) Modifier.fillMaxSize() else Modifier)
                .platformNestedScrollInterop()
                .scrollable(
                    state = rememberScrollableState { 0f },
                    orientation = Orientation.Vertical,
                ),
        ) {
            content()
        }
    }

    fun setContent(content: @Composable () -> Unit) {
        val isBottomSheet = this is BottomSheet
        val isSheet = isBottomSheet || this is Modal
        val wrappedContent: @Composable () -> Unit = if (isSheet) {
            { SheetDragBridge(fillSheet = isBottomSheet, content = content) }
        } else {
            content
        }
        view?.findViewById<ComposeView>(R.id.compose_view)?.setContent {
            MoonTheme(colorScheme = theme) {
                val parent = LocalNavigationEventDispatcherOwner.current
                if (parent == null) {
                    CompositionLocalProvider(LocalIsTopFragment provides isTopFragment) {
                        wrappedContent()
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
                        wrappedContent()
                    }
                }
            }
        }
    }
}
