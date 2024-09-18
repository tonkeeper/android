plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

android {
    namespace = Build.namespacePrefix("wallet.data.tonconnect")
}

dependencies {
    implementation(Dependence.ton)
    implementation(Dependence.Squareup.okhttp)

    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.Data.account))
    implementation(project(Dependence.Wallet.Data.rn))

    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.sqlite))
    implementation(project(Dependence.Lib.security))
    implementation(project(Dependence.Lib.blockchain))
}
