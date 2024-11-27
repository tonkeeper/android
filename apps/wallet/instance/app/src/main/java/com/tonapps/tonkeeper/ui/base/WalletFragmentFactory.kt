package com.tonapps.tonkeeper.ui.base

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.wallet.data.account.entities.WalletEntity

class WalletFragmentFactory: FragmentFactory() {

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        try {
            val fragmentClass = loadFragmentClass(classLoader, className)
            val constructors = fragmentClass.constructors
            val constructor = constructors.first()
            val parameters = constructor.parameterTypes
            if (parameters.isEmpty()) {
                try {
                    return fragmentClass.getConstructor().newInstance()
                } catch (e: Throwable) {
                    val walletConstructor = fragmentClass.getDeclaredConstructor(WalletEntity::class.java)
                    return walletConstructor.newInstance(WalletEntity.EMPTY)
                }
            }
            val parameter = parameters.first()
            if (parameter == WalletEntity::class.java) {
                return fragmentClass.getDeclaredConstructor(WalletEntity::class.java).newInstance(WalletEntity.EMPTY)
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        return super.instantiate(classLoader, className)
    }
}