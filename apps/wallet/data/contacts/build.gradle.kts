plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.contacts")
}

dependencies {
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.sqlite))
}
