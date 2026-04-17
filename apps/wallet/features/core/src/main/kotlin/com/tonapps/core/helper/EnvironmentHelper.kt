package com.tonapps.core.helper

class EnvironmentHelper(
    private val delegate: Delegate
) {
    interface Delegate {
        fun deviceCountry(): String
        fun storeCountry(): String?
        fun simCountry(): String?
        fun timezone(): String?
        fun isVpnActive(): Boolean
    }

    fun deviceCountry(): String = delegate.deviceCountry()
    fun storeCountry(): String? = delegate.storeCountry()
    fun simCountry(): String? = delegate.simCountry()
    fun timezone(): String? = delegate.timezone()
    fun isVpnActive(): Boolean = delegate.isVpnActive()
}
