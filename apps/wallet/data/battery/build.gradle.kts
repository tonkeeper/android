plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.battery")
}

dependencies {
    implementation(Dependence.Squareup.moshi)
    implementation(Dependence.Squareup.moshiAdapters)
    implementation(Dependence.Squareup.okhttp)

    implementation(project(Dependence.Module.tonApi))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.icu))
    implementation(project(Dependence.Lib.security))
}
