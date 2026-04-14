package com.tonapps.core.flags

interface FeatureKey {
    val featureKey: String
}

abstract class Features<T> {

    abstract val key: FeatureKey
    protected abstract fun provide(): T

    val value: T by lazy { provide() }
    val isEnabled: Boolean by lazy { FeatureManager.isEnabled(key) }
    val isDisabled: Boolean get() = !isEnabled

    protected fun getValue(default: String = ""): String {
        return FeatureManager.getValue(key, default)
    }

    protected fun optValue(): String? {
        return FeatureManager.optValue(key)
    }
}


