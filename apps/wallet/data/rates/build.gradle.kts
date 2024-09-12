plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.rates")
}

dependencies {
    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Module.tonApi))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.icu))
}

