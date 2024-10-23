plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.dapps")
}


dependencies {
    implementation(Dependence.TON.tvm)
    implementation(Dependence.TON.crypto)
    implementation(Dependence.TON.tlb)
    implementation(Dependence.TON.blockTlb)
    implementation(Dependence.TON.tonapiTl)
    implementation(Dependence.TON.contract)
    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.Data.rn))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.sqlite))
    implementation(project(Dependence.Lib.security))
    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.base64))
}