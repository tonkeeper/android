package com.tonapps.tonkeeper.ui.base

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.tonapps.wallet.data.account.entities.WalletEntity

class WalletFragmentFactory: FragmentFactory() {

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        val fragmentClass = loadFragmentClass(classLoader, className)
        val constructors = fragmentClass.constructors
        if (constructors.size > 1) {
            throw IllegalStateException("Fragment class should have only one constructor")
        }
        val constructor = constructors.first()
        val parameters = constructor.parameterTypes
        if (parameters.size > 1) {
            throw IllegalStateException("Fragment class should have only one constructor with one parameter")
        } else if (parameters.isEmpty()) {
            return fragmentClass.getConstructor().newInstance()
        }
        val parameter = parameters.first()
        if (parameter == WalletEntity::class.java) {
            return fragmentClass.getDeclaredConstructor(WalletEntity::class.java).newInstance(WalletEntity.EMPTY)
        }
        return super.instantiate(classLoader, className)
    }
}