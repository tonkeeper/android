package com.tonapps.core.screenrender

import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.tonapps.bus.screenrender.ScreenRenderId
import com.tonapps.bus.screenrender.ScreenRenderLifecycle
import com.tonapps.bus.screenrender.ScreenRenderTracker

/**
 * Reports a fragment's lifecycle to [ScreenRenderTracker] on behalf of [owner], the object that also reports the
 * screen's stages (a screen that can have several instances passes its ViewModel).
 *
 * Tabs are shown and hidden by MainScreen without new onStart / onResume, so a tab forwards [onHiddenChanged].
 */
class ScreenRenderFragmentLifecycle(
    private val fragment: Fragment,
    private val screen: ScreenRenderId,
    private val owner: () -> Any = { fragment },
) {

    private val tracker: ScreenRenderTracker
        get() = ScreenRenderTracker.shared

    init {
        fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (!fragment.isHidden) {
                    report(ScreenRenderLifecycle.WillShow)
                }
            }

            override fun onResume(owner: LifecycleOwner) {
                if (!fragment.isHidden) {
                    report(ScreenRenderLifecycle.Shown)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                tracker.abandon(screen, this@ScreenRenderFragmentLifecycle.owner())
            }
        })
    }

    fun onViewCreated() {
        report(ScreenRenderLifecycle.Created)
    }

    fun onHiddenChanged(hidden: Boolean) {
        if (hidden) {
            tracker.abandon(screen, owner())
        } else {
            report(ScreenRenderLifecycle.WillShow)
            fragment.view?.post { report(ScreenRenderLifecycle.Shown) } ?: report(ScreenRenderLifecycle.Shown)
        }
    }

    private fun report(event: ScreenRenderLifecycle) {
        tracker.lifecycle(event, screen, owner())
    }
}
