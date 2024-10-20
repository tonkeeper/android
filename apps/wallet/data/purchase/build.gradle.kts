plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

android {
    namespace = Build.namespacePrefix("wallet.data.purchase")
}

dependencies {
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Lib.extensions))

    api(Dependence.ton)
}
