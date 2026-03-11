/**
 * DEPRECATED: This module is deprecated.
 * Please migrate to specific modules:
 * - tonapi:core (infrastructure, serializers)
 * - tonapi:tonkeeper (Tonkeeper Tonapi API)
 * - tonapi:battery (Battery API)
 * - tonapi:legacy (all APIs - temporary compatibility layer)
 */

plugins {
    id("target.android.library")
    kotlin("plugin.serialization")
}

dependencies {
    // Redirect to legacy module for backward compatibility
    api(projects.tonapi.legacy)
}