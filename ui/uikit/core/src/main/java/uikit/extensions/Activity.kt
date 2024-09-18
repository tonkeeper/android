package uikit.extensions

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import uikit.base.BaseFragment
import uikit.navigation.NavigationActivity
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

val FragmentActivity.primaryFragment: Fragment?
    get() = supportFragmentManager.primaryNavigationFragment

suspend fun NavigationActivity.addForResult(
    fragment: BaseFragment
): Bundle = suspendCancellableCoroutine { continuation ->
    val isCompleted = AtomicBoolean(false)

    addForResult(fragment) { result ->
        if (continuation.isActive && isCompleted.compareAndSet(false, true)) {
            continuation.resume(result)
        }
    }
    continuation.invokeOnCancellation {
        runOnUiThread {
            fragment.finish()
        }
    }
}