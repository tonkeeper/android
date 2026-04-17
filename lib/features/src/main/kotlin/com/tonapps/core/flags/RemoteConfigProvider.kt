package com.tonapps.core.flags

interface RemoteConfigProvider {
    fun isFeatureEnabled(feature: FeatureKey): Boolean
    fun getFeatureValue(feature: FeatureKey, default: String): String
    fun optFeatureValue(feature: FeatureKey): String?
}
