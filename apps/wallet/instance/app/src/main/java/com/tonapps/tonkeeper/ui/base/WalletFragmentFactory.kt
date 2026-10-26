package com.tonapps.tonkeeper.ui.base

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.model.legacy.WalletEntity
import java.lang.reflect.Constructor

class WalletFragmentFactory: FragmentFactory() {

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        try {
            val fragmentClass = loadFragmentClass(classLoader, className)
            fragmentClass.findConstructor()?.let {
                return it.newInstance()
            }
            fragmentClass.findConstructor(WalletEntity::class.java)?.let {
                return it.newInstance(WalletEntity.EMPTY)
            }
            return super.instantiate(classLoader, className)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            return EmptyFragment()
        }
    }

    private fun Class<out Fragment>.findConstructor(vararg parameterTypes: Class<*>): Constructor<out Fragment>? {
        val constructor = declaredConstructors.firstOrNull { it.parameterTypes.contentEquals(parameterTypes) } ?: return null
        constructor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return constructor as Constructor<out Fragment>
    }

    class EmptyFragment: Fragment()
}
