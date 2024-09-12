plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.browser")
}

dependencies {
    implementation(Dependence.Squareup.okhttp)

    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.Data.account))

    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.extensions))
}

