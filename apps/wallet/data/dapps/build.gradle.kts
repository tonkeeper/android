plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.dapps")
}


dependencies {
    implementation(Dependence.ton)
    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.Data.rn))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.sqlite))
    implementation(project(Dependence.Lib.security))
    implementation(project(Dependence.Lib.network))
}