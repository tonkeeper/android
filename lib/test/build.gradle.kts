plugins {
    id("target.android.library")
}

android {
    // Everything here lives in testFixtures, so the only way to depend on it is
    // `testImplementation(testFixtures(projects.lib.test))`. A production `implementation` of this
    // project resolves to an empty main artifact instead of quietly pulling JUnit into the app.
    testFixtures {
        enable = true
    }
}

dependencies {
    testFixturesApi(libs.junit)
    testFixturesApi(libs.kotlinx.coroutines.android)
    testFixturesApi(libs.kotlinx.coroutines.test)

    testFixturesImplementation(projects.kmp.async)
}
