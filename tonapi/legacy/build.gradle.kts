plugins {
    id("target.android.library")
    kotlin("plugin.serialization")
}

dependencies {
    // Expose all APIs for backward compatibility
    api(projects.tonapi.core)
    api(projects.tonapi.tonkeeper)
    api(projects.tonapi.battery)
}
