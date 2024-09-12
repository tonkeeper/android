plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
    kotlin("plugin.serialization") version "2.0.0"
}

android {
    namespace = Build.namespacePrefix("wallet.data.account")
}

dependencies {
    implementation(Dependence.KotlinX.serializationJSON)
    implementation(Dependence.KotlinX.coroutines)
    implementation(Dependence.Koin.core)
    implementation(Dependence.ton)
    implementation(project(Dependence.Module.tonApi))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.Data.rn))
    implementation(project(Dependence.Wallet.Data.rates))
    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Lib.security))
    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.sqlite))
    implementation(project(Dependence.Lib.ledger))
}
