package com.tonapps.wallet.testing

import android.app.Application
import android.content.SharedPreferences
import com.tonapps.core.flags.FeatureKey
import com.tonapps.core.flags.FeatureManager
import com.tonapps.core.flags.RemoteConfigProvider
import com.tonapps.extensions.AppContext
import com.trustwallet.core.WalletCoreLibLoader
import io.mockk.every
import io.mockk.mockk
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * The globals a feature reaches for before it has a screen, set up the way the app sets them up at
 * startup: the chainkit native lib, [AppContext], and [FeatureManager] backed by [features].
 *
 * The Context is a mock with just enough behaviour for that: a package name (asset image urls are
 * built from it), preferences that never override a flag, and strings that resolve to their name.
 */
class AndroidGlobalsRule : TestWatcher() {

    val features = TestRemoteConfig()

    val context: Application = mockAndroidContext()

    override fun starting(description: Description) {
        // The jvm artifact unpacks its own native lib from the jar; nothing loads it for us.
        WalletCoreLibLoader.loadLibrary()

        AppContext.initialize(context)
        FeatureManager.initialize(context, features)
    }

    override fun finished(description: Description) {
        features.clear()
    }
}

/** Feature flags a test can flip at any point, in place of Firebase's remote config. */
class TestRemoteConfig : RemoteConfigProvider {

    private val values = mutableMapOf<String, Boolean>()

    fun set(feature: FeatureKey, isEnabled: Boolean) = apply {
        values[feature.featureKey] = isEnabled
    }

    fun clear() {
        values.clear()
    }

    override fun isFeatureEnabled(feature: FeatureKey): Boolean = values[feature.featureKey] ?: false

    override fun getFeatureValue(feature: FeatureKey, default: String): String = default

    override fun optFeatureValue(feature: FeatureKey): String? = null

    override fun isRemoteResolved(feature: FeatureKey): Boolean = feature.featureKey in values
}

/** A Context for code that only needs one to read preferences, strings and its own package name. */
fun mockAndroidContext(packageName: String = "com.tonapps.test"): Application {
    val preferences = mockk<SharedPreferences>()
    every { preferences.getBoolean(any(), any()) } answers { secondArg() }
    every { preferences.getString(any(), any()) } answers { secondArg() }
    every { preferences.contains(any()) } returns false

    val context = mockk<Application>(relaxed = true)
    every { context.packageName } returns packageName
    every { context.applicationContext } returns context
    every { context.getSharedPreferences(any(), any()) } returns preferences
    // The vararg overload is a method of its own: without it a formatted string falls through to
    // the relaxed mock and comes back empty instead of failing.
    every { context.getString(any()) } answers { "string:${firstArg<Int>()}" }
    every { context.getString(any(), *anyVararg()) } answers { "string:${firstArg<Int>()}" }
    return context
}
