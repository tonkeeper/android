package uikit.extensions

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

val FragmentActivity.primaryFragment: Fragment?
    get() = supportFragmentManager.primaryNavigationFragment