plugins {
    id("target.android.library")
}

android {
    // Same reason as lib:test — mockk must not be reachable from a production configuration.
    testFixtures {
        enable = true
    }
}

dependencies {
    testFixturesApi(testFixtures(projects.lib.test))
    testFixturesApi(projects.apps.wallet.data.multichain.wallet)
    testFixturesApi(libs.mockk)

    testFixturesImplementation(projects.lib.extensions)
    testFixturesImplementation(projects.lib.features)
    testFixturesImplementation(libs.chainkit.models)
    testFixturesImplementation(libs.kotlin.bignum)

    // Only a jvm unit test has this artifact; instrumented consumers get the native lib from the
    // aar instead, so it must not leak into their runtime.
    testFixturesCompileOnly(libs.wallet.core.jvm)
}
