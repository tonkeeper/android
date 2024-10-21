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

    api(Dependence.TON.tvm)
    api(Dependence.TON.crypto)
    api(Dependence.TON.tlb)
    api(Dependence.TON.blockTlb)
    api(Dependence.TON.tonapiTl)
    api(Dependence.TON.contract)
}
