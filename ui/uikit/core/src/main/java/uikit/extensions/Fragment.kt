package uikit.extensions

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

fun Fragment.withAnimation(duration: Long = 120, block: () -> Unit) {
    view?.withAnimation(duration, block)
}

fun Fragment.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
}
