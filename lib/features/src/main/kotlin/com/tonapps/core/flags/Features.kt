package com.tonapps.core.flags

interface FeatureKey {
    val featureKey: String
}

abstract class Features<T> {

    abstract val key: FeatureKey
    protected abstract fun provide(): T

    open val value: T by lazy { provide() }
    open val isEnabled: Boolean get() = FeatureManager.isEnabled(key)
    open val isOverridden: Boolean get() = FeatureManager.isOverridden(key)
    open val isDisabled: Boolean get() = !isEnabled

    protected fun getValue(default: String = ""): String {
        return FeatureManager.getValue(key, default)
    }

    protected fun optValue(): String? {
        return FeatureManager.optValue(key)
    }
}


